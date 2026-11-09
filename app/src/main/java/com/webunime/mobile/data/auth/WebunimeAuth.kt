package com.webunime.mobile.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

private val Context.webunimeSession by preferencesDataStore(name = "webunime_session")

@JsonClass(generateAdapter = false)
data class WebUser(
    val id: Long = 0,
    val email: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val isAdmin: Boolean = false,
) {
    fun label(): String = displayName?.takeIf { it.isNotBlank() } ?: username ?: "Pengguna"
}

@JsonClass(generateAdapter = false)
internal data class UserEnvelope(val user: WebUser? = null, val error: String? = null)

@JsonClass(generateAdapter = false)
internal data class ErrorEnvelope(val error: String? = null)

/** Gagal dari server WEBUNIME dengan pesan yang bisa ditampilkan apa adanya. */
class WebunimeApiException(val code: Int, message: String) : Exception(message)

/**
 * Akun WEBUNIME (sama dengan web): `/api/auth` di server, sesi 14 hari.
 * Server menaruh sesi di cookie `webunime_sid`; app menyimpannya lalu mengirim
 * sebagai `Authorization: Bearer` (didukung `readSid` di server).
 */
class WebunimeAuth(
    context: Context,
    apiBase: String,
) {
    private val store = context.applicationContext.webunimeSession

    val origin: HttpUrl = apiBase.toHttpUrl().resolve("/")!!

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val userAdapter = moshi.adapter(UserEnvelope::class.java)
    private val errorAdapter = moshi.adapter(ErrorEnvelope::class.java)

    private val _user = MutableStateFlow<WebUser?>(null)
    val user: StateFlow<WebUser?> = _user.asStateFlow()

    @Volatile
    private var token: String? = null

    val isSignedIn: Boolean get() = token != null && _user.value != null

    /** Muat sesi tersimpan lalu cek ke server; 401 → sesi dibuang. */
    suspend fun restore() {
        val saved = store.data.map { it[KEY_TOKEN] }.first() ?: return
        token = saved
        runCatching { refresh() }
    }

    suspend fun refresh(): WebUser? {
        if (token == null) return null
        return try {
            val user = call(Request.Builder().url(authUrl("me")).get()).user
            _user.value = user
            user
        } catch (e: WebunimeApiException) {
            if (e.code == 401) clearSession()
            throw e
        }
    }

    suspend fun login(login: String, password: String): WebUser {
        val body = mapOf("login" to login.trim(), "password" to password)
        return signIn(Request.Builder().url(authUrl("login")).post(json(body)))
    }

    suspend fun register(
        displayName: String,
        username: String,
        email: String,
        password: String,
    ): WebUser {
        val body = mapOf(
            "displayName" to displayName.trim(),
            "username" to username.trim().lowercase(),
            "email" to email.trim(),
            "password" to password,
        )
        return signIn(Request.Builder().url(authUrl("register")).post(json(body)))
    }

    suspend fun logout() {
        runCatching { call(Request.Builder().url(authUrl("logout")).post(json(emptyMap<String, String>()))) }
        clearSession()
    }

    /** Presence seperti web (hitungan "user sedang login" di dasbor admin). */
    suspend fun heartbeat() {
        if (token == null) return
        try {
            call(Request.Builder().url(authUrl("heartbeat")).post(json(emptyMap<String, String>())))
        } catch (e: WebunimeApiException) {
            // Sesi diakhiri admin atau kedaluwarsa.
            if (e.code == 401) clearSession()
        }
    }

    /** Tambahkan sesi ke request API lain (nonton bareng). */
    fun authorize(builder: Request.Builder): Request.Builder = builder.apply {
        token?.let { header("Authorization", "Bearer $it") }
        header("User-Agent", USER_AGENT)
        header("Accept", "application/json")
    }

    /** Pesan error server (`{"error": "..."}`) atau fallback. */
    fun errorMessage(response: Response, body: String): String =
        runCatching { errorAdapter.fromJson(body)?.error }.getOrNull()
            ?: "Gagal (HTTP ${response.code})"

    suspend fun clearSession() {
        token = null
        _user.value = null
        store.edit { it.remove(KEY_TOKEN) }
    }

    private suspend fun signIn(builder: Request.Builder): WebUser {
        val (envelope, sid) = withContext(Dispatchers.IO) {
            client.newCall(authorize(builder).build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw WebunimeApiException(response.code, errorMessage(response, body))
                }
                val sid = response.headers("Set-Cookie")
                    .firstNotNullOfOrNull { SID_RE.find(it)?.groupValues?.get(1) }
                    ?: throw IOException("Sesi tidak diterima dari server")
                parseUser(body) to sid.lowercase()
            }
        }
        val user = envelope.user ?: throw IOException("Respons akun kosong")
        token = sid
        _user.value = user
        store.edit { it[KEY_TOKEN] = sid }
        return user
    }

    private suspend fun call(builder: Request.Builder): UserEnvelope = withContext(Dispatchers.IO) {
        client.newCall(authorize(builder).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw WebunimeApiException(response.code, errorMessage(response, body))
            }
            parseUser(body)
        }
    }

    private fun parseUser(body: String): UserEnvelope =
        runCatching { userAdapter.fromJson(body) }.getOrNull() ?: UserEnvelope()

    private fun authUrl(path: String): HttpUrl =
        origin.newBuilder().addPathSegments("api/auth/$path").build()

    private fun json(body: Map<String, String>) =
        moshi.adapter(Map::class.java).toJson(body).toRequestBody(JSON)

    companion object {
        const val USER_AGENT = "WEBUNIME-Mobile/0.5"
        private val KEY_TOKEN = stringPreferencesKey("sid")
        private val SID_RE = Regex("""(?:^|;\s*)webunime_sid=([a-fA-F0-9]{64})(?:;|$)""")
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
