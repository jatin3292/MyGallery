package com.example.mygallery

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
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
import kotlinx.coroutines.launch

/**
 * Root composable owning all top-level app state: the media list, the
 * All/Folders tabs (swipeable), the currently open folder (if any),
 * media-type filtering, multi-select state, theme toggle, and the
 * fullscreen viewer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryApp(isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
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

    // Media type filter (Images / Videos), applied everywhere below.
    var showImages by remember { mutableStateOf(true) }
    var showVideos by remember { mutableStateOf(true) }
    val filteredMedia = remember(allMedia, showImages, showVideos) {
        allMedia.filter { (it.isVideo && showVideos) || (!it.isVideo && showImages) }
    }

    val folders = remember(filteredMedia) { filteredMedia.toFolders() }

    // Bottom tab / swipe state — page 0 = All, page 1 = Folders.
    val tabPagerState = rememberPagerState(initialPage = 0) { 2 }
    val coroutineScope = rememberCoroutineScope()

    // Storing just the id (not the whole MediaFolder) keeps this reactive to
    // the filter above: if the filter changes while a folder is open, its
    // contents update instead of showing a stale snapshot.
    var currentFolderId by remember { mutableStateOf<String?>(null) }
    val currentFolder = folders.find { it.bucketId == currentFolderId }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    var viewerList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    val displayedItems = currentFolder?.items ?: filteredMedia

    fun toggleSelected(item: MediaItem) {
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    // System back button/gesture: exit selection mode first if active,
    // otherwise leave the open folder and return to the folders list —
    // instead of the default behavior of closing the whole app.
    BackHandler(enabled = selectionMode || currentFolderId != null) {
        when {
            selectionMode -> exitSelection()
            currentFolderId != null -> currentFolderId = null
        }
    }

    // Exiting selection mode when the user swipes to a different tab
    // avoids a stale selection carrying over.
    LaunchedEffect(tabPagerState.currentPage) {
        exitSelection()
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
                        if (currentFolderId != null) {
                            IconButton(onClick = { currentFolderId = null }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to folders")
                            }
                        }
                    },
                    actions = {
                        FilterDropdown(
                            showImages = showImages,
                            showVideos = showVideos,
                            onToggleImages = { showImages = !showImages },
                            onToggleVideos = { showVideos = !showVideos }
                        )
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentFolderId == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tabPagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { tabPagerState.animateScrollToPage(0) } },
                        icon = { Icon(Icons.Filled.Photo, contentDescription = null) },
                        label = { Text("All") }
                    )
                    NavigationBarItem(
                        selected = tabPagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { tabPagerState.animateScrollToPage(1) } },
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
                    items = currentFolder.items,
                    modifier = Modifier.padding(padding),
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onItemClick = { index ->
                        val item = currentFolder.items[index]
                        if (selectionMode) toggleSelected(item)
                        else {
                            viewerList = currentFolder.items
                            viewerIndex = index
                        }
                    },
                    onItemLongClick = { index ->
                        selectionMode = true
                        toggleSelected(currentFolder.items[index])
                    }
                )
            }
            else -> {
                // Swipeable All / Folders tabs.
                HorizontalPager(
                    state = tabPagerState,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) { page ->
                    if (page == 0) {
                        if (filteredMedia.isEmpty()) {
                            EmptyState()
                        } else {
                            MediaGrid(
                                items = filteredMedia,
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onItemClick = { index ->
                                    val item = filteredMedia[index]
                                    if (selectionMode) toggleSelected(item)
                                    else {
                                        viewerList = filteredMedia
                                        viewerIndex = index
                                    }
                                },
                                onItemLongClick = { index ->
                                    selectionMode = true
                                    toggleSelected(filteredMedia[index])
                                }
                            )
                        }
                    } else {
                        FolderGrid(
                            folders = folders,
                            onFolderClick = { folder -> currentFolderId = folder.bucketId }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterDropdown(
    showImages: Boolean,
    showVideos: Boolean,
    onToggleImages: () -> Unit,
    onToggleVideos: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.FilterList, contentDescription = "Filter by type")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Images") },
                leadingIcon = { Checkbox(checked = showImages, onCheckedChange = null) },
                onClick = onToggleImages
            )
            DropdownMenuItem(
                text = { Text("Videos") },
                leadingIcon = { Checkbox(checked = showVideos, onCheckedChange = null) },
                onClick = onToggleVideos
            )
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