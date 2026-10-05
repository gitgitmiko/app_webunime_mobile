package com.webunime.mobile.data

import java.net.URI

/**
 * Normalisasi URL poster katalog.
 * Beberapa entri anime-movies.json memakai root situs
 * (mis. https://v2.samehadaku.how/) sebagai thumbnail, bukan file gambar.
 */
object PosterUrls {
    fun isUsable(url: String?): Boolean {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return false
        if (!raw.startsWith("http://", ignoreCase = true) &&
            !raw.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }
        val path = runCatching {
            URI(raw).path?.trim().orEmpty()
        }.getOrDefault("").trimEnd('/')
        if (path.isEmpty()) return false
        val lower = path.lowercase()
        return IMAGE_EXT.any { lower.endsWith(it) } ||
            lower.contains("/uploads/") ||
            lower.contains("/upload/") ||
            lower.contains("/img/")
    }

    fun resolve(vararg candidates: String?): String? =
        candidates.firstOrNull { isUsable(it) }?.trim()

    private val IMAGE_EXT = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif")
}
