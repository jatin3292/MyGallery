package com.example.mygallery

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RotateRight
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
import coil.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fullscreen, swipeable media viewer.
 * - Images: pinch to zoom, two-finger rotate (snaps smoothly to 90° steps,
 *   also triggerable via the rotate button), double-tap to zoom, pan while
 *   zoomed, basic crop editor.
 * - Videos: inline playback via ExoPlayer, auto-pauses when swiped away from,
 *   long-press to manually pause/resume.
 * - Swipe down anywhere (while not zoomed in) to close.
 * - System back button/gesture also closes the viewer.
 * - Left/right swipe navigates between items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenViewer(
    mediaList: List<MediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    onEdited: () -> Unit
) {
    var editingItem by remember { mutableStateOf<MediaItem?>(null) }

    if (editingItem != null) {
        EditScreen(
            item = editingItem!!,
            onClose = { editingItem = null },
            onSaved = {
                editingItem = null
                onEdited()
            }
        )
        return
    }

    BackHandler { onClose() }

    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { mediaList.size })

    // Whether the currently visible page is zoomed in. While true, the
    // swipe-down-to-close gesture is disabled so it doesn't fight with panning.
    var currentPageZoomed by remember { mutableStateOf(false) }

    // Bumped by the rotate button; each page watches this and only rotates
    // itself if it's the currently visible page.
    var rotateTrigger by remember { mutableStateOf(0) }

    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(targetValue = dragOffsetY, label = "dragOffsetY")
    val backgroundAlpha = 1f - (abs(dragOffsetY) / 1200f).coerceIn(0f, 0.6f)

    val currentItem = mediaList[pagerState.currentPage]

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
                VideoPage(item = item, isCurrentPage = isCurrentPage)
            } else {
                ZoomableImagePage(
                    item = item,
                    isCurrentPage = isCurrentPage,
                    rotateTrigger = rotateTrigger,
                    onZoomChanged = { zoomed -> if (isCurrentPage) currentPageZoomed = zoomed }
                )
            }
        }

        // Top overlay bar: back, rotate, edit, share, delete.
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
                if (!currentItem.isVideo) {
                    IconButton(onClick = { rotateTrigger++ }) {
                        Icon(Icons.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White)
                    }
                    IconButton(onClick = { editingItem = currentItem }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit / crop", tint = Color.White)
                    }
                }
                IconButton(onClick = { onShare(currentItem) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                }
                IconButton(onClick = { onDelete(currentItem) }) {
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
    rotateTrigger: Int,
    onZoomChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val animScope = rememberCoroutineScope()

    // Animatable (rather than plain state) so zoom/pan can either track
    // fingers exactly during a pinch (snapTo, no easing lag) or animate
    // smoothly to a target (animateTo, used for double-tap and reset).
    val scale = remember(item.id) { Animatable(1f) }
    val offsetX = remember(item.id) { Animatable(0f) }
    val offsetY = remember(item.id) { Animatable(0f) }

    // Rotation snaps to 90° steps instead of following the raw (jittery)
    // two-finger rotation directly. rotationAccumulator tracks how far the
    // fingers have twisted since the last snap; once it crosses 45°, we
    // commit a full 90° turn and animate smoothly to it. The rotate button
    // (rotateTrigger) commits a 90° turn the same way.
    var committedRotation by remember { mutableStateOf(0f) }
    var rotationAccumulator by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = committedRotation,
        animationSpec = tween(durationMillis = 250),
        label = "rotation"
    )

    suspend fun animateResetZoomAndPan() {
        coroutineScope {
            launch { scale.animateTo(1f, tween(220)) }
            launch { offsetX.animateTo(0f, tween(220)) }
            launch { offsetY.animateTo(0f, tween(220)) }
        }
    }

    LaunchedEffect(scale.value, isCurrentPage) {
        onZoomChanged(isCurrentPage && scale.value > 1f)
    }

    LaunchedEffect(rotateTrigger) {
        if (isCurrentPage && rotateTrigger > 0) {
            committedRotation += 90f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .crossfade(200)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                    rotationZ = animatedRotation,
                    translationX = offsetX.value,
                    translationY = offsetY.value
                )
                // Pinch-zoom, two-finger rotate (90° snapping), pan-while-zoomed.
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
                                // Two+ fingers: pinch zoom (tracked exactly,
                                // no animation lag), accumulate rotation
                                // toward the next 90° snap.
                                val newScale = (scale.value * zoomChange).coerceIn(1f, 6f)
                                scale.snapTo(newScale)
                                rotationAccumulator += rotationChange
                                if (abs(rotationAccumulator) >= 45f) {
                                    committedRotation += if (rotationAccumulator > 0) 90f else -90f
                                    rotationAccumulator = 0f
                                }
                                if (newScale > 1f) {
                                    offsetX.snapTo(offsetX.value + panChange.x)
                                    offsetY.snapTo(offsetY.value + panChange.y)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (scale.value > 1f) {
                                // One finger, already zoomed in: pan around.
                                offsetX.snapTo(offsetX.value + panChange.x)
                                offsetY.snapTo(offsetY.value + panChange.y)
                                event.changes.forEach { it.consume() }
                            }
                            // One finger, not zoomed: don't consume — lets the
                            // pager handle it as a page swipe instead.
                        } while (event.changes.any { it.pressed })

                        rotationAccumulator = 0f
                        if (scale.value <= 1f) animateResetZoomAndPan()
                    }
                }
                // Double-tap to zoom in/out, smoothly (doesn't affect rotation).
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            animScope.launch {
                                if (scale.value > 1f) {
                                    animateResetZoomAndPan()
                                } else {
                                    scale.animateTo(3f, tween(250))
                                }
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun VideoPage(item: MediaItem, isCurrentPage: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            prepare()
        }
    }

    // Auto-pause when this page is swiped away from, auto-resume when it
    // becomes the visible page again — prevents multiple videos playing
    // (and their audio overlapping) at once as you swipe through the pager.
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(item.id) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
            })
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    // Return false so normal tap handling (show/hide controls)
                    // still happens in addition to our long-press detection.
                    false
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
