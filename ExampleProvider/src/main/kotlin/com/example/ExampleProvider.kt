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

    // We changed this part to ask for the right Lego blocks (file_id, media_id, etc.)
    data class PartItem(
        @JsonProperty("file_id") val fileId: String = "",
        @JsonProperty("size") val size: Long = 0,
        @JsonProperty("original_name") val originalName: String = "",
        @JsonProperty("media_id") val mediaId: Double = 0.0,
        @JsonProperty("access_hash") val accessHash: Double = 0.0,
        @JsonProperty("dc_id") val dcId: Int = 0,
        @JsonProperty("file_reference") val fileReference: String = ""
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
            // We use fileId here so the app remembers exactly which movie you clicked
            newMovieSearchResponse(item.title, p.fileId, TvType.Movie) { }
        }
        return newHomePageResponse(HomePageList("Telegram Videos", list))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return getCatalog().filter { it.title.contains(query, true) }.map { item ->
            val p = item.parts.first()
            // We use fileId here too for search results
            newMovieSearchResponse(item.title, p.fileId, TvType.Movie) { }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val item = getCatalog().firstOrNull {
            // We find the movie by matching the fileId you clicked on
            it.parts.first().let { p -> p.fileId == url }
        }
        return newMovieLoadResponse(item?.title ?: "Telegram Video", url, TvType.Movie, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // This is the play button! It will take your file_id (which is passed as 'data')
        // and add it to the end of your play URL.
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