package com.webunime.mobile.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun WatchGateDialog(
    keys: Int,
    isPremium: Boolean,
    onWatchAd: () -> Unit,
    onGoAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPremium) "Premium" else "Butuh kunci") },
        text = {
            Text(
                if (isPremium) {
                    "Akun Premium aktif."
                } else {
                    "Kunci tersisa: $keys.\nNonton 1 episode = 1 kunci.\nDapatkan kunci dengan nonton iklan atau upgrade Premium."
                },
            )
        },
        confirmButton = {
            if (!isPremium && keys <= 0) {
                TextButton(onClick = onWatchAd) { Text("Nonton iklan") }
            }
        },
        dismissButton = {
            TextButton(onClick = onGoAccount) { Text("Ke Akun") }
            TextButton(onClick = onDismiss) { Text("Tutup") }
        },
    )
}
