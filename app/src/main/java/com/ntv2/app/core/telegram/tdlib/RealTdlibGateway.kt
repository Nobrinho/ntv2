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
import com.ntv2.app.core.telegram.media.TdlibMediaGateway
import com.ntv2.app.core.telegram.media.TdlibPlaybackFileState
import com.ntv2.app.core.telegram.media.TdlibPlaybackGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var client: Client? = null
    private val auth = MutableStateFlow<TdAuthorizationState>(TdAuthorizationState.Unknown)
    // Acessado pela thread de callback do TDLib (handleUpdate) e por corrotinas.
    private val fileStates = java.util.concurrent.ConcurrentHashMap<Int, MutableStateFlow<TdlibPlaybackFileState>>()

    override val authorizationState: Flow<TdAuthorizationState> = auth.asStateFlow()

    override suspend fun initialize() {
        ensureConfigured()
        ensureClient()
        when (val result = send(TdApi.GetAuthorizationState())) {
            is TdApi.AuthorizationState -> mapAuthorizationState(result)
            is TdApi.Error -> auth.value = TdAuthorizationState.Error(result.message)
        }
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
        send(TdApi.LogOut())
    }

    override suspend fun close() {
        if (!config.enabled) return
        send(TdApi.Close())
    }

    override suspend fun listChats(limit: Int): List<TelegramChatSummary> {
        ensureConfigured()
        val chats = send(TdApi.GetChats(TdApi.ChatListMain(), limit))
        if (chats !is TdApi.Chats) return emptyList()

        val result = mutableListOf<TelegramChatSummary>()
        chats.chatIds.forEach { chatId ->
            val chat = send(TdApi.GetChat(chatId)) as? TdApi.Chat ?: return@forEach
            result += mapChat(chat)
            if (result.size >= limit) return result
        }
        return result
    }

    override suspend fun listVideoMessages(chatId: Long, limit: Int): List<TelegramVideoMessage> {
        ensureConfigured()
        val result = send(
            TdApi.SearchChatMessages(
                chatId,
                null,
                "",
                null,
                0L,
                0,
                limit,
                TdApi.SearchMessagesFilterVideo()
            )
        )
        if (result !is TdApi.FoundChatMessages) return emptyList()

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
            if (items.size >= limit) return items
        }
        return items
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
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                auth.value = TdAuthorizationState.WaitTdlibParameters
                scope.launch {
                    runCatching {
                        send(setTdlibParameters())
                    }.onFailure {
                        auth.value = TdAuthorizationState.Error(it.message ?: "Failed to configure TDLib")
                    }
                }
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> auth.value = TdAuthorizationState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> auth.value = TdAuthorizationState.WaitCode
            is TdApi.AuthorizationStateWaitPassword -> auth.value = TdAuthorizationState.WaitPassword
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                auth.value = TdAuthorizationState.WaitQrCode(state.link)
            }

            is TdApi.AuthorizationStateReady -> {
                scope.launch {
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
            }

            is TdApi.AuthorizationStateLoggingOut -> auth.value = TdAuthorizationState.LoggingOut
            is TdApi.AuthorizationStateClosed -> auth.value = TdAuthorizationState.Closed()
            is TdApi.AuthorizationStateClosing -> auth.value = TdAuthorizationState.Closed("Closing session")
            else -> Unit
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
