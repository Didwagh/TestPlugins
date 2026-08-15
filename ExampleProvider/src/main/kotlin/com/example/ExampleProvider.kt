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

    // This still points at Render — Render is now ONLY the text catalog
    // (chat_id/message_id lookup), never video bytes.
    override var mainUrl = "https://tg-cs3.onrender.com"
    override var name = "Telegram Vault"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    private val mapper = ObjectMapper().registerKotlinModule()
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

    private suspend fun getCatalog(): List<CatalogItem> {
        return try {
            val text = app.get("$mainUrl/catalog").text
            mapper.readValue(text, object : TypeReference<List<CatalogItem>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun PartItem.toData() = "$chatId:$messageId"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = getCatalog().map { item ->
            val p = item.parts.first()
            newMovieSearchResponse(item.title, p.toData(), TvType.Movie) { }
        }
        return newHomePageResponse(HomePageList("Telegram Videos", list))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return getCatalog().filter { it.title.contains(query, true) }.map { item ->
            val p = item.parts.first()
            newMovieSearchResponse(item.title, p.toData(), TvType.Movie) { }
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
        val chatId = parts[0].toLongOrNull() ?: return false
        val messageId = parts[1].toLongOrNull() ?: return false

        val port = StreamServerHolder.ensureStarted(chatId, messageId)

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "Telegram Stream",
                url = "http://127.0.0.1:$port/video",
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}