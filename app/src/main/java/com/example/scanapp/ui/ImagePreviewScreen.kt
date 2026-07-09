@file:OptIn(ExperimentalFoundationApi::class)
package com.example.scanapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter

/**
 * Full-screen page-by-page viewer: pinch/double-tap to zoom, swipe left/right
 * between pages, with a floating Edit button (bottom-right) that opens the
 * currently visible page in ML Kit's re-edit flow.
 */
@Composable
fun ImagePreviewScreen(
    pages: List<DetailPage>,
    initialIndex: Int,
    onBackClick: () -> Unit,
    onEditClick: (DetailPage) -> Unit
) {
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, pages.lastIndex)
    ) { pages.size }

    // While the current page is pinch-zoomed in, the pager shouldn't also
    // try to interpret drags as a page swipe.
    var currentPageZoomed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !currentPageZoomed,
            key = { pages[it].pageId }
        ) { pageIndex ->
            ZoomableImage(
                page = pages[pageIndex],
                isCurrentPage = pagerState.currentPage == pageIndex,
                onZoomChanged = { zoomed -> currentPageZoomed = zoomed }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${pages.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            // Balances the back button so the page counter stays centered.
            Box(modifier = Modifier.size(48.dp))
        }

        FloatingActionButton(
            onClick = {
                pages.getOrNull(pagerState.currentPage)?.let(onEditClick)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit page")
        }
    }
}

@Composable
private fun ZoomableImage(
    page: DetailPage,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember(page.pageId) { mutableStateOf(1f) }
    var offset by remember(page.pageId) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale, isCurrentPage) {
        if (isCurrentPage) onZoomChanged(scale > 1.05f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(page.pageId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        if (changes.none { it.pressed }) break

                        // Only claim the gesture for ourselves when it's an
                        // actual pinch (2+ fingers) or the image is already
                        // zoomed in (so a single finger pans around inside
                        // it). A plain single-finger drag at 1x scale is
                        // left completely unconsumed so HorizontalPager's
                        // own swipe detector sees it and flips pages.
                        val isPinch = changes.size > 1
                        if (isPinch || scale > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            offset = if (newScale > 1f) {
                                val maxX = (size.width * (newScale - 1f)) / 2f
                                val maxY = (size.height * (newScale - 1f)) / 2f
                                Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                Offset.Zero
                            }
                            scale = newScale
                            changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                }
            }
            .pointerInput(page.pageId) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(page.uri),
            contentDescription = "Page ${page.pageIndex + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
