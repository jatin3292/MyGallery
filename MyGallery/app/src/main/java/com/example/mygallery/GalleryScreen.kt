package com.example.mygallery

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Top-level gallery screen: shows a grid of thumbnails, and swaps to a
 * fullscreen pager when an item is tapped.
 */
@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        mediaList = MediaRepository.loadMedia(context)
        isLoading = false
        if (mediaList.isEmpty()) {
            Toast.makeText(context, "No photos or videos found", Toast.LENGTH_SHORT).show()
        }
    }

    if (selectedIndex != null) {
        FullscreenViewer(
            mediaList = mediaList,
            startIndex = selectedIndex!!,
            onClose = { selectedIndex = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("My Gallery") })
            }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(mediaList.size) { index ->
                        ThumbnailCell(item = mediaList[index]) {
                            selectedIndex = index
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailCell(item: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.LightGray)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.isVideo) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            )
        }
    }
}
