package com.example.scanapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Which Telegram network operation, if any, is currently in flight — drives the sync animation. */
enum class TelegramActivityState { NONE, UPLOADING, DOWNLOADING }

/**
 * Small bouncing/pulsing cloud icon used to indicate live Telegram upload or
 * download activity, in place of a generic spinner.
 */
@Composable
private fun TelegramActivityIcon(
    activity: TelegramActivityState,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "telegramActivity")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (activity == TelegramActivityState.UPLOADING) -5f else 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Icon(
        imageVector = if (activity == TelegramActivityState.UPLOADING) Icons.Filled.CloudUpload else Icons.Filled.CloudDownload,
        contentDescription = if (activity == TelegramActivityState.UPLOADING) "Uploading to Telegram" else "Downloading from Telegram",
        tint = tint.copy(alpha = alpha),
        modifier = modifier
            .size(20.dp)
            .graphicsLayer { translationY = bounce }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    isProcessing: Boolean,
    statusMessage: String?,
    savedBotToken: String = "",
    savedChatId: String = "",
    onLocalBackup: (password: String) -> Unit,
    onLocalRestore: (password: String) -> Unit,
    onTelegramSync: (token: String, chat: String, pass: String) -> Unit,
    onTelegramRestore: (token: String, pass: String) -> Unit,
    onGoogleDriveBackup: (password: String) -> Unit = {},
    onGoogleDriveRestore: (password: String) -> Unit = {},
    onSaveTelegramCredentials: (token: String, chat: String) -> Unit = { _, _ -> },
    onExportTelegramCredentials: (password: String) -> Unit = {},
    onImportTelegramCredentials: (password: String) -> Unit = {},
    telegramActivity: TelegramActivityState = TelegramActivityState.NONE,
    onHomeClick: () -> Unit = {},
    onToolsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var botToken by remember { mutableStateOf(savedBotToken) }
    var chatId by remember { mutableStateOf(savedChatId) }
    // Credentials fields are hidden by default once a token/chat ID is already
    // saved; the user must explicitly tap "Change credentials" to reveal them.
    var credentialsEditing by remember { mutableStateOf(savedBotToken.isBlank() && savedChatId.isBlank()) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // savedBotToken/savedChatId can change from outside this composable (e.g.
    // a credentials import completes elsewhere and the Activity re-reads
    // persisted prefs) — keep local fields and the editing/saved view in sync
    // whenever that happens, rather than only seeding them once on first
    // composition.
    LaunchedEffect(savedBotToken, savedChatId) {
        if (savedBotToken.isNotBlank() || savedChatId.isNotBlank()) {
            botToken = savedBotToken
            chatId = savedChatId
            credentialsEditing = false
        }
    }

    // ScanAppBottomNav is overlaid directly on the content Box below instead
    // of living in Scaffold's bottomBar slot, so it floats over the scrolled
    // content (and the real background shows around/behind it) rather than
    // Scaffold reserving a plain rectangle of layout space for it.
    var navBarHeightPx by remember { mutableStateOf(0) }
    val navBarHeightDp = with(LocalDensity.current) { navBarHeightPx.toDp() }

    Scaffold(
        // Match HomeScreen: don't let Scaffold reserve an opaque
        // system-bar-height strip behind the bottom bar — ScanAppBottomNav
        // now handles its own transparent navigation-bar inset padding, so
        // the app's real background should show through around it.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
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

        // Google Drive Sync Area Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Google Drive Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Stored in your Drive account's hidden app-data area — not visible in the " +
                        "regular Drive app, and only ScanApp can read it. Uses your Google sign-in, " +
                        "no separate account setup needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onGoogleDriveBackup(password) }, enabled = !isProcessing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Backup")
                    }
                    OutlinedButton(
                        onClick = { onGoogleDriveRestore(password) },
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
            Column(
                // Extra bottom padding reserves clearance for the floating
                // nav pill *inside* the card, so the card's own (lighter)
                // background extends to cover that space instead of leaving
                // a gap that shows raw (much darker) page background —
                // that raw-background gap was what read as a stark black
                // rectangle sitting above the pill.
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + navBarHeightDp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (telegramActivity != TelegramActivityState.NONE) {
                        TelegramActivityIcon(activity = telegramActivity, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Telegram Bot Endpoint Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (credentialsEditing) {
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
                        onClick = {
                            if (botToken.isNotBlank() && chatId.isNotBlank()) {
                                credentialsEditing = false
                                onSaveTelegramCredentials(botToken, chatId)
                            }
                        },
                        enabled = botToken.isNotBlank() && chatId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save credentials")
                    }
                    OutlinedButton(
                        onClick = { onImportTelegramCredentials(password) },
                        enabled = !isProcessing && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import credentials from file")
                    }
                    if (password.isBlank()) {
                        Text(
                            "Enter the Archive Cipher passphrase above to import an encrypted credentials file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Bot token saved", style = MaterialTheme.typography.bodyMedium)
                            Text("Chat ID saved", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { credentialsEditing = true }) {
                            Text("Change credentials")
                        }
                    }
                    OutlinedButton(
                        onClick = { onExportTelegramCredentials(password) },
                        enabled = !isProcessing && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export credentials to file")
                    }
                    if (password.isBlank()) {
                        Text(
                            "Enter the Archive Cipher passphrase above to export an encrypted credentials file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { onTelegramSync(botToken, chatId, password) },
                    enabled = !isProcessing && botToken.isNotBlank() && chatId.isNotBlank() && !credentialsEditing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (telegramActivity == TelegramActivityState.UPLOADING) {
                        TelegramActivityIcon(activity = telegramActivity, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Uploading to Telegram…")
                    } else if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Run backup to Telegram")
                    }
                }
                OutlinedButton(
                    onClick = { onTelegramRestore(botToken, password) },
                    enabled = !isProcessing && botToken.isNotBlank() && !credentialsEditing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (telegramActivity == TelegramActivityState.DOWNLOADING) {
                        TelegramActivityIcon(activity = telegramActivity, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Downloading from Telegram…")
                    } else {
                        Text("Restore from Telegram")
                    }
                }
            }
        }

        // Small trailing gap for breathing room below the last card — the
        // card itself now reserves the real nav clearance (see above), so
        // this doesn't need to match navBarHeightDp.
        Spacer(Modifier.height(16.dp))
    }

    // Overlaid on top of the scrolled content instead of reserved via
    // Scaffold's bottomBar, so the real page background shows through
    // around/behind the pill instead of a plain rectangle.
    ScanAppBottomNav(
        selectedIndex = 2,
        onHomeClick = onHomeClick,
        onToolsClick = onToolsClick,
        onSettingsClick = onSettingsClick,
        onBackupClick = {},
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .onGloballyPositioned { navBarHeightPx = it.size.height }
    )
    }
    }
}