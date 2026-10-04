package com.webunime.mobile.data

object PlayerRouter {

    private val filmPrefer = listOf("turbovip", "hydrax", "cast")

    fun preferred(players: List<PlayerServer>, film: Boolean = false): List<PlayerServer> {
        val raw = players.filter { !it.url.isNullOrBlank() }
        if (raw.isEmpty()) return emptyList()
        return if (film) rankFilm(raw) else rankAnime(raw)
    }

    fun pickDefault(players: List<PlayerServer>, film: Boolean = false): PlayerServer? =
        preferred(players, film).firstOrNull()

    fun isDirectMedia(url: String): Boolean {
        val u = url.lowercase()
        if (u.contains("/iframe3/") ||
            u.contains("videonode.") ||
            u.contains("playeriframe") ||
            u.contains("abyssplayer") ||
            u.contains("short.icu") ||
            u.contains("iamcdn") ||
            u.contains("gn1r5n") ||
            u.contains("turbo") ||
            u.contains("emturbovid") ||
            u.contains("playcdn") ||
            u.contains("p2pplay") ||
            u.contains("blogger.com") ||
            u.contains("mega.nz") ||
            u.contains("filedon.co/embed") ||
            u.contains("api.wibufile.com/embed") ||
            u.contains("login.wibufile.com")
        ) {
            return false
        }
        return u.contains(".mp4") || u.contains(".m3u8") || u.contains(".webm") ||
            u.contains("wibufile.com/video")
    }

    private fun rankFilm(raw: List<PlayerServer>): List<PlayerServer> {
        val ranked = filmPrefer.mapNotNull { key ->
            raw.firstOrNull { p -> matchesFilmKey(p, key) }
        }
        val rest = raw.filter { p ->
            ranked.none { it.url == p.url } && !isP2p(p)
        }
        val p2p = raw.filter { isP2p(it) }
        return (ranked + rest + p2p).distinctBy { it.url }
    }

    private fun matchesFilmKey(p: PlayerServer, key: String): Boolean {
        val s = (p.server ?: "").lowercase()
        val l = (p.label ?: "").lowercase()
        val u = (p.url ?: "").lowercase()
        return when (key) {
            "turbovip" ->
                s.contains("turbo") || l.contains("turbo") ||
                    u.contains("turbo") || u.contains("emturbovid")
            "hydrax" ->
                s.contains("hydrax") || l.contains("hydrax") ||
                    u.contains("abyss") || u.contains("/iframe/hydrax") ||
                    u.contains("/iframe3/hydrax")
            "cast" ->
                s.contains("cast") || l.contains("cast") ||
                    u.contains("gn1r5n") || u.contains("/iframe/cast") ||
                    u.contains("/iframe3/cast")
            else -> s.contains(key) || l.contains(key) || u.contains(key)
        }
    }

    private fun isP2p(p: PlayerServer): Boolean {
        val s = (p.server ?: "").lowercase()
        val l = (p.label ?: "").lowercase()
        val u = (p.url ?: "").lowercase()
        return s.contains("p2p") || l.contains("p2p") ||
            u.contains("p2pplay") || u.contains("playcdn") || u.contains("/iframe3/p2p")
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
