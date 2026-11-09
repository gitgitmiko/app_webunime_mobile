package com.webunime.mobile.data.party

import android.os.SystemClock
import com.webunime.mobile.data.auth.WebunimeApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class RoomRole { Host, Viewer }

data class RoomState(
    val code: String,
    val role: RoomRole,
    val hostName: String?,
    val hostOnline: Boolean,
    val viewers: List<String>,
    val media: RoomMedia,
    val playback: RoomPlayback,
    /** Jam lokal saat [playback] diterima, untuk menghitung posisi berjalan. */
    val playbackAt: Long,
    /** Diisi saat ruang berakhir: "ended", "host-away", "replaced", "gone". */
    val closedReason: String? = null,
) {
    fun position(now: Long = SystemClock.elapsedRealtime()): Double =
        if (playback.playing) playback.time + (now - playbackAt) / 1000.0 else playback.time
}

/**
 * Satu ruang nonton bareng yang sedang diikuti app ini.
 * Port dari logika `room` di web: SSE untuk penonton, host melaporkan posisi.
 */
class WatchPartySession(
    private val api: WatchPartyApi,
    snapshot: RoomSnapshot,
    myUserId: Long,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        RoomState(
            code = snapshot.code,
            role = if (snapshot.hostId == myUserId) RoomRole.Host else RoomRole.Viewer,
            hostName = snapshot.hostName,
            hostOnline = snapshot.hostOnline,
            viewers = snapshot.viewers,
            media = snapshot.media,
            playback = snapshot.playback,
            playbackAt = SystemClock.elapsedRealtime(),
        ),
    )
    val state: StateFlow<RoomState> = _state.asStateFlow()

    val code: String get() = _state.value.code
    val isHost: Boolean get() = _state.value.role == RoomRole.Host

    private var eventsJob: Job? = null
    private var lastPushAt = 0L

    fun start() {
        if (eventsJob != null) return
        eventsJob = scope.launch { runEvents() }
    }

    /** Lepas dari ruang di perangkat ini. Ruang tetap hidup untuk yang lain. */
    fun leave() {
        eventsJob?.cancel()
        eventsJob = null
    }

    /** Host mengakhiri ruang untuk semua penonton. */
    suspend fun end() {
        leave()
        runCatching { api.end(code) }
    }

    /** Laporan pemutar host; dikirim bila play/pause berubah, lompat >2 dtk, atau tiap 5 dtk. */
    fun reportPlayback(playing: Boolean, time: Double) {
        if (!isHost || !time.isFinite() || time < 0) return
        val now = SystemClock.elapsedRealtime()
        val current = _state.value
        val flipped = playing != current.playback.playing
        val jumped = abs(time - current.position(now)) > 2
        val due = now - lastPushAt >= HEARTBEAT_MS
        if (!flipped && !jumped && !due) return
        lastPushAt = now
        _state.update { it.copy(playback = RoomPlayback(playing, time), playbackAt = now) }
        scope.launch { runCatching { api.pushPlayback(code, playing, time) } }
    }

    /**
     * Host mengganti episode/server → semua penonton ikut.
     * Return posisi lanjutan untuk pemutar host (ganti server di episode sama),
     * atau null bila media tidak berubah.
     */
    suspend fun pushMedia(
        collection: String,
        slug: String,
        episodeSlug: String?,
        serverUrl: String,
    ): Result<Double?> {
        if (!isHost) return Result.success(null)
        val prev = _state.value.media
        val sameEpisode = prev.slug == slug && prev.episodeSlug == episodeSlug
        if (sameEpisode && prev.serverUrl == serverUrl) return Result.success(null)
        val resumeAt = if (sameEpisode) _state.value.position() else 0.0
        _state.update {
            it.copy(
                media = prev.copy(
                    collection = collection,
                    slug = slug,
                    episodeSlug = episodeSlug,
                    serverUrl = serverUrl,
                ),
                playback = if (sameEpisode) it.playback else it.playback.copy(time = 0.0),
                playbackAt = SystemClock.elapsedRealtime(),
            )
        }
        return runCatching {
            api.pushMedia(code, collection, slug, episodeSlug, serverUrl)
            resumeAt
        }
    }

    private suspend fun runEvents() {
        while (true) {
            try {
                api.events(code).collect(::onEvent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RoomStreamRejected) {
                if (e.code == 404) return close("gone")
                if (e.code == 401) return close("auth")
            } catch (_: Exception) {
                // Putus biasa: cek ruang masih ada, lalu sambung ulang.
            }
            if (_state.value.closedReason != null) return
            val alive = try {
                api.room(code)
                true
            } catch (e: WebunimeApiException) {
                e.code != 404
            } catch (_: Exception) {
                true
            }
            if (!alive) return close("gone")
            delay(RECONNECT_MS)
        }
    }

    private fun onEvent(event: RoomEvent) {
        val now = SystemClock.elapsedRealtime()
        _state.update { s ->
            when (event.type) {
                "state" -> {
                    val room = event.room ?: return@update s
                    s.copy(
                        hostName = room.hostName,
                        hostOnline = room.hostOnline,
                        viewers = room.viewers,
                        // Tersambung ulang: media bisa berubah selama koneksi putus.
                        media = if (s.role == RoomRole.Viewer) room.media else s.media,
                        playback = if (s.role == RoomRole.Viewer) room.playback else s.playback,
                        playbackAt = if (s.role == RoomRole.Viewer) now else s.playbackAt,
                    )
                }
                "presence" -> s.copy(
                    viewers = event.viewers ?: s.viewers,
                    hostOnline = event.hostOnline ?: s.hostOnline,
                )
                "playback" -> if (s.role == RoomRole.Viewer && event.playback != null) {
                    s.copy(playback = event.playback, playbackAt = now)
                } else {
                    s
                }
                "media" -> if (s.role == RoomRole.Viewer && event.media != null) {
                    s.copy(
                        media = event.media,
                        playback = event.playback ?: s.playback,
                        playbackAt = now,
                    )
                } else {
                    s
                }
                "closed" -> s.copy(closedReason = event.reason ?: "ended")
                else -> s
            }
        }
        if (event.type == "closed") leave()
    }

    private fun close(reason: String) {
        _state.update { it.copy(closedReason = reason) }
        eventsJob = null
    }

    companion object {
        private const val HEARTBEAT_MS = 5_000L
        private const val RECONNECT_MS = 3_000L
    }
}
