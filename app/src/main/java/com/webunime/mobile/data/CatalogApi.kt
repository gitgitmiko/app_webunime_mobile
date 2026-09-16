package com.webunime.mobile.data

import android.content.Context

/**
 * Facade tipis: signature method tetap untuk screens,
 * implementasi dari [CatalogRepository] (JSON GitHub + cache).
 */
class CatalogApi(
    context: Context,
    baseUrl: String = CatalogRepository.DEFAULT_GITHUB_RAW,
) {
    private val repo = CatalogRepository(
        context = context.applicationContext,
        githubRawBase = baseUrl,
    )

    suspend fun home(): HomeResponse = repo.home()

    suspend fun search(q: String, limit: Int = 30): SearchResponse =
        repo.search(q, limit)

    suspend fun calendar(): CalendarResponse = repo.calendar()

    suspend fun anime(slug: String): AnimeDetail = repo.anime(slug)

    suspend fun seasonsFor(slug: String): List<SeasonGroup> = repo.seasonsFor(slug)

    suspend fun episode(slug: String, n: Int): EpisodePlayback =
        repo.episode(slug, n)
}
