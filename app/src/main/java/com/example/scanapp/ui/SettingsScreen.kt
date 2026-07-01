package com.example.scanapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
 * raised, embossed feel) in gold, plus a little original silhouette
 * character that moonwalks in alongside it.
 *
 * "Bony" and "Biswas" both start off-screen at the extreme right at the very
 * same instant the moonwalker starts its glide — that's the sync point: the
 * moment the animation begins, both the text and the dancer are already
 * moving together. The words slam past their resting spot into a
 * mid-air collision, bounce off it a couple of times (like they're
 * scuffling), then settle. The moonwalker glides in on its own step cycle,
 * hangs around grooving in place near the settled text, then moonwalks back
 * off-screen and fades out.
 */
@Composable
private fun DeveloperCreditLine() {
    // Keyframe values are absolute horizontal offsets (in dp) from each
    // word's final settled position. Large positive = far off-screen right.
    val bonyOffset = remember { Animatable(900f) }
    val biswasOffset = remember { Animatable(900f) }
    val moonwalkOffset = remember { Animatable(700f) }
    val moonwalkAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // All three launches start in the same LaunchedEffect frame, so the
        // moonwalker and both words begin sliding in from the right at
        // exactly the same instant.
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
        // Moonwalker: glides in from the right (starting at the same t=0 as
        // the words above), settles near the text, grooves in place for a
        // beat, then moonwalks back off-screen to the left and fades.
        launch {
            moonwalkOffset.animateTo(
                targetValue = -500f,
                animationSpec = keyframes {
                    durationMillis = 4300
                    700f at 0 using LinearOutSlowInEasing
                    0f at 1400 using FastOutSlowInEasing
                    0f at 3400 using LinearEasing              // hangs around, grooving on the spot
                    -500f at 4300 using FastOutSlowInEasing    // moonwalks off-screen
                }
            )
        }
        launch {
            moonwalkAlpha.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 4300
                    1f at 0
                    1f at 3900
                    0f at 4300   // fades out as it exits
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
        MoonwalkerSilhouette(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.CenterStart)
                .offset(x = moonwalkOffset.value.dp)
                .alpha(moonwalkAlpha.value)
        )
    }
}

/**
 * A small original silhouette character doing a moonwalk-style step cycle:
 * one leg planted forward with the heel down while the other trails behind
 * up on its toe, swapping continuously — the classic illusion of walking
 * forward while actually gliding. One arm is bent up near the chest, the
 * other trails back, and the whole figure has a slight bounce to sell the
 * groove. Drawn entirely with Canvas primitives as an original character
 * design — not a reproduction of any existing footage or artwork.
 */
@Composable
private fun MoonwalkerSilhouette(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "moonwalkCycle")
    // Drives which leg is "forward, heel down" vs "trailing, up on toe",
    // continuously swapping — the stepping motion of the glide.
    val stepPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stepPhase"
    )
    // Tiny up-down bounce on each step for a bit of groove.
    val bob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 260, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    Canvas(modifier = modifier.size(40.dp, 56.dp)) {
        val w = size.width
        val h = size.height
        val silhouette = Color(0xFF1C1C1C)
        val liftY = bob * (h * 0.02f)

        // Head
        drawCircle(
            color = silhouette,
            radius = w * 0.17f,
            center = androidx.compose.ui.geometry.Offset(w * 0.52f, h * 0.13f - liftY)
        )

        // Torso — slight backward lean sells the glide.
        drawLine(
            color = silhouette,
            start = androidx.compose.ui.geometry.Offset(w * 0.52f, h * 0.24f - liftY),
            end = androidx.compose.ui.geometry.Offset(w * 0.46f, h * 0.56f - liftY),
            strokeWidth = w * 0.16f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Bent arm up near the chest (the classic pose), other arm trailing back.
        drawLine(
            color = silhouette,
            start = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.30f - liftY),
            end = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.20f - liftY),
            strokeWidth = w * 0.08f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = silhouette,
            start = androidx.compose.ui.geometry.Offset(w * 0.48f, h * 0.34f - liftY),
            end = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.48f - liftY),
            strokeWidth = w * 0.08f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Legs — alternate which one is planted forward (heel down) versus
        // trailing behind (heel lifted, dragging on the toe), swapping every
        // cycle as they slide sideways past each other.
        val frontIsLeft = stepPhase < 0.5f
        val cyclePos = if (frontIsLeft) stepPhase * 2f else (stepPhase - 0.5f) * 2f

        val forwardKneeX = w * (0.40f - 0.10f * cyclePos)
        val forwardFootX = w * (0.28f - 0.16f * cyclePos)
        val trailingKneeX = w * (0.54f + 0.10f * cyclePos)
        val trailingFootX = w * (0.68f + 0.18f * cyclePos)
        val trailingHeelLift = h * 0.05f * (1f - cyclePos)

        val hipY = h * 0.56f - liftY
        val kneeY = h * 0.78f - liftY
        val footY = h * 0.97f - liftY

        val leftKneeX = if (frontIsLeft) forwardKneeX else trailingKneeX
        val leftFootX = if (frontIsLeft) forwardFootX else trailingFootX
        val leftFootY = if (frontIsLeft) footY else footY - trailingHeelLift

        val rightKneeX = if (frontIsLeft) trailingKneeX else forwardKneeX
        val rightFootX = if (frontIsLeft) trailingFootX else forwardFootX
        val rightFootY = if (frontIsLeft) footY - trailingHeelLift else footY

        val hipPoint = androidx.compose.ui.geometry.Offset(w * 0.48f, hipY)
        drawLine(silhouette, hipPoint, androidx.compose.ui.geometry.Offset(leftKneeX, kneeY), strokeWidth = w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(silhouette, androidx.compose.ui.geometry.Offset(leftKneeX, kneeY), androidx.compose.ui.geometry.Offset(leftFootX, leftFootY), strokeWidth = w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(silhouette, hipPoint, androidx.compose.ui.geometry.Offset(rightKneeX, kneeY), strokeWidth = w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(silhouette, androidx.compose.ui.geometry.Offset(rightKneeX, kneeY), androidx.compose.ui.geometry.Offset(rightFootX, rightFootY), strokeWidth = w * 0.09f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
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
