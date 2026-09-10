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
                .map { chatId -> async { send(TdApi.GetChat(chatId)) as? TdApi.Chat } }
                .awaitAll()
                .filterNotNull()
                .map { mapChat(it) }
        }
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

        val items = mutableListOf<TelegramVideoMessage>()
        result.messages.orEmpty().forEach { msg ->
            val content = msg.content as? TdApi.MessageVideo ?: return@forEach
            val video = content.video ?: return@forEach
            val tdFile = video.video ?: return@forEach
            items += TelegramVideoMessage(
                mediaId = "${msg.chatId}_${msg.id}",
                chatId = msg.chatId,
                messageId = msg.id,
                title = video.fileName.ifBlank { "Video ${msg.id}" },
                caption = content.caption?.text,
                fileName = video.fileName.ifBlank { null },
                durationSeconds = video.duration,
                thumbnailPath = video.thumbnail?.file?.local?.path?.takeIf { it.isNotBlank() },
                fileId = tdFile.id
            )
        }
        // nextFromMessageId=0 => fim (doc TDLib).
        return TelegramVideoPage(videos = items, nextFromMessageId = result.nextFromMessageId)
    }

    override suspend fun openFile(fileId: Int): TdlibPlaybackFileState {
        ensureConfigured()
        val result = send(TdApi.DownloadFile(fileId, 2, 0L, 0L, false))
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

    override suspend fun deleteFile(fileId: Int) {
        if (!config.enabled) return
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
            // Cliente novo: precisará receber SetTdlibParameters uma vez.
            authReducer.onClientCreated()
            client = Client.create(
                { update -> handleUpdate(update) },
                { error -> auth.value = TdAuthorizationState.Error(error.message ?: "TDLib error") },
                { error -> auth.value = TdAuthorizationState.Error(error.message ?: "TDLib fatal error") }
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
                Client.ResultHandler { result ->
                    continuation.resume(result)
                },
                Client.ExceptionHandler { exception ->
                    continuation.resume(TdApi.Error(500, exception.message ?: "Unknown TDLib error"))
                }
            )
        }
    }
}
