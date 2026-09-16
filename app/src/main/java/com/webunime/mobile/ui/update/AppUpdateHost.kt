package com.webunime.mobile.ui.update

import android.app.Activity
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.webunime.mobile.BuildConfig
import com.webunime.mobile.data.AppUpdateChecker
import com.webunime.mobile.data.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cek OTA saat launch (+ tombol manual via [checkNow]).
 * Setelah izin "install unknown apps", lanjut install APK yang tertunda.
 */
@Composable
fun AppUpdateHost(
    autoCheck: Boolean = true,
    checkTrigger: Int = 0,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val checker = remember { AppUpdateChecker(context.applicationContext) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var available by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var pendingApk by remember { mutableStateOf<File?>(null) }
    var autoChecked by remember { mutableStateOf(false) }

    fun toast(msg: String, long: Boolean = false) {
        Toast.makeText(
            context,
            msg,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }

    fun startDownload(info: AppUpdateInfo) {
        if (downloading) return
        downloading = true
        progress = 0
        scope.launch {
            val latest = runCatching { checker.fetchAvailableUpdate() }.getOrNull()
            val toInstall = when {
                latest == null -> info
                latest.versionCode >= info.versionCode -> latest
                else -> info
            }
            val apk = runCatching {
                checker.downloadApk(toInstall) { pct ->
                    progress = pct
                }
            }.getOrElse { err ->
                downloading = false
                toast("Gagal update: ${err.message ?: "download"}", long = true)
                return@launch
            }
            downloading = false
            if (!checker.canInstallPackages()) {
                pendingApk = apk
                toast(
                    "Izinkan install dari sumber ini di pengaturan, lalu kembali ke app.",
                    long = true,
                )
                checker.openInstallPermissionSettings(activity)
                return@launch
            }
            withContext(Dispatchers.Main) {
                toast("Membuka pemasang APK…")
                runCatching { checker.installApk(activity, apk) }
                    .onFailure {
                        toast("Gagal update: ${it.message ?: "install"}", long = true)
                    }
            }
        }
    }

    fun runCheck(fromManual: Boolean) {
        scope.launch {
            if (fromManual) toast("Memeriksa update…")
            val info = runCatching { checker.fetchAvailableUpdate() }.getOrNull()
            if (info == null) {
                if (fromManual) {
                    toast("Sudah versi terbaru (${BuildConfig.VERSION_NAME})", long = true)
                }
                return@launch
            }
            available = info
            dialogVisible = true
        }
    }

    LaunchedEffect(autoCheck) {
        if (autoCheck && !autoChecked) {
            autoChecked = true
            runCheck(fromManual = false)
        }
    }

    LaunchedEffect(checkTrigger) {
        if (checkTrigger > 0) runCheck(fromManual = true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val apk = pendingApk
            if (apk != null && apk.exists() && checker.canInstallPackages()) {
                pendingApk = null
                toast("Membuka pemasang APK…")
                runCatching { checker.installApk(activity, apk) }
                    .onFailure {
                        toast("Gagal update: ${it.message ?: "install"}", long = true)
                    }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (dialogVisible) {
        val info = available
        if (info != null) {
            val notes = info.changelog?.takeIf { it.isNotBlank() }.orEmpty()
            AlertDialog(
                onDismissRequest = { if (!downloading) dialogVisible = false },
                title = { Text("Update tersedia") },
                text = {
                    Text(
                        buildString {
                            append("Versi ${info.versionName} siap diunduh.")
                            if (notes.isNotBlank()) {
                                append("\n")
                                append(notes)
                            }
                            if (downloading) {
                                append("\n\nMengunduh… $progress%")
                            }
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { startDownload(info) },
                        enabled = !downloading,
                    ) { Text(if (downloading) "Mengunduh…" else "Update") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { dialogVisible = false },
                        enabled = !downloading,
                    ) { Text("Nanti") }
                },
            )
        }
    }
}
