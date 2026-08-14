package com.example.mygallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Fullscreen, swipeable media viewer.
 * - Images: pinch to zoom, two-finger rotate (snaps smoothly to 90 degree
 *   steps, also triggerable via the rotate button), double-tap to zoom, pan
 *   while zoomed, basic crop editor. Single tap toggles the icon row;
 *   long-press shows file info.
 * - Videos: single tap toggles the icon row + a draggable progress bar
 *   (both stay shown/hidden until tapped again — no auto-hide timeout);
 *   double tap toggles play/pause; long-press shows file info. Loops
 *   indefinitely instead of stopping at the end, and auto-pauses when
 *   swiped away from.
 * - Swipe down anywhere (while not zoomed in) to close.
 * - System back button/gesture also closes the viewer.
 * - Left/right swipe navigates between items.
 * - Shuffle button jumps to a random item in the current list.
 * - Star icon toggles favorite; info icon (or long-press) shows file details.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenViewer(
    mediaList: List<MediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    onEdited: () -> Unit,
    isFavorite: (MediaItem) -> Boolean,
    onToggleFavorite: (MediaItem) -> Unit
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

    val viewerScope = rememberCoroutineScope()

    // Shuffle Mode: when off, pages map 1:1 to mediaList. When on, pages map
    // through a shuffled permutation instead, so every swipe (forward or
    // back) moves through a randomized order rather than sequential order.
    var shuffleMode by remember { mutableStateOf(false) }
    var shuffledIndices by remember { mutableStateOf(mediaList.indices.toList()) }

    val pagerState = rememberPagerState(initialPage = startIndex) {
        if (shuffleMode) shuffledIndices.size else mediaList.size
    }

    fun mediaIndexForPage(page: Int): Int = if (shuffleMode) shuffledIndices[page] else page

    // Whether the currently visible page is zoomed in. While true, the
    // swipe-down-to-close gesture is disabled so it doesn't fight with panning.
    var currentPageZoomed by remember { mutableStateOf(false) }

    // Bumped by the rotate button; each page watches this and only rotates
    // itself if it's the currently visible page.
    var rotateTrigger by remember { mutableStateOf(0) }

    var showInfo by remember { mutableStateOf(false) }

    // Shared toggle for the top icon row AND (on video pages) the progress
    // bar — a single tap on either an image or a video toggles this, and it
    // stays as-is (no auto-hide timeout) until tapped again. Resets to
    // visible whenever you swipe to a different item.
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState.currentPage) {
        controlsVisible = true
    }

    var dragOffsetY by remember { mutableStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(targetValue = dragOffsetY, label = "dragOffsetY")
    val backgroundAlpha = 1f - (abs(dragOffsetY) / 1200f).coerceIn(0f, 0.6f)

    val currentItem = mediaList[mediaIndexForPage(pagerState.currentPage)]

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
            val item = mediaList[mediaIndexForPage(page)]
            val isCurrentPage = page == pagerState.currentPage
            if (item.isVideo) {
                VideoPage(
                    item = item,
                    isCurrentPage = isCurrentPage,
                    controlsVisible = controlsVisible,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onShowInfo = { showInfo = true }
                )
            } else {
                ZoomableImagePage(
                    item = item,
                    isCurrentPage = isCurrentPage,
                    rotateTrigger = rotateTrigger,
                    onZoomChanged = { zoomed -> if (isCurrentPage) currentPageZoomed = zoomed },
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onShowInfo = { showInfo = true }
                )
            }
        }

        if (controlsVisible) {
            // Subtle gradient scrim behind the top icons for legibility over
            // any photo, rather than a flat tint.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            // Top overlay bar: back, shuffle, favorite, info, rotate, edit, share, delete.
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconToggleButton(
                        checked = shuffleMode,
                        onCheckedChange = { checked ->
                            if (checked) {
                                // Turning on: shuffle everything except the
                                // current item, which stays first so nothing
                                // jumps the moment shuffle is enabled.
                                val currentMediaIndex = mediaIndexForPage(pagerState.currentPage)
                                val rest = mediaList.indices.filter { it != currentMediaIndex }.shuffled()
                                shuffledIndices = listOf(currentMediaIndex) + rest
                                shuffleMode = true
                                viewerScope.launch { pagerState.scrollToPage(0) }
                            } else {
                                // Turning off: jump back to this item's normal
                                // sequential position.
                                val currentMediaIndex = mediaIndexForPage(pagerState.currentPage)
                                shuffleMode = false
                                viewerScope.launch { pagerState.scrollToPage(currentMediaIndex) }
                            }
                        },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle mode")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { onToggleFavorite(currentItem) }) {
                        Icon(
                            imageVector = if (isFavorite(currentItem)) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (isFavorite(currentItem)) Color(0xFFFFC107) else Color.White
                        )
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Info", tint = Color.White)
                    }
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

        if (showInfo) {
            InfoPanel(item = currentItem, onDismiss = { showInfo = false })
        }
    }
}

