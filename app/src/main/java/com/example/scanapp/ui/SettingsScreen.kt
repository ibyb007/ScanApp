package com.example.scanapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** What the update-check row should currently show. */
enum class UpdateCheckUiStatus { IDLE, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, ERROR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    versionCode: Int,
    updateStatus: UpdateCheckUiStatus,
    updateStatusMessage: String? = null,
    onCheckForUpdateClick: () -> Unit,
    onOpenReleaseClick: () -> Unit = {},
    checkUpdatesOnStart: Boolean = true,
    onCheckUpdatesOnStartChange: (Boolean) -> Unit = {},
    autoInstallUpdates: Boolean = false,
    onAutoInstallUpdatesChange: (Boolean) -> Unit = {},
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit = {},
    onToolsClick: () -> Unit = {},
    onBackupClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Settings gear — this screen IS the settings screen; kept as
                    // a visible icon since more settings (theme, default export
                    // format, etc.) will likely be added here later.
                    IconButton(onClick = { /* already on the settings screen */ }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            ScanAppBottomNav(
                selectedIndex = 3,
                onHomeClick = onHomeClick,
                onToolsClick = onToolsClick,
                onBackupClick = onBackupClick,
                onSettingsClick = {}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            SectionLabel("General")
            Spacer(Modifier.height(4.dp))

            // Check for update
            ListItem(
                headlineContent = { Text("Check for update") },
                supportingContent = {
                    Text(
                        text = when (updateStatus) {
                            UpdateCheckUiStatus.IDLE -> "Tap to check for the latest version"
                            UpdateCheckUiStatus.CHECKING -> "Checking…"
                            UpdateCheckUiStatus.UP_TO_DATE -> "You're on the latest version"
                            UpdateCheckUiStatus.UPDATE_AVAILABLE -> updateStatusMessage ?: "A new version is available"
                            UpdateCheckUiStatus.ERROR -> updateStatusMessage ?: "Couldn't check for updates"
                        },
                        color = when (updateStatus) {
                            UpdateCheckUiStatus.UPDATE_AVAILABLE -> MaterialTheme.colorScheme.primary
                            UpdateCheckUiStatus.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                leadingContent = {
                    // One icon, swapped per state — never more than one
                    // loading/status indicator visible on this row at a time.
                    when (updateStatus) {
                        UpdateCheckUiStatus.CHECKING -> {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        UpdateCheckUiStatus.UP_TO_DATE -> {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        UpdateCheckUiStatus.UPDATE_AVAILABLE -> {
                            Icon(
                                Icons.Filled.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        UpdateCheckUiStatus.ERROR -> {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        UpdateCheckUiStatus.IDLE -> {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                    }
                },
                trailingContent = {
                    when (updateStatus) {
                        UpdateCheckUiStatus.UPDATE_AVAILABLE -> {
                            Button(onClick = onOpenReleaseClick) { Text("Update") }
                        }
                        UpdateCheckUiStatus.CHECKING -> {
                            // No trailing icon while checking — the leading
                            // spinner is already the single loading indicator
                            // for this row. Previously a refresh IconButton
                            // (merely disabled, not hidden) sat here too, so
                            // the row showed what looked like two separate
                            // loading spinners side by side during a check.
                        }
                        else -> {
                            IconButton(onClick = onCheckForUpdateClick) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Check now")
                            }
                        }
                    }
                },
                modifier = Modifier.clickable(
                    enabled = updateStatus != UpdateCheckUiStatus.CHECKING
                ) { onCheckForUpdateClick() }
            )

            // Version info
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("$versionName (build $versionCode)") },
                leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) }
            )

            // Check Updates on Start — silently checks GitHub Releases once
            // at launch and shows the standard "update available" popup if
            // a newer release is found, independent of this row's own
            // "Check for update" status above (which only reflects manual
            // taps, not this background check).
            ListItem(
                headlineContent = { Text("Check Updates on Start") },
                supportingContent = { Text("Automatically check for updates when app starts") },
                trailingContent = {
                    Switch(checked = checkUpdatesOnStart, onCheckedChange = onCheckUpdatesOnStartChange)
                }
            )

            // Auto Install Updates — when the startup check (above) finds a
            // newer release, downloads it and opens the system install
            // prompt automatically rather than waiting for a tap on the
            // popup's "Update now" button. Android still requires the
            // person to confirm the actual install themselves — no API
            // available to a normal app skips that — so this saves the
            // download-and-open-prompt steps, not the final tap.
            ListItem(
                headlineContent = { Text("Auto Install Updates") },
                supportingContent = { Text("Download and prompt to install automatically when found") },
                trailingContent = {
                    Switch(
                        checked = autoInstallUpdates,
                        onCheckedChange = onAutoInstallUpdatesChange,
                        enabled = checkUpdatesOnStart
                    )
                }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            SectionLabel("Coming soon")
            Spacer(Modifier.height(4.dp))

            // Placeholder for the upcoming Telegram-based cloud sync feature.
            // Non-interactive for now — just reserves the spot in the menu
            // and sets expectations until the real feature is wired up.
            ListItem(
                headlineContent = { Text("Telegram cloud sync") },
                supportingContent = { Text("Back up and sync your scans via Telegram — coming soon") },
                leadingContent = { Icon(Icons.Filled.CloudQueue, contentDescription = null) },
                trailingContent = {
                    AssistChip(onClick = {}, enabled = false, label = { Text("Soon") })
                }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            SectionLabel("Developer")
            Spacer(Modifier.height(4.dp))

            DeveloperInfoSection()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

/** Developer contact rows: Telegram (inline link) and GitHub profile (inline link). */
@Composable
private fun DeveloperInfoSection() {
    val uriHandler = LocalUriHandler.current

    ListItem(
        headlineContent = { Text("Telegram") },
        supportingContent = { Text("t.me/ibyb007", color = MaterialTheme.colorScheme.primary) },
        leadingContent = { TelegramIcon() },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open Telegram",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable { uriHandler.openUri("https://t.me/ibyb007") }
    )

    ListItem(
        headlineContent = { Text("GitHub") },
        supportingContent = { Text("@ibyb007", color = MaterialTheme.colorScheme.primary) },
        leadingContent = { GitHubIcon() },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open GitHub profile",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable { uriHandler.openUri("https://github.com/ibyb007") }
    )
}

/**
 * Telegram glyph drawn with a Compose Canvas (paper-plane on a circle) so no
 * brand SVG asset needs to be bundled for a single icon.
 */
@Composable
private fun TelegramIcon() {
    Canvas(modifier = Modifier.size(28.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(color = Color(0xFF29A9EB), radius = radius)

        val plane = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.52f)
            lineTo(size.width * 0.80f, size.height * 0.22f)
            lineTo(size.width * 0.64f, size.height * 0.80f)
            lineTo(size.width * 0.46f, size.height * 0.60f)
            lineTo(size.width * 0.34f, size.height * 0.70f)
            lineTo(size.width * 0.38f, size.height * 0.50f)
            close()
        }
        drawPath(plane, color = Color.White)
    }
}

/**
 * GitHub mark approximated with a dark circular badge and a ">_" text glyph
 * layered on top — a precise Octocat silhouette is GitHub's brand artwork,
 * so a neutral "developer" glyph is drawn directly as text instead of
 * relying on an icon name from the extended set that may not exist.
 */
@Composable
private fun GitHubIcon() {
    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(modifier = Modifier.size(28.dp)) {
            drawCircle(color = Color(0xFF181717), radius = size.minDimension / 2f)
        }
        Text(
            ">_",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
