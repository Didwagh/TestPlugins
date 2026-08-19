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

    // Same channel ID you used as CHANNEL_ID in the old Python .env.
    // Only used to ask the companion app "list this channel" - the
    // companion app does the actual Telegram work with its own login.
    private val channelId = -1004374443616L

    // Render is no longer used at all - the companion app now builds the
    // catalog itself, live from TDLib, using the same session that streams.
    override var mainUrl = "http://127.0.0.1:38471"
    override var name = "Telegram Vault"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    private val mapper = ObjectMapper().registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    data class CatalogItem(
        @JsonProperty("title") val title: String = "",
        @JsonProperty("parts") val parts: List<PartItem> = emptyList()
    )

    data class PartItem(
        @JsonProperty("chat_id") val chatId: Long = 0,
        @JsonProperty("message_id") val messageId: Long = 0
    )

    private suspend fun getCatalog(): List<CatalogItem> {
        return try {
            val text = app.get("$mainUrl/catalog?channel_id=$channelId").text
            mapper.readValue(text, object : TypeReference<List<CatalogItem>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun PartItem.toData() = "$chatId:$messageId"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = getCatalog().map { item ->
            newMovieSearchResponse(item.title, item.parts.first().toData(), TvType.Movie) { }
        }
        return newHomePageResponse(HomePageList("Telegram Videos", list))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return getCatalog().filter { it.title.contains(query, true) }.map { item ->
            newMovieSearchResponse(item.title, item.parts.first().toData(), TvType.Movie) { }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val item = getCatalog().firstOrNull { it.parts.first().toData() == url }
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
