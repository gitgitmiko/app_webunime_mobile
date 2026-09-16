package com.webunime.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Helper embed untuk WebView Mobile (Wibufile dll).
 * Wibufile menolak akses top-level: "please access the embed page using an iframe!".
 */
object EmbedPlayback {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun isWibufileEmbed(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("api.wibufile.com/embed") ||
            u.contains("login.wibufile.com/v/") ||
            (u.contains("wibufile") && u.contains("/embed"))
    }

    /** Embed yang wajib di dalam iframe (bukan loadUrl top-level). */
    fun needsIframeWrapper(url: String): Boolean = isWibufileEmbed(url)

    fun wrapperBaseUrl(url: String): String = when {
        isWibufileEmbed(url) -> "https://api.wibufile.com/"
        else -> {
            val host = runCatching { java.net.URI(url).host }.getOrNull()
            if (!host.isNullOrBlank()) "https://$host/" else "https://localhost/"
        }
    }

    fun iframeWrapperHtml(embedUrl: String): String {
        val safe = embedUrl
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
        return """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <style>
            html,body{margin:0;padding:0;height:100%;width:100%;background:#000;overflow:hidden}
            iframe#wuEmbed{
              border:0!important;outline:0!important;margin:0!important;padding:0!important;
              width:100vw!important;height:100vh!important;display:block;background:#000!important;
            }
            </style></head><body>
            <iframe id="wuEmbed" src="$safe"
              allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
              allowfullscreen scrolling="no" referrerpolicy="origin"></iframe>
            </body></html>
        """.trimIndent()
    }

    /**
     * Ambil MP4 dari halaman embed Wibufile (JWPlayer) agar bisa ExoPlayer.
     * Gagal → null (fallback iframe wrapper).
     */
    suspend fun resolveDirectMedia(url: String): String? = withContext(Dispatchers.IO) {
        if (!isWibufileEmbed(url)) return@withContext null
        runCatching {
            val html = fetch(url, referer = "https://api.wibufile.com/")
            extractMp4(html)
        }.getOrNull()
    }

    private fun fetch(url: String, referer: String): String {
        val req = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .header("Referer", referer)
            .header("Accept", "text/html,application/xhtml+xml,*/*")
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) error("HTTP ${res.code}")
            return res.body?.string().orEmpty()
        }
    }

    private fun extractMp4(html: String): String? {
        val patterns = listOf(
            Regex(""""file"\s*:\s*"((?:https?:)?\\/\\/[^"]+\.mp4[^"]*)"""", RegexOption.IGNORE_CASE),
            Regex(""""file"\s*:\s*"((?:https?://)[^"]+\.mp4[^"]*)"""", RegexOption.IGNORE_CASE),
            Regex("""file\s*:\s*["'](https?://[^"']+\.mp4[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""(https?://[^\s"'<>]+\.mp4(?:\?[^\s"'<>]*)?)""", RegexOption.IGNORE_CASE),
        )
        for (re in patterns) {
            val m = re.find(html) ?: continue
            var out = m.groupValues[1].replace("\\/", "/")
            if (out.startsWith("//")) out = "https:$out"
            runCatching { return java.net.URI(out).toString() }
        }
        return null
    }
}
