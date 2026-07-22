package com.example.mygallery

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles deleting and sharing media items, accounting for the different
 * storage permission models across Android versions.
 */
object MediaActions {

    /** Opens the system share sheet for one or more media items. */
    fun share(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return

        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = if (items[0].isVideo) "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, items[0].uri)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    /**
     * Requests deletion of the given items.
     * - Android 11+ (API 30+): shows a single system confirmation dialog for
     *   the whole batch via MediaStore.createDeleteRequest.
     * - Android 10 (API 29): MediaStore items owned by other apps require
     *   catching RecoverableSecurityException and launching its action intent.
     * - Android 9 and below: direct ContentResolver.delete() works given the
     *   WRITE_EXTERNAL_STORAGE permission declared in the manifest.
     *
     * The launcher's result callback (registered by the caller) is
     * responsible for refreshing the media list afterward.
     */
    fun delete(
        context: Context,
        items: List<MediaItem>,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            }

            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                try {
                    uris.forEach { context.contentResolver.delete(it, null, null) }
                } catch (e: RecoverableSecurityException) {
                    // On API 29, deleting an item not created by this app throws this;
                    // its action intent shows the same kind of confirmation dialog.
                    val intentSender = e.userAction.actionIntent.intentSender
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            }

            else -> {
                uris.forEach {
                    try {
                        context.contentResolver.delete(it, null, null)
                    } catch (_: SecurityException) {
                        // Best effort on very old versions; nothing further we can do here.
                    }
                }
            }
        }
    }

    /**
     * Saves a bitmap (e.g. a crop result) as a brand new image in the gallery,
     * leaving the original untouched. Returns the new item's Uri, or null on failure.
     */
    suspend fun saveEditedCopy(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val filename = "IMG_${System.currentTimeMillis()}_edited.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MyGallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        uri
    }
}