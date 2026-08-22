package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class ExampleProvider : MainAPI() {

    private val defaultPort = 38471
    private val defaultChannelId = -1004374443616L

    override var name = "Telegram Vault"
    override var mainUrl = "http://127.0.0.1:$defaultPort"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    // Robust Base URL Builder (handles 192.168.0.194, http://..., with or without port)
    private fun getBaseUrl(): String {
        var host = getKey<String>(ExamplePlugin.PREF_HOST)?.trim() ?: "127.0.0.1"
        if (host.isBlank()) host = "127.0.0.1"

        // Strip http:// or https:// if manually typed
        if (host.startsWith("http://", ignoreCase = true)) {
            host = host.substring(7)
        } else if (host.startsWith("https://", ignoreCase = true)) {
            host = host.substring(8)
        }
        host = host.trimEnd('/')

        // Append port 38471 if user only typed an IP address
        if (!host.contains(":")) {
            host = "$host:$defaultPort"
        }

        return "http://$host"
    }

    private fun getChannelId(): String {
        val saved = getKey<String>(ExamplePlugin.PREF_CHANNEL_ID)?.trim()
        return if (!saved.isNullOrBlank()) saved else defaultChannelId.toString()
    }

    data class CatalogItem(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("total_size") val total_size: Long? = null,
        @JsonProperty("parts") val parts: List<PartItem>? = null
    )

    data class PartItem(
        @JsonProperty("original_name") val original_name: String? = null,
        @JsonProperty("size") val size: Long? = null,
        @JsonProperty("chat_id") val chat_id: Long? = null,
        @JsonProperty("message_id") val message_id: Long? = null
    )

    private suspend fun getCatalog(): List<CatalogItem> {
        val base = getBaseUrl()
        val channel = getChannelId()
        val url = "$base/catalog?channel_id=$channel"

        return try {
            val response = app.get(url, timeout = 15L).text
            parseJson<List<CatalogItem>>(response)
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyList()
        }
    }

    override val mainPage = mainPageOf(
        "catalog" to "Telegram Videos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val base = getBaseUrl()
        val catalog = getCatalog()
        val list = catalog.mapNotNull { item ->
            val part = item.parts?.firstOrNull() ?: return@mapNotNull null
            val chatId = part.chat_id ?: return@mapNotNull null
            val messageId = part.message_id ?: return@mapNotNull null

            val streamUrl = "$base/video?chat_id=$chatId&message_id=$messageId"
            val title = item.title?.ifBlank { null }
                ?: part.original_name?.ifBlank { null }
                ?: "Video ($messageId)"

            newMovieSearchResponse(
                name = title,
                url = streamUrl,
                type = TvType.Movie
            )
        }
        return newHomePageResponse(request.name, list, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val base = getBaseUrl()
        val catalog = getCatalog()
        return catalog
            .filter { it.title?.contains(query, ignoreCase = true) == true }
            .mapNotNull { item ->
                val part = item.parts?.firstOrNull() ?: return@mapNotNull null
                val chatId = part.chat_id ?: return@mapNotNull null
                val messageId = part.message_id ?: return@mapNotNull null

                val streamUrl = "$base/video?chat_id=$chatId&message_id=$messageId"
                val title = item.title?.ifBlank { null }
                    ?: part.original_name?.ifBlank { null }
                    ?: "Video ($messageId)"

                newMovieSearchResponse(
                    name = title,
                    url = streamUrl,
                    type = TvType.Movie
                )
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val base = getBaseUrl()
        val catalog = getCatalog()

        val matchedItem = catalog.firstOrNull { item ->
            item.parts?.any { part ->
                val constructed = "$base/video?chat_id=${part.chat_id}&message_id=${part.message_id}"
                constructed == url
            } == true
        }

        val title = matchedItem?.title?.ifBlank { null }
            ?: matchedItem?.parts?.firstOrNull()?.original_name?.ifBlank { null }
            ?: "Telegram Video"

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            dataUrl = url
        ) {
            this.plot = "TeleStream Direct Telegram Stream"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val base = getBaseUrl()
        val streamUrl = if (data.startsWith("http")) {
            data
        } else {
            "$base/video?$data"
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "Direct Stream",
                url = streamUrl,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}