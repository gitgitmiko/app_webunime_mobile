package com.webunime.mobile.data.library

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.webunime.mobile.data.auth.WebunimeAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = false)
data class FavoriteItem(
    /** Koleksi asli di server (anime, anime-movies, series, movies, …). */
    val collection: String = "",
    val slug: String = "",
    val title: String? = null,
    val thumbnail: String? = null,
    val createdAt: String? = null,
) {
    val key: String get() = "$collection:$slug"

    fun displayTitle(): String = title?.takeIf { it.isNotBlank() } ?: slug

    /** Koleksi untuk navigasi app: anime-movies dibuka lewat rute anime. */
    fun appCollection(): String = if (collection == "anime-movies") "anime" else collection

    /** Judul berepisode yang dipantau untuk notifikasi episode baru. */
    fun hasEpisodes(): Boolean = collection == "anime" || collection == "series"

    fun isAnimeCatalog(): Boolean = collection == "anime" || collection == "anime-movies"
}

@JsonClass(generateAdapter = false)
internal data class FavoritesEnvelope(val items: List<FavoriteItem> = emptyList())

/**
 * Favorit akun WEBUNIME (`/api/v1/me/favorites`) — sama dengan web,
 * jadi favorit yang ditambah di web muncul di app dan sebaliknya.
 */
class FavoritesRepository(private val auth: WebunimeAuth) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listAdapter = moshi.adapter(FavoritesEnvelope::class.java)
    private val mapAdapter = moshi.adapter(Map::class.java)

    private val _items = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val items: StateFlow<List<FavoriteItem>> = _items.asStateFlow()

    /** True setelah daftar akun berhasil dimuat; sebelum itu [items] kosong belum tentu berarti tak ada favorit. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    suspend fun refresh(): List<FavoriteItem> {
        if (auth.user.value == null) {
            _items.value = emptyList()
            return emptyList()
        }
        val list = listAdapter.fromJson(auth.send(Request.Builder().url(url()).get()))?.items.orEmpty()
        _items.value = list
        _loaded.value = true
        return list
    }

    fun isFavorite(collection: String, slug: String): Boolean =
        _items.value.any { it.collection == collection && it.slug == slug.lowercase() }

    suspend fun add(collection: String, slug: String, title: String?, thumbnail: String?) {
        val body = mapAdapter.toJson(
            mapOf("collection" to collection, "slug" to slug, "title" to title, "thumbnail" to thumbnail),
        )
        auth.send(Request.Builder().url(url()).post(body.toRequestBody(JSON)))
        val item = FavoriteItem(collection, slug.lowercase(), title, thumbnail)
        _items.update { list -> listOf(item) + list.filterNot { it.key == item.key } }
        runCatching { refresh() }
    }

    suspend fun remove(collection: String, slug: String) {
        auth.send(Request.Builder().url(url(collection, slug.lowercase())).delete())
        _items.update { list -> list.filterNot { it.collection == collection && it.slug == slug.lowercase() } }
    }

    fun clear() {
        _items.value = emptyList()
        _loaded.value = false
    }

    private fun url(vararg segments: String): HttpUrl =
        auth.origin.newBuilder().addPathSegments("api/v1/me/favorites").apply {
            segments.forEach { addPathSegment(it) }
        }.build()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
