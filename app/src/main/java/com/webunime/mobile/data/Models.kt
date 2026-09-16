package com.webunime.mobile.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class HomeResponse(
    val updatedAt: String? = null,
    val latest: List<LatestItem> = emptyList(),
    val movies: List<AnimeCard> = emptyList(),
    val scheduleToday: ScheduleDay? = null,
)

@JsonClass(generateAdapter = false)
data class LatestItem(
    val anime_slug: String? = null,
    val slug: String? = null,
    val judul: String? = null,
    val nama: String? = null,
    val episode: Int? = null,
    val thumbnail: String? = null,
    val released_at: String? = null,
    val released_on: String? = null,
) {
    fun displayTitle(): String = judul ?: nama ?: slug ?: anime_slug ?: "Tanpa judul"
    fun catalogSlug(): String = anime_slug ?: slug ?: ""
}

@JsonClass(generateAdapter = false)
data class AnimeCard(
    val slug: String? = null,
    val judul: String? = null,
    val nama: String? = null,
    val thumbnail: String? = null,
    val rating: String? = null,
    val type: String? = null,
    val genre: List<String>? = null,
    val episodes_count: Int? = null,
) {
    fun displayTitle(): String = judul ?: nama ?: slug ?: "Tanpa judul"
}

@JsonClass(generateAdapter = false)
data class SearchResponse(
    val q: String? = null,
    val count: Int? = null,
    val items: List<AnimeCard> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class CalendarResponse(
    val source: String? = null,
    val scraped_at: String? = null,
    val timezone: String? = null,
    val today: String? = null,
    val days: List<ScheduleDay> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class ScheduleDay(
    val day: String? = null,
    val label: String? = null,
    val items: List<ScheduleItem> = emptyList(),
)

@JsonClass(generateAdapter = false)
data class ScheduleItem(
    val slug: String? = null,
    val judul: String? = null,
    val nama: String? = null,
    val thumbnail: String? = null,
    val rating: String? = null,
    val type: String? = null,
    val genre: List<String>? = null,
    val time: String? = null,
    val source: String? = null,
) {
    fun displayTitle(): String = judul ?: nama ?: slug ?: "Tanpa judul"
}

@JsonClass(generateAdapter = false)
data class AnimeDetail(
    val slug: String? = null,
    val judul: String? = null,
    val nama: String? = null,
    val thumbnail: String? = null,
    val rating: String? = null,
    val type: String? = null,
    val genre: List<String>? = null,
    val sinopsis: String? = null,
    val episodes_count: Int? = null,
    val mal_id: Int? = null,
    val episodes: List<EpisodeSummary> = emptyList(),
) {
    fun displayTitle(): String = judul ?: nama ?: slug ?: "Tanpa judul"
}

@JsonClass(generateAdapter = false)
data class EpisodeSummary(
    val episode: Int? = null,
    val title: String? = null,
    val slug: String? = null,
    val source: String? = null,
    val has_players: Boolean? = null,
    val skip: Any? = null,
)

@JsonClass(generateAdapter = false)
data class EpisodePlayback(
    val slug: String? = null,
    val judul: String? = null,
    val thumbnail: String? = null,
    val episode: EpisodePayload? = null,
)

@JsonClass(generateAdapter = false)
data class EpisodePayload(
    val episode: Int? = null,
    val title: String? = null,
    val slug: String? = null,
    val source: String? = null,
    val players: List<PlayerServer> = emptyList(),
    val skip: Any? = null,
)

@JsonClass(generateAdapter = false)
data class PlayerServer(
    val no: Int? = null,
    val server: String? = null,
    val label: String? = null,
    val url: String? = null,
    @Json(name = "default") val isDefault: Boolean? = null,
) {
    fun displayLabel(): String = label?.takeIf { it.isNotBlank() } ?: server ?: "Server"
}

/** Item penuh dari anime.json / anime-movies.json (termasuk players). */
@JsonClass(generateAdapter = false)
data class CatalogAnimeItem(
    val slug: String? = null,
    val judul: String? = null,
    val nama: String? = null,
    val thumbnail: String? = null,
    val rating: String? = null,
    val type: String? = null,
    val genre: List<String>? = null,
    val sinopsis: String? = null,
    val episodes_count: Int? = null,
    val mal_id: Int? = null,
    val episodes: List<CatalogEpisode> = emptyList(),
    val players: List<PlayerServer> = emptyList(),
) {
    fun displayTitle(): String = judul ?: nama ?: slug ?: "Tanpa judul"

    fun toDetail(): AnimeDetail {
        val eps = episodes.map { ep ->
            EpisodeSummary(
                episode = ep.episode,
                title = ep.title,
                slug = ep.slug,
                source = ep.source,
                has_players = ep.players.isNotEmpty() || players.isNotEmpty(),
                skip = ep.skip,
            )
        }
        return AnimeDetail(
            slug = slug,
            judul = judul,
            nama = nama,
            thumbnail = thumbnail,
            rating = rating,
            type = type,
            genre = genre,
            sinopsis = sinopsis,
            episodes_count = episodes_count ?: eps.size.takeIf { it > 0 },
            mal_id = mal_id,
            episodes = eps,
        )
    }

    fun toCard(): AnimeCard = AnimeCard(
        slug = slug,
        judul = judul,
        nama = nama,
        thumbnail = thumbnail,
        rating = rating,
        type = type,
        genre = genre,
        episodes_count = episodes_count ?: episodes.size.takeIf { it > 0 },
    )

    fun playbackFor(n: Int): EpisodePlayback {
        val ep = episodes.firstOrNull { it.episode == n }
            ?: episodes.firstOrNull { it.episode == null && n == 1 }
            ?: episodes.getOrNull(n - 1)
        val players = when {
            ep != null && ep.players.isNotEmpty() -> ep.players
            players.isNotEmpty() -> players
            else -> emptyList()
        }
        return EpisodePlayback(
            slug = slug,
            judul = displayTitle(),
            thumbnail = thumbnail,
            episode = EpisodePayload(
                episode = ep?.episode ?: n,
                title = ep?.title ?: "Episode $n",
                slug = ep?.slug,
                source = ep?.source,
                players = players,
                skip = ep?.skip,
            ),
        )
    }
}

@JsonClass(generateAdapter = false)
data class CatalogEpisode(
    val episode: Int? = null,
    val title: String? = null,
    val slug: String? = null,
    val source: String? = null,
    val players: List<PlayerServer> = emptyList(),
    val skip: Any? = null,
)
