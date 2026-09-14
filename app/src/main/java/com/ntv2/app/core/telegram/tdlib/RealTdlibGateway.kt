package com.ntv2.app.core.telegram.tdlib

import android.content.Context
import android.os.Build
import android.util.Log
import com.ntv2.app.BuildConfig
import com.ntv2.app.core.telegram.auth.TdAuthorizationState
import com.ntv2.app.core.telegram.auth.TdlibAuthGateway
import com.ntv2.app.core.telegram.channels.TelegramChatSummary
import com.ntv2.app.core.telegram.channels.TelegramChatType
import com.ntv2.app.core.telegram.channels.TdlibChannelsGateway
import com.ntv2.app.core.telegram.media.MovieMetadataParser
import com.ntv2.app.core.telegram.media.TelegramVideoMessage
import com.ntv2.app.core.telegram.media.TelegramVideoPage
import com.ntv2.app.core.telegram.media.TdlibMediaGateway
import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.Normalizer
import kotlin.coroutines.resume

data class TdlibRuntimeConfig(
    val enabled: Boolean,
    val apiId: Int,
    val apiHash: String
)

class RealTdlibGateway(
    context: Context,
    private val config: TdlibRuntimeConfig
) : TdlibAuthGateway, TdlibChannelsGateway, TdlibMediaGateway, TdlibPlaybackGateway {
    companion object {
        private const val TAG = "RealTdlibGateway"

        // Trecho inicial baixado ao abrir um arquivo (suficiente para começar; o resto vem pela
        // janela deslizante durante a reprodução).
        private const val INITIAL_DOWNLOAD_LIMIT_BYTES = 8L * 1024L * 1024L

        @Volatile
        private var nativeProbeDone = false

        @Volatile
        private var nativeAvailable = false

        @Volatile
        private var nativeFailureReason: String? = null

        fun nativeFailureReason(): String? = nativeFailureReason

        fun isNativeTdlibAvailable(): Boolean {
            if (nativeProbeDone) return nativeAvailable
            synchronized(this) {
                if (nativeProbeDone) return nativeAvailable
                if (!hasTdApiGitCommitHashField()) {
                    nativeFailureReason =
                        "Incompatibilidade TDLib Java/JNI: TdApi.GIT_COMMIT_HASH ausente/inválido."
                    nativeAvailable = false
                    nativeProbeDone = true
                    Log.e(TAG, nativeFailureReason ?: "TDLib Java/JNI mismatch")
                    return false
                }
                nativeAvailable = loadNative("tdjni") || loadNative("tdjsonjava")
                if (!nativeAvailable && nativeFailureReason == null) {
                    nativeFailureReason = "Biblioteca nativa TDLib não encontrada ou inválida."
                }
                if (!nativeAvailable) {
                    Log.e(TAG, nativeFailureReason ?: "Falha ao carregar TDLib nativa")
                }
                nativeProbeDone = true
                return nativeAvailable
            }
        }

        private fun loadNative(name: String): Boolean {
            return runCatching {
                System.loadLibrary(name)
                true
            }.getOrElse {
                nativeFailureReason = it.message ?: "Falha ao carregar biblioteca TDLib '$name'."
                Log.e(TAG, "Falha em System.loadLibrary('$name')", it)
                false
            }
        }

        private fun hasTdApiGitCommitHashField(): Boolean {
            return runCatching {
                val field = TdApi::class.java.getDeclaredField("GIT_COMMIT_HASH")
                field.type == String::class.java || field.type == Array<String>::class.java
            }.getOrDefault(false)
        }
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()

    @Volatile
    private var client: Client? = null
    private val auth = MutableStateFlow<TdAuthorizationState>(TdAuthorizationState.Unknown)
    // Acessado pela thread de callback do TDLib (handleUpdate) e por corrotinas.
    private val fileStates = java.util.concurrent.ConcurrentHashMap<Int, MutableStateFlow<TdlibPlaybackFileState>>()

    override val authorizationState: Flow<TdAuthorizationState> = auth.asStateFlow()

    private val authReducer = TdlibAuthReducer()

    override suspend fun initialize() {
        // Apenas cria o cliente. O estado inicial (WaitTdlibParameters → Ready/WaitPhoneNumber)
        // chega pelo update (handleUpdate). NÃO chamamos GetAuthorizationState aqui: isso rodava
        // mapAuthorizationState em paralelo com o update e disparava SetTdlibParameters duas vezes,
        // resetando o TDLib (Ready → WaitTdlibParameters → travado).
        ensureConfigured()
    }

    override suspend fun requestQrCodeAuthentication() {
        if (!config.enabled) return
        send(TdApi.RequestQrCodeAuthentication(longArrayOf()))
    }

    override suspend fun setAuthenticationPhoneNumber(phoneNumber: String) {
        ensureConfigured()
        send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
    }

    override suspend fun checkAuthenticationCode(code: String) {
        ensureConfigured()
        send(TdApi.CheckAuthenticationCode(code))
    }

    override suspend fun checkAuthenticationPassword(password: String) {
        ensureConfigured()
        send(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun logout() {
        if (!config.enabled) return
        auth.value = TdAuthorizationState.LoggingOut
        runCatching { send(TdApi.LogOut()) }
        // O TDLib exige um cliente NOVO para autenticar após LogOut. Esperamos o fechamento
        // (LogOut → Closed), zeramos o cliente e criamos um novo, que fluirá para WaitPhoneNumber
        // via updates — momento em que a UI pede o QR. Sem isso, o re-login não gerava QR.
        withTimeoutOrNull(5_000L) { auth.first { it is TdAuthorizationState.Closed } }
        client = null
        // ensureClient() cria um cliente novo e já reseta o guard de parâmetros (onClientCreated).
        runCatching { ensureClient() }
    }

    override suspend fun close() {
        if (!config.enabled) return
        send(TdApi.Close())
    }

    override suspend fun listChats(limit: Int): List<TelegramChatSummary> {
        ensureConfigured()
        // GetChats sozinho pode vir vazio numa sessão nova: LoadChats popula a lista principal.
        loadMainChatList(limit)

        val chats = send(TdApi.GetChats(TdApi.ChatListMain(), limit))
        if (chats !is TdApi.Chats) return emptyList()

        // Resolve os chats em paralelo (antes: N+1 serial, ~1 round-trip por chat).
        return coroutineScope {
            chats.chatIds.take(limit)
                .map { chatId ->
                    async {
                        val chat = send(TdApi.GetChat(chatId)) as? TdApi.Chat ?: return@async null
                        val summary = mapChat(chat)
                        // Baixa a miniatura do avatar (arquivo pequeno) para termos um caminho
                        // local — sem isso photo.small.local.path fica vazio e nada é exibido.
                        summary.copy(avatarPath = resolveAvatarPath(chat) ?: summary.avatarPath)
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    /**
     * Garante um caminho local para o avatar (foto pequena) do chat. Se já estiver baixado,
     * reaproveita; senão dispara um download síncrono — a foto small tem poucos KB, então é
     * rápido e roda em paralelo por chat. Retorna null quando o chat não tem foto.
     */
    private suspend fun resolveAvatarPath(chat: TdApi.Chat): String? {
        val small = chat.photo?.small ?: return null
        small.local?.let { local ->
            if (local.isDownloadingCompleted && local.path.isNotBlank()) return local.path
        }
        val file = send(TdApi.DownloadFile(small.id, 16, 0L, 0L, true)) as? TdApi.File ?: return null
        return file.local?.path?.takeIf { it.isNotBlank() }
    }

    /**
     * Garante um caminho local para a miniatura do vídeo (arquivo pequeno). Reaproveita se já
     * baixada; senão baixa de forma síncrona (roda em paralelo por item). Null se não houver.
     */
    private suspend fun resolveThumbnailPath(video: TdApi.Video): String? {
        val thumb = video.thumbnail?.file ?: return null
        thumb.local?.let { local ->
            if (local.isDownloadingCompleted && local.path.isNotBlank()) return local.path
        }
        val file = send(TdApi.DownloadFile(thumb.id, 16, 0L, 0L, true)) as? TdApi.File ?: return null
        return file.local?.path?.takeIf { it.isNotBlank() }
    }

    /**
     * Anda para trás a partir do vídeo, PULANDO apenas outros vídeos (episódios irmãos), até a 1ª
     * FOTO com legenda de filme. Aceita esse pôster quando:
     *  - o título dele CASA com o nome do vídeo (filme A/B), OU
     *  - o vídeo faz parte da sequência de episódios sob ele (pulou ≥1 vídeo) — SÉRIE, OU
     *  - o nome do vídeo parece episódio ("Episódio/EP/Cap/Temporada/SxEy").
     * Para na 1ª mensagem que não seja vídeo nem foto-pôster (fronteira) → evita capa de outro post.
     * GetChatHistory pode voltar vazio na 1ª chamada enquanto carrega do servidor — 1 retry.
     */
    private suspend fun findMatchingPoster(chatId: Long, beforeMessageId: Long, videoName: String): TdApi.Message? {
        repeat(2) {
            val res = send(TdApi.GetChatHistory(chatId, beforeMessageId, 0, 12, false)) as? TdApi.Messages
            val msgs = res?.messages?.filterNotNull().orEmpty()
            if (msgs.isEmpty()) return@repeat // provável carregamento — tenta de novo
            var skipped = 0
            for (m in msgs) {
                when (val c = m.content) {
                    is TdApi.MessageVideo -> {
                        skipped++
                        if (skipped > 12) return null
                    }
                    is TdApi.MessageText -> {
                        // Formato rico: mensagem de texto (metadados) imediatamente antes do vídeo.
                        // É a fonte de metadados desse vídeo (1 texto por vídeo) — aceita direto.
                        val meta = MovieMetadataParser.parse(c.text?.text)
                        return if (meta.isRich) m else null
                    }
                    is TdApi.MessagePhoto -> {
                        val cap = c.caption?.text
                        val posterTitle = cap?.let {
                            MovieMetadataParser.parse(it).title
                                ?: it.lineSequence().map { l -> l.trim() }.firstOrNull { l -> l.isNotEmpty() }
                        } ?: return null // foto sem legenda de filme = fronteira
                        // Aceita: filme (título casa) OU episódio de série (nome do vídeo parece
                        // episódio). NÃO aceita só por "pulou vídeos" — evitava capa de outro post.
                        val accept = titlesMatch(videoName, posterTitle) || looksLikeEpisode(videoName)
                        return if (accept) m else null
                    }
                    else -> return null // fronteira (texto/serviço) — não é o mesmo bloco
                }
            }
            return null // só vídeos na janela, sem foto
        }
        return null
    }

    /** Heurística: o nome parece de episódio de série (número no início, "Episódio", "EP", "Cap"…). */
    private fun looksLikeEpisode(name: String): Boolean {
        // Número no início: "1. Ausência", "2) ...", "03 - ...".
        if (Regex("^\\s*\\d{1,3}\\s*[.)\\-]\\s").containsMatchIn(name)) return true
        val n = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return Regex("epis|\\bep\\b|\\bep\\.?\\s*\\d|\\bcap\\b|capitulo|temporada|\\bs\\d+\\s*e\\d+\\b|\\bt\\d+\\b").containsMatchIn(n)
    }

    /** Casa dois títulos por conjunto de tokens (ignora acentos, [MKV], stopwords e tags técnicas). */
    private fun titlesMatch(a: String, b: String): Boolean {
        val ta = titleTokens(a)
        val tb = titleTokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return false
        if (ta.all { it in tb } || tb.all { it in ta }) return true
        val inter = ta.intersect(tb).size.toDouble()
        val union = (ta + tb).size.toDouble()
        return union > 0 && inter / union >= 0.6
    }

    private val TITLE_STOPWORDS = setOf("de", "da", "do", "das", "dos", "e", "a", "o", "os", "as", "um", "uma", "the", "of")

    private fun titleTokens(s: String): Set<String> =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\[.*?\\]|\\(.*?\\)"), " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in TITLE_STOPWORDS && it !in VIDEO_TECH_TAGS }
            .toSet()

    /** Baixa o pôster (maior tamanho da foto) e retorna o caminho local; null se não houver. */
    private suspend fun resolvePosterPath(photo: TdApi.Photo): String? {
        val sizes = photo.sizes?.takeIf { it.isNotEmpty() } ?: return null
        // Escolhe a MENOR variante com largura suficiente para o card (evita baixar/decodificar o
        // pôster full-res). O Coil ainda reamostra para o tamanho exato do card.
        val size = sizes.filter { it.width >= 500 }.minByOrNull { it.width * it.height }
            ?: sizes.maxByOrNull { it.width * it.height }
            ?: return null
        val f = size.photo ?: return null
        f.local?.let { local ->
            if (local.isDownloadingCompleted && local.path.isNotBlank()) return local.path
        }
        val file = send(TdApi.DownloadFile(f.id, 16, 0L, 0L, true)) as? TdApi.File ?: return null
        return file.local?.path?.takeIf { it.isNotBlank() }
    }

    private suspend fun loadMainChatList(limit: Int) {
        // LoadChats carrega mais chats por vez e retorna Error 404 quando a lista acaba.
        // Loop limitado para não bloquear indefinidamente.
        val chatList = TdApi.ChatListMain()
        var iterations = (limit / 100) + 1
        while (iterations-- > 0) {
            if (send(TdApi.LoadChats(chatList, limit)) is TdApi.Error) break
        }
    }

    override suspend fun listVideoMessages(chatId: Long, fromMessageId: Long, limit: Int): TelegramVideoPage =
        searchVideos(chatId, query = "", fromMessageId = fromMessageId, limit = limit)

    override suspend fun searchVideoMessages(chatId: Long, query: String, fromMessageId: Long, limit: Int): TelegramVideoPage =
        searchVideos(chatId, query = query, fromMessageId = fromMessageId, limit = limit)

    private suspend fun searchVideos(chatId: Long, query: String, fromMessageId: Long, limit: Int): TelegramVideoPage {
        ensureConfigured()
        // Garante que o TDLib conheça o chat (logo após o login a lista de diálogos pode não ter
        // carregado e SearchChatMessages volta vazio). GetChat força o carregamento do chat.
        runCatching { send(TdApi.GetChat(chatId)) }
        val result = send(
            TdApi.SearchChatMessages(
                chatId,
                null,
                query,
                null,
                fromMessageId,
                0,
                limit,
                TdApi.SearchMessagesFilterVideo()
            )
        )
        if (result !is TdApi.FoundChatMessages) return TelegramVideoPage(emptyList(), 0L)

        // Monta os itens em paralelo: cada um baixa sua miniatura (arquivo pequeno) para termos
        // um caminho local — sem isso thumbnail.file.local.path fica vazio e o card fica cinza.
        val items = coroutineScope {
            result.messages.orEmpty().mapNotNull { msg ->
                val content = msg.content as? TdApi.MessageVideo ?: return@mapNotNull null
                val video = content.video ?: return@mapNotNull null
                val tdFile = video.video ?: return@mapNotNull null
                async {
                    val videoCaption = content.caption?.text
                    val videoMeta = MovieMetadataParser.parse(videoCaption)
                    // Nome do PRÓPRIO vídeo (fonte da verdade p/ casar com o pôster): título da
                    // legenda → fileName limpo → 1ª linha da legenda.
                    val videoName = videoMeta.title
                        ?: video.fileName.ifBlank { null }?.let { cleanDisplayName(it).ifBlank { it } }
                        ?: videoCaption?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

                    // Pareia com a mensagem anterior: TEXTO rico (canal próprio) OU foto-pôster
                    // (Polemic / séries).
                    val metaMsg = videoName?.let { findMatchingPoster(msg.chatId, msg.id, it) }
                    val photoContent = metaMsg?.content as? TdApi.MessagePhoto
                    val textContent = metaMsg?.content as? TdApi.MessageText
                    val metaCaption = photoContent?.caption?.text ?: textContent?.text?.text
                    val posterMeta = metaCaption?.let { MovieMetadataParser.parse(it) }
                    val richText = textContent != null && posterMeta?.isRich == true

                    // Episódio de série (só no modo foto): pareou com um pôster cujo título NÃO é o
                    // nome do vídeo → nome do episódio + sinopse do episódio, herdando pôster/metadados.
                    val isSeriesEpisode = !richText && posterMeta?.title != null && videoName != null &&
                        !titlesMatch(videoName, posterMeta.title!!)
                    val displayTitle = when {
                        richText -> posterMeta?.title ?: videoName ?: "Video ${msg.id}"
                        isSeriesEpisode -> videoName!!.trim()
                        else -> ((posterMeta ?: videoMeta).title ?: videoName)
                            ?.let { cleanDisplayName(it).ifBlank { it } } ?: "Video ${msg.id}"
                    }

                    val synopsis = if (isSeriesEpisode) {
                        videoMeta.synopsis ?: posterMeta?.synopsis
                    } else {
                        posterMeta?.synopsis ?: videoMeta.synopsis
                    }
                    val year = posterMeta?.year ?: videoMeta.year
                    val director = posterMeta?.director ?: videoMeta.director
                    val audio = posterMeta?.audio ?: videoMeta.audio
                    val genres = posterMeta?.genres ?: videoMeta.genres

                    // Pôster: URL do post rico; senão a foto do Telegram (Polemic). Fundo: só rico.
                    val photo = photoContent?.photo
                    val posterPath = posterMeta?.posterUrl ?: photo?.let { resolvePosterPath(it) }
                    val backdropPath = posterMeta?.backdropUrl

                    // Proporção real só é conhecida para a foto do Telegram; URL → desconhecida (0).
                    val posterAspect = photo?.sizes
                        ?.firstOrNull { it.width > 0 && it.height > 0 }
                        ?.let { it.width.toFloat() / it.height }
                    val coverAspect = when {
                        posterMeta?.posterUrl != null -> 0f
                        posterAspect != null -> posterAspect
                        video.height > 0 -> video.width.toFloat() / video.height
                        else -> 0f
                    }

                    TelegramVideoMessage(
                        mediaId = "${msg.chatId}_${msg.id}",
                        chatId = msg.chatId,
                        messageId = msg.id,
                        title = displayTitle,
                        caption = metaCaption ?: videoCaption,
                        fileName = video.fileName.ifBlank { null },
                        durationSeconds = video.duration,
                        thumbnailPath = resolveThumbnailPath(video),
                        fileId = tdFile.id,
                        width = video.width,
                        height = video.height,
                        coverAspectRatio = coverAspect,
                        posterPath = posterPath,
                        synopsis = synopsis,
                        year = year,
                        director = director,
                        audio = audio,
                        genres = genres,
                        originalTitle = posterMeta?.originalTitle,
                        backdropPath = backdropPath,
                        rating = posterMeta?.rating,
                        ageRating = posterMeta?.ageRating,
                        country = posterMeta?.country,
                        quality = posterMeta?.quality,
                        studio = posterMeta?.studio,
                        cast = posterMeta?.cast ?: emptyList(),
                        trailerUrl = posterMeta?.trailerUrl,
                        tmdbId = posterMeta?.tmdbId,
                        category = posterMeta?.category,
                        collection = posterMeta?.collection,
                        tags = posterMeta?.tags
                    )
                }
            }.awaitAll()
        }
        // nextFromMessageId=0 => fim (doc TDLib).
        return TelegramVideoPage(videos = items, nextFromMessageId = result.nextFromMessageId)
    }

    override suspend fun openFile(fileId: Int): TdlibPlaybackFileState {
        ensureConfigured()
        // Inicia o download de um trecho inicial (cria o arquivo local e chega ao mínimo de
        // playback) em prioridade alta. limit=0 baixava o arquivo INTEIRO (enchia o disco →
        // abort do TDLib); a janela deslizante continua durante a reprodução.
        val result = send(TdApi.DownloadFile(fileId, 32, 0L, INITIAL_DOWNLOAD_LIMIT_BYTES, false))
        val file = (result as? TdApi.File) ?: throw IllegalStateException("Invalid TDLib file for fileId=$fileId")
        return mapFileState(file).also { state ->
            fileStates.computeIfAbsent(fileId) { MutableStateFlow(state) }.value = state
        }
    }

    override fun observeFile(fileId: Int): Flow<TdlibPlaybackFileState> {
        return fileStates.computeIfAbsent(fileId) {
            MutableStateFlow(
                TdlibPlaybackFileState(
                    fileId = fileId,
                    localPath = "",
                    downloadedBytes = 0L,
                    expectedBytes = 0L,
                    isDownloadComplete = false
                )
            )
        }.asStateFlow()
    }

    override suspend fun requestChunk(fileId: Int, offsetBytes: Long, lengthBytes: Long, priority: Int) {
        ensureConfigured()
        send(TdApi.DownloadFile(fileId, priority.coerceIn(1, 32), offsetBytes, lengthBytes, false))
    }

    override suspend fun cancelFile(fileId: Int) {
        if (!config.enabled) return
        send(TdApi.CancelDownloadFile(fileId, false))
    }

    override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long {
        if (!config.enabled) return 0L
        val result = send(TdApi.GetFileDownloadedPrefixSize(fileId, offset))
        return (result as? TdApi.FileDownloadedPrefixSize)?.size ?: 0L
    }

    override suspend fun deleteFile(fileId: Int) {
        if (!config.enabled) return
        // Cancela o download ativo ANTES de deletar. Sem isso, o download seguia ativo após sair
        // do vídeo e ocupava os slots do TDLib, impedindo o próximo vídeo de baixar (downloaded=0).
        runCatching { send(TdApi.CancelDownloadFile(fileId, false)) }
        runCatching { send(TdApi.DeleteFile(fileId)) }
        fileStates.remove(fileId)
    }

    private suspend fun ensureClient() {
        if (client != null) return
        initMutex.withLock {
            if (client != null) return
            if (!isNativeTdlibAvailable()) {
                auth.value = TdAuthorizationState.Error(
                    nativeFailureReason() ?: "Biblioteca nativa TDLib indisponível no dispositivo"
                )
                Log.e(TAG, "Inicialização TDLib bloqueada: ${nativeFailureReason()}")
                throw IllegalStateException("TDLib native library unavailable")
            }
            // Reduz o log nativo do TDLib (default é altíssimo: milhares de linhas/s no download,
            // custo de I/O e ruído). 1 = apenas erros.
            runCatching { Client.execute(TdApi.SetLogVerbosityLevel(1)) }
            // Cliente novo: precisará receber SetTdlibParameters uma vez.
            authReducer.onClientCreated()
            client = Client.create(
                { update -> handleUpdate(update) },
                { error ->
                    Log.e(TAG, "TDLib update exception", error)
                    auth.value = TdAuthorizationState.Error(error.message ?: "TDLib error")
                },
                { error ->
                    Log.e(TAG, "TDLib fatal error", error)
                    auth.value = TdAuthorizationState.Error(error.message ?: "TDLib fatal error")
                }
            )
        }
    }

    private suspend fun ensureConfigured() {
        if (!config.enabled) {
            auth.value = TdAuthorizationState.Error("TDLib real disabled: configure telegramApiId/telegramApiHash")
            throw IllegalStateException("TDLib real disabled")
        }
        ensureClient()
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> mapAuthorizationState(update.authorizationState)
            is TdApi.UpdateFile -> {
                val mapped = mapFileState(update.file)
                fileStates.computeIfAbsent(mapped.fileId) { MutableStateFlow(mapped) }.value = mapped
            }
        }
    }

    private fun mapAuthorizationState(state: TdApi.AuthorizationState) {
        val update = when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> TdlibAuthReducer.Update.WaitTdlibParameters
            is TdApi.AuthorizationStateWaitPhoneNumber -> TdlibAuthReducer.Update.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> TdlibAuthReducer.Update.WaitCode
            is TdApi.AuthorizationStateWaitPassword -> TdlibAuthReducer.Update.WaitPassword
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation ->
                TdlibAuthReducer.Update.WaitOtherDeviceConfirmation(state.link)
            is TdApi.AuthorizationStateReady -> TdlibAuthReducer.Update.Ready
            is TdApi.AuthorizationStateLoggingOut -> TdlibAuthReducer.Update.LoggingOut
            is TdApi.AuthorizationStateClosed -> TdlibAuthReducer.Update.Closed(null)
            is TdApi.AuthorizationStateClosing -> TdlibAuthReducer.Update.Closing
            else -> return
        }

        val result = authReducer.reduce(update)
        result.state?.let { auth.value = it }
        result.effects.forEach { effect -> applyAuthEffect(effect) }
    }

    private fun applyAuthEffect(effect: TdlibAuthReducer.Effect) {
        when (effect) {
            TdlibAuthReducer.Effect.SendParameters -> scope.launch {
                runCatching {
                    send(setTdlibParameters())
                }.onFailure {
                    authReducer.onSendParametersFailed()
                    auth.value = TdAuthorizationState.Error(it.message ?: "Failed to configure TDLib")
                }
            }

            TdlibAuthReducer.Effect.FetchCurrentUser -> scope.launch {
                val me = send(TdApi.GetMe()) as? TdApi.User
                val display = me?.let {
                    listOf(it.firstName, it.lastName)
                        .filter { name -> name.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { null }
                }
                auth.value = TdAuthorizationState.Ready(
                    userId = me?.id ?: 0L,
                    displayName = display
                )
            }

            // TDLib fecha o cliente após LogOut/Close; zeramos a referência para que ensureClient
            // recrie um cliente novo no próximo login.
            TdlibAuthReducer.Effect.ClearClient -> {
                client = null
            }
        }
    }

    private fun setTdlibParameters(): TdApi.SetTdlibParameters {
        val filesRoot = File(appContext.filesDir, "tdlib")
        if (!filesRoot.exists()) filesRoot.mkdirs()
        val dbDir = File(filesRoot, "db").apply { mkdirs() }
        val mediaDir = File(filesRoot, "files").apply { mkdirs() }

        return TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = dbDir.absolutePath
            filesDirectory = mediaDir.absolutePath
            databaseEncryptionKey = byteArrayOf()
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = config.apiId
            apiHash = config.apiHash
            systemLanguageCode = "pt-BR"
            deviceModel = Build.MODEL ?: "AndroidTV"
            systemVersion = "Android ${Build.VERSION.RELEASE}"
            applicationVersion = BuildConfig.VERSION_NAME
        }
    }

    private fun mapChat(chat: TdApi.Chat): TelegramChatSummary {
        return TelegramChatSummary(
            id = chat.id,
            title = chat.title,
            type = when (val type = chat.type) {
                is TdApi.ChatTypePrivate -> TelegramChatType.Private
                is TdApi.ChatTypeBasicGroup -> TelegramChatType.Group
                is TdApi.ChatTypeSupergroup -> if (type.isChannel) TelegramChatType.Channel else TelegramChatType.Supergroup
                else -> TelegramChatType.Unknown
            },
            avatarPath = chat.photo?.small?.local?.path?.takeIf { it.isNotBlank() },
            canReadHistory = true
        )
    }

    private fun mapFileState(file: TdApi.File): TdlibPlaybackFileState {
        val localPath = file.local?.path.orEmpty()
        val downloaded = file.local?.downloadedSize ?: 0L
        val expected = when {
            file.expectedSize > 0L -> file.expectedSize
            file.size > 0L -> file.size
            else -> downloaded
        }
        return TdlibPlaybackFileState(
            fileId = file.id,
            localPath = localPath,
            downloadedBytes = downloaded,
            expectedBytes = expected,
            isDownloadComplete = file.local?.isDownloadingCompleted == true,
            downloadOffset = file.local?.downloadOffset ?: 0L,
            downloadedPrefixBytes = file.local?.downloadedPrefixSize ?: 0L
        )
    }

    private suspend fun send(function: TdApi.Function<out TdApi.Object>): TdApi.Object {
        val activeClient = client ?: throw IllegalStateException("TDLib client not initialized")
        return suspendCancellableCoroutine { continuation ->
            activeClient.send(
                function,
                Client.ResultHandler { result -> continuation.resume(result) },
                Client.ExceptionHandler { exception ->
                    continuation.resume(TdApi.Error(500, exception.message ?: "Unknown TDLib error"))
                }
            )
        }
    }
}

// Tags técnicas comuns em nomes de arquivo/legendas de vídeo (fonte, qualidade, codec, áudio).
private val VIDEO_TECH_TAGS: Set<String> = setOf(
    "hdcam", "cam", "ts", "tc", "hdts", "hdtc", "telesync", "telecine",
    "hdrip", "dvdrip", "dvdscr", "brrip", "bdrip", "bluray", "blu-ray",
    "webdl", "web-dl", "webrip", "web", "hdtv", "hdr", "sdr",
    "1080p", "720p", "480p", "2160p", "4k", "uhd", "fullhd",
    "x264", "x265", "h264", "h265", "hevc", "avc", "10bit",
    "aac", "ac3", "eac3", "dts", "mp3",
    "dual", "dualaudio", "remux", "nacional", "dublado", "dub", "legendado", "leg", "extended"
)

private val VIDEO_EXTENSION_REGEX =
    Regex("\\.(mp4|mkv|avi|mov|m4v|webm|ts|wmv|flv|mpg|mpeg)$", RegexOption.IGNORE_CASE)

/**
 * Limpa o nome exibido: remove a extensão de vídeo, troca separadores (._) por espaço e descarta
 * tokens técnicos (HDCAM, DUBLADO, 1080p, x264...). Mantém separadores como "-" e anos. Retorna
 * vazio só se sobrar nada — nesse caso o chamador cai de volta para o nome bruto.
 */
internal fun cleanDisplayName(raw: String): String {
    val noExt = VIDEO_EXTENSION_REGEX.replace(raw.trim(), "")
    val spaced = noExt.replace('_', ' ').replace('.', ' ')
    val kept = spaced
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .filterNot { token ->
            val normalized = token.trim('(', ')', '[', ']', '{', '}').lowercase()
            normalized in VIDEO_TECH_TAGS
        }
    return kept.joinToString(" ")
        .replace(Regex("\\(\\s*\\)|\\[\\s*\\]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('-', '|', '•', '_', ' ')
        .trim()
}
