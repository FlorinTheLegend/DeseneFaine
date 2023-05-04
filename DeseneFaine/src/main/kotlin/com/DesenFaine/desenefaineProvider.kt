package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class DeseneFaineProvider : MainAPI() {
    override var mainUrl = "https://www.desenefaine.net"
    override var name = "Desene Faine"
    override val hasQuickSearch = true
    override val hasMainPage = true

    override val supportedTypes = setOf(TvType.Cartoon)

    private fun fixUrl(url: String): String {
        return if (url.startsWith("/")) mainUrl + url else url
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val response = mutableListOf(
            HomePageList(
                "Latest Update",
                doc.select("div.item a.item-box").map {
                    AnimeSearchResponse(
                        it.select("h3").text(),
                        mainUrl + it.attr("href"),
                        mainUrl,
                        TvType.Cartoon,
                        fixUrl(it.select("img").attr("src"))
                    )
                }
            )
        )
        val list = mapOf(
            "Popular" to "ul.items-list-popular",
            "Trending" to "ul.items-list-trending",
            "Recently Added" to "ul.items-list-latest",
            "Ongoing Series" to "ul.items-list-ongoing"
        )
        response.addAll(list.map { item ->
            HomePageList(
                item.key,
                doc.select("${item.value} > li").map {
                    AnimeSearchResponse(
                        it.select("h3").text(),
                        mainUrl + it.select("a")[0].attr("href"),
                        mainUrl,
                        TvType.Cartoon,
                        fixUrl(it.select("img").attr("src"))
                    )
                }
            )
        })
        return HomePageResponse(response)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/search",
            data = mapOf("q" to query)
        ).document
            .select("div.item a.item-box")
            .map {
                AnimeSearchResponse(
                    it.select("h3").text(),
                    mainUrl + it.attr("href"),
                    name,
                    TvType.Cartoon,
                    fixUrl(it.select("img").attr("src"))
                )
            }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return app.post(
            "$mainUrl/ajax-search",
            data = mapOf("query" to query)
        ).document.select("a").map {
            AnimeSearchResponse(
                it.text(),
                it.attr("href"),
                mainUrl,
                TvType.Cartoon,
            )
        }
    }

    private fun getStatus(from: String?): ShowStatus? {
        return when {
            from?.contains("Completed") == true -> ShowStatus.Completed
            from?.contains("Ongoing") == true -> ShowStatus.Ongoing
            else -> null
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val info = doc.select("div.description-box")
        val name = info.select("h1").text()
        val eps = doc.select("ul.episodes li").map {
            Episode(
                fixUrl(it.select("a")[0].attr("href")),
                it.select("span").text().trim()
            )
        }
        val
        val description = info.select("p").text()
        val imageUrl = fixUrl(doc.select("div.thumb img").attr("src"))
        val genres = doc.select("div.category a").map { it.text() }
        val status = getStatus(doc.select("div.status").text())

        return LoadResponse(
            anime = AnimeMeta(
                name,
                description,
                imageUrl,
                genres,
                status
            ),
            episodes = eps
        )
    }

    override suspend fun getEpisodeInfo(episodeUrl: String): EpisodeInfoResponse {
        val doc = app.get(episodeUrl).document
        val videoUrl = doc.select("iframe").attr("src")

        val extractor = loadExtractor(videoUrl)
        val extractedLinks = extractor?.extractLinks(videoUrl)
            ?: throw Exception("Failed to extract video links")

        val sources = extractedLinks.map { link ->
            EpisodeInfo(
                link.url,
                link.title,
                link.subtype
            )
        }

        return EpisodeInfoResponse(sources)
    }

    override suspend fun getVideoLink(link: String): String {
        val extractor = loadExtractor(link)
        val extractedLink = extractor?.getDirectLink(link)
            ?: throw Exception("Failed to extract direct video link")

        return extractedLink.url
    }
}
