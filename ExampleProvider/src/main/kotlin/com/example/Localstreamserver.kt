package com.example

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/**
 * Pulls bytes from ChunkBridge on demand as ExoPlayer/NanoHTTPD reads
 * from this InputStream. Blocking by design — InputStream.read() is a
 * blocking contract, and NanoHTTPD serves each request on its own
 * worker thread, so blocking here does not stall the rest of the app.
 */
private class ChunkBridgeInputStream(
    private val bridge: ChunkBridge,
    start: Long,
    private val length: Long
) : InputStream() {
    private var position = start
    private val endPosExclusive = start + length

    override fun read(): Int {
        val single = ByteArray(1)
        val n = read(single, 0, 1)
        return if (n <= 0) -1 else (single[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= endPosExclusive) return -1
        val remaining = (endPosExclusive - position).coerceAtMost(len.toLong()).toInt()
        val chunk = runBlocking { bridge.read(position, remaining.toLong()) }
        System.arraycopy(chunk, 0, b, off, chunk.size)
        position += chunk.size
        return chunk.size
    }
}

class LocalStreamServer(
    private val chunkBridge: ChunkBridge,
    private val fileSize: Long,
    port: Int
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/video") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }

        val rangeHeader = session.headers["range"]

        val (start, endInclusive) = if (rangeHeader != null) {
            parseRange(rangeHeader, fileSize)
        } else {
            0L to (fileSize - 1)
        }
        val length = endInclusive - start + 1

        val stream = ChunkBridgeInputStream(chunkBridge, start, length)
        val status = if (rangeHeader != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK

        val resp = newFixedLengthResponse(status, "video/mp4", stream, length)
        resp.addHeader("Accept-Ranges", "bytes")
        if (rangeHeader != null) {
            resp.addHeader("Content-Range", "bytes $start-$endInclusive/$fileSize")
        }
        return resp
    }

    private fun parseRange(header: String, fileSize: Long): Pair<Long, Long> {
        val spec = header.removePrefix("bytes=")
        val parts = spec.split("-")
        val start = parts[0].toLong()
        val end = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toLong() else fileSize - 1
        return start to end
    }
}