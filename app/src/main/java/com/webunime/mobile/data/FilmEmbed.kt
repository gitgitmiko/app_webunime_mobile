package com.webunime.mobile.data

/**
 * Port ringkas dari pemutar TV untuk film LK21 (Hydrax / TurboVIP / iframe3).
 */
object FilmEmbed {

    const val ABYSS_WRAPPER_BASE = "https://playeriframe.sbs/"
    const val VIDEONODE_WRAPPER_BASE = "https://videonode.de/"

    private val wrapperHost = Regex("(playeriframe|videonode)", RegexOption.IGNORE_CASE)

    fun isIframe3(url: String): Boolean {
        if (!wrapperHost.containsMatchIn(url)) return false
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        return path.startsWith("/iframe3/", ignoreCase = true)
    }

    fun parseIframe3(url: String): Pair<String, String>? {
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        val m = Regex(
            """^/iframe3/(hydrax|turbovip|turbo|cast|p2p)/([^/]+)/?$""",
            RegexOption.IGNORE_CASE,
        ).find(path) ?: return null
        val server = m.groupValues[1].lowercase().let { if (it == "turbo") "turbovip" else it }
        val id = m.groupValues[2].trim()
        if (id.isBlank()) return null
        return server to id
    }

    fun wrapperOrigin(url: String): String {
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        val host = uri?.host?.takeIf { it.isNotBlank() }
        val scheme = uri?.scheme?.takeIf { it.isNotBlank() } ?: "https"
        return if (host != null) "$scheme://$host/" else VIDEONODE_WRAPPER_BASE
    }

    fun isAbyss(url: String): Boolean {
        val h = hostOf(url)
        return h.contains("abyss") || h.contains("iamcdn") || h.contains("short.icu")
    }

    fun isTurbo(url: String): Boolean {
        val h = hostOf(url)
        if (h.contains("playeriframe") || h.contains("videonode")) return false
        return h.contains("turbo") || h.contains("emturbo")
    }

    fun mapLegacyIframe(url: String): String? {
        val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty()
        val m = Regex(
            """^/iframe/(hydrax|turbovip|turbo|cast|p2p)/([^/]+)/?$""",
            RegexOption.IGNORE_CASE,
        ).find(path) ?: return null
        val id = m.groupValues[2].trim()
        if (id.isBlank()) return null
        return when (m.groupValues[1].lowercase()) {
            "hydrax" -> "https://abyssplayer.com/$id"
            "turbovip", "turbo" -> "https://emturbovid.com/t/$id"
            "cast" -> "https://gn1r5n.org/e/$id"
            "p2p" -> "https://playcdn.de/video.php?id=$id&t=1"
            else -> null
        }
    }

    fun iframe3BootstrapHtml(server: String, id: String): String {
        val safeServer = server.filter { it.isLetterOrDigit() }.ifBlank { "turbovip" }
        val safeId = id
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("</", "<\\/")
        return """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            html,body{margin:0;height:100%;width:100%;background:#000;color:#ddd;
              font-family:sans-serif;display:flex;align-items:center;justify-content:center}
            iframe#wuWarm{position:absolute;width:1px;height:1px;opacity:0;pointer-events:none;border:0}
            </style></head>
            <body><div id="msg">Memuat server…</div>
            <iframe id="wuWarm" src="/iframe3/$safeServer/$safeId"></iframe>
            <script>
            (function(){
              var doneOnce=false;
              var body='host='+encodeURIComponent('$safeServer')+
                '&id='+encodeURIComponent('$safeId');
              function done(url){
                if(doneOnce) return;
                doneOnce=true;
                try{ WebunimePlayback.onResolvedEmbed(url||''); }catch(e){}
              }
              function resolve(){
                fetch('/api.php',{
                  method:'POST',
                  headers:{
                    'Content-Type':'application/x-www-form-urlencoded',
                    'Accept':'application/json, text/plain, */*'
                  },
                  body:body,
                  credentials:'include'
                }).then(function(r){
                  if(!r.ok) throw new Error('http');
                  return r.json();
                }).then(function(j){
                  done(j && j.embedUrl ? String(j.embedUrl) : '');
                }).catch(function(){ done(''); });
              }
              var warm=document.getElementById('wuWarm');
              var kicked=false;
              function kick(){
                if(kicked) return;
                kicked=true;
                setTimeout(resolve, 400);
              }
              if(warm){
                warm.addEventListener('load', kick);
                warm.addEventListener('error', kick);
              }
              setTimeout(kick, 2200);
            })();
            </script></body></html>
        """.trimIndent()
    }

    fun abyssWrapperHtml(embedUrl: String): String = iframePlayerHtml(embedUrl)

    fun turboWrapperHtml(embedUrl: String): String = iframePlayerHtml(embedUrl)

    private fun iframePlayerHtml(embedUrl: String): String {
        val safe = embedUrl
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
        return """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <style>
            html,body{margin:0;padding:0;height:100%;width:100%;background:#000;overflow:hidden}
            iframe#wuEmbed{
              border:0!important;outline:0!important;margin:0!important;padding:0!important;
              width:100vw!important;height:100vh!important;display:block;background:#000!important;
            }
            </style></head>
            <body>
            <iframe id="wuEmbed" src="$safe"
              allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
              allowfullscreen scrolling="no"></iframe>
            </body></html>
        """.trimIndent()
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
}
