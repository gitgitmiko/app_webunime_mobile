package com.webunime.mobile.data

object PlayerRouter {

    fun preferred(players: List<PlayerServer>): List<PlayerServer> {
        val raw = players.filter { !it.url.isNullOrBlank() }
        if (raw.isEmpty()) return emptyList()
        return rankAnime(raw)
    }

    fun pickDefault(players: List<PlayerServer>): PlayerServer? =
        preferred(players).firstOrNull()

    fun isDirectMedia(url: String): Boolean {
        val u = url.lowercase()
        if (u.contains("abyssplayer") || u.contains("gn1r5n") ||
            u.contains("turbo") || u.contains("emturbovid") || u.contains("blogger.com") ||
            u.contains("mega.nz") || u.contains("filedon.co/embed") ||
            u.contains("api.wibufile.com/embed") || u.contains("login.wibufile.com")
        ) {
            return false
        }
        return u.contains(".mp4") || u.contains(".m3u8") || u.contains(".webm") ||
            u.contains("wibufile.com/video")
    }

    private fun rankAnime(raw: List<PlayerServer>): List<PlayerServer> {
        fun score(p: PlayerServer): Int {
            val u = (p.url ?: "").lowercase()
            val l = (p.label ?: "").lowercase()
            val s = (p.server ?: "").lowercase()
            val res = resolutionRank(l, u)
            val isMega = u.contains("mega.nz") || s.contains("mega") || l.contains("mega")
            val isWibu = u.contains("wibufile") || s.contains("wibu") || l.contains("wibufile")
            val isBlog = u.contains("blogger.com") || s.contains("blogspot") || l.contains("blogspot")
            return when {
                isMega -> 10 + res
                isWibu && (u.contains("wibufile.com/video") || u.contains(".mp4")) -> 40 + res
                isWibu -> 50 + res
                isBlog -> 70
                u.contains("filedon") || s.contains("vip") -> 90
                else -> 100
            }
        }
        return raw.sortedBy { score(it) }.distinctBy { it.url }
    }

    private fun resolutionRank(label: String, url: String): Int {
        val t = "$label $url"
        return when {
            t.contains("1080") || t.contains("mp4hd") -> 0
            t.contains("720") -> 1
            t.contains("480") || t.contains("360") -> 2
            else -> 3
        }
    }
}
