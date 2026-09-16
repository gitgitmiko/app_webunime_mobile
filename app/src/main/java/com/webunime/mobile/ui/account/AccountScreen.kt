package com.webunime.mobile.ui.account

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webunime.mobile.BuildConfig
import com.webunime.mobile.WebunimeApp
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(contentPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val activity = context as Activity
    val app = context.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    val profile by app.userRepository.profileFlow.collectAsStateWithLifecycle(
        initialValue = null,
    )
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        scope.launch {
            busy = true
            val res = app.authRepository.handleGoogleSignInResult(result.data)
            res.onSuccess {
                app.userRepository.pullCloudIfSignedIn()
                message = "Login berhasil"
            }.onFailure {
                message = it.message ?: "Login gagal"
            }
            busy = false
        }
    }

    val p = profile
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Akun", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p?.uid != null) {
                    Text(p.displayName ?: "Pengguna", style = MaterialTheme.typography.titleMedium)
                    Text(p.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Belum login", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ekonomi lokal aktif. Login Google untuk sync cloud.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatRow("Level", "${p?.level ?: 1}")
                StatRow("XP", "${p?.xp ?: 0} / ${p?.xpToNextLevel() ?: 50}")
                StatRow("Kunci", "${p?.keys ?: 0}")
                StatRow("Gem", "${p?.gems ?: 0}")
                StatRow(
                    "Premium",
                    if (p?.effectivePremium() == true) "Aktif" else "Tidak",
                )
            }
        }

        if (p?.uid == null) {
            Button(
                onClick = {
                    val intent = app.authRepository.signInIntent(activity)
                    if (intent == null) {
                        message = "Set GOOGLE_WEB_CLIENT_ID + google-services.json dulu"
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    } else {
                        signInLauncher.launch(intent)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Login dengan Google") }
        } else {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        app.authRepository.signOut(activity)
                        message = "Logout"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Logout") }
        }

        Text("Dapat kunci", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    val ok = app.rewardedAds.show(activity)
                    if (ok) {
                        app.userRepository.grantKeys(1)
                        message = "+1 kunci dari iklan"
                    } else {
                        message = "Iklan belum siap / gagal — coba lagi"
                        app.rewardedAds.preload()
                    }
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Nonton iklan → +1 kunci") }

        OutlinedButton(
            onClick = {
                scope.launch {
                    val res = app.userRepository.exchangeGemsForKey()
                    message = res.fold(
                        onSuccess = { "Tukar berhasil: -${BuildConfig.GEMS_PER_KEY} gem, +1 kunci" },
                        onFailure = { it.message },
                    )
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Tukar ${BuildConfig.GEMS_PER_KEY} gem → 1 kunci") }

        Text("Premium", style = MaterialTheme.typography.titleMedium)
        Text(
            "Produk Play Billing: webunime_premium_1m / 3m / 6m / 12m",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        app.billingRepository.plans.forEach { plan ->
            Button(
                onClick = {
                    val launched = app.billingRepository.launchPlan(activity, plan.productId)
                    if (!launched) {
                        if (BuildConfig.DEBUG) {
                            scope.launch {
                                app.userRepository.applyPremiumDays(plan.days, plan.bonusGems)
                                message = "Debug: aktifkan ${plan.title} (+${plan.bonusGems} gem)"
                            }
                        } else {
                            message = "Billing belum siap / produk belum di Play Console"
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${plan.title} · +${plan.bonusGems} gem")
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
