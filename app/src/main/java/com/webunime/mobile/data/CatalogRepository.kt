package com.webunime.mobile.data

import android.content.Context
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Katalog Mobile dari JSON GitHub (`public/data/mobile/`),
 * cache lokal + seed assets ringan, hydrate slug dari anime.json tanpa parse penuh.
 */
class CatalogRepository(
    private val context: Context,
    private val githubRawBase: String = DEFAULT_GITHUB_RAW,
    private val githubJsDelivrBase: String = DEFAULT_JSDELIVR,
) {
    private val moshi: Moshi = Moshi.Builder()
        .add(LenientIntAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()

    private val latestListType =
        Types.newParameterizedType(List::class.java, LatestItem::class.java)
    private val latestAdapter = moshi.adapter<List<LatestItem>>(latestListType)

    private val cardListType =
        Types.newParameterizedType(List::class.java, AnimeCard::class.java)
    private val cardListAdapter = moshi.adapter<List<AnimeCard>>(cardListType)

    private val animeItemAdapter = moshi.adapter(CatalogAnimeItem::class.java)
    private val animeListType =
        Types.newParameterizedType(List::class.java, CatalogAnimeItem::class.java)
    private val animeListAdapter = moshi.adapter<List<CatalogAnimeItem>>(animeListType)

    private val calendarAdapter = moshi.adapter(CalendarResponse::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    private val cacheDir: File
        get() = File(context.filesDir, "catalog/mobile").also { if (!it.exists()) it.mkdirs() }

    private val filmCacheDir: File
        get() = File(context.filesDir, "catalog/film").also { if (!it.exists()) it.mkdirs() }

    private val filmRawBase: String
        get() = githubRawBase.replace("/mobile/", "/")

    private val filmCdnBase: String
        get() = githubJsDelivrBase.replace("/mobile/", "/")

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val lightMutex = Mutex()
    private val heavyMutex = Mutex()
    private val filmMutex = Mutex()
    private val lightSyncDone = AtomicBoolean(false)
    private val itemCache = ConcurrentHashMap<String, CatalogAnimeItem>()

    suspend fun home(): HomeResponse = withContext(Dispatchers.IO) {
        ensureLightFiles()
        val latest = readLatestList(FILE_LATEST)
        val anime = readAnimeList(FILE_INDEX).map { it.toCard() }.ifEmpty {
            readCardList(FILE_INDEX)
        }.take(HOME_ANIME_LIMIT)
        val movies = readAnimeList(FILE_MOVIES).map { it.toCard() }.ifEmpty {
            readCardList(FILE_MOVIES)
        }
        val schedule = readCalendar(FILE_SCHEDULE)
        HomeResponse(
            updatedAt = schedule.scraped_at,
            latest = latest,
            anime = anime,
            movies = movies,
            scheduleToday = schedule.days.firstOrNull {
                it.day.equals(schedule.today, ignoreCase = true)
            },
        )
    }

    suspend fun filmHome(): FilmHome = withContext(Dispatchers.IO) {
        ensureFilmFiles()
        val movies = readAnimeList(FILE_LK_MOVIES, filmCacheDir).map { it.toCard() }
        val horror = readAnimeList(FILE_HORROR, filmCacheDir).map { it.toCard() }
        val series = readAnimeList(FILE_SERIES_INDEX, filmCacheDir).map { it.toCard() }
        val latestSeries = readLatestList(FILE_SERIES_LATEST, filmCacheDir)
        FilmHome(
            latestMovies = movies.take(HOME_ROW_LIMIT),
            topMovies = topRated(movies),
            latestHorror = horror.take(HOME_ROW_LIMIT),
            topHorror = topRated(horror),
            latestSeries = latestSeries.take(HOME_ROW_LIMIT),
            series = series.take(HOME_ROW_LIMIT),
        )
    }

    suspend fun search(q: String, limit: Int = 30, catalogMode: String = "anime"): SearchResponse =
        withContext(Dispatchers.IO) {
        val query = q.trim().lowercase(Locale.ROOT)
        if (query.length < 2) {
            return@withContext SearchResponse(q = q, count = 0, items = emptyList())
        }
        val pool = if (catalogMode == "film") {
            ensureFilmFiles()
            buildList {
                addAll(readAnimeList(FILE_LK_MOVIES, filmCacheDir).map { it.toCard("movies") })
                addAll(readAnimeList(FILE_HORROR, filmCacheDir).map { it.toCard("horror") })
                addAll(readAnimeList(FILE_SERIES_INDEX, filmCacheDir).map { it.toCard("series") })
            }
        } else {
            ensureLightFiles()
            readAnimeList(FILE_INDEX).map { it.toCard("anime") }.ifEmpty {
                readCardList(FILE_INDEX).map { it.copy(catalog = "anime") }
            } + readAnimeList(FILE_MOVIES).map { it.toCard("anime") }
        }
        val items = pool
            .asSequence()
            .filter { card ->
                val hay = listOfNotNull(card.judul, card.nama, card.slug)
                    .joinToString(" ")
                    .lowercase(Locale.ROOT)
                hay.contains(query)
            }
            .distinctBy { "${it.catalog}:${it.slug?.lowercase(Locale.ROOT)}" }
            .take(limit.coerceIn(1, 100))
            .toList()
        SearchResponse(q = q, count = items.size, items = items)
    }

    suspend fun calendar(): CalendarResponse = withContext(Dispatchers.IO) {
        ensureLightFiles()
        readCalendar(FILE_SCHEDULE)
    }

    suspend fun anime(slug: String, collection: String = "anime"): AnimeDetail = withContext(Dispatchers.IO) {
        val item = hydrate(slug, collection) ?: error("Judul tidak ditemukan: $slug")
        item.toDetail()
    }

    /**
     * Season saat ini + related (S1/S2/…) untuk dropdown terpisah.
     * Related di-hydrate on-demand (cache dipakai ulang).
     */
    suspend fun seasonsFor(slug: String, collection: String = "anime"): List<SeasonGroup> =
        withContext(Dispatchers.IO) {
        if (collection == "movies" || collection == "horror") return@withContext emptyList()
        if (collection == "series") {
            val current = hydrate(slug, "series") ?: error("Series tidak ditemukan: $slug")
            val detail = current.toDetail()
            val currentSlug = current.slug?.takeIf { it.isNotBlank() } ?: slug
            return@withContext listOf(
                SeasonGroup(
                    label = "Episode",
                    animeSlug = currentSlug,
                    episodes = detail.episodes,
                    isCurrent = true,
                ),
            )
        }
        val current = hydrate(slug) ?: error("Anime tidak ditemukan: $slug")
        val groups = mutableListOf<SeasonGroup>()
        val currentSlug = current.slug?.takeIf { it.isNotBlank() } ?: slug
        groups += SeasonGroup(
            label = SeasonLabels.from(current.judul ?: current.nama, currentSlug, current.season_label),
            animeSlug = currentSlug,
            episodes = current.toDetail().episodes,
            isCurrent = true,
        )
        val seen = mutableSetOf(currentSlug.lowercase(Locale.ROOT))
        for (rel in current.related) {
            val relSlug = rel.slug?.trim().orEmpty()
            if (relSlug.isBlank()) continue
            if (!seen.add(relSlug.lowercase(Locale.ROOT))) continue
            val item = hydrate(relSlug) ?: continue
            val detail = item.toDetail()
            if (detail.episodes.isEmpty()) continue
            groups += SeasonGroup(
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
        groups.sortedWith(
            compareBy<SeasonGroup> { SeasonLabels.sortKey(it.label) }
                .thenBy { it.label },
        )
    }

    suspend fun episode(slug: String, n: Int, collection: String = "anime"): EpisodePlayback =
        withContext(Dispatchers.IO) {
        val item = hydrate(slug, collection) ?: error("Judul tidak ditemukan: $slug")
        val playback = item.playbackFor(n)
        if (playback.episode?.players.isNullOrEmpty()) {
            error("Tidak ada player untuk episode $n")
        }
        playback
    }

    private suspend fun hydrate(slug: String, collection: String = "anime"): CatalogAnimeItem? {
        val key = slug.trim()
        if (key.isBlank()) return null
        val cacheKey = "${collection.lowercase(Locale.ROOT)}:${key.lowercase(Locale.ROOT)}"
        itemCache[cacheKey]?.let { return it }
        when (collection) {
            "movies" -> {
                ensureFilmFiles()
                findInFile(FILE_LK_MOVIES, key, filmCacheDir)?.let {
                    remember(it, "movies")
                    return it
                }
                return null
            }
            "horror" -> {
                ensureFilmFiles()
                findInFile(FILE_HORROR, key, filmCacheDir)?.let {
                    remember(it, "horror")
                    return it
                }
                return null
            }
            "series" -> {
                ensureSeriesFile()
                findInFile(FILE_SERIES, key, filmCacheDir)?.let {
                    remember(it, "series")
                    return it
                }
                return null
            }
        }
        itemCache["anime:${key.lowercase(Locale.ROOT)}"]?.let { return it }

        ensureLightFiles()
        // Coba movies dulu (lebih kecil) sebelum unduh anime.json.
        findInFile(FILE_MOVIES, key)?.let {
            remember(it, "anime")
            return it
        }

        ensureHeavyFile(FILE_ANIME)
        findInFile(FILE_ANIME, key)?.let {
            remember(it, "anime")
            return it
        }
        return null
    }

    private fun remember(item: CatalogAnimeItem, collection: String) {
        item.slug?.takeIf { it.isNotBlank() }?.let {
            itemCache["${collection.lowercase(Locale.ROOT)}:${it.lowercase(Locale.ROOT)}"] = item
        }
    }

    private suspend fun ensureLightFiles() {
        if (lightSyncDone.get() && lightFilesPresent()) return
        lightMutex.withLock {
            if (lightSyncDone.get() && lightFilesPresent()) return
            if (needsLightRefreshToday()) {
                var ok = 0
                for (name in LIGHT_FILES) {
                    if (runCatching { downloadAndCache(name) }.isSuccess) ok++
                }
                if (ok > 0) {
                    prefs.edit().putString(KEY_LAST_SYNC_DAY, todayKey()).apply()
                }
            }
            lightSyncDone.set(true)
        }
    }

    private suspend fun ensureHeavyFile(fileName: String) {
        val dest = File(cacheDir, fileName)
        if (dest.exists() && dest.length() > 50_000L) return
        heavyMutex.withLock {
            if (dest.exists() && dest.length() > 50_000L) return
            downloadAndCache(fileName)
        }
    }

    private fun needsLightRefreshToday(): Boolean {
        if (!lightFilesPresent()) return true
        return prefs.getString(KEY_LAST_SYNC_DAY, null) != todayKey()
    }

    private fun lightFilesPresent(): Boolean =
        LIGHT_FILES.any { name ->
            val f = File(cacheDir, name)
            f.exists() && f.length() > 2
        }

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun deviceWeekday(): String {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (day) {
            Calendar.MONDAY -> "monday"
            Calendar.TUESDAY -> "tuesday"
            Calendar.WEDNESDAY -> "wednesday"
            Calendar.THURSDAY -> "thursday"
            Calendar.FRIDAY -> "friday"
            Calendar.SATURDAY -> "saturday"
            else -> "sunday"
        }
    }

    private suspend fun ensureFilmFiles() {
        filmMutex.withLock {
            val missing = FILM_LIGHT_FILES.any { name ->
                val f = File(filmCacheDir, name)
                !f.exists() || f.length() < 2L
            }
            if (!missing && prefs.getString(KEY_FILM_SYNC, null) == todayKey()) return
            var ok = 0
            for (name in FILM_LIGHT_FILES) {
                if (runCatching {
                        downloadAndCache(name, filmCacheDir, filmRawBase, filmCdnBase)
                    }.isSuccess
                ) {
                    ok++
                }
            }
            if (ok > 0) {
                prefs.edit().putString(KEY_FILM_SYNC, todayKey()).apply()
            }
        }
    }

    private suspend fun ensureSeriesFile() {
        val dest = File(filmCacheDir, FILE_SERIES)
        if (dest.exists() && dest.length() > 50_000L) return
        heavyMutex.withLock {
            if (dest.exists() && dest.length() > 50_000L) return
            downloadAndCache(FILE_SERIES, filmCacheDir, filmRawBase, filmCdnBase)
        }
    }

    private fun topRated(items: List<AnimeCard>, limit: Int = TOP_ROW_LIMIT): List<AnimeCard> {
        fun score(card: AnimeCard): Double {
            val raw = card.rating?.trim()?.replace(',', '.') ?: return 0.0
            return raw.toDoubleOrNull() ?: 0.0
        }
        val high = items.filter { score(it) > 8.0 }
        val pool = if (high.size >= 8) high else items.sortedByDescending { score(it) }
        return pool.shuffled().take(limit)
    }

    private fun readLatestList(fileName: String, dir: File = cacheDir): List<LatestItem> {
        openStream(fileName, dir)?.use { input ->
            return runCatching {
                latestAdapter.fromJson(input.source().buffer()).orEmpty()
            }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    private fun readCardList(fileName: String): List<AnimeCard> {
        openStream(fileName)?.use { input ->
            return runCatching {
                cardListAdapter.fromJson(input.source().buffer()).orEmpty()
            }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    private fun readAnimeList(fileName: String, dir: File = cacheDir): List<CatalogAnimeItem> {
        openStream(fileName, dir)?.use { input ->
            return runCatching {
                animeListAdapter.fromJson(input.source().buffer()).orEmpty()
            }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    private fun readCalendar(fileName: String): CalendarResponse {
        val raw = openStream(fileName)?.use { input ->
            runCatching {
                calendarAdapter.fromJson(input.source().buffer())
            }.getOrNull()
        }
        val base = raw ?: CalendarResponse()
        return base.copy(today = base.today?.takeIf { it.isNotBlank() } ?: deviceWeekday())
    }

    private fun findInFile(fileName: String, slug: String, dir: File = cacheDir): CatalogAnimeItem? {
        openStream(fileName, dir)?.use { input ->
            return findItemInStream(input, slug)
        }
        return null
    }

    private fun findItemInStream(input: java.io.InputStream, slug: String): CatalogAnimeItem? {
        val source = input.source().buffer()
        source.use {
            val reader = JsonReader.of(it)
            reader.beginArray()
            while (reader.hasNext()) {
                val item = animeItemAdapter.fromJson(reader) ?: continue
                if (item.slug.equals(slug, ignoreCase = true)) {
                    return item
                }
            }
            reader.endArray()
        }
        return null
    }

    /** Cache file, lalu assets seed (hanya file ringan). */
    private fun openStream(fileName: String, dir: File = cacheDir): java.io.InputStream? {
        val cached = File(dir, fileName)
        if (cached.exists() && cached.length() > 2) {
            return cached.inputStream()
        }
        return runCatching {
            context.assets.open("data/$fileName")
        }.getOrNull()
    }

    private fun downloadAndCache(
        fileName: String,
        dir: File = cacheDir,
        rawBase: String = githubRawBase,
        cdnBase: String = githubJsDelivrBase,
    ) {
        val baseRaw = rawBase.trimEnd('/') + "/"
        val baseCdn = cdnBase.trimEnd('/') + "/"
        val urls = listOf("$baseRaw$fileName", "$baseCdn$fileName")
        var lastError: Throwable? = null
        for (url in urls) {
            val result = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "WEBUNIME-Mobile/0.1")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code} for $fileName")
                    val body = response.body ?: error("Empty body $fileName")
                    val tmp = File(dir, "$fileName.part")
                    tmp.outputStream().use { out ->
                        body.byteStream().use { input -> input.copyTo(out) }
                    }
                    if (tmp.length() < 2L) {
                        tmp.delete()
                        error("Empty body $fileName")
                    }
                    tmp.inputStream().buffered().use { raw ->
                        var c = raw.read()
                        while (c != -1 && c.toChar().isWhitespace()) {
                            c = raw.read()
                        }
                        val first = c.toChar()
                        if (first != '[' && first != '{') {
                            tmp.delete()
                            error("Invalid JSON root $fileName")
                        }
                    }
                    val dest = File(dir, fileName)
                    if (dest.exists()) dest.delete()
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                }
            }
            if (result.isSuccess) return
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: error("Download failed $fileName")
    }

    companion object {
        const val DEFAULT_GITHUB_RAW =
            "https://raw.githubusercontent.com/gitgitmiko/WEBUNIME/main/public/data/mobile/"
        const val DEFAULT_JSDELIVR =
            "https://cdn.jsdelivr.net/gh/gitgitmiko/WEBUNIME@main/public/data/mobile/"

        private const val PREFS_NAME = "catalog_mobile_sync"
        private const val KEY_LAST_SYNC_DAY = "last_github_sync_day"

        private const val FILE_LATEST = "anime-latest.json"
        private const val FILE_MOVIES = "anime-movies.json"
        private const val FILE_SCHEDULE = "anime-schedule.json"
        private const val FILE_INDEX = "anime-index.json"
        private const val FILE_ANIME = "anime.json"
        private const val FILE_LK_MOVIES = "movies.json"
        private const val FILE_HORROR = "horror.json"
        private const val FILE_SERIES_LATEST = "series-latest.json"
        private const val FILE_SERIES_INDEX = "series-index.json"
        private const val FILE_SERIES = "series.json"
        private const val HOME_ANIME_LIMIT = 24
        private const val HOME_ROW_LIMIT = 24
        private const val TOP_ROW_LIMIT = 18
        private const val KEY_FILM_SYNC = "last_film_sync_day"

        private val LIGHT_FILES = listOf(
            FILE_LATEST,
            FILE_MOVIES,
            FILE_SCHEDULE,
            FILE_INDEX,
        )

        private val FILM_LIGHT_FILES = listOf(
            FILE_LK_MOVIES,
            FILE_HORROR,
            FILE_SERIES_LATEST,
            FILE_SERIES_INDEX,
        )
    }
}
