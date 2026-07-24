package com.example.mygallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a single media item (photo or video) from the device.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val dateAdded: Long,
    val displayName: String,
    val bucketId: String,
    val bucketName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int
)

/**
 * Represents a device folder/album (e.g. "Camera", "Screenshots", "WhatsApp Images"),
 * grouping together all MediaItems that share the same bucket.
 */
data class MediaFolder(
    val bucketId: String,
    val name: String,
    val coverUri: Uri,
    val itemCount: Int,
    val items: List<MediaItem>
)

/** Available ways to order the media grid / viewer. */
enum class SortOrder(val label: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    LARGEST_FIRST("Largest first"),
    SMALLEST_FIRST("Smallest first")
}

/**
 * Queries the device MediaStore for images and videos, merges and sorts them
 * by date added (newest first). Uses MediaStore so no broad file storage
 * permission is required on modern Android versions.
 */
object MediaRepository {

    fun loadMedia(context: Context): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        items.addAll(queryImages(context))
        items.addAll(queryVideos(context))
        return items.sortedByDescending { it.dateAdded }
    }

    private fun queryImages(context: Context): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                items.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        isVideo = false,
                        dateAdded = cursor.getLong(dateCol),
                        displayName = cursor.getString(nameCol) ?: "",
                        bucketId = cursor.getString(bucketIdCol) ?: "unknown",
                        bucketName = cursor.getString(bucketNameCol) ?: "Unknown",
                        sizeBytes = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol)
                    )
                )
            }
        }
        return items
    }

    private fun queryVideos(context: Context): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                items.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        isVideo = true,
                        dateAdded = cursor.getLong(dateCol),
                        displayName = cursor.getString(nameCol) ?: "",
                        bucketId = cursor.getString(bucketIdCol) ?: "unknown",
                        bucketName = cursor.getString(bucketNameCol) ?: "Unknown",
                        sizeBytes = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol)
                    )
                )
            }
        }
        return items
    }
}

/**
 * Groups a flat media list into folders (albums), each sorted newest-first,
 * with the folders themselves ordered by their most recent item.
 */
fun List<MediaItem>.toFolders(): List<MediaFolder> {
    return this.groupBy { it.bucketId }
        .map { (bucketId, groupItems) ->
            val sorted = groupItems.sortedByDescending { it.dateAdded }
            MediaFolder(
                bucketId = bucketId,
                name = sorted.first().bucketName,
                coverUri = sorted.first().uri,
                itemCount = sorted.size,
                items = sorted
            )
        }
        .sortedByDescending { it.items.first().dateAdded }
}

/** Applies the chosen sort order to a media list. */
fun List<MediaItem>.sortedByOrder(order: SortOrder): List<MediaItem> = when (order) {
    SortOrder.NEWEST_FIRST -> sortedByDescending { it.dateAdded }
    SortOrder.OLDEST_FIRST -> sortedBy { it.dateAdded }
    SortOrder.LARGEST_FIRST -> sortedByDescending { it.sizeBytes }
    SortOrder.SMALLEST_FIRST -> sortedBy { it.sizeBytes }
}

/** Formats a byte count as a human-readable string (e.g. "4.2 MB"). */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

/** Formats a MediaStore DATE_ADDED value (seconds since epoch) as a readable date/time. */
fun formatDate(epochSeconds: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy \u00b7 h:mm a", Locale.getDefault())
    return sdf.format(Date(epochSeconds * 1000))
}