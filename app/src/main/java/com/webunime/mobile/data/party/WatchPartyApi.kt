package com.webunime.mobile.data.party

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.webunime.mobile.data.auth.WebunimeApiException
import com.webunime.mobile.data.auth.WebunimeAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = false)
data class RoomMedia(
    val collection: String = "",
    val slug: String = "",
    val episodeSlug: String? = null,
    val serverUrl: String = "",
    val title: String? = null,
    val episodeLabel: String? = null,
)

@JsonClass(generateAdapter = false)
data class RoomPlayback(val playing: Boolean = false, val time: Double = 0.0)

@JsonClass(generateAdapter = false)
data class RoomSnapshot(
    val code: String = "",
    val hostId: Long = 0,
    val hostName: String? = null,
    val hostOnline: Boolean = false,
    val media: RoomMedia = RoomMedia(),
    val playback: RoomPlayback = RoomPlayback(),
    val viewers: List<String> = emptyList(),
    val createdAt: Long = 0,
)

/** Event SSE `/rooms/:code/events` — field terisi sesuai [type]. */
@JsonClass(generateAdapter = false)
data class RoomEvent(
    val type: String = "",
    val room: RoomSnapshot? = null,
    val viewers: List<String>? = null,
    val hostOnline: Boolean? = null,
    val playback: RoomPlayback? = null,
    val media: RoomMedia? = null,
    val reason: String? = null,
)

@JsonClass(generateAdapter = false)
internal data class RoomEnvelope(val room: RoomSnapshot? = null)

@JsonClass(generateAdapter = false)
internal data class RoomsEnvelope(val rooms: List<RoomSnapshot> = emptyList())

/** Event stream ditutup server (404 ruang habis, 401 sesi hilang). */
class RoomStreamRejected(val code: Int, message: String) : IOException(message)

/**
 * Nonton bareng WEBUNIME (`/api/v1/rooms`, sama dengan web).
 * Hanya host yang boleh mengendalikan; penonton mengikuti lewat SSE.
 */
class WatchPartyApi(private val auth: WebunimeAuth) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val roomAdapter = moshi.adapter(RoomEnvelope::class.java)
    private val roomsAdapter = moshi.adapter(RoomsEnvelope::class.java)
    private val eventAdapter = moshi.adapter(RoomEvent::class.java)
    private val mapAdapter = moshi.adapter(Map::class.java)

    /** SSE: tanpa batas baca, server mengirim ping tiap 25 detik. */
    private val streamClient = auth.client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun rooms(): List<RoomSnapshot> =
        roomsAdapter.fromJson(send(Request.Builder().url(url()).get()))?.rooms.orEmpty()

    suspend fun room(code: String): RoomSnapshot =
        parseRoom(send(Request.Builder().url(url(code.trim().uppercase())).get()))

    suspend fun create(
        collection: String,
        slug: String,
        episodeSlug: String?,
        serverUrl: String,
    ): RoomSnapshot = parseRoom(
        send(Request.Builder().url(url()).post(mediaBody(collection, slug, episodeSlug, serverUrl))),
    )

    suspend fun pushPlayback(code: String, playing: Boolean, time: Double) {
        val body = mapAdapter.toJson(mapOf("playing" to playing, "time" to time))
        send(Request.Builder().url(url(code, "playback")).post(body.toRequestBody(JSON)))
    }

    suspend fun pushMedia(
        code: String,
        collection: String,
        slug: String,
        episodeSlug: String?,
        serverUrl: String,
    ) {
        send(
            Request.Builder().url(url(code, "media"))
                .post(mediaBody(collection, slug, episodeSlug, serverUrl)),
        )
    }

    suspend fun end(code: String) {
        send(Request.Builder().url(url(code)).delete())
    }

    /** Aliran event ruang; selesai saat koneksi putus (pemanggil yang menyambung ulang). */
    fun events(code: String): Flow<RoomEvent> = callbackFlow {
        val request = auth.authorize(Request.Builder().url(url(code, "events")))
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call: Call = streamClient.newCall(request)
        val reader = Thread {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        throw RoomStreamRejected(response.code, auth.errorMessage(response, body))
                    }
                    val source = response.body?.source() ?: throw IOException("Stream kosong")
                    val data = StringBuilder()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> {
                                if (data.isNotEmpty()) {
                                    runCatching { eventAdapter.fromJson(data.toString()) }
                                        .getOrNull()
                                        ?.let { trySend(it) }
                                    data.clear()
                                }
                            }
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.removePrefix("data:").trimStart())
                            }
                            // Komentar ": ping" dan field lain diabaikan.
                        }
                    }
                }
                close()
            } catch (e: Exception) {
                close(if (call.isCanceled()) null else e)
            }
        }.apply {
            name = "room-events-$code"
            isDaemon = true
            start()
        }
        awaitClose {
            call.cancel()
            reader.interrupt()
        }
    }.flowOn(Dispatchers.IO)

    private fun mediaBody(collection: String, slug: String, episodeSlug: String?, serverUrl: String) =
        mapAdapter.toJson(
            mapOf(
                "collection" to collection,
                "slug" to slug,
                "episodeSlug" to episodeSlug,
                "serverUrl" to serverUrl,
            ),
        ).toRequestBody(JSON)

    private fun parseRoom(body: String): RoomSnapshot =
        roomAdapter.fromJson(body)?.room ?: throw IOException("Respons ruang kosong")

    private suspend fun send(builder: Request.Builder): String = withContext(Dispatchers.IO) {
        auth.client.newCall(auth.authorize(builder).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401) auth.clearSession()
                throw WebunimeApiException(response.code, auth.errorMessage(response, body))
            }
            body
        }
    }

    private fun url(vararg segments: String): HttpUrl =
        auth.origin.newBuilder().addPathSegments("api/v1/rooms").apply {
            segments.forEach { addPathSegment(it) }
        }.build()

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Embed lintas-origin yang tidak bisa dikendalikan — sama dengan web. */
        private val UNSYNCABLE_HOST_RE =
            Regex("""blogger\.com|mega\.(nz|io)|p2pplay\.|barplay\.|p2pstream\.""", RegexOption.IGNORE_CASE)

        fun isSyncableServer(url: String?): Boolean {
            val host = runCatching { java.net.URI(url ?: return false).host }.getOrNull() ?: return false
            return !UNSYNCABLE_HOST_RE.containsMatchIn(host)
        }

        /** Tautan undangan web: `/?nobar=KODE`. */
        fun inviteLink(auth: WebunimeAuth, code: String): String =
            auth.origin.newBuilder().addQueryParameter("nobar", code).build().toString()
    }
}
