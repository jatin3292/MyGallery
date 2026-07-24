package com.example.mygallery

import android.content.Context

/** Tracks which media items the user has starred as favorites, persisted locally. */
object FavoritesStore {
    private const val PREFS = "gallery_favorites_prefs"
    private const val KEY = "favorite_ids"

    fun load(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggle(context: Context, id: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = load(context).toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        prefs.edit().putStringSet(KEY, current.map { it.toString() }.toSet()).apply()
    }
}