package com.webunime.mobile.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Katalog dari REST API WEBUNIME (`/api/v1`, MySQL di server).
 */
class CatalogRepository(
    apiBase: String = DEFAULT_API_BASE,
) {
    private val baseUrl: HttpUrl = apiBase.trimEnd('/').plus("/").toHttpUrl()

    private val moshi: Moshi = Moshi.Builder()
        .add(LenientIntAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    private val cardPageAdapter = moshi.adapter(CardPage::class.java)
    private val latestPageAdapter = moshi.adapter(LatestPage::class.java)
    private val itemAdapter = moshi.adapter(CatalogAnimeItem::class.java)
    private val calendarAdapter = moshi.adapter(CalendarResponse::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private class CachedItem(val at: Long, val item: CatalogAnimeItem)

    private val itemCache = ConcurrentHashMap<String, CachedItem>()

    /** API tidak terjangkau (jaringan / 5xx / JSON rusak). */
    private class ApiUnavailable(cause: Throwable?) : Exception(cause)

    /** API menjawab 404: judul memang tidak ada. */
    private class ApiNotFound(message: String) : Exception(message)

    suspend fun home(): HomeResponse = apiCall {
        coroutineScope {
            val latest = async { latestPage("anime-latest", HOME_ROW_LIMIT) }
            val anime = async { cardPage("anime", limit = pageSizeFor("anime")) }
            val movies = async { cardPage("anime-movies", limit = pageSizeFor("anime-movies")) }
            val schedule = async { calendarDoc() }
            val cal = schedule.await()
            HomeResponse(
                updatedAt = cal.scraped_at,
                latest = latest.await().items,
                anime = anime.await().items.map { it.withResolvedThumbnail("anime") },
                movies = movies.await().items.map { it.withResolvedThumbnail("anime") },
                scheduleToday = cal.days.firstOrNull {
                    it.day.equals(cal.today, ignoreCase = true)
                },
            )
        }
    }

    suspend fun filmHome(): FilmHome = apiCall {
        coroutineScope {
            val movies = async { cardPage("movies", limit = HOME_ROW_LIMIT) }
            val topMovies = async { cardPage("movies", limit = TOP_ROW_LIMIT, sort = "top_random") }
            val horror = async { cardPage("horror", limit = HOME_ROW_LIMIT) }
            val topHorror = async { cardPage("horror", limit = TOP_ROW_LIMIT, sort = "top_random") }
            val latestSeries = async { latestPage("series-latest", HOME_ROW_LIMIT) }
            val series = async { cardPage("series", limit = HOME_ROW_LIMIT) }
            FilmHome(
                latestMovies = movies.await().items.map { it.withResolvedThumbnail("movies") },
                topMovies = topMovies.await().items.map { it.withResolvedThumbnail("movies") },
                latestHorror = horror.await().items.map { it.withResolvedThumbnail("horror") },
                topHorror = topHorror.await().items.map { it.withResolvedThumbnail("horror") },
                latestSeries = latestSeries.await().items,
                series = series.await().items.map { it.withResolvedThumbnail("series") },
            )
        }
    }

    suspend fun search(q: String, limit: Int = 30, catalogMode: String = "anime"): SearchResponse {
        val query = q.trim()
        if (query.length < 2) return SearchResponse(q = q, count = 0, items = emptyList())
        return apiCall {
            val allowed = if (catalogMode == "film") FILM_COLLECTIONS else ANIME_COLLECTIONS
            val url = endpoint("search") {
                addQueryParameter("q", query)
                addQueryParameter("limit", "80")
            }
            val page = parse(cardPageAdapter, getBody(url))
            val items = page.items
                .filter { (it.catalog ?: "") in allowed }
                .map { it.withResolvedThumbnail(it.catalog.toAppCatalog()) }
                .distinctBy { "${it.catalog}:${it.slug?.lowercase(Locale.ROOT)}" }
                .take(limit.coerceIn(1, 100))
            SearchResponse(q = q, count = items.size, items = items)
        }
    }

    /**
     * Halaman berikutnya untuk baris beranda yang digeser sampai mentok.
     * Ukuran halaman sama dengan yang dipakai [home] / [filmHome], jadi page 2 menyambung.
     */
    suspend fun cardsPage(collection: String, page: Int): Paged<AnimeCard> = apiCall {
        val res = cardPage(collection, limit = pageSizeFor(collection), page = page)
        val thumbFallback = if (collection == "anime-movies") "anime" else collection
        Paged(
            items = res.items.map { it.withResolvedThumbnail(thumbFallback) },
            hasMore = res.hasMore(page),
        )
    }

    suspend fun latestItemsPage(collection: String, page: Int): Paged<LatestItem> = apiCall {
        val res = latestPage(collection, limit = pageSizeFor(collection), page = page)
        Paged(items = res.items, hasMore = res.hasMore(page))
    }

    suspend fun calendar(): CalendarResponse = apiCall { calendarDoc() }

    /** Koleksi asli di server (mis. judul "anime" bisa ada di anime-movies), untuk favorit. */
    suspend fun serverCollection(slug: String, collection: String): String =
        apiCall { locate(slug, collection)?.first ?: collection }

    suspend fun anime(slug: String, collection: String = "anime"): AnimeDetail =
        apiCall {
            val item = hydrate(slug, collection) ?: throw ApiNotFound("Judul tidak ditemukan: $slug")
            item.toDetail()
        }

    /**
     * Season saat ini + related (S1/S2/…) untuk dropdown terpisah.
     * Related diambil paralel dari API per slug.
     */
    suspend fun seasonsFor(slug: String, collection: String = "anime"): List<SeasonGroup> {
        if (collection == "movies" || collection == "horror") return emptyList()
        return apiCall {
            if (collection == "series") {
                val current = hydrate(slug, "series")
                    ?: throw ApiNotFound("Series tidak ditemukan: $slug")
                val currentSlug = current.slug?.takeIf { it.isNotBlank() } ?: slug
                return@apiCall listOf(
                    SeasonGroup(
                        label = "Episode",
                        animeSlug = currentSlug,
                        episodes = current.toDetail().episodes,
                        isCurrent = true,
                    ),
                )
            }
            val current = hydrate(slug) ?: throw ApiNotFound("Anime tidak ditemukan: $slug")
            val currentSlug = current.slug?.takeIf { it.isNotBlank() } ?: slug
            val groups = mutableListOf(
                SeasonGroup(
                    label = SeasonLabels.from(
                        current.judul ?: current.nama,
                        currentSlug,
                        current.season_label,
                    ),
                    animeSlug = currentSlug,
                    episodes = current.toDetail().episodes,
                    isCurrent = true,
                ),
            )
            val seen = mutableSetOf(currentSlug.lowercase(Locale.ROOT))
            val related = current.related.filter {
                val s = it.slug?.trim().orEmpty()
                s.isNotBlank() && seen.add(s.lowercase(Locale.ROOT))
            }
            val loaded = coroutineScope {
                related.map { rel ->
                    async {
                        val relSlug = rel.slug!!.trim()
                        val item = runCatching { hydrate(relSlug) }.getOrNull()
                            ?: return@async null
                        val detail = item.toDetail()
                        if (detail.episodes.isEmpty()) return@async null
                        SeasonGroup(
                            label = SeasonLabels.from(
                                rel.title ?: item.judul ?: item.nama,
                                relSlug,
                                item.season_label,
                            ),
                            animeSlug = relSlug,
                            episodes = detail.episodes,
                            isCurrent = false,
                        )
                    }
                }.mapNotNull { it.await() }
            }
            groups += loaded
            groups.sortedWith(
                compareBy<SeasonGroup> { SeasonLabels.sortKey(it.label) }
                    .thenBy { it.label },
            )
        }
    }

    suspend fun episode(slug: String, n: Int, collection: String = "anime"): EpisodePlayback =
        apiCall {
            val (found, item) = locate(slug, collection)
                ?: throw ApiNotFound("Judul tidak ditemukan: $slug")
            val playback = item.playbackFor(n).copy(collection = found)
            if (playback.episode?.players.isNullOrEmpty()) {
                error("Tidak ada player untuk episode $n")
            }
            playback
        }

    // --- API ---

    private suspend fun cardPage(
        collection: String,
        limit: Int,
        sort: String? = null,
        page: Int = 1,
    ): CardPage {
        val url = endpoint("catalog", collection) {
            addQueryParameter("limit", limit.toString())
            if (page > 1) addQueryParameter("page", page.toString())
            if (sort != null) addQueryParameter("sort", sort)
        }
        return parse(cardPageAdapter, getBody(url))
    }

    private suspend fun latestPage(collection: String, limit: Int, page: Int = 1): LatestPage {
        val url = endpoint("catalog", collection) {
            addQueryParameter("limit", limit.toString())
            if (page > 1) addQueryParameter("page", page.toString())
        }
        return parse(latestPageAdapter, getBody(url))
    }

    private suspend fun calendarDoc(): CalendarResponse {
        val doc = parse(calendarAdapter, getBody(endpoint("docs", "anime-schedule")))
        return doc.copy(today = doc.today?.takeIf { it.isNotBlank() } ?: deviceWeekday())
    }

    private suspend fun hydrate(slug: String, collection: String = "anime"): CatalogAnimeItem? =
        locate(slug, collection)?.second

    /** Item + koleksi tempat ia ditemukan (anime bisa ada di anime-movies). */
    private suspend fun locate(
        slug: String,
        collection: String = "anime",
    ): Pair<String, CatalogAnimeItem>? {
        val key = slug.trim()
        if (key.isBlank()) return null
        val candidates = when (collection) {
            "movies", "horror", "series", "marvel", "indonesia" -> listOf(collection)
            else -> listOf("anime", "anime-movies")
        }
        for (name in candidates) {
            val cacheKey = "$name:${key.lowercase(Locale.ROOT)}"
            itemCache[cacheKey]?.let {
                if (System.currentTimeMillis() - it.at < ITEM_TTL_MS) return name to it.item
            }
            val item = try {
                parse(itemAdapter, getBody(endpoint("catalog", name, key)))
            } catch (e: ApiNotFound) {
                continue
            }
            val patched = item.withResolvedThumbnail()
            itemCache[cacheKey] = CachedItem(System.currentTimeMillis(), patched)
            return name to patched
        }
        return null
    }

    private fun endpoint(
        vararg segments: String,
        params: HttpUrl.Builder.() -> Unit = {},
    ): HttpUrl = baseUrl.newBuilder().apply {
        segments.forEach { addPathSegment(it) }
        params()
    }.build()

    private suspend fun getBody(url: HttpUrl): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "WEBUNIME-Mobile/0.4")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> throw ApiNotFound("HTTP 404 ${url.encodedPath}")
                    !response.isSuccessful -> throw ApiUnavailable(IOException("HTTP ${response.code}"))
                    else -> response.body?.string() ?: throw ApiUnavailable(IOException("Empty body"))
                }
            }
        } catch (e: IOException) {
            throw ApiUnavailable(e)
        }
    }

    private fun <T> parse(adapter: com.squareup.moshi.JsonAdapter<T>, body: String): T =
        try {
            adapter.fromJson(body) ?: throw ApiUnavailable(IOException("Empty JSON"))
        } catch (e: IOException) {
            throw ApiUnavailable(e)
        } catch (e: com.squareup.moshi.JsonDataException) {
            throw ApiUnavailable(e)
        }

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiUnavailable) {
        throw IOException("Tidak bisa terhubung ke server WEBUNIME", e)
    } catch (e: ApiNotFound) {
        throw IllegalStateException(e.message, e)
    }

    private fun String?.toAppCatalog(): String? =
        if (this == "anime-movies") "anime" else this

    private fun deviceWeekday(): String =
        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "monday"
            Calendar.TUESDAY -> "tuesday"
            Calendar.WEDNESDAY -> "wednesday"
            Calendar.THURSDAY -> "thursday"
            Calendar.FRIDAY -> "friday"
            Calendar.SATURDAY -> "saturday"
            else -> "sunday"
        }

    companion object {
        const val DEFAULT_API_BASE = "https://gitgitmiko.my.id/api/v1/"

        private const val HOME_ROW_LIMIT = 24
        private const val TOP_ROW_LIMIT = 10
        private const val MOVIES_LIMIT = 60
        private const val ITEM_TTL_MS = 10 * 60 * 1000L

        private fun pageSizeFor(collection: String): Int =
            if (collection == "anime-movies") MOVIES_LIMIT else HOME_ROW_LIMIT

        private val ANIME_COLLECTIONS = setOf("anime", "anime-movies")
        private val FILM_COLLECTIONS = setOf("movies", "horror", "series")
    }
}

/** Satu halaman baris beranda; [hasMore] false bila sudah mentok di akhir katalog. */
data class Paged<T>(val items: List<T>, val hasMore: Boolean)

@JsonClass(generateAdapter = false)
internal data class CardPage(
    val items: List<AnimeCard> = emptyList(),
    val limit: Int? = null,
    val total: Int? = null,
) {
    fun hasMore(page: Int): Boolean = pageHasMore(page, limit, total, items.size)
}

@JsonClass(generateAdapter = false)
internal data class LatestPage(
    val items: List<LatestItem> = emptyList(),
    val limit: Int? = null,
    val total: Int? = null,
) {
    fun hasMore(page: Int): Boolean = pageHasMore(page, limit, total, items.size)
}

private fun pageHasMore(page: Int, limit: Int?, total: Int?, count: Int): Boolean =
    if (limit != null && total != null) page * limit < total else count > 0
