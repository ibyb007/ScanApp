package com.example.scanapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * "Update available" as a frosted glass slab. While the APK downloads, the slab itself is the
 * progress bar (Nuvio's updater style): an accent fill sweeps across the whole slab from left to
 * right, and the text flips to a contrasting colour exactly where the fill has passed.
 * Release notes live behind the (i) button and unfold inside the slab.
 */
@Composable
fun UpdateGlassSlab(
    version: String,
    changelog: List<String>,
    isDownloading: Boolean,
    downloadedBytes: Long,
    totalBytes: Long,
    errorMessage: String?,
    canInstallInApp: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isDownloading) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = !isDownloading,
            dismissOnClickOutside = !isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            SlabContent(
                version = version,
                changelog = changelog,
                isDownloading = isDownloading,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                errorMessage = errorMessage,
                canInstallInApp = canInstallInApp,
                onUpdate = onUpdate,
                onLater = onLater
            )
        }
    }
}

@Composable
private fun SlabContent(
    version: String,
    changelog: List<String>,
    isDownloading: Boolean,
    downloadedBytes: Long,
    totalBytes: Long,
    errorMessage: String?,
    canInstallInApp: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    var showNotes by remember { mutableStateOf(false) }

    val determinate = isDownloading && totalBytes > 0
    val target = if (determinate) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(target, tween(180), label = "slabProgress")

    // Unknown size: no honest percentage, so the whole slab just breathes softly.
    val pulse by rememberInfiniteTransition(label = "slabPulse").animateFloat(
        initialValue = 0.10f, targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "slabPulseAlpha"
    )
    val indeterminate = isDownloading && !determinate

    val fillBrush = Brush.horizontalGradient(listOf(colors.primary, colors.primary.copy(alpha = 0.78f)))

    val subtitle = when {
        errorMessage != null -> "Download failed: $errorMessage"
        determinate -> "Downloading ${(progress * 100).toInt()}%  ·  ${formatMb(downloadedBytes)} / ${formatMb(totalBytes)}"
        isDownloading -> "Downloading…  ${formatMb(downloadedBytes)}"
        else -> "A new version is ready"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(colors.surfaceContainerHigh.copy(alpha = 0.86f))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.02f), Color.White.copy(alpha = 0.06f))
                )
            )
            // progress fill across the full slab
            .drawBehind {
                if (progress > 0f) {
                    drawRect(fillBrush, size = Size(size.width * progress, size.height))
                }
                if (indeterminate) drawRect(colors.primary.copy(alpha = pulse))
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.35f))
                ),
                shape
            )
    ) {
        // The same content is drawn twice: normal colours, then (clipped to the filled part) in the
        // "on fill" colours, so text stays readable on both sides of the progress edge.
        Box {
            // normal-colour copy only shows to the right of the progress edge
            Box(Modifier.clipFromFraction(if (determinate) progress else 0f)) {
            SlabBody(
                textColor = colors.onSurface, mutedColor = colors.onSurfaceVariant, errorColor = colors.error,
                iconBg = colors.primary.copy(alpha = 0.18f), iconTint = colors.primary,
                version = version, subtitle = subtitle, hasError = errorMessage != null,
                changelog = changelog, showNotes = showNotes, onToggleNotes = { showNotes = !showNotes },
                isDownloading = isDownloading, canInstallInApp = canInstallInApp,
                onUpdate = onUpdate, onLater = onLater, interactive = true
            )
            }
            if (determinate && progress > 0f) {
                Box(
                    Modifier.matchParentSizeClip(progress)
                ) {
                    SlabBody(
                        textColor = colors.onPrimary, mutedColor = colors.onPrimary.copy(alpha = 0.85f), errorColor = colors.onPrimary,
                        iconBg = colors.onPrimary.copy(alpha = 0.2f), iconTint = colors.onPrimary,
                        version = version, subtitle = subtitle, hasError = errorMessage != null,
                        changelog = changelog, showNotes = showNotes, onToggleNotes = {},
                        isDownloading = isDownloading, canInstallInApp = canInstallInApp,
                        onUpdate = {}, onLater = {}, interactive = false
                    )
                }
            }
        }
    }
}

/** Draws only the part of this box's content to the right of [fraction] of its width. */
private fun Modifier.clipFromFraction(fraction: Float): Modifier =
    if (fraction <= 0f) this else this.drawWithContent {
        clipRect(left = size.width * fraction) { this@drawWithContent.drawContent() }
    }

/** Draws only the left [fraction] of this box's content (used for the on-fill copy of the text). */
private fun Modifier.matchParentSizeClip(fraction: Float): Modifier = this.drawWithContent {
    clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() }
}

@Composable
private fun SlabBody(
    textColor: Color,
    mutedColor: Color,
    errorColor: Color,
    iconBg: Color,
    iconTint: Color,
    version: String,
    subtitle: String,
    hasError: Boolean,
    changelog: List<String>,
    showNotes: Boolean,
    onToggleNotes: () -> Unit,
    isDownloading: Boolean,
    canInstallInApp: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    interactive: Boolean
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp).background(iconBg, CircleShape)
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Update available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    "v$version",
                    style = MaterialTheme.typography.labelLarge,
                    color = mutedColor
                )
            }
            if (changelog.isNotEmpty()) {
                IconButton(onClick = onToggleNotes, enabled = interactive) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "What's new",
                        tint = if (showNotes) iconTint else mutedColor
                    )
                }
            }
        }

        Spacer(Modifier.padding(top = 8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasError) errorColor else mutedColor,
            modifier = Modifier.padding(end = 8.dp)
        )

        AnimatedVisibility(
            visible = showNotes && changelog.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                Modifier
                    .padding(top = 12.dp, end = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("What's new", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = textColor)
                Spacer(Modifier.padding(top = 4.dp))
                changelog.forEach { entry ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = mutedColor)
                        Text(entry, style = MaterialTheme.typography.bodyMedium, color = mutedColor)
                    }
                }
            }
        }

        if (!isDownloading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 4.dp)
            ) {
                TextButton(onClick = onLater, enabled = interactive) { Text("Later", color = mutedColor) }
                if (interactive) {
                    Button(onClick = onUpdate) {
                        Text(
                            when {
                                hasError -> "Retry"
                                canInstallInApp -> "Update"
                                else -> "View release"
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatMb(bytes: Long): String = "%.1f MB".format(bytes / (1024.0 * 1024.0))
