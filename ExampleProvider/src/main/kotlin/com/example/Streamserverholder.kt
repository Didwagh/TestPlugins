package com.example

object StreamServerHolder {
    private const val PORT = 38471

    private var server: LocalStreamServer? = null
    private var currentKey: String? = null

    /**
     * Ensures TDLib is logged in, resolves the file for chatId/messageId,
     * and (re)starts the local server pointing at it. Safe to call every
     * time the user hits play — it's a no-op if already serving this file.
     * Returns the port the server is listening on.
     */
    suspend fun ensureStarted(chatId: Long, messageId: Long): Int {
        TelegramClient.init()
        TelegramClient.awaitReady()

        val key = "$chatId:$messageId"
        if (currentKey == key && server != null) return PORT

        server?.stop()

        val file = TelegramFileResolver.resolve(chatId, messageId)
        val fileSizeLong = file.size.toLong()
        val bridge = ChunkBridge(file.id, fileSizeLong)

        server = LocalStreamServer(bridge, fileSizeLong, PORT).also {
            it.start(NanoHTTPDStartTimeoutMillis, false)
        }
        currentKey = key
        return PORT
    }

    fun stop() {
        server?.stop()
        server = null
        currentKey = null
    }

    private const val NanoHTTPDStartTimeoutMillis = 5000
}