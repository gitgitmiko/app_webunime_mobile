package com.webunime.mobile.data

import android.content.Context

/**
 * Facade tipis: signature method tetap untuk screens,
 * implementasi dari [CatalogRepository] (REST API WEBUNIME, fallback JSON GitHub).
 */
class CatalogApi(
    context: Context,
    baseUrl: String = CatalogRepository.DEFAULT_API_BASE,
) {
    private val repo = CatalogRepository(
        context = context.applicationContext,
        apiBase = baseUrl,
    )

    suspend fun home(): HomeResponse = repo.home()

    suspend fun filmHome(): FilmHome = repo.filmHome()

    suspend fun search(q: String, limit: Int = 30, catalogMode: String = "anime"): SearchResponse =
        repo.search(q, limit, catalogMode)

    suspend fun calendar(): CalendarResponse = repo.calendar()

    suspend fun anime(slug: String, collection: String = "anime"): AnimeDetail =
        repo.anime(slug, collection)

    suspend fun seasonsFor(slug: String, collection: String = "anime"): List<SeasonGroup> =
        repo.seasonsFor(slug, collection)

    suspend fun episode(slug: String, n: Int, collection: String = "anime"): EpisodePlayback =
        repo.episode(slug, n, collection)
}
