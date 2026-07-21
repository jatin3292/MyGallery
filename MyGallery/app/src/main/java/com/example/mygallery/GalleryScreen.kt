package com.example.mygallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Root composable owning all top-level app state: the media list, the
 * All/Folders tabs, the currently open folder (if any), multi-select state,
 * and the fullscreen viewer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryApp() {
    val context = LocalContext.current

    var allMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTrigger) {
        allMedia = MediaRepository.loadMedia(context)
        isLoading = false
    }

    // Handles the system confirmation dialog for deletes (API 29+), and for
    // items where a direct delete needed RecoverableSecurityException handling.
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        // Refresh either way so removed items disappear from the grid.
        reloadTrigger++
    }

    val folders = remember(allMedia) { allMedia.toFolders() }

    var selectedTab by remember { mutableStateOf(0) } // 0 = All, 1 = Folders
    var currentFolder by remember { mutableStateOf<MediaFolder?>(null) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    var viewerList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    val displayedItems = currentFolder?.items ?: allMedia

    fun toggleSelected(item: MediaItem) {
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    if (viewerIndex != null) {
        FullscreenViewer(
            mediaList = viewerList,
            startIndex = viewerIndex!!,
            onClose = { viewerIndex = null },
            onDelete = { item ->
                MediaActions.delete(context, listOf(item), deleteLauncher)
                viewerIndex = null
            },
            onShare = { item -> MediaActions.share(context, listOf(item)) }
        )
        return
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val items = displayedItems.filter { it.id in selectedIds }
                            MediaActions.share(context, items)
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share selected")
                        }
                        IconButton(onClick = {
                            val items = displayedItems.filter { it.id in selectedIds }
                            MediaActions.delete(context, items, deleteLauncher)
                            exitSelection()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(currentFolder?.name ?: "My Gallery") },
                    navigationIcon = {
                        if (currentFolder != null) {
                            IconButton(onClick = { currentFolder = null }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to folders")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentFolder == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; exitSelection() },
                        icon = { Icon(Icons.Filled.Photo, contentDescription = null) },
                        label = { Text("All") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; exitSelection() },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        label = { Text("Folders") }
                    )
                }
            }
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            currentFolder != null -> {
                MediaGrid(
                    items = currentFolder!!.items,
                    modifier = Modifier.padding(padding),
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onItemClick = { index ->
                        val item = currentFolder!!.items[index]
                        if (selectionMode) toggleSelected(item)
                        else {
                            viewerList = currentFolder!!.items
                            viewerIndex = index
                        }
                    },
                    onItemLongClick = { index ->
                        selectionMode = true
                        toggleSelected(currentFolder!!.items[index])
                    }
                )
            }
            selectedTab == 0 -> {
                if (allMedia.isEmpty()) {
                    EmptyState(modifier = Modifier.padding(padding))
                } else {
                    MediaGrid(
                        items = allMedia,
                        modifier = Modifier.padding(padding),
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onItemClick = { index ->
                            val item = allMedia[index]
                            if (selectionMode) toggleSelected(item)
                            else {
                                viewerList = allMedia
                                viewerIndex = index
                            }
                        },
                        onItemLongClick = { index ->
                            selectionMode = true
                            toggleSelected(allMedia[index])
                        }
                    )
                }
            }
            else -> {
                FolderGrid(
                    folders = folders,
                    modifier = Modifier.padding(padding),
                    onFolderClick = { folder -> currentFolder = folder }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No photos or videos found")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items.size) { index ->
            val item = items[index]
            ThumbnailCell(
                item = item,
                isSelected = item.id in selectedIds,
                selectionMode = selectionMode,
                onClick = { onItemClick(index) },
                onLongClick = { onItemLongClick(index) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailCell(
    item: MediaItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.LightGray)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            )
        }
        if (selectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
private fun FolderGrid(
    folders: List<MediaFolder>,
    modifier: Modifier = Modifier,
    onFolderClick: (MediaFolder) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(folders.size) { index ->
            val folder = folders[index]
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onFolderClick(folder) }
            ) {
                AsyncImage(
                    model = folder.coverUri,
                    contentDescription = folder.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.itemCount} items",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
