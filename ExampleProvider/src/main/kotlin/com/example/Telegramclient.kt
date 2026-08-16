package com.example

import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.td.libcore.telegram.Client
import org.drinkless.td.libcore.telegram.TdApi
import java.io.File
import kotlin.coroutines.resume

/**
 * Login states the UI (LoginBottomSheet) reacts to.
 */
sealed class AuthState {
    object Idle : AuthState()
    object WaitPhone : AuthState()
    object WaitCode : AuthState()
    object WaitPassword : AuthState()
    object Ready : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Singleton wrapper around one TDLib Client for the whole plugin.
 * One login, reused for every stream.
 *
 * Verified against the actual tdlibx/td 1.6.0 source
 * (org.drinkless.td.libcore.telegram package) — not guessed.
 *
 * KNOWN LIMITATION of this specific TDLib build: DownloadFile's offset/limit
 * are 32-bit Int (this version predates TDLib's move to 64-bit int53 file
 * offsets). That caps addressable file position at ~2.14GB. For files
 * under that size this is fine; for your 2-3GB files, playback/seeking
 * past ~2.14GB will not work correctly with this artifact. Fine for
 * proving the MVP pipeline on a small test file — revisit before real use
 * on your full-size files.
 */
object TelegramClient {

    val authState = MutableStateFlow<AuthState>(AuthState.Idle)

    private var client: Client? = null
    private var pendingPhoneNumber: String? = null

    // fileId -> list of listeners waiting on TdApi.UpdateFile for that file
    private val fileListeners = HashMap<Int, MutableList<(TdApi.File) -> Unit>>()
    private val fileListenersLock = Any()

    fun init() {
        if (client != null) return
        require(AppContextHolder.isReady()) { "AppContextHolder.appContext not set — call from ExamplePlugin.load() first" }

        val dbDir = File(AppContextHolder.appContext.filesDir, "tdlib").absolutePath

        client = Client.create(
            { update -> handleUpdate(update) },
            null,
            null
        )

        val parameters = TdApi.TdlibParameters().apply {
            databaseDirectory = dbDir
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = Config.API_ID
            apiHash = Config.API_HASH
            systemLanguageCode = "en"
            deviceModel = "Android"
            systemVersion = Build.VERSION.RELEASE ?: "Unknown"
            applicationVersion = "0.1"
            enableStorageOptimizer = true
        }

        client?.send(TdApi.SetTdlibParameters(parameters)) { }
    }

    fun submitPhone(phone: String) {
        pendingPhoneNumber = phone
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { }
    }

    fun submitCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { }
    }

    fun submitPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { }
    }

    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthState(update.authorizationState)
            is TdApi.UpdateFile -> dispatchFileUpdate(update.file)
            else -> { /* ignore everything else */ }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> authState.value = AuthState.WaitPhone
            is TdApi.AuthorizationStateWaitCode -> authState.value = AuthState.WaitCode
            is TdApi.AuthorizationStateWaitPassword -> authState.value = AuthState.WaitPassword
            is TdApi.AuthorizationStateReady -> authState.value = AuthState.Ready
            is TdApi.AuthorizationStateClosed -> authState.value = AuthState.Error("Session closed")
            else -> { /* WaitTdlibParameters etc handled automatically by TDLib after SetTdlibParameters call */ }
        }
    }

    /** Suspends until login is fully READY (session persisted, or already logged in). */
    suspend fun awaitReady() {
        if (authState.value is AuthState.Ready) return
        suspendCancellableCoroutine<Unit> { cont ->
            val job = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                authState.collect { state ->
                    if (state is AuthState.Ready && cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
            cont.invokeOnCancellation { job.cancel() }
        }
    }

    fun rawClient(): Client = client ?: error("TelegramClient.init() not called yet")

    // ---- file update listener registry (used by ChunkBridge) ----

    fun addFileListener(fileId: Int, listener: (TdApi.File) -> Unit) {
        synchronized(fileListenersLock) {
            fileListeners.getOrPut(fileId) { mutableListOf() }.add(listener)
        }
    }

    fun removeFileListener(fileId: Int, listener: (TdApi.File) -> Unit) {
        synchronized(fileListenersLock) {
            fileListeners[fileId]?.remove(listener)
        }
    }

    private fun dispatchFileUpdate(file: TdApi.File) {
        val listeners = synchronized(fileListenersLock) {
            fileListeners[file.id]?.toList() ?: emptyList()
        }
        listeners.forEach { it(file) }
    }
}