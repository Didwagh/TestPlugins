package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.mvvm.logError

class ExampleProvider : MainAPI() {

    override var name = "TeleStream"
    
    override var mainUrl: String
        get() = PluginConfig.getBaseUrl()
        set(value) {}

    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    data class FilePartDto(
        @JsonProperty("original_name") val originalName: String = "",
        @JsonProperty("size") val size: Long = 0L,
        @JsonProperty("chat_id") val chatId: Long = 0L,
        @JsonProperty("message_id") val messageId: Long = 0L,
        @JsonProperty("label") val label: String = ""
    )

    data class EpisodeDto(
        @JsonProperty("season") val season: Int = 1,
        @JsonProperty("episode") val episode: Int = 1,
        @JsonProperty("total_size") val totalSize: Long = 0L,
        @JsonProperty("parts") val parts: List<FilePartDto> = emptyList()
    )

    data class CatalogItemDto(
        @JsonProperty("type") val type: String = "movie",
        @JsonProperty("title") val title: String = "",
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("total_size") val totalSize: Long = 0L,
        @JsonProperty("parts") val parts: List<FilePartDto> = emptyList(),
        @JsonProperty("episodes") val episodes: List<EpisodeDto> = emptyList(),
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("rating") val rating: Double? = null,
        @JsonProperty("runtime_minutes") val runtimeMinutes: Int? = null,
        @JsonProperty("genres") val genres: List<String> = emptyList(),
        @JsonProperty("cast") val cast: List<String> = emptyList()
    )

    override val mainPage = listOf(
        MainPageData("Telegram Library", "catalog")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val channelId = PluginConfig.getChannelId()
        val url = "$mainUrl/catalog?channel_id=$channelId"
        val response = app.get(url).text
        val items = parseJson<List<CatalogItemDto>>(response)

        val searchList = items.map { item ->
            val serialized = item.toJson()
            if (item.type.equals("series", ignoreCase = true)) {
                newTvSeriesSearchResponse(
                    name = item.title,
                    url = serialized,
                    type = TvType.TvSeries
                ) {
                    this.posterUrl = item.poster
                    this.year = item.year
                }
            } else {
                newMovieSearchResponse(
                    name = item.title,
                    url = serialized,
                    type = TvType.Movie
                ) {
                    this.posterUrl = item.poster
                    this.year = item.year
                }
            }
        }

        return newHomePageResponse(request.name, searchList, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val channelId = PluginConfig.getChannelId()
        val url = "$mainUrl/search?query=$query&channel_id=$channelId"
        val response = app.get(url).text
        val items = parseJson<List<CatalogItemDto>>(response)

        return items.map { item ->
            val serialized = item.toJson()
            if (item.type.equals("series", ignoreCase = true)) {
                newTvSeriesSearchResponse(
                    name = item.title,
                    url = serialized,
                    type = TvType.TvSeries
                ) {
                    this.posterUrl = item.poster
                    this.year = item.year
                }
            } else {
                newMovieSearchResponse(
                    name = item.title,
                    url = serialized,
                    type = TvType.Movie
                ) {
                    this.posterUrl = item.poster
                    this.year = item.year
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val item = parseJson<CatalogItemDto>(url)

        // Pre-buffer / Warm up the stream as soon as the info page opens
        val warmPart = item.parts.firstOrNull() ?: item.episodes.firstOrNull()?.parts?.firstOrNull()
        if (warmPart != null) {
            try {
                app.get("$mainUrl/warmup?chat_id=${warmPart.chatId}&message_id=${warmPart.messageId}", timeout = 2L)
            } catch (ignored: Throwable) {}
        }

        // TMDB gives a 0-10 score; CloudStream's rating field is 0-10000
        // internally so it can show one decimal place without floats.
        val ratingInt = item.rating?.let { (it * 1000).toInt() }
        val tagsList = item.genres.ifEmpty { null }
        val castList = item.cast.ifEmpty { null }
        val durationText = item.runtimeMinutes?.let { "$it minutes" }

        return if (item.type.equals("series", ignoreCase = true)) {
            val episodesList = item.episodes.map { ep ->
                val epDataJson = ep.parts.toJson()
                val epSizeMb = ep.totalSize / (1024 * 1024)
                newEpisode(epDataJson) {
                    this.name = "Season ${ep.season} Episode ${ep.episode} (${epSizeMb} MB)"
                    this.season = ep.season
                    this.episode = ep.episode
                    this.posterUrl = item.poster
                }
            }

            newTvSeriesLoadResponse(
                name = item.title,
                url = url,
                type = TvType.TvSeries,
                episodes = episodesList
            ) {
                this.posterUrl = item.poster
                this.year = item.year
                this.plot = item.overview ?: item.imdbId?.let { "IMDb ID: $it" }
                this.tags = tagsList
                this.rating = ratingInt
                addActors(castList)
                addDuration(durationText)
            }
        } else {
            val partsDataJson = item.parts.toJson()
            newMovieLoadResponse(
                name = item.title,
                url = url,
                type = TvType.Movie,
                dataUrl = partsDataJson
            ) {
                this.posterUrl = item.poster
                this.year = item.year
                this.plot = item.overview ?: item.imdbId?.let { "IMDb ID: $it" }
                this.tags = tagsList
                this.rating = ratingInt
                addActors(castList)
                addDuration(durationText)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = parseJson<List<FilePartDto>>(data)

        parts.forEachIndexed { index, part ->
            val streamUrl = "$mainUrl/video?chat_id=${part.chatId}&message_id=${part.messageId}"
            val sizeMb = part.size / (1024 * 1024)

            val linkName = if (part.label.isNotBlank()) {
                "$name - ${part.label} (${sizeMb} MB)"
            } else if (parts.size > 1) {
                "$name - Part ${index + 1} (${sizeMb} MB)"
            } else {
                "$name (${sizeMb} MB)"
            }

            callback(
                newExtractorLink(
                    source = name,
                    name = linkName,
                    url = streamUrl
                ) {
                    this.quality = Qualities.P1080.value
                    this.referer = mainUrl
                }
            )
        }

        return true
    }
}