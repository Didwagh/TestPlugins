package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
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
 * IMPORTANT: If your confirmed-working test plugin imported TDLib classes
 * from a different package than "org.drinkless.tdlib", change the two
 * import lines above to match it exactly. Everything else in this file
 * is independent of that.
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

        client = Client.create({ update -> handleUpdate(update) }, null, null)

        client?.send(
            TdApi.SetTdlibParameters().apply {
                databaseDirectory = dbDir
                useMessageDatabase = true
                useSecretChats = false
                apiId = Config.API_ID
                apiHash = Config.API_HASH
                systemLanguageCode = "en"
                deviceModel = "Android"
                applicationVersion = "0.1"
            }
        ) { }
        // NOTE ON TDLIB VERSION DIFFERENCES:
        // Some TDLib Java binding versions removed the nested
        // TdApi.SetTdlibParameters object shape above and instead take
        // these same fields as flat constructor arguments, e.g.:
        //   TdApi.SetTdlibParameters(dbDir, false, true, false, ..., apiId, apiHash, ...)
        // If Android Studio shows a constructor mismatch error here,
        // check your test plugin's working init code and match its exact
        // SetTdlibParameters call shape — the field names/order are the
        // only thing that differs between versions.
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