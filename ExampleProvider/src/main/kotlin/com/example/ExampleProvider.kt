package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class ExampleProvider : MainAPI() {

    private val companionAppPort = 38471
    private val channelId = -1004374443616L

    override var mainUrl = "http://127.0.0.1:$companionAppPort"
    override var name = "Telegram Vault"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

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
        return try {
            val response = app.get("$mainUrl/catalog?channel_id=$channelId").text
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
        val catalog = getCatalog()
        val list = catalog.mapNotNull { item ->
            val part = item.parts?.firstOrNull() ?: return@mapNotNull null
            val chatId = part.chat_id ?: return@mapNotNull null
            val messageId = part.message_id ?: return@mapNotNull null

            val streamUrl = "$mainUrl/video?chat_id=$chatId&message_id=$messageId"
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
        val catalog = getCatalog()
        return catalog
            .filter { it.title?.contains(query, ignoreCase = true) == true }
            .mapNotNull { item ->
                val part = item.parts?.firstOrNull() ?: return@mapNotNull null
                val chatId = part.chat_id ?: return@mapNotNull null
                val messageId = part.message_id ?: return@mapNotNull null

                val streamUrl = "$mainUrl/video?chat_id=$chatId&message_id=$messageId"
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
        // Find by exact streamUrl match
        val catalog = getCatalog()
        val matchedItem = catalog.firstOrNull { item ->
            item.parts?.any { part ->
                val constructed = "$mainUrl/video?chat_id=${part.chat_id}&message_id=${part.message_id}"
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
            this.plot = "Local Telegram stream playback"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val streamUrl = if (data.startsWith("http")) {
            data
        } else {
            "$mainUrl/video?$data"
        }

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "Direct Telegram Stream",
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