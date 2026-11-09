package com.webunime.mobile.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.webunime.mobile.WebunimeApp
import com.webunime.mobile.data.auth.WebUser
import kotlinx.coroutines.launch

/** Masuk / daftar akun WEBUNIME — akun yang sama dengan web. */
@Composable
fun AuthDialog(
    onDismiss: () -> Unit,
    onSignedIn: (WebUser) -> Unit = {},
    initialRegister: Boolean = false,
    lead: String? = null,
) {
    val app = LocalContext.current.applicationContext as WebunimeApp
    val scope = rememberCoroutineScope()
    var register by rememberSaveable { mutableStateOf(initialRegister) }
    var login by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            runCatching {
                if (register) {
                    app.webAuth.register(displayName, username, email, password)
                } else {
                    app.webAuth.login(login, password)
                }
            }
                .onSuccess { user ->
                    password = ""
                    onSignedIn(user)
                    onDismiss()
                }
                .onFailure { error = it.message ?: "Gagal. Coba lagi." }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(if (register) "Daftar akun WEBUNIME" else "Masuk ke WEBUNIME") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                lead?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !register,
                        onClick = { register = false; error = null },
                        label = { Text("Masuk") },
                    )
                    FilterChip(
                        selected = register,
                        onClick = { register = true; error = null },
                        label = { Text("Daftar") },
                    )
                }
                if (register) {
                    Field("Nama tampilan", displayName, { displayName = it.take(64) })
                    Field(
                        "Username",
                        username,
                        { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(32) },
                        supporting = "3–32 karakter: a-z, 0-9, underscore",
                        capitalization = KeyboardCapitalization.None,
                    )
                    Field(
                        "Email",
                        email,
                        { email = it.trim().take(191) },
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None,
                    )
                } else {
                    Field(
                        "Username atau email",
                        login,
                        { login = it },
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None,
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("Kata sandi") },
                    supportingText = if (register) {
                        { Text("Minimal 10 karakter, ada huruf dan angka") }
                    } else {
                        null
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (register) "Daftar" else "Masuk")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Batal") }
        },
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
