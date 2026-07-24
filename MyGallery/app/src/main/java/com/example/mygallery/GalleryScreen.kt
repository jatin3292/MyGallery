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
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 * All/Favorites/Folders/Trash tabs (swipeable), the currently open folder
 * (if any), media-type filtering, sort order, multi-select state, theme
 * toggle, trash/favorites bookkeeping, and the fullscreen viewer.
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

    // Trash bookkeeping: id -> when it was trashed. Loaded once, then kept
    // in memory and updated directly (rather than round-tripping through
    // SharedPreferences on every read) for snappy UI updates.
    var trashedMap by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var favoriteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var hasCheckedExpiredTrash by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        trashedMap = TrashStore.load(context)
        favoriteIds = FavoritesStore.load(context)
    }

    // Handles the system confirmation dialog for PERMANENT deletes (API 29+
    // requires this — apps can't silently erase media they didn't create).
    // Used only by "Delete Forever" in the Trash tab and the 30-day auto-sweep.
    var pendingForeverDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        if (pendingForeverDeleteIds.isNotEmpty()) {
            TrashStore.remove(context, pendingForeverDeleteIds)
            trashedMap = TrashStore.load(context)
            pendingForeverDeleteIds = emptyList()
        }
        reloadTrigger++
    }

    fun moveToTrash(items: List<MediaItem>) {
        TrashStore.addToTrash(context, items.map { it.id })
        trashedMap = TrashStore.load(context)
    }

    fun restoreFromTrash(items: List<MediaItem>) {
        TrashStore.remove(context, items.map { it.id })
        trashedMap = TrashStore.load(context)
    }

    fun deleteForever(items: List<MediaItem>) {
        pendingForeverDeleteIds = items.map { it.id }
        MediaActions.delete(context, items, deleteLauncher)
    }

    fun toggleFavorite(item: MediaItem) {
        FavoritesStore.toggle(context, item.id)
        favoriteIds = FavoritesStore.load(context)
    }

    // Once per session, after media has loaded: sweep for trash items older
    // than 30 days and prompt to permanently remove them (a single batched
    // system confirmation, since silent deletion isn't possible).
    LaunchedEffect(allMedia) {
        if (!hasCheckedExpiredTrash && allMedia.isNotEmpty()) {
            hasCheckedExpiredTrash = true
            val expiredIds = TrashStore.findExpired(context, retentionDays = 30)
            val itemsToForceDelete = allMedia.filter { it.id in expiredIds }
            if (itemsToForceDelete.isNotEmpty()) {
                deleteForever(itemsToForceDelete)
            }
        }
    }

    // Media type filter (Images / Videos) and sort order, applied everywhere below.
    var showImages by remember { mutableStateOf(true) }
    var showVideos by remember { mutableStateOf(true) }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }

    val visibleMedia = remember(allMedia, trashedMap) {
        allMedia.filter { it.id !in trashedMap.keys }
    }
    val filteredMedia = remember(visibleMedia, showImages, showVideos, sortOrder) {
        visibleMedia
            .filter { (it.isVideo && showVideos) || (!it.isVideo && showImages) }
            .sortedByOrder(sortOrder)
    }
    val favoritesMedia = remember(filteredMedia, favoriteIds) {
        filteredMedia.filter { it.id in favoriteIds }
    }
    val trashMedia = remember(allMedia, trashedMap) {
        allMedia.filter { it.id in trashedMap.keys }
            .sortedByDescending { trashedMap[it.id] ?: 0L }
    }

    val folders = remember(filteredMedia) { filteredMedia.toFolders() }

    // Bottom tab / swipe state — page 0 = All, 1 = Favorites, 2 = Folders, 3 = Trash.
    val tabPagerState = rememberPagerState(initialPage = 0) { 4 }
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

    var trashActionItem by remember { mutableStateOf<MediaItem?>(null) }

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
                moveToTrash(listOf(item))
                viewerIndex = null
            },
            onShare = { item -> MediaActions.share(context, listOf(item)) },
            onEdited = { reloadTrigger++ },
            isFavorite = { item -> item.id in favoriteIds },
            onToggleFavorite = { item -> toggleFavorite(item) }
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
                            moveToTrash(items)
                            exitSelection()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Move selected to trash")
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
                        if (tabPagerState.currentPage != 3) {
                            SortDropdown(current = sortOrder, onSelect = { sortOrder = it })
                            FilterDropdown(
                                showImages = showImages,
                                showVideos = showVideos,
                                onToggleImages = { showImages = !showImages },
                                onToggleVideos = { showVideos = !showVideos }
                            )
                        }
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
                        icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        label = { Text("Favorites") }
                    )
                    NavigationBarItem(
                        selected = tabPagerState.currentPage == 2,
                        onClick = { coroutineScope.launch { tabPagerState.animateScrollToPage(2) } },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        label = { Text("Folders") }
                    )
                    NavigationBarItem(
                        selected = tabPagerState.currentPage == 3,
                        onClick = { coroutineScope.launch { tabPagerState.animateScrollToPage(3) } },
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        label = { Text("Trash") }
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
                    favoriteIds = favoriteIds,
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
                // Swipeable All / Favorites / Folders / Trash tabs.
                HorizontalPager(
                    state = tabPagerState,
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) { page ->
                    when (page) {
                        0 -> {
                            if (filteredMedia.isEmpty()) {
                                EmptyState()
                            } else {
                                MediaGrid(
                                    items = filteredMedia,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    favoriteIds = favoriteIds,
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
                        }
                        1 -> {
                            if (favoritesMedia.isEmpty()) {
                                EmptyState(message = "No favorites yet — tap the star in the viewer")
                            } else {
                                MediaGrid(
                                    items = favoritesMedia,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    favoriteIds = favoriteIds,
                                    onItemClick = { index ->
                                        val item = favoritesMedia[index]
                                        if (selectionMode) toggleSelected(item)
                                        else {
                                            viewerList = favoritesMedia
                                            viewerIndex = index
                                        }
                                    },
                                    onItemLongClick = { index ->
                                        selectionMode = true
                                        toggleSelected(favoritesMedia[index])
                                    }
                                )
                            }
                        }
                        2 -> {
                            FolderGrid(
                                folders = folders,
                                onFolderClick = { folder -> currentFolderId = folder.bucketId }
                            )
                        }
                        else -> {
                            TrashGrid(
                                items = trashMedia,
                                onItemClick = { item -> trashActionItem = item }
                            )
                        }
                    }
                }
            }
        }
    }

    if (trashActionItem != null) {
        val item = trashActionItem!!
        AlertDialog(
            onDismissRequest = { trashActionItem = null },
            title = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text("This item is in Trash. Restore it, or delete it forever.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreFromTrash(listOf(item))
                    trashActionItem = null
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteForever(listOf(item))
                    trashActionItem = null
                }) { Text("Delete Forever") }
            }
        )
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
private fun SortDropdown(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Sort, contentDescription = "Sort order")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = { RadioButton(selected = current == option, onClick = null) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, message: String = "No photos or videos found") {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    favoriteIds: Set<Long>,
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
                isFavorite = item.id in favoriteIds,
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
    isFavorite: Boolean,
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
        if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Favorite",
                tint = Color(0xFFFFC107),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
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
    if (folders.isEmpty()) {
        EmptyState(modifier = modifier)
        return
    }
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

/**
 * Trash items are shown dimmed and tapping one opens a small Restore /
 * Delete Forever dialog rather than the full viewer, since almost every
 * interaction here is one of those two actions.
 */
@Composable
private fun TrashGrid(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    onItemClick: (MediaItem) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState(modifier = modifier, message = "Trash is empty")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items.size) { index ->
            val item = items[index]
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(Color.DarkGray)
                    .clickable { onItemClick(item) }
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.6f)
                )
                if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    )
                }
            }
        }
    }
}