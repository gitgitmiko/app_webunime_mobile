package com.webunime.mobile.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class HomeResponse(
    val updatedAt: String? = null,
    val latest: List<LatestItem> = emptyList(),
    val anime: List<AnimeCard> = emptyList(),
    val movies: List<AnimeCard> = emptyList(),
    val scheduleToday: ScheduleDay? = null,
)

@JsonClass(generateAdapter = false)
data class FilmHome(
    val latestMovies: List<AnimeCard> = emptyList(),
    val topMovies: List<AnimeCard> = emptyList(),
    val latestHorror: List<AnimeCard> = emptyList(),
    val topHorror: List<AnimeCard> = emptyList(),
    val latestSeries: List<LatestItem> = emptyList(),
    val series: List<AnimeCard> = emptyList(),
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
    val series_slug: String? = null,
) {
    fun displayTitle(): String = judul ?: nama ?: slug ?: series_slug ?: anime_slug ?: "Tanpa judul"
    fun catalogSlug(): String = series_slug ?: anime_slug ?: slug ?: ""
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
    val catalog: String? = null,
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
    val season_label: String? = null,
    val related: List<RelatedAnime> = emptyList(),
    val episodes: List<EpisodeSummary> = emptyList(),
) {
    fun displayTitle(): String = judul ?: nama ?: slug ?: "Tanpa judul"
}

@JsonClass(generateAdapter = false)
data class RelatedAnime(
    val title: String? = null,
    val slug: String? = null,
    val source: String? = null,
) {
    fun displayTitle(): String = title?.takeIf { it.isNotBlank() } ?: slug ?: "Season"
}

/** Grup episode per season untuk dropdown di detail/player. */
data class SeasonGroup(
    val label: String,
    val animeSlug: String,
    val episodes: List<EpisodeSummary>,
    val isCurrent: Boolean = false,
)

@JsonClass(generateAdapter = false)
data class EpisodeSummary(
    val episode: Int? = null,
    val title: String? = null,
    val slug: String? = null,
    val source: String? = null,
    val has_players: Boolean? = null,
    val skip: Any? = null,
) {
    fun displayLabel(): String {
        val n = episode
        val raw = title?.takeIf { it.isNotBlank() }
        if (n != null) {
            // Samakan label biar urut kebaca: "Episode 12"
            if (raw == null || !raw.contains(n.toString())) {
                return "Episode $n"
            }
        }
        return raw ?: "Episode ${n ?: "?"}"
    }
}


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
    val season_label: String? = null,
    val related: List<RelatedAnime> = emptyList(),
    val episodes: List<CatalogEpisode> = emptyList(),
    val players: List<PlayerServer> = emptyList(),
) {
    fun displayTitle(): String = judul ?: nama ?: slug ?: "Tanpa judul"

    fun toDetail(): AnimeDetail {
        val eps = episodes.map { ep ->
            val num = EpisodeNumbers.resolve(ep.episodeNumber(), ep.title, ep.slug)
            EpisodeSummary(
                episode = num,
                title = ep.title,
                slug = ep.slug,
                source = ep.source,
                has_players = ep.players.isNotEmpty() || players.isNotEmpty(),
                skip = ep.skip,
            )
        }.sortedWith(
            compareBy<EpisodeSummary> { it.episode ?: Int.MAX_VALUE }
                .thenBy { it.title.orEmpty() },
        )
        val playable = if (eps.isEmpty() && players.isNotEmpty()) {
            listOf(
                EpisodeSummary(
                    episode = 1,
                    title = "Putar",
                    has_players = true,
                ),
            )
        } else {
            eps
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
            episodes = playable,
            mal_id = mal_id,
            season_label = season_label,
            related = related,
        )
    }

    fun toCard(catalog: String? = null): AnimeCard = AnimeCard(
        slug = slug,
        judul = judul,
        nama = nama,
        thumbnail = thumbnail,
        rating = rating,
        type = type,
        genre = genre,
        episodes_count = episodes_count ?: episodes.size.takeIf { it > 0 },
        catalog = catalog,
    )

    fun playbackFor(n: Int): EpisodePlayback {
        val ep = episodes.firstOrNull {
            EpisodeNumbers.resolve(it.episodeNumber(), it.title, it.slug) == n
        }
            ?: episodes.firstOrNull { it.episodeNumber() == n }
            ?: episodes.firstOrNull { it.episode == null && n == 1 }
            ?: episodes.getOrNull(n - 1)
        val players = when {
            ep != null && ep.players.isNotEmpty() -> ep.players
            players.isNotEmpty() -> players
            else -> emptyList()
        }
        val resolved = ep?.let {
            EpisodeNumbers.resolve(it.episodeNumber(), it.title, it.slug)
        } ?: n
        return EpisodePlayback(
            slug = slug,
            judul = displayTitle(),
            thumbnail = thumbnail,
            episode = EpisodePayload(
                episode = resolved,
                title = ep?.title ?: "Episode $resolved",
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
    val episode: Double? = null,
    val title: String? = null,
    val slug: String? = null,
    val source: String? = null,
    val players: List<PlayerServer> = emptyList(),
    val skip: Any? = null,
) {
    fun episodeNumber(): Int? = episode?.let { kotlin.math.round(it).toInt() }
}

