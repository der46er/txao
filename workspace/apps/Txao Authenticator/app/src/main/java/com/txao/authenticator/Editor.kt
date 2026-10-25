package com.txao.authenticator

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Add ([initial] == null) or edit an account by hand. */
@Composable
fun AccountEditor(initial: Account?, onDismiss: () -> Unit, onSave: (Account) -> Unit) {
    var issuer by remember { mutableStateOf(initial?.issuer ?: "") }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var secret by remember { mutableStateOf(initial?.secret ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: OtpType.TOTP) }
    var algorithm by remember { mutableStateOf(initial?.algorithm ?: OtpAlgorithm.SHA1) }
    var digits by remember { mutableStateOf(initial?.digits ?: 6) }
    var period by remember { mutableStateOf((initial?.period ?: 30).toString()) }
    var counter by remember { mutableStateOf((initial?.counter ?: 0L).toString()) }
    var advanced by remember { mutableStateOf(initial != null && (initial.type != OtpType.TOTP || initial.digits != 6 || initial.algorithm != OtpAlgorithm.SHA1 || initial.period != 30)) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val clean = secret.filter { it != ' ' && it != '-' }.trimEnd('=').uppercase()
        val p = period.toIntOrNull() ?: 0
        val c = counter.toLongOrNull() ?: -1L
        error = when {
            issuer.isBlank() && label.isBlank() -> "Enter a name for the account"
            Base32.decode(clean) == null -> "The setup key must use the letters A–Z and digits 2–7"
            type == OtpType.TOTP && p <= 0 -> "Period must be a positive number of seconds"
            type == OtpType.HOTP && c < 0 -> "Counter must be 0 or more"
            else -> null
        }
        if (error != null) return
        val base = initial ?: Account(issuer = "", label = "", secret = clean)
        onSave(
            base.copy(
                issuer = issuer.trim(), label = label.trim(), secret = clean, type = type,
                algorithm = if (type == OtpType.STEAM) OtpAlgorithm.SHA1 else algorithm,
                digits = if (type == OtpType.STEAM) 5 else digits,
                period = if (type == OtpType.TOTP) p else 30,
                counter = if (type == OtpType.HOTP) c else 0,
            ),
        )
    }

    AlertDialog(
            shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add account" else "Edit account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(issuer, { issuer = it }, label = { Text("Service (e.g. GitHub)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(label, { label = it }, label = { Text("Account (e.g. you@mail.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    secret, { secret = it; error = null }, label = { Text("Setup key") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide options" else "More options") }
                if (advanced) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OtpType.entries.forEach {
                            FilterChip(selected = type == it, onClick = { type = it }, label = { Text(if (it == OtpType.STEAM) "Steam" else it.name) })
                        }
                    }
                    if (type != OtpType.STEAM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OtpAlgorithm.entries.forEach {
                                FilterChip(selected = algorithm == it, onClick = { algorithm = it }, label = { Text(it.uriName) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6, 7, 8).forEach {
                                FilterChip(selected = digits == it, onClick = { digits = it }, label = { Text("$it digits") })
                            }
                        }
                    }
                    if (type == OtpType.TOTP) {
                        OutlinedTextField(
                            period, { period = it.filter(Char::isDigit) }, label = { Text("Period (seconds)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(180.dp),
                        )
                    }
                    if (type == OtpType.HOTP) {
                        OutlinedTextField(
                            counter, { counter = it.filter(Char::isDigit) }, label = { Text("Counter") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(180.dp),
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = { Button(onClick = ::submit) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
