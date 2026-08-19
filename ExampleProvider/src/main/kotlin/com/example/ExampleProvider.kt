package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

class ExampleProvider : MainAPI() {

        private val companionAppPort = 38471

            override var mainUrl = "http://127.0.0.1:38471"
                override var name = "Telegram Vault"

                    override val hasMainPage = true
                        override val supportedTypes = setOf(TvType.Movie)

                            private val mapper = ObjectMapper()
                                    .registerKotlinModule()
                                            .configure(
                                                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                                                                        false
                                            )

                                                /*
                                                     * Fake catalog item.
                                                          *
                                                               * This is ALWAYS included.
                                                                    * It lets you verify that the CloudStream plugin itself
                                                                         * is working even if the companion API is unavailable.
                                                                              */
                                                                                  private val fakeCatalogJson = """
                                                                                          [
                                                                                                        {
                                                                                                                            "original_name": "TEST - Badla_2019_WebRip_Hindi_720p_x264_AAC_5_1_ESub.mkv",
                                                                                                                                            "size": 1094694614,
                                                                                                                                                            "chat_id": -1004374443616,
                                                                                                                                                                            "message_id": 4194304
                                                                                                        }
                                                                                          ]
                                                                                              """.trimIndent()

                                                                                                  data class CatalogItem(
                                                                                                            @JsonProperty("original_name")
                                                                                                                    val originalName: String = "",

                                                                                                                            @JsonProperty("size")
                                                                                                                                    val size: Long = 0,

                                                                                                                                            @JsonProperty("chat_id")
                                                                                                                                                    val chatId: Long = 0,

                                                                                                                                                            @JsonProperty("message_id")
                                                                                                                                                                    val messageId: Long = 0
                                                                                                  )

                                                                                                      /*
                                                                                                           * Convert a catalog item into the data passed to load().
                                                                                                                *
                                                                                                                     * Example:
                                                                                                                          * -1004374443616:4194304
                                                                                                                               */
                                                                                                                                   private fun CatalogItem.toData(): String {
                                                                                                                                            return "$chatId:$messageId"
                                                                                                                                   }

                                                                                                                                       /*
                                                                                                                                            * Parse the built-in fake catalog.
                                                                                                                                                 */
                                                                                                                                                     private fun getFakeCatalog(): List<CatalogItem> {
                                                                                                                                                                return try {
                                                                                                                                                                                mapper.readValue(
                                                                                                                                                                                                    fakeCatalogJson,
                                                                                                                                                                                                                    object : TypeReference<List<CatalogItem>>() {}
                                                                                                                                                                                )
                                                                                                                                                                } catch (e: Exception) {
                                                                                                                                                                                emptyList()
                                                                                                                                                                }
                                                                                                                                                     }

                                                                                                                                                         /*
                                                                                                                                                              * Fetch the real catalog from your companion API.
                                                                                                                                                                   *
                                                                                                                                                                        * Expected endpoint:
                                                                                                                                                                             *
                                                                                                                                                                                  * GET http://127.0.0.1:38471/catalog
                                                                                                                                                                                       */
                                                                                                                                                                                           private suspend fun getApiCatalog(): List<CatalogItem> {
                                                                                                                                                                                                    return try {

                                                                                                                                                                                                                    val response = app.get(
                                                                                                                                                                                                                                        "$mainUrl/catalog"
                                                                                                                                                                                                                    )

                                                                                                                                                                                                                                mapper.readValue(
                                                                                                                                                                                                                                                    response.text,
                                                                                                                                                                                                                                                                    object : TypeReference<List<CatalogItem>>() {}
                                                                                                                                                                                                                                )

                                                                                                                                                                                                    } catch (e: Exception) {

                                                                                                                                                                                                                    /*
                                                                                                                                                                                                                                 * API failure should NOT kill the plugin.
                                                                                                                                                                                                                                              * The fake catalog will still be available.
                                                                                                                                                                                                                                                           */
                                                                                                                                                                                                                                                                       emptyList()
                                                                                                                                                                                                    }
                                                                                                                                                                                           }

                                                                                                                                                                                               /*
                                                                                                                                                                                                    * Return BOTH catalogs.
                                                                                                                                                                                                         *
                                                                                                                                                                                                              * Fake data + API data.
                                                                                                                                                                                                                   */
                                                                                                                                                                                                                       private suspend fun getCatalog(): List<CatalogItem> {

                                                                                                                                                                                                                                val fakeCatalog = getFakeCatalog()
                                                                                                                                                                                                                                        val apiCatalog = getApiCatalog()

                                                                                                                                                                                                                                                return fakeCatalog + apiCatalog
                                                                                                                                                                                                                       }

