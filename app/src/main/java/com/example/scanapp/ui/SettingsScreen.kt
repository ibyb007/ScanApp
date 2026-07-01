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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
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
 * Credit shown in the Developer section: a colored truck emoji (🚛) tows the
 * "Bony Biswas" text in on a rope/chain from the left edge of the screen,
 * drags it into its resting spot, then drives on and exits — leaving only
 * the settled text behind. The truck puffs engine smoke the whole time it's
 * on screen, and while the text is being dragged it throws up a bit of
 * friction fire/sparks from scraping along the ground.
 */
@Composable
private fun DeveloperCreditLine() {
    // 0f = tow just starting (text off-screen left), 1f = text fully
    // settled at its resting spot. Overshoots slightly past 1 then rebounds,
    // so the arrival reads as a yank-then-settle rather than a dead stop.
    val progress = remember { Animatable(0f) }
    // Extra distance the truck drives after letting go of the rope, added
    // on top of its towing position — carries it off-screen to the right.
    val truckExit = remember { Animatable(0f) }
    val truckExitAlpha = remember { Animatable(1f) }
    var showTruck by remember { mutableStateOf(true) }

    // Continuous looping phases for the exhaust smoke and friction fire —
    // always ticking so they're in sync whenever they're visible.
    val fxTransition = rememberInfiniteTransition(label = "creditFx")
    val smokePhase by fxTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "smokePhase"
    )
    val firePhase by fxTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 180, easing = LinearEasing)),
        label = "firePhase"
    )

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 1900
                0f at 0 using LinearOutSlowInEasing
                1.08f at 1300 using FastOutSlowInEasing   // yanked in fast, overshoots the resting spot
                0.96f at 1550 using FastOutSlowInEasing    // rebounds back
                1f at 1900 using FastOutSlowInEasing       // settles
            }
        )
        // Rope lets go once the text has settled — truck drives on alone
        // and fades out as it exits.
        coroutineScope {
            launch {
                truckExit.animateTo(
                    targetValue = 650f,
                    animationSpec = tween(durationMillis = 1300, easing = FastOutSlowInEasing)
                )
            }
            launch {
                truckExitAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 1300
                        1f at 0
                        1f at 900
                        0f at 1300   // fades out in the final stretch as it drives off
                    }
                )
            }
        }
        showTruck = false
    }

    // Text width and rope span are fixed estimates (not measured) — fine
    // for a decorative rope/chain, since the truck's position is derived
    // directly from the text's position so the gap between them stays
    // visually consistent throughout the tow.
    val textWidthDp = 150f
    val ropeSpanDp = 40f
    val towLeadGapDp = textWidthDp + ropeSpanDp
    val towStartX = -1000f

    val textLeftX = towStartX * (1f - progress.value)
    val truckLeftX = textLeftX + towLeadGapDp + truckExit.value
    val isDragging = progress.value < 0.98f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (showTruck) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val ropeAlpha = (1f - (truckExit.value / 40f)).coerceIn(0f, 1f)
                val groundY = size.height * 0.86f

                // Rope/chain between the text's near edge and the truck's
                // back — only visible while still attached, fades the
                // instant the truck starts pulling away.
                if (ropeAlpha > 0f) {
                    drawTowRope(
                        fromX = (textLeftX + textWidthDp).dp.toPx(),
                        toX = truckLeftX.dp.toPx(),
                        y = groundY,
                        alpha = ropeAlpha
                    )
                }

                // Engine smoke puffing from the truck's back the whole time
                // it's on screen.
                drawExhaustSmoke(
                    anchorX = truckLeftX.dp.toPx(),
                    anchorY = size.height * 0.40f,
                    phase = smokePhase
                )

                // Friction sparks/fire where the text is scraping along the
                // ground while it's being dragged.
                if (isDragging) {
                    drawFrictionFire(
                        fromX = textLeftX.dp.toPx(),
                        toX = (textLeftX + textWidthDp).dp.toPx(),
                        y = groundY,
                        phase = firePhase
                    )
                }
            }

            // Truck is deliberately larger than the text it's towing.
            Text(
                text = "🚛",
                fontSize = 42.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = truckLeftX.dp)
                    .alpha(truckExitAlpha.value)
            )
        }

        Extruded3DText(
            text = "Bony Biswas",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = textLeftX.dp, y = (-4).dp)
        )
    }
}

/** Draws a loosely sagging rope/chain between two ground-level points, with a few link/knot dots along it. */
private fun DrawScope.drawTowRope(fromX: Float, toX: Float, y: Float, alpha: Float) {
    if (toX <= fromX) return
    val midX = (fromX + toX) / 2f
    val path = Path().apply {
        moveTo(fromX, y)
        quadraticTo(midX, y + 10f, toX, y)
    }
    drawPath(
        path = path,
        color = Color(0xFF5B4636).copy(alpha = alpha),
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )
    val linkCount = 4
    for (i in 1 until linkCount) {
        val t = i / linkCount.toFloat()
        val x = fromX + (toX - fromX) * t
        drawCircle(
            color = Color(0xFF3A2E24).copy(alpha = alpha),
            radius = 4f,
            center = Offset(x, y + 10f * (4f * t * (1f - t)))
        )
    }
}

/** Small puffs drifting up and back from the truck's tailpipe, looping continuously while the truck is on screen. */
private fun DrawScope.drawExhaustSmoke(anchorX: Float, anchorY: Float, phase: Float) {
    repeat(3) { i ->
        val t = (phase + i / 3f) % 1f
        val rise = t * 40f
        val drift = t * -26f
        val alpha = (1f - t) * 0.55f
        val radius = 6f + t * 10f
        drawCircle(
            color = Color(0xFF8C8C8C).copy(alpha = alpha),
            radius = radius,
            center = Offset(anchorX + drift, anchorY - rise)
        )
    }
}

/** Small flickering flame tufts and spark dashes where the text scrapes the ground while being towed. */
private fun DrawScope.drawFrictionFire(fromX: Float, toX: Float, y: Float, phase: Float) {
    if (toX <= fromX) return
    val tuftCount = 4
    for (i in 0 until tuftCount) {
        val baseT = i / (tuftCount - 1).toFloat()
        val x = fromX + (toX - fromX) * baseT
        val flicker = (phase + baseT * 0.6f) % 1f
        val height = 10f + flicker * 14f
        val sway = (flicker - 0.5f) * 8f

        val flamePath = Path().apply {
            moveTo(x - 6f, y)
            quadraticTo(x + sway, y - height, x, y - height * 1.6f)
            quadraticTo(x - sway, y - height, x + 6f, y)
            close()
        }
        val flameColor = if (flicker < 0.5f) Color(0xFFFF7A18) else Color(0xFFFFC93C)
        drawPath(flamePath, color = flameColor.copy(alpha = 0.85f))

        if (flicker > 0.7f) {
            drawLine(
                color = Color(0xFFFFE9A8),
                start = Offset(x, y - 2f),
                end = Offset(x + sway * 2f, y - 10f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
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
