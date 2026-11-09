package com.webunime.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.webunime.mobile.data.Paged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Isi baris beranda yang bertambah per halaman: setiap kali digeser sampai mentok,
 * [loadMore] mengambil page berikutnya dari API dan menyambungkannya.
 */
class PagedRowState<T>(
    initial: List<T>,
    private val keyOf: (T) -> String,
    private val scope: CoroutineScope,
    private val fetch: suspend (page: Int) -> Paged<T>,
) {
    var items by mutableStateOf(initial.distinctBy(keyOf))
        private set
    var loading by mutableStateOf(false)
        private set
    private var hasMore = initial.isNotEmpty()
    private var page = 1

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        scope.launch {
            try {
                val seen = items.mapTo(HashSet(), keyOf)
                // Page yang isinya sudah tampil semua (katalog bergeser) dilewati,
                // supaya baris tidak berhenti di tengah.
                repeat(MAX_PAGES_PER_LOAD) {
                    val res = fetch(page + 1)
                    page += 1
                    val fresh = res.items.filter { seen.add(keyOf(it)) }
                    hasMore = res.hasMore && res.items.isNotEmpty()
                    if (fresh.isNotEmpty()) items = items + fresh
                    if (fresh.isNotEmpty() || !hasMore) return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Gagal: dicoba lagi saat baris digeser sampai mentok berikutnya.
            } finally {
                loading = false
            }
        }
    }

    private companion object {
        const val MAX_PAGES_PER_LOAD = 3
    }
}

@Composable
fun <T> rememberPagedRow(
    initial: List<T>,
    keyOf: (T) -> String,
    fetch: suspend (page: Int) -> Paged<T>,
): PagedRowState<T> {
    val scope = rememberCoroutineScope()
    return remember(initial) { PagedRowState(initial, keyOf, scope, fetch) }
}

@Composable
fun <T> PagedPosterRow(
    state: PagedRowState<T>,
    titleOf: (T) -> String,
    thumbOf: (T) -> String?,
    subtitleOf: (T) -> String? = { null },
    onClick: (T) -> Unit,
) {
    HorizontalPosterRow(
        items = state.items,
        titleOf = titleOf,
        thumbOf = thumbOf,
        subtitleOf = subtitleOf,
        onClick = onClick,
        loadingMore = state.loading,
        onReachEnd = state::loadMore,
    )
}