                                                                                                                                                                                                                           override suspend fun getMainPage(
                                                                                                                                                                                                                                    page: Int,
                                                                                                                                                                                                                                            request: MainPageRequest
                                                                                                                                                                                                                           ): HomePageResponse {

                                                                                                                                                                                                                                    val list = getCatalog()
                                                                                                                                                                                                                                                .filter {
                                                                                                                                                                                                                                                                    it.originalName.isNotBlank()
                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                            .map { item ->
                                                                                                                                                                                                                                                            
                                                                                                                                                                                                                                                                            newMovieSearchResponse(
                                                                                                                                                                                                                                                                                                    item.originalName,
                                                                                                                                                                                                                                                                                                                        item.toData(),
                                                                                                                                                                                                                                                                                                                                            TvType.Movie
                                                                                                                                                                                                                                                                            ) { }
                                                                                                                                                                                                                                                            }

                                                                                                                                                                                                                                                                    return newHomePageResponse(
                                                                                                                                                                                                                                                                                    HomePageList(
                                                                                                                                                                                                                                                                                                        "Telegram Videos",
                                                                                                                                                                                                                                                                                                                        list
                                                                                                                                                                                                                                                                                    )
                                                                                                                                                                                                                                                                    )
                                                                                                                                                                                                                           }

                                                                                                                                                                                                                               override suspend fun search(
                                                                                                                                                                                                                                        query: String
                                                                                                                                                                                                                               ): List<SearchResponse> {

                                                                                                                                                                                                                                        return getCatalog()
                                                                                                                                                                                                                                                    .filter {
                                                                                                                                                                                                                                                                        it.originalName.contains(
                                                                                                                                                                                                                                                                                                query,
                                                                                                                                                                                                                                                                                                                    ignoreCase = true
                                                                                                                                                                                                                                                                        )
                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                .map { item ->
                                                                                                                                                                                                                                                                
                                                                                                                                                                                                                                                                                newMovieSearchResponse(
                                                                                                                                                                                                                                                                                                        item.originalName,
                                                                                                                                                                                                                                                                                                                            item.toData(),
                                                                                                                                                                                                                                                                                                                                                TvType.Movie
                                                                                                                                                                                                                                                                                ) { }
                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                               }

                                                                                                                                                                                                                                   override suspend fun load(
                                                                                                                                                                                                                                            url: String
                                                                                                                                                                                                                                   ): LoadResponse {

                                                                                                                                                                                                                                            val item = getCatalog()
                                                                                                                                                                                                                                                        .firstOrNull {
                                                                                                                                                                                                                                                                            it.toData() == url
                                                                                                                                                                                                                                                        }

                                                                                                                                                                                                                                                                return newMovieLoadResponse(
                                                                                                                                                                                                                                                                                item?.originalName ?: "Telegram Video",
                                                                                                                                                                                                                                                                                            url,
                                                                                                                                                                                                                                                                                                        TvType.Movie,
                                                                                                                                                                                                                                                                                                                    url
                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                   }

                                                                                                                                                                                                                                       override suspend fun loadLinks(
                                                                                                                                                                                                                                                data: String,
                                                                                                                                                                                                                                                        isCasting: Boolean,
                                                                                                                                                                                                                                                                subtitleCallback: (SubtitleFile) -> Unit,
                                                                                                                                                                                                                                                                        callback: (ExtractorLink) -> Unit
                                                                                                                                                                                                                                       ): Boolean {

                                                                                                                                                                                                                                                val parts = data.split(":")

                                                                                                                                                                                                                                                        if (parts.size != 2) {
                                                                                                                                                                                                                                                                        return false
                                                                                                                                                                                                                                                        }

                                                                                                                                                                                                                                                                val chatId = parts[0]
                                                                                                                                                                                                                                                                        val messageId = parts[1]

                                                                                                                                                                                                                                                                                val streamUrl =
                                                                                                                                                                                                                                                                                            "http://127.0.0.1:$companionAppPort/video" +
                                                                                                                                                                                                                                                                                                        "?chat_id=$chatId&message_id=$messageId"

                                                                                                                                                                                                                                                                                                                callback.invoke(
                                                                                                                                                                                                                                                                                                                                newExtractorLink(
                                                                                                                                                                                                                                                                                                                                                    source = this.name,
                                                                                                                                                                                                                                                                                                                                                                    name = "Telegram Stream",
                                                                                                                                                                                                                                                                                                                                                                                    url = streamUrl,
                                                                                                                                                                                                                                                                                                                                                                                                    type = ExtractorLinkType.VIDEO
                                                                                                                                                                                                                                                                                                                                ) {
                                                                                                                                                                                                                                                                                                                                                    referer = ""
                                                                                                                                                                                                                                                                                                                                                                    quality = Qualities.Unknown.value
                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                )

                                                                                                                                                                                                                                                                                                                        return true
                                                                                                                                                                                                                                       }
}
                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                       }
                                                                                                                                                                                                                                       )
                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                   }
                                                                                                                                                                                                                                   )
                                                                                                                                                                                                                                                                                )}
                                                                                                                                                                                                                                                                        )
                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                               }
                                                                                                                                                                                                                               )
                                                                                                                                                                                                                                                                                    )
                                                                                                                                                                                                                                                                    )
                                                                                                                                                                                                                                                                            )}
                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                           }
                                                                                                                                                                                                                           )
                                                                                                                                                                                                                       }
                                                                                                                                                                                                    }
                                                                                                                                                                                                                                )
                                                                                                                                                                                                                    )
                                                                                                                                                                                                    }
                                                                                                                                                                                           }
                                                                                                                                                                }
                                                                                                                                                                                )
                                                                                                                                                                }
                                                                                                                                                     }
                                                                                                                                   }
                                                                                                  )
                                                                                                        }
                                                                                          ]
                                            )
}