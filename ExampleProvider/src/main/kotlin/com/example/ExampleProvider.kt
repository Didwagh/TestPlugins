// ============ ExampleProvider.kt ============
// ROOT CAUSE OF "No Links Found":
// CloudStream runs every SearchResponse.url through fixUrl(), which prepends
// mainUrl to anything that does NOT start with "http". So your raw token
// "-1004374443616:15728640" arrives in load()/loadLink() as
// "http://127.0.0.1:38471/-1004374443616:15728640".
//   -> load() equality check fails  => fallback title "Telegram Video" (your screenshot)
//   -> loadLink() split(":") yields 4 chunks, not 2 => returns false => "No Links Found"
// VLC works because you hand it the real /video?... URL directly.
// FIX: always recover the "chatId:messageId" pair from the tail of the url,
// and store the clean pair as dataUrl so loadLink() gets sane input.
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


    // Must match StreamService.PORT in the companion app.
    private val companionAppPort = 38471


    // Same channel_id you use in the companion app's own login screen.
    private val channelId = -1004374443616L


    override var mainUrl = "http://127.0.0.1:$companionAppPort"
    override var name = "Telegram Vault"


    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)


    private val mapper = ObjectMapper()
        .registerKotlinModule()
        .configure(DeserializationFeature.FAILONUNKNOWN_PROPERTIES, false)


    data class CatalogItem(
        @JsonProperty("title") val title: String = "",
        @JsonProperty("total_size") val totalSize: Long = 0,
        @JsonProperty("parts") val parts: List = emptyList()
    )


    data class PartItem(
        @JsonProperty("original_name") val originalName: String = "",
        @JsonProperty("size") val size: Long = 0,
        @JsonProperty("chat_id") val chatId: Long = 0,
        @JsonProperty("message_id") val messageId: Long = 0
    )


    private fun PartItem.toData() = "$chatId:$messageId"


    // Accepts BOTH the raw token "-1004374443616:15728640" AND the
    // fixUrl()-rewritten "http://127.0.0.1:38471/-1004374443616:15728640".
    // We look only at the segment after the last '/' so the ":38471" port
    // in the host part can never be mis-parsed as the chat/message pair.
    private fun extractData(url: String): String? {
        val tail = url.substringAfterLast('/', url)
        val match = Regex("^(-?\\d+):(\\d+)$").find(tail) ?: return null
        return "${match.groupValues[1]}:${match.groupValues[2]}"
    }


    private suspend fun getCatalog(): List {
        return try {
            val text = app.get("$mainUrl/catalog?channel_id=$channelId").text
            mapper.readValue(text, object : TypeReference>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = getCatalog().mapNotNull { item ->
            val part = item.parts.firstOrNull() ?: return@mapNotNull null
            newMovieSearchResponse(item.title, part.toData(), TvType.Movie)
        }
        return newHomePageResponse(HomePageList("Telegram Videos", list))
    }


    override suspend fun search(query: String): List {
        return getCatalog().filter { it.title.contains(query, true) }.mapNotNull { item ->
            val part = item.parts.firstOrNull() ?: return@mapNotNull null
            newMovieSearchResponse(item.title, part.toData(), TvType.Movie)
        }
    }


    override suspend fun load(url: String): LoadResponse {
        // Recover the clean "chatId:messageId" pair no matter how CS3 rewrote the url.
        val data = extractData(url)
        val item = getCatalog().firstOrNull { entry ->
            entry.parts.any { it.toData() == data }
        }
        // Store the CLEAN pair as dataUrl -> loadLink() receives parseable input,
        // and the real title now shows instead of the "Telegram Video" fallback.
        return newMovieLoadResponse(
            item?.title ?: "Telegram Video",
            url,
            TvType.Movie,
            data ?: url
        )
    }


    override suspend fun loadLink(
        data: String, // now guaranteed to be "chatId:messageId"
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pair = extractData(data) ?: return false
        val (chatId, messageId) = pair.split(":")


        // Dynamically build the direct stream endpoint for this specific message ID
        val streamUrl = "$mainUrl/video?chatid=$chatId&messageid=$messageId"


        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "Telegram Stream",
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