package com.example.mygallery

import android.content.Context

/**
 * Tracks "trashed" items locally (SharedPreferences), without touching the
 * actual files. This gives a Recently-Deleted safety net: the normal Delete
 * action just records an item here (instant, no system confirmation needed)
 * and hides it from the regular views. Only "Delete Forever" from the Trash
 * screen — or the automatic 30-day sweep — actually removes the file, which
 * still requires a one-time system confirmation on Android 10+ since apps
 * can't silently delete media they didn't create.
 */
object TrashStore {
    private const val PREFS = "gallery_trash_prefs"
    private const val KEY = "trashed_items" // set of "id:trashedAtEpochMillis"

    /** Returns a map of media id -> the time it was trashed (epoch millis). */
    fun load(context: Context): Map<Long, Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val id = parts[0].toLongOrNull()
                val ts = parts[1].toLongOrNull()
                if (id != null && ts != null) id to ts else null
            } else null
        }.toMap()
    }

    private fun save(context: Context, map: Map<Long, Long>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = map.map { (id, ts) -> "$id:$ts" }.toSet()
        prefs.edit().putStringSet(KEY, set).apply()
    }

    /** Moves the given ids into trash (recording the current time). */
    fun addToTrash(context: Context, ids: List<Long>) {
        val map = load(context).toMutableMap()
        val now = System.currentTimeMillis()
        ids.forEach { map[it] = now }
        save(context, map)
    }

    /** Removes the given ids from trash bookkeeping (restore, or after permanent deletion). */
    fun remove(context: Context, ids: List<Long>) {
        val map = load(context).toMutableMap()
        ids.forEach { map.remove(it) }
        save(context, map)
    }

    /** Returns ids that have been sitting in trash longer than [retentionDays]. */
    fun findExpired(context: Context, retentionDays: Int = 30): List<Long> {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        return load(context).filter { it.value < cutoff }.keys.toList()
    }
}