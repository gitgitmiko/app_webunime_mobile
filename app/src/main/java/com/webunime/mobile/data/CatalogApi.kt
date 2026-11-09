package com.webunime.mobile.data

/**
 * Facade tipis: signature method tetap untuk screens,
 * implementasi dari [CatalogRepository] (REST API WEBUNIME).
 */
class CatalogApi(
    baseUrl: String = CatalogRepository.DEFAULT_API_BASE,
) {
    private val repo = CatalogRepository(apiBase = baseUrl)

    suspend fun home(): HomeResponse = repo.home()

    suspend fun filmHome(): FilmHome = repo.filmHome()

    suspend fun cardsPage(collection: String, page: Int): Paged<AnimeCard> =
        repo.cardsPage(collection, page)

    suspend fun latestItemsPage(collection: String, page: Int): Paged<LatestItem> =
        repo.latestItemsPage(collection, page)

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
