package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

class ExampleProvider : MainAPI() {

    override var mainUrl = "https://tg-cs3.onrender.com"
    override var name = "Telegram Vault"

    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    // Jackson mapper that KEEPS generic types (fixes the LinkedHashMap crash)
    private val mapper = ObjectMapper().registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // ---- JSON Models ----
    data class CatalogItem(
        @JsonProperty("title") val title: String = "",
        @JsonProperty("total_size") val totalSize: Long = 0,
        @JsonProperty("parts") val parts: List<PartItem> = emptyList()
    )

    data class PartItem(
        @JsonProperty("file_id") val fileId: String = "",
        @JsonProperty("size") val size: Long = 0,
        @JsonProperty("original_name") val originalName: String = "",
        @JsonProperty("chat_id") val chatId: Long = 0,
        @JsonProperty("message_id") val messageId: Int = 0
    )

    private suspend fun getCatalog(): List<CatalogItem> {
        return try {
            val text = app.get("$mainUrl/catalog").text
            // This preserves the generic type info, unlike parsedSafe
            mapper.readValue(text, object : TypeReference<List<CatalogItem>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = getCatalog().map { item ->
            val p = item.parts.first()
            newMovieSearchResponse(item.title, "${p.chatId}/${p.messageId}", TvType.Movie) { }
        }
        return newHomePageResponse(HomePageList("Telegram Videos", list))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return getCatalog().filter { it.title.contains(query, true) }.map { item ->
            val p = item.parts.first()
            newMovieSearchResponse(item.title, "${p.chatId}/${p.messageId}", TvType.Movie) { }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val item = getCatalog().firstOrNull {
            it.parts.first().let { p -> "${p.chatId}/${p.messageId}" == url }
        }
        return newMovieLoadResponse(item?.title ?: "Telegram Video", url, TvType.Movie, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = newExtractorLink(
            source = this.name,
            name = "Telegram Stream",
            url = "$mainUrl/play/$data",
            type = ExtractorLinkType.VIDEO
        ) {
            this.referer = ""
            this.quality = Qualities.Unknown.value
        }
        callback.invoke(link)
        return true
    }
}