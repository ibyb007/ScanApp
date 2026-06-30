package com.example.scanapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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

            SectionLabel("Developer")
            Spacer(Modifier.height(4.dp))

            DeveloperCreditLine()
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

/**
 * Credit shown in the Developer section: just the name "Bony Biswas", rendered
 * with a faux-3D extruded look (stacked offset layers giving it depth/a
 * raised, embossed feel) in gold, plus a little original cartoon mascot that
 * shoves the words into place.
 *
 * "Bony" and "Biswas" both start off-screen at the extreme right, as if shoved
 * in from outside the screen. They slam inward past their final resting
 * spot, overlap/collide with each other near the middle, bounce off that
 * collision a couple of times (decreasing each bounce, like they're
 * scuffling), then settle into their final positions. The mascot trails
 * "Biswas" the whole way, leaning into the push with a strained grin,
 * squashing on every impact, then takes a little victory hop and scoots
 * off-screen once the words are settled. The whole sequence runs over a
 * deliberately long window so the gag reads clearly.
 */
@Composable
private fun DeveloperCreditLine() {
    // Keyframe values are absolute horizontal offsets (in dp) from each
    // word's final settled position. Large positive = far off-screen right.
    val bonyOffset = remember { Animatable(900f) }
    val biswasOffset = remember { Animatable(900f) }
    val mascotOffset = remember { Animatable(1300f) }
    val mascotRotation = remember { Animatable(0f) }
    val mascotSquash = remember { Animatable(1f) }
    val mascotAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch {
            bonyOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 3200
                    900f at 0 using LinearOutSlowInEasing
                    -60f at 900 using FastOutLinearInEasing   // slams past center, into Biswas — collision
                    50f at 1500 using FastOutSlowInEasing      // bounced back from the impact
                    -28f at 2050 using FastOutSlowInEasing     // scuffle: lunges back in
                    14f at 2550 using FastOutSlowInEasing      // smaller bounce
                    -5f at 2900 using FastOutSlowInEasing
                    0f at 3200
                }
            )
        }
        launch {
            biswasOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 3200
                    900f at 0 using LinearOutSlowInEasing
                    -80f at 950 using FastOutLinearInEasing    // slams further left, deep overlap with Bony
                    65f at 1550 using FastOutSlowInEasing
                    -36f at 2100 using FastOutSlowInEasing
                    18f at 2600 using FastOutSlowInEasing
                    -6f at 2950 using FastOutSlowInEasing
                    0f at 3200
                }
            )
        }
        // Mascot trails just behind "Biswas", arriving a beat later and
        // leaning into every shove, then exits once the job's done.
        launch {
            mascotOffset.animateTo(
                targetValue = 400f,
                animationSpec = keyframes {
                    durationMillis = 4300
                    1300f at 0 using LinearOutSlowInEasing
                    90f at 950 using FastOutLinearInEasing
                    130f at 1550 using FastOutSlowInEasing
                    80f at 2100 using FastOutSlowInEasing
                    110f at 2600 using FastOutSlowInEasing
                    90f at 2950 using FastOutSlowInEasing
                    70f at 3200 using FastOutSlowInEasing
                    60f at 3700 using FastOutSlowInEasing
                    400f at 4300 using FastOutSlowInEasing
                }
            )
        }
        launch {
            mascotRotation.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 4300
                    0f at 0
                    -22f at 950   // leans hard into the first shove
                    12f at 1550
                    -18f at 2100
                    8f at 2600
                    -10f at 2950
                    0f at 3200
                    0f at 3700
                    18f at 4300   // tips back, scooting off
                }
            )
        }
        launch {
            mascotSquash.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 4300
                    1f at 0
                    0.7f at 950    // squish on impact
                    1.05f at 1100
                    0.78f at 1550
                    1.05f at 1700
                    0.8f at 2100
                    1f at 2300
                    0.82f at 2600
                    1f at 2950
                    1f at 3200
                    1.2f at 3450   // little victory hop stretch
                    1f at 3700
                    1f at 4300
                }
            )
        }
        launch {
            mascotAlpha.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 4300
                    1f at 0
                    1f at 3700
                    0f at 4300   // fades out as it scoots off after the push
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row {
            Extruded3DText(
                text = "Bony",
                modifier = Modifier.offset(x = bonyOffset.value.dp)
            )
            Spacer(Modifier.width(10.dp))
            Extruded3DText(
                text = "Biswas",
                modifier = Modifier.offset(x = biswasOffset.value.dp)
            )
        }
        PushyMascot(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.CenterStart)
                .offset(x = mascotOffset.value.dp)
                .rotate(mascotRotation.value)
                .scale(scaleX = 2f - mascotSquash.value, scaleY = mascotSquash.value)
                .alpha(mascotAlpha.value)
        )
    }
}

