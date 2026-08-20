package com.example

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.lagradost.cloudstream3.*
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

    private val mapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    data class CatalogItem(
        @JsonProperty("title") val title: String = "",
        @JsonProperty("total_size") val totalSize: Long = 0,
        @JsonProperty("parts") val parts: List<PartItem> = emptyList()
    )

    data class PartItem(
        @JsonProperty("original_name") val originalName: String = "",
        @JsonProperty("size") val size: Long = 0,
        @JsonProperty("chat_id") val chatId: Long = 0,
        @JsonProperty("message_id") val messageId: Long = 0
    )

    // Formats directly into the query param format
    private fun PartItem.toQuery(): String = "chat_id=$chatId&message_id=$messageId"

    private suspend fun getCatalog(): List<CatalogItem> {
        return try {
            val text = app.get("$mainUrl/catalog?channel_id=$channelId").text
            mapper.readValue(text, object : TypeReference<List<CatalogItem>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    override val mainPage = mainPageOf(
        "catalog" to "Telegram Videos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = getCatalog().mapNotNull { item ->
            val part = item.parts.firstOrNull() ?: return@mapNotNull null
            newMovieSearchResponse(
                name = item.title.ifBlank { part.originalName.ifBlank { "Video" } },
                url = "$mainUrl/video?${part.toQuery()}",
                type = TvType.Movie
            )
        }
        return newHomePageResponse(request.name, list, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return getCatalog()
            .filter { it.title.contains(query, ignoreCase = true) }
            .mapNotNull { item ->
                val part = item.parts.firstOrNull() ?: return@mapNotNull null
                newMovieSearchResponse(
                    name = item.title.ifBlank { part.originalName.ifBlank { "Video" } },
                    url = "$mainUrl/video?${part.toQuery()}",
                    type = TvType.Movie
                )
            }
    }

    override suspend fun load(url: String): LoadResponse {
        // Find matching item from catalog or fallback to default title
        val item = getCatalog().firstOrNull { catalogItem ->
            catalogItem.parts.any { "$mainUrl/video?${it.toQuery()}" == url }
        }

        val title = item?.title?.ifBlank { null }
            ?: item?.parts?.firstOrNull()?.originalName?.ifBlank { null }
            ?: "Telegram Video"

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            dataUrl = url
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Ensure streamUrl is properly formed
        val streamUrl = if (data.startsWith("http")) {
            data
        } else {
            "$mainUrl/video?$data"
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