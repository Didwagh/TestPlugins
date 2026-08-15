package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.fasterxml.jackson.annotation.JsonProperty

class ExampleProvider : MainAPI() {

    override var mainUrl = "https://tg-cs3.onrender.com"
    override var name = "Telegram Vault"

    override val hasMainPage = true
    override val hasSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    // ---- JSON Models ----
    data class CatalogItem(
        @JsonProperty("title") val title: String,
        @JsonProperty("total_size") val totalSize: Long,
        @JsonProperty("parts") val parts: List<PartItem>
    )

    data class PartItem(
        @JsonProperty("file_id") val fileId: String,
        @JsonProperty("size") val size: Long,
        @JsonProperty("original_name") val originalName: String,
        @JsonProperty("chat_id") val chatId: Long,
        @JsonProperty("message_id") val messageId: Int
    )

    private suspend fun getCatalog(): List<CatalogItem> {
        return try {
            app.get("$mainUrl/catalog").parsedSafe<List<CatalogItem>>() ?: emptyList()
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
        // We use the constructor directly. Ignore the "deprecated" warning in logs, it still works perfectly.
        val link = ExtractorLink(
            source = this.name,
            name = "Telegram Stream",
            url = "$mainUrl/play/$data",
            referer = "",
            quality = Qualities.Unknown.value,
            isM3u8 = false
        )
        callback.invoke(link)
        return true
    }
}