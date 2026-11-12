package com.webunime.mobile.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.webunime.mobile.MainActivity
import com.webunime.mobile.R
import com.webunime.mobile.WebunimeApp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL

/** Jenis konten yang punya topic push sendiri di server WEBUNIME. */
enum class ContentKind(val key: String, val label: String) {
    FILM("film", "Film"),
    SERIES("series", "Series"),
    ANIME("anime", "Anime"),
    ;

    val topic: String get() = "konten-baru-$key"
}

/**
 * Push "konten baru" dari server (scripts/notify-new-content.js) lewat topic FCM,
 * jadi tidak perlu akun maupun pendaftaran token. Pilihan per jenis disimpan di perangkat.
 */
class NewContentPush(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("new_content_push", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(ContentKind.entries.associateWith { isEnabled(it) })
    val enabled: StateFlow<Map<ContentKind, Boolean>> = _enabled.asStateFlow()

    private fun isEnabled(kind: ContentKind) = prefs.getBoolean("on_${kind.key}", true)

    fun setEnabled(kind: ContentKind, on: Boolean) {
        prefs.edit().putBoolean("on_${kind.key}", on).apply()
        _enabled.value = _enabled.value + (kind to on)
        apply(kind, on)
    }

    /** Samakan langganan topic dengan pilihan (dipanggil tiap app start; idempoten). */
    fun sync() {
        ContentKind.entries.forEach { apply(it, isEnabled(it)) }
    }

    /**
     * Samakan langganan topic episode favorit dengan daftar favorit akun yang masuk
     * (kosong saat keluar). Yang sudah dilanggan dicatat supaya bisa dilepas lagi.
     */
    fun syncFavoriteTopics(topics: Set<String>) {
        val current = prefs.getStringSet("fav_topics", emptySet()).orEmpty()
        val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return
        (current - topics).forEach { messaging.unsubscribeFromTopic(it) }
        // Subscribe ulang yang lama juga: idempoten dan memulihkan bila token FCM berganti.
        topics.forEach { messaging.subscribeToTopic(it) }
        prefs.edit().putStringSet("fav_topics", topics).apply()
    }

    private fun apply(kind: ContentKind, on: Boolean) {
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            if (on) messaging.subscribeToTopic(kind.topic) else messaging.unsubscribeFromTopic(kind.topic)
        }
    }

    companion object {
        const val CHANNEL_ID = "konten_baru"

        /** Harus sama dengan favoriteTopic() di scripts/notify-new-content.js server. */
        fun favoriteTopic(collection: String, slug: String): String =
            "fav-$collection-${slug.lowercase()}".replace(Regex("[^a-zA-Z0-9\\-_.~%]"), "_").take(900)

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Konten baru", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Film, series, dan anime yang baru ditambahkan"
                },
            )
        }
    }
}

class WebunimeMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] == "fav_episode") {
            handleFavoriteEpisode(data)
            return
        }
        if (data["type"] != "new_content") return
        val kind = ContentKind.entries.firstOrNull { it.key == data["kind"] } ?: return
        // Pilihan bisa dimatikan saat pesan masih di antrean FCM.
        if (!getSharedPreferences("new_content_push", MODE_PRIVATE).getBoolean("on_${kind.key}", true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val title = data["title"].orEmpty().ifBlank { "${kind.label} baru" }
        val body = data["body"].orEmpty()
        val slug = data["slug"]?.takeIf { it.isNotBlank() }
        val collection = data["collection"]?.takeIf { it.isNotBlank() }
        val notifId = "new:${kind.key}:${slug ?: System.currentTimeMillis()}".hashCode()

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (slug != null && collection != null) {
            intent.putExtra(MainActivity.EXTRA_OPEN_COLLECTION, collection)
                .putExtra(MainActivity.EXTRA_OPEN_SLUG, slug)
        }
        val pending = PendingIntent.getActivity(
            this,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NewContentPush.ensureChannel(this)
        val builder = NotificationCompat.Builder(this, NewContentPush.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        data["image"]?.takeIf { it.startsWith("http") }?.let(::downloadBitmap)?.let { poster ->
            builder.setLargeIcon(poster)
        }
        runCatching { NotificationManagerCompat.from(this).notify(notifId, builder.build()) }
    }

    /** Episode baru judul favorit: diteruskan ke EpisodeAlerts supaya masuk kotak notifikasi akun. */
    private fun handleFavoriteEpisode(data: Map<String, String>) {
        val collection = data["collection"]?.takeIf { it.isNotBlank() } ?: return
        val slug = data["slug"]?.takeIf { it.isNotBlank() } ?: return
        val episode = data["episode"]?.toIntOrNull() ?: return
        val app = applicationContext as WebunimeApp
        val poster = data["image"]?.takeIf { it.startsWith("http") }?.let(::downloadBitmap)
        runBlocking {
            app.webAuth.awaitRestored()
            if (app.favorites.items.value.isEmpty()) runCatching { app.favorites.refresh() }
            app.episodeAlerts.onPush(
                collection = collection,
                slug = slug,
                episode = episode,
                label = data["label"]?.takeIf { it.isNotBlank() } ?: "Episode $episode",
                title = data["title"].orEmpty(),
                thumbnail = data["image"],
                poster = poster,
            )
        }
    }

    /** Token tidak dipakai: server mengirim ke topic. */
    override fun onNewToken(token: String) = Unit

    private fun downloadBitmap(url: String): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.inputStream.use { BitmapFactory.decodeStream(it) }.also { conn.disconnect() }
    }.getOrNull()
}
