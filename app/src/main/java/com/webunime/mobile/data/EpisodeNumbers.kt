package com.webunime.mobile.data

/**
 * Nomor episode di JSON kadang salah (mis. One Piece: episode=1 tapi judul "Episode 1031").
 * Ambil nomor efektif dari title/slug dulu, baru fallback field episode.
 */
object EpisodeNumbers {

    fun fromTitleOrSlug(title: String?, slug: String?): Int? {
        fromText(title)?.let { return it }
        fromText(slug?.replace('-', ' '))?.let { return it }
        Regex("""(?i)(?:^|[-_/])episode-(\d+)""")
            .find(slug.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        return null
    }

    private fun fromText(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        Regex("""(?i)episode\s*(\d+)""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        return null
    }

    fun resolve(episodeField: Int?, title: String?, slug: String?): Int? =
        fromTitleOrSlug(title, slug) ?: episodeField
}