/** Bottom-sheet-style overlay showing basic file details for the current item. */
@Composable
private fun InfoPanel(item: MediaItem, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(20.dp)
        ) {
            Text(
                text = item.displayName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow("Folder", item.bucketName)
            InfoRow("Date", formatDate(item.dateAdded))
            InfoRow("Resolution", "${item.width} \u00d7 ${item.height}")
            InfoRow("Size", formatFileSize(item.sizeBytes))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun ZoomableImagePage(
    item: MediaItem,
    isCurrentPage: Boolean,
    rotateTrigger: Int,
    onZoomChanged: (Boolean) -> Unit,
    onToggleControls: () -> Unit,
    onShowInfo: () -> Unit
) {
    val context = LocalContext.current
    val animScope = rememberCoroutineScope()

    // Plain (non-animated) state for live pinch/pan tracking — updated
    // synchronously as touches come in, so panning stays perfectly attached
    // to your finger with zero lag. Programmatic transitions (double-tap,
    // post-pinch reset) instead drive these same values smoothly via the
    // animate() coroutine below.
    var scale by remember(item.id) { mutableStateOf(1f) }
    var offsetX by remember(item.id) { mutableStateOf(0f) }
    var offsetY by remember(item.id) { mutableStateOf(0f) }

    // Rotation snaps to 90 degree steps instead of following the raw
    // (jittery) two-finger rotation directly.
    var committedRotation by remember { mutableStateOf(0f) }
    var rotationAccumulator by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = committedRotation,
        animationSpec = tween(durationMillis = 250),
        label = "rotation"
    )

    suspend fun animateResetZoomAndPan() {
        coroutineScope {
            launch { animate(scale, 1f, animationSpec = tween(220)) { value, _ -> scale = value } }
            launch { animate(offsetX, 0f, animationSpec = tween(220)) { value, _ -> offsetX = value } }
            launch { animate(offsetY, 0f, animationSpec = tween(220)) { value, _ -> offsetY = value } }
        }
    }

    LaunchedEffect(scale, isCurrentPage) {
        onZoomChanged(isCurrentPage && scale > 1f)
    }

    LaunchedEffect(rotateTrigger) {
        if (isCurrentPage && rotateTrigger > 0) {
            committedRotation += 90f
        }
    }

    // Request built once per item, not rebuilt on every recomposition
    // (touch-driven zoom/pan updates were otherwise rebuilding it every frame).
    val imageRequest = remember(item.id) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(200)
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                // Lambda form of graphicsLayer: updates the transform at
                // draw time without triggering recomposition on every touch
                // event, which is what made panning feel laggy before.
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = animatedRotation
                    translationX = offsetX
                    translationY = offsetY
                }
                // Pinch-zoom, two-finger rotate (90 degree snapping), pan-while-zoomed.
                // Single-finger drags at 1x scale are deliberately left
                // UNCONSUMED so the parent HorizontalPager still receives
                // them and can swipe between images.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var involvedMultiTouch = false
                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val rotationChange = event.calculateRotation()
                            val panChange = event.calculatePan()

                            if (event.changes.size > 1) {
                                involvedMultiTouch = true
                                scale = (scale * zoomChange).coerceIn(1f, 6f)
                                rotationAccumulator += rotationChange
                                if (abs(rotationAccumulator) >= 45f) {
                                    committedRotation += if (rotationAccumulator > 0) 90f else -90f
                                    rotationAccumulator = 0f
                                }
                                if (scale > 1f) {
                                    offsetX += panChange.x
                                    offsetY += panChange.y
                                }
                                event.changes.forEach { it.consume() }
                            } else if (scale > 1f) {
                                offsetX += panChange.x
                                offsetY += panChange.y
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })

                        rotationAccumulator = 0f
                        if (involvedMultiTouch && scale <= 1f) {
                            animScope.launch { animateResetZoomAndPan() }
                        }
                    }
                }
                // Single tap toggles the icon row; double-tap zooms in/out
                // smoothly (doesn't affect rotation); long-press shows info.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleControls() },
                        onDoubleTap = {
                            animScope.launch {
                                if (scale > 1f) {
                                    animateResetZoomAndPan()
                                } else {
                                    animate(scale, 3f, animationSpec = tween(250)) { value, _ -> scale = value }
                                }
                            }
                        },
                        onLongPress = { onShowInfo() }
                    )
                }
        )
    }
}

/**
 * Fully custom Compose controls (not the native PlayerView controller):
 * - Single tap toggles [controlsVisible] (shared with the parent's icon row).
 * - Double tap toggles play/pause.
 * - Long-press shows file info.
 * - A draggable Slider at the bottom scrubs playback, shown/hidden with the
 *   rest of the controls.
 * Loops indefinitely (REPEAT_MODE_ONE) instead of stopping at the end, and
 * auto-pauses when swiped away from.
 */
@Composable
private fun VideoPage(
    item: MediaItem,
    isCurrentPage: Boolean,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onShowInfo: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(item.id) {
        onDispose { exoPlayer.release() }
    }

    var progress by remember(item.id) { mutableStateOf(0f) }
    var durationMs by remember(item.id) { mutableStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }

    // Polls playback position for the progress bar, except while the user
    // is actively dragging it (so the drag isn't fought by the poll).
    LaunchedEffect(item.id) {
        while (true) {
            if (!isScrubbing) {
                val duration = exoPlayer.duration
                if (duration > 0) {
                    durationMs = duration
                    progress = (exoPlayer.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(300)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onLongPress = { onShowInfo() }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (controlsVisible) {
            Slider(
                value = progress,
                onValueChange = { newValue ->
                    isScrubbing = true
                    progress = newValue
                },
                onValueChangeFinished = {
                    if (durationMs > 0) {
                        exoPlayer.seekTo((progress * durationMs).toLong())
                    }
                    isScrubbing = false
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}