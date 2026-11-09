package com.webunime.mobile.ui.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.webunime.mobile.data.party.RoomRole
import com.webunime.mobile.data.party.RoomState
import com.webunime.mobile.data.party.WatchSyncAgent
import org.json.JSONObject
import kotlin.math.abs

/**
 * Jembatan ruang nonton bareng ↔ pemutar aktif.
 * ExoPlayer dikendalikan langsung; WebView lewat [WatchSyncAgent] (sama dengan web).
 */
class PlayerSync {
    /** null = tidak sedang di ruang. */
    var role by mutableStateOf<RoomRole?>(null)
    /** Video sudah terdeteksi di pemutar (kunci penonton aktif setelah ini). */
    var frameReady by mutableStateOf(false)
    /** Autoplay diblokir: penonton perlu mengetuk untuk menyalakan suara. */
    var needsTap by mutableStateOf(false)
    var muted by mutableStateOf(false)

    var onReport: (playing: Boolean, time: Double) -> Unit = { _, _ -> }

    private val main = Handler(Looper.getMainLooper())
    private var exo: ExoPlayer? = null
    private var web: WebView? = null
    private var resumeAt: Double? = null
    private var resumeToken = 0L
    private var exoResumeToken = 0L
    private var lastSeek = 0L

    fun attach(player: ExoPlayer) {
        exo = player
        web = null
        player.volume = if (muted) 0f else 1f
    }

    fun attach(view: WebView) {
        web = view
        exo = null
    }

    fun detach(player: ExoPlayer) {
        if (exo === player) exo = null
    }

    fun detach(view: WebView) {
        if (web === view) web = null
    }

    /** Pemutar baru dimuat (ganti episode/server): tunggu video terdeteksi lagi. */
    fun onNewMedia() {
        frameReady = false
    }

    /** Host ganti server di episode sama / bergabung ulang: lanjut dari posisi ini. */
    fun hostResume(at: Double) {
        resumeAt = at
        resumeToken = SystemClock.elapsedRealtime()
    }

    fun leave() {
        role = null
        resumeAt = null
        needsTap = false
        web?.evaluateJavascript(WatchSyncAgent.command("""{"cmd":"leave"}"""), null)
    }

    fun toggleMute() {
        muted = !muted
        needsTap = false
        exo?.volume = if (muted) 0f else 1f
        val cmd = JSONObject().put("cmd", "volume").put("muted", muted).put("volume", 1.0)
        web?.evaluateJavascript(WatchSyncAgent.command(cmd.toString()), null)
    }

    /** Dipanggil tiap tick dan setiap status ruang berubah; idempoten. */
    fun sync(state: RoomState) {
        val r = role ?: return
        exo?.let { return if (r == RoomRole.Host) syncHostExo(it) else enforceExo(it, state) }
        val view = web ?: return
        val msg = JSONObject().put("cmd", "sync").put("role", if (r == RoomRole.Host) "host" else "viewer")
        if (r == RoomRole.Viewer) {
            msg.put(
                "playback",
                JSONObject().put("playing", state.playback.playing).put("time", state.position()),
            )
        } else {
            // Dikirim tiap tick; agen menerapkannya sekali per token (frame bisa belum siap).
            resumeAt?.let { msg.put("resumeAt", it).put("resumeToken", resumeToken) }
        }
        view.evaluateJavascript(WatchSyncAgent.command(msg.toString()), null)
    }

    private fun syncHostExo(player: ExoPlayer) {
        if (player.duration == C.TIME_UNSET || player.playbackState == Player.STATE_IDLE) return
        frameReady = true
        val at = resumeAt
        if (at != null && exoResumeToken != resumeToken) {
            exoResumeToken = resumeToken
            if (at > 3) player.seekTo((at * 1000).toLong())
            return
        }
        val playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        onReport(playing, player.currentPosition / 1000.0)
    }

    private fun enforceExo(player: ExoPlayer, state: RoomState) {
        if (player.duration == C.TIME_UNSET) return
        frameReady = true
        val desired = state.playback.playing
        if (desired != player.playWhenReady) player.playWhenReady = desired
        val target = state.position()
        val limit = if (desired) DRIFT_SECONDS else 0.5
        val now = SystemClock.elapsedRealtime()
        if (abs(player.currentPosition / 1000.0 - target) > limit && now - lastSeek > SEEK_COOLDOWN_MS) {
            lastSeek = now
            player.seekTo((target * 1000).toLong())
        }
    }

    /** `WebunimeRoom.post` dari frame teratas WebView (thread JavaBridge). */
    val bridge = object {
        @JavascriptInterface
        fun post(json: String) {
            val d = runCatching { JSONObject(json) }.getOrNull() ?: return
            main.post { onAgentMessage(d) }
        }
    }

    private fun onAgentMessage(d: JSONObject) {
        if (role == null) return
        when (d.optString("status")) {
            "ready" -> frameReady = true
            "muted", "blocked" -> needsTap = true
        }
        val report = d.optJSONObject("report") ?: return
        frameReady = true
        if (role == RoomRole.Host) {
            onReport(report.optBoolean("playing"), report.optDouble("time", Double.NaN))
        }
    }

    private companion object {
        const val DRIFT_SECONDS = 2.5
        const val SEEK_COOLDOWN_MS = 3_000L
    }
}
