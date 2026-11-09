package com.webunime.mobile.data.library

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.webunime.mobile.MainActivity
import com.webunime.mobile.R
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.CatalogApi
import com.webunime.mobile.data.auth.WebunimeAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Satu notifikasi "episode baru" di kotak notifikasi akun. */
data class EpisodeAlert(
    val id: String,
    val collection: String,
    val slug: String,
    val title: String,
    val thumbnail: String?,
    val episode: Int,
    /** Jumlah episode baru sejak pengecekan terakhir. */
    val newCount: Int,
    val at: Long,
    val read: Boolean,
) {
    fun appCollection(): String = if (collection == "anime-movies") "anime" else collection

    fun message(): String =
        if (newCount > 1) "$newCount episode baru, terbaru Episode $episode" else "Episode $episode sudah rilis"
}

/**
 * Pantau favorit berepisode (anime & series) dan beri tahu saat episode baru rilis.
 * Server belum punya push, jadi app mengecek sendiri: saat dibuka dan tiap 3 jam di
 * latar belakang. Patokan & kotak notifikasi disimpan per akun di perangkat.
 */
class EpisodeAlerts(
    context: Context,
    private val auth: WebunimeAuth,
    private val favorites: FavoritesRepository,
    private val catalog: CatalogApi,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("episode_alerts", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _inbox = MutableStateFlow<List<EpisodeAlert>>(emptyList())
    val inbox: StateFlow<List<EpisodeAlert>> = _inbox.asStateFlow()

    /** Muat kotak notifikasi akun yang sedang masuk (kosong bila keluar). */
    fun loadInbox() {
        val uid = auth.user.value?.id
        _inbox.value = if (uid == null) emptyList() else readInbox(uid)
    }

    fun markAllRead() {
        val uid = auth.user.value?.id ?: return
        writeInbox(uid, _inbox.value.map { it.copy(read = true) })
    }

    fun markRead(id: String) {
        val uid = auth.user.value?.id ?: return
        writeInbox(uid, _inbox.value.map { if (it.id == id) it.copy(read = true) else it })
        NotificationManagerCompat.from(appContext).cancel(id.hashCode())
    }

    fun clearInbox() {
        val uid = auth.user.value?.id ?: return
        writeInbox(uid, emptyList())
    }

    /** Patokan judul yang baru difavoritkan, supaya episode lama tidak dianggap baru. */
    suspend fun remember(collection: String, slug: String, latestEpisode: Int?) {
        val uid = auth.user.value?.id ?: return
        val ep = latestEpisode ?: return
        mutex.withLock {
            val base = readBaseline(uid)
            base.put(key(collection, slug), ep)
            prefs.edit().putString("base_$uid", base.toString()).apply()
        }
    }

    /** Cek semua favorit; return jumlah notifikasi baru. */
    suspend fun check(): Int = mutex.withLock {
        val user = auth.user.value ?: return@withLock 0
        val watched = favorites.refresh().filter { it.hasEpisodes() }
        val base = readBaseline(user.id)
        val limit = Semaphore(4)
        val latest = coroutineScope {
            watched.map { fav ->
                async {
                    limit.withPermit {
                        val detail = try {
                            catalog.anime(fav.slug, fav.appCollection())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            return@withPermit null
                        }
                        val max = detail.episodes.mapNotNull { it.episode }.maxOrNull() ?: return@withPermit null
                        Triple(fav, detail, max)
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val fresh = mutableListOf<EpisodeAlert>()
        val now = System.currentTimeMillis()
        for ((fav, detail, max) in latest) {
            val k = key(fav.collection, fav.slug)
            val known = if (base.has(k)) base.optInt(k) else null
            if (known != null && max > known) {
                val newCount = detail.episodes.count { (it.episode ?: 0) > known }.coerceAtLeast(1)
                fresh += EpisodeAlert(
                    id = "$k#$max",
                    collection = fav.collection,
                    slug = fav.slug,
                    title = detail.displayTitle().takeIf { it != "Tanpa judul" } ?: fav.displayTitle(),
                    thumbnail = detail.thumbnail ?: fav.thumbnail,
                    episode = max,
                    newCount = newCount,
                    at = now,
                    read = false,
                )
            }
            if (known == null || max > known) base.put(k, max)
        }
        // Judul yang tidak lagi difavoritkan tidak perlu patokan.
        val keep = watched.map { key(it.collection, it.slug) }.toSet()
        base.keys().asSequence().toList().filterNot { it in keep }.forEach { base.remove(it) }
        prefs.edit().putString("base_${user.id}", base.toString()).apply()

        if (fresh.isNotEmpty()) {
            val inbox = (fresh + readInbox(user.id).filterNot { old -> fresh.any { it.id == old.id } })
                .take(MAX_INBOX)
            writeInbox(user.id, inbox)
            fresh.forEach(::notify)
        } else if (auth.user.value?.id == user.id) {
            _inbox.value = readInbox(user.id)
        }
        fresh.size
    }

    private fun notify(alert: EpisodeAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(appContext)
        val intent = Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_COLLECTION, alert.appCollection())
            .putExtra(MainActivity.EXTRA_OPEN_SLUG, alert.slug)
            .putExtra(MainActivity.EXTRA_OPEN_EPISODE, alert.episode)
            .putExtra(MainActivity.EXTRA_ALERT_ID, alert.id)
        val pending = PendingIntent.getActivity(
            appContext,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alert.title)
            .setContentText(alert.message())
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(alert.id.hashCode(), notification) }
    }

    private fun readBaseline(uid: Long): JSONObject =
        runCatching { JSONObject(prefs.getString("base_$uid", null) ?: "{}") }.getOrDefault(JSONObject())

    private fun readInbox(uid: Long): List<EpisodeAlert> {
        val arr = runCatching { JSONArray(prefs.getString("inbox_$uid", null) ?: "[]") }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            EpisodeAlert(
                id = o.optString("id"),
                collection = o.optString("collection"),
                slug = o.optString("slug"),
                title = o.optString("title"),
                thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
                episode = o.optInt("episode"),
                newCount = o.optInt("newCount", 1),
                at = o.optLong("at"),
                read = o.optBoolean("read"),
            )
        }
    }

    private fun writeInbox(uid: Long, items: List<EpisodeAlert>) {
        val arr = JSONArray()
        items.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("collection", a.collection)
                    .put("slug", a.slug)
                    .put("title", a.title)
                    .put("thumbnail", a.thumbnail ?: "")
                    .put("episode", a.episode)
                    .put("newCount", a.newCount)
                    .put("at", a.at)
                    .put("read", a.read),
            )
        }
        prefs.edit().putString("inbox_$uid", arr.toString()).apply()
        if (auth.user.value?.id == uid) _inbox.value = items
    }

    private fun key(collection: String, slug: String) = "$collection:${slug.lowercase()}"

    companion object {
        const val CHANNEL_ID = "episode_baru"
        private const val MAX_INBOX = 50
        private const val WORK_NAME = "favorite-episode-check"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Episode baru", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Episode baru dari anime dan series favorit"
                },
            )
        }

        /** Cek berkala di latar belakang; worker diam bila belum login. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

class EpisodeCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WebunimeApp
        app.webAuth.awaitRestored()
        if (app.webAuth.user.value == null) return Result.success()
        return try {
            app.episodeAlerts.check()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
