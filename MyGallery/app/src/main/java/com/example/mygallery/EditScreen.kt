package com.example.mygallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Basic image editor: crop only, for now. Loads the full-resolution bitmap,
 * shows a draggable/resizable crop rectangle over it (drag a corner to
 * resize, drag the middle to move), and saves the cropped result as a new
 * image in the gallery (the original is left untouched).
 */
@Composable
fun EditScreen(
    item: MediaItem,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }

    val bmp = bitmap
    if (bmp == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var imageDisplaySize by remember(item.id) { mutableStateOf(Size.Zero) }
    var cropRect by remember(item.id) { mutableStateOf(Rect.Zero) }
    var initialized by remember(item.id) { mutableStateOf(false) }

    val minCropSize = 60f
    val cornerGrabRadius = 60f

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 88.dp)
                .onGloballyPositioned { coords ->
                    if (!initialized) {
                        val w = coords.size.width.toFloat()
                        val h = coords.size.height.toFloat()
                        imageDisplaySize = Size(w, h)
                        val margin = 0.1f
                        cropRect = Rect(
                            left = w * margin,
                            top = h * margin,
                            right = w * (1 - margin),
                            bottom = h * (1 - margin)
                        )
                        initialized = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            if (initialized) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(item.id, imageDisplaySize) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val pos = change.position

                                val proposed = when {
                                    (pos - cropRect.topLeft).getDistance() < cornerGrabRadius ->
                                        Rect(cropRect.left + dragAmount.x, cropRect.top + dragAmount.y, cropRect.right, cropRect.bottom)
                                    (pos - cropRect.topRight).getDistance() < cornerGrabRadius ->
                                        Rect(cropRect.left, cropRect.top + dragAmount.y, cropRect.right + dragAmount.x, cropRect.bottom)
                                    (pos - cropRect.bottomLeft).getDistance() < cornerGrabRadius ->
                                        Rect(cropRect.left + dragAmount.x, cropRect.top, cropRect.right, cropRect.bottom + dragAmount.y)
                                    (pos - cropRect.bottomRight).getDistance() < cornerGrabRadius ->
                                        Rect(cropRect.left, cropRect.top, cropRect.right + dragAmount.x, cropRect.bottom + dragAmount.y)
                                    else ->
                                        cropRect.translate(dragAmount)
                                }

                                if (proposed.width >= minCropSize && proposed.height >= minCropSize) {
                                    cropRect = Rect(
                                        left = proposed.left.coerceIn(0f, imageDisplaySize.width - minCropSize),
                                        top = proposed.top.coerceIn(0f, imageDisplaySize.height - minCropSize),
                                        right = proposed.right.coerceIn(minCropSize, imageDisplaySize.width),
                                        bottom = proposed.bottom.coerceIn(minCropSize, imageDisplaySize.height)
                                    )
                                }
                            }
                        }
                ) {
                    val dim = Color.Black.copy(alpha = 0.55f)
                    // Dim everything outside the crop rectangle (4 strips).
                    drawRect(color = dim, topLeft = Offset(0f, 0f), size = Size(size.width, cropRect.top))
                    drawRect(color = dim, topLeft = Offset(0f, cropRect.bottom), size = Size(size.width, size.height - cropRect.bottom))
                    drawRect(color = dim, topLeft = Offset(0f, cropRect.top), size = Size(cropRect.left, cropRect.height))
                    drawRect(color = dim, topLeft = Offset(cropRect.right, cropRect.top), size = Size(size.width - cropRect.right, cropRect.height))

                    // Crop border.
                    drawRect(color = Color.White, topLeft = cropRect.topLeft, size = cropRect.size, style = Stroke(width = 4f))

                    // Corner handles.
                    val handleSize = 28f
                    listOf(cropRect.topLeft, cropRect.topRight, cropRect.bottomLeft, cropRect.bottomRight).forEach { corner ->
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(corner.x - handleSize / 2, corner.y - handleSize / 2),
                            size = Size(handleSize, handleSize)
                        )
                    }
                }
            }
        }

        // Top bar: cancel, title, save.
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }
            Text("Crop", color = Color.White)
            IconButton(
                onClick = {
                    if (isSaving) return@IconButton
                    isSaving = true
                    val scaleX = bmp.width / imageDisplaySize.width
                    val scaleY = bmp.height / imageDisplaySize.height
                    val cropX = (cropRect.left * scaleX).toInt().coerceIn(0, bmp.width - 1)
                    val cropY = (cropRect.top * scaleY).toInt().coerceIn(0, bmp.height - 1)
                    val cropW = (cropRect.width * scaleX).toInt().coerceIn(1, bmp.width - cropX)
                    val cropH = (cropRect.height * scaleY).toInt().coerceIn(1, bmp.height - cropY)
                    val cropped = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)

                    coroutineScope.launch {
                        MediaActions.saveEditedCopy(context, cropped)
                        isSaving = false
                        onSaved()
                    }
                }
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Save crop", tint = Color.White)
            }
        }

        if (isSaving) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}