package com.example.mygallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlin.math.abs

/**
 * Fullscreen, swipeable media viewer.
 * - Images: pinch to zoom, two-finger rotate, double-tap to zoom, pan while zoomed.
 * - Videos: inline playback via ExoPlayer.
 * - Swipe down anywhere (while not zoomed in) to close.
 * - Left/right swipe navigates between items, and keeps working because the
 *   per-image gesture handler only consumes touches when it needs to
 *   (multi-touch, or single-finger while already zoomed in) — otherwise it
 *   lets the drag pass through to the pager untouched.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenViewer(
    mediaList: List<MediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { mediaList.size })

    // Whether the currently visible page is zoomed in. While true, the
    // swipe-down-to-close gesture is disabled so it doesn't fight with panning.
    var currentPageZoomed by remember { mutableStateOf(false) }

    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(targetValue = dragOffsetY, label = "dragOffsetY")
    val backgroundAlpha = 1f - (abs(dragOffsetY) / 1200f).coerceIn(0f, 0.6f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(currentPageZoomed) {
                if (!currentPageZoomed) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount
                        },
                        onDragEnd = {
                            if (abs(dragOffsetY) > 300f) {
                                onClose()
                            } else {
                                dragOffsetY = 0f
                            }
                        }
                    )
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = animatedOffsetY }
        ) { page ->
            val item = mediaList[page]
            val isCurrentPage = page == pagerState.currentPage
            if (item.isVideo) {
                VideoPage(item = item)
            } else {
                ZoomableImagePage(
                    item = item,
                    isCurrentPage = isCurrentPage,
                    onZoomChanged = { zoomed -> if (isCurrentPage) currentPageZoomed = zoomed }
                )
            }
        }

        // Top overlay bar: back, share, delete.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Row {
                IconButton(onClick = { onShare(mediaList[pagerState.currentPage]) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                }
                IconButton(onClick = { onDelete(mediaList[pagerState.currentPage]) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    item: MediaItem,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    fun resetTransform() {
        scale = 1f
        rotation = 0f
        offsetX = 0f
        offsetY = 0f
    }

    LaunchedEffect(scale, isCurrentPage) {
        onZoomChanged(isCurrentPage && scale > 1f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    rotationZ = rotation,
                    translationX = offsetX,
                    translationY = offsetY
                )
                // Pinch-zoom, two-finger rotate, pan-while-zoomed.
                // Single-finger drags at 1x scale are deliberately left
                // UNCONSUMED so the parent HorizontalPager still receives
                // them and can swipe between images.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val rotationChange = event.calculateRotation()
                            val panChange = event.calculatePan()

                            if (event.changes.size > 1) {
                                // Two+ fingers: pinch zoom and rotate together.
                                scale = (scale * zoomChange).coerceIn(1f, 6f)
                                rotation += rotationChange
                                if (scale > 1f) {
                                    offsetX += panChange.x
                                    offsetY += panChange.y
                                }
                                event.changes.forEach { it.consume() }
                            } else if (scale > 1f) {
                                // One finger, already zoomed in: pan around.
                                offsetX += panChange.x
                                offsetY += panChange.y
                                event.changes.forEach { it.consume() }
                            }
                            // One finger, not zoomed: don't consume — lets the
                            // pager handle it as a page swipe instead.
                        } while (event.changes.any { it.pressed })

                        if (scale <= 1f) resetTransform()
                    }
                }
                // Double-tap to zoom in/out.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                resetTransform()
                            } else {
                                scale = 3f
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun VideoPage(item: MediaItem) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
        }
    }

    DisposableEffect(item.id) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}