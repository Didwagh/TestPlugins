package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

class ExampleProvider : MainAPI() {

    // Must match StreamService.PORT in the companion app.
    private val companionAppPort = 38471

    // Same channel_id you use in the companion app's own login screen.
    private val channelId = -1004374443616L

    // The companion app itself - it builds the catalog live from TDLib now.
    // Render is not used anywhere in this file.
    override var mainUrl = "http://127.0.0.1:38471"
    override var name = "Telegram Vault"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    private val mapper = ObjectMapper().registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // Matches the REAL shape the companion app's /catalog returns:
    // [{ "title": ..., "total_size": ..., "parts": [{ "original_name":...,
    //    "size":..., "chat_id":..., "message_id":... }] }]
    // This is NOT a flat list - "parts" is a nested array. A flat model
    // here silently parses into blank fields instead of throwing, which
    // is exactly what caused entries to disappear before.
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

    private fun PartItem.toData() = "$chatId:$messageId"

    private suspend fun getCatalog(): List<CatalogItem> {
        return try {
            // channel_id is REQUIRED by the companion app's /catalog route -
            // without it the server returns 400 and this silently produced
            // an empty list before, which looked identical to "No Links Found".
            val text = app.get("$mainUrl/catalog?channel_id=$channelId").text
            mapper.readValue(text, object : TypeReference<List<CatalogItem>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = getCatalog().mapNotNull { item ->
            val part = item.parts.firstOrNull() ?: return@mapNotNull null
            newMovieSearchResponse(item.title, part.toData(), TvType.Movie) { }
        }
        return newHomePageResponse(HomePageList("Telegram Videos", list))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return getCatalog().filter { it.title.contains(query, true) }.mapNotNull { item ->
            val part = item.parts.firstOrNull() ?: return@mapNotNull null
            newMovieSearchResponse(item.title, part.toData(), TvType.Movie) { }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val item = getCatalog().firstOrNull { it.parts.firstOrNull()?.toData() == url }
        return newMovieLoadResponse(item?.title ?: "Telegram Video", url, TvType.Movie, url)
    }

    override suspend fun loadLinks(
        data: String, // "chatId:messageId"
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split(":")
        if (parts.size != 2) return false
        val chatId = parts[0]
        val messageId = parts[1]

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "Telegram Stream",
                url = "http://127.0.0.1:$companionAppPort/video?chat_id=$chatId&message_id=$messageId",
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
