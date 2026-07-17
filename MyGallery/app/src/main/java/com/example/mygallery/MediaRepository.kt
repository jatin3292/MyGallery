package com.example.mygallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * Represents a single media item (photo or video) from the device.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val dateAdded: Long,
    val displayName: String
)

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
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                items.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        isVideo = false,
                        dateAdded = cursor.getLong(dateCol),
                        displayName = cursor.getString(nameCol) ?: ""
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
            MediaStore.Video.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                items.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        isVideo = true,
                        dateAdded = cursor.getLong(dateCol),
                        displayName = cursor.getString(nameCol) ?: ""
                    )
                )
            }
        }
        return items
    }
}
