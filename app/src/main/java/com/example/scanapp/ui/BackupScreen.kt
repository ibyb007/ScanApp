package com.example.scanapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    isProcessing: Boolean,
    statusMessage: String?,
    onLocalBackup: (password: String) -> Unit,
    onLocalRestore: (password: String) -> Unit,
    onTelegramSync: (token: String, chat: String, pass: String) -> Unit,
    onHomeClick: () -> Unit = {},
    onToolsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            ScanAppBottomNav(
                selectedIndex = 2,
                onHomeClick = onHomeClick,
                onToolsClick = onToolsClick,
                onSettingsClick = onSettingsClick,
                onBackupClick = {}
            )
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Backup & Sync Matrix", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        statusMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Encryption Configuration Box
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Archive Cipher Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("AES-256 Bit Passphrase") },
                    visualTransformation = if (passwordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide passphrase" else "Show passphrase"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Local Device Flash Sync Operations Box
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Local Filesystem Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onLocalBackup(password) }, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Backup")
                    }
                    OutlinedButton(
                        onClick = { onLocalRestore(password) },
                        enabled = !isProcessing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Restore")
                    }
                }
            }
        }

        // Dedicated Bot Cloud Sync Area Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Telegram Bot Endpoint Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("1. Create Bot via @BotFather")
                }

                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Paste Telegram Bot Token") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Paste Target Private Channel ID") },
                    placeholder = { Text("e.g. -100123456789") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onTelegramSync(botToken, chatId, password) },
                    enabled = !isProcessing && botToken.isNotBlank() && chatId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("2. Run Clean Rotation Sync")
                    }
                }
            }
        }
    }
    }
}