/**
 * A small original cartoon mascot — round body, big effort-squinted eyes,
 * a gritted-teeth grin, one arm braced out front mid-shove, and a sweat
 * drop for comic effect. Drawn entirely with Canvas primitives; this is an
 * original character design, not a depiction of any existing copyrighted
 * character.
 */
@Composable
private fun PushyMascot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(50.dp)) {
        val w = size.width
        val h = size.height

        // Body — round, peachy-orange, leaning into the push.
        drawOval(
            color = Color(0xFFFF8A4C),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.16f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.74f)
        )

        // Stubby legs.
        drawOval(
            color = Color(0xFFE56F2E),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.26f, h * 0.82f),
            size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.16f)
        )
        drawOval(
            color = Color(0xFFE56F2E),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.52f, h * 0.82f),
            size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.16f)
        )

        // Bracing arm out front, with a little fist — this is the "push".
        drawLine(
            color = Color(0xFFE56F2E),
            start = androidx.compose.ui.geometry.Offset(w * 0.74f, h * 0.55f),
            end = androidx.compose.ui.geometry.Offset(w * 1.02f, h * 0.50f),
            strokeWidth = w * 0.10f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFFFF8A4C),
            radius = w * 0.09f,
            center = androidx.compose.ui.geometry.Offset(w * 1.04f, h * 0.49f)
        )

        // Eyes — wide white sclera, dark pupils, furrowed brows for effort.
        val eyeY = h * 0.38f
        listOf(w * 0.36f, w * 0.58f).forEach { eyeX ->
            drawCircle(color = Color.White, radius = w * 0.11f, center = androidx.compose.ui.geometry.Offset(eyeX, eyeY))
            drawCircle(color = Color(0xFF2B2B2B), radius = w * 0.05f, center = androidx.compose.ui.geometry.Offset(eyeX + w * 0.01f, eyeY))
            drawLine(
                color = Color(0xFF7A3E12),
                start = androidx.compose.ui.geometry.Offset(eyeX - w * 0.10f, eyeY - h * 0.13f),
                end = androidx.compose.ui.geometry.Offset(eyeX + w * 0.10f, eyeY - h * 0.07f),
                strokeWidth = w * 0.035f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Gritted-teeth grin — strained effort smile.
        drawRoundRect(
            color = Color(0xFF5A2E0C),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.34f, h * 0.58f),
            size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.10f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.36f, h * 0.585f),
            size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.05f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f)
        )

        // Sweat drop — sells the effort.
        drawOval(
            color = Color(0xFF6EC6FF),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.10f, h * 0.16f)
        )
    }
}

/**
 * Draws [text] with a faux-3D extruded/embossed look: several copies of the
 * glyph are stacked behind the front-facing layer, each nudged one pixel
 * further down-right and shaded progressively darker, so it reads as a
 * raised gold block letter rather than flat text. No real 3D rendering is
 * involved — it's the standard "layered shadow stack" trick for faux depth
 * in a 2D UI.
 */
@Composable
private fun Extruded3DText(text: String, modifier: Modifier = Modifier) {
    val depth = 6
    Box(modifier = modifier) {
        for (i in depth downTo 1) {
            Text(
                text = text,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
                color = GoldShadeDark.copy(alpha = 1f - (i * 0.06f)),
                modifier = Modifier.offset(x = i.dp, y = i.dp)
            )
        }
        // Front face with a subtle highlight-to-base gradient for a polished,
        // raised-metal look, plus a soft drop shadow for separation.
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic,
            fontSize = 22.sp,
            color = GoldColor,
            style = LocalTextStyle.current.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                    blurRadius = 3f
                )
            )
        )
    }
}

/** Metallic gold used for the developer credit name — reads clearly on a light surface. */
private val GoldColor = Color(0xFFCC9A06)

/** Darker gold used for the extruded "depth" layers behind the front face. */
private val GoldShadeDark = Color(0xFF7A5C04)

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
