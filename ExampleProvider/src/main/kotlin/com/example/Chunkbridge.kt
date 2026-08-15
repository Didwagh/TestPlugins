package com.example

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import java.io.RandomAccessFile
import kotlin.coroutines.resume

/**
 * One ChunkBridge per active file/stream.
 * read(position, length) always returns exactly `length` bytes (or throws).
 */
class ChunkBridge(
    private val fileId: Int,
    private val fileSize: Long,
    private val chunkSize: Long = 8L * 1024 * 1024
) {
    private var filePath: String? = null
    private val fetchedRanges = mutableListOf<LongRange>()
    private val fetchedLock = Any()

    suspend fun read(position: Long, length: Long): ByteArray {
        val chunkStart = (position / chunkSize) * chunkSize
        val chunkEndExclusive = minOf(
            ((position + length + chunkSize - 1) / chunkSize) * chunkSize,
            fileSize
        )
        val chunkLen = chunkEndExclusive - chunkStart

        ensureDownloaded(chunkStart, chunkLen)

        val path = filePath ?: error("No local file path after download completed — unexpected TDLib state")
        val raf = RandomAccessFile(path, "r")
        try {
            raf.seek(position)
            val buf = ByteArray(length.toInt())
            raf.readFully(buf)
            return buf
        } finally {
            raf.close()
        }
    }

    private suspend fun ensureDownloaded(offset: Long, limit: Long) {
        val alreadyHave = synchronized(fetchedLock) {
            fetchedRanges.any { offset >= it.first && offset + limit <= it.last }
        }
        if (alreadyHave) return

        val client = TelegramClient.rawClient()

        try {
            withTimeout(30_000) {
                suspendCancellableCoroutine<Unit> { cont ->
                    lateinit var listener: (TdApi.File) -> Unit
                    listener = { f ->
                        filePath = f.local.path
                        val downloadedTo = f.local.downloadOffset + f.local.downloadedPrefixSize
                        val covered = downloadedTo >= (offset + limit) || f.local.isDownloadingCompleted
                        if (covered && cont.isActive) {
                            synchronized(fetchedLock) { fetchedRanges.add(offset..(offset + limit)) }
                            TelegramClient.removeFileListener(fileId, listener)
                            cont.resume(Unit)
                        }
                    }
                    TelegramClient.addFileListener(fileId, listener)
                    cont.invokeOnCancellation { TelegramClient.removeFileListener(fileId, listener) }

                    // priority 32 = normal manual download priority (1-32 range in TDLib)
                    client.send(TdApi.DownloadFile(fileId, 32, offset, limit, false)) { }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw RuntimeException("Timed out waiting for Telegram to deliver bytes at offset=$offset limit=$limit", e)
        }
    }
}