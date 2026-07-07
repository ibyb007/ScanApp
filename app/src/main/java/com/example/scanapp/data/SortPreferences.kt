package com.example.scanapp.data

import android.content.Context

/**
 * Persists the person's chosen Home list sort (field + direction) across
 * app restarts, the same way ThemePreferences persists the day/night
 * override — plain SharedPreferences rather than DataStore, since this is
 * a couple of small values read once at startup and written once per sort
 * change.
 */
object SortPreferences {

    private const val PREFS_NAME = "sort_prefs"
    private const val KEY_SORT_BY = "home_sort_by"
    private const val KEY_SORT_DIRECTION = "home_sort_direction"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Falls back to DATE_MODIFIED / DESCENDING if nothing's been saved yet, or the saved name no longer matches an enum value (e.g. after a rename). */
    fun getSortBy(context: Context): DocumentSortBy {
        val name = prefs(context).getString(KEY_SORT_BY, null) ?: return DocumentSortBy.DATE_MODIFIED
        return runCatching { DocumentSortBy.valueOf(name) }.getOrDefault(DocumentSortBy.DATE_MODIFIED)
    }

    fun getSortDirection(context: Context): SortDirection {
        val name = prefs(context).getString(KEY_SORT_DIRECTION, null) ?: return SortDirection.DESCENDING
        return runCatching { SortDirection.valueOf(name) }.getOrDefault(SortDirection.DESCENDING)
    }

    fun setSort(context: Context, sortBy: DocumentSortBy, direction: SortDirection) {
        prefs(context).edit()
            .putString(KEY_SORT_BY, sortBy.name)
            .putString(KEY_SORT_DIRECTION, direction.name)
            .apply()
    }
}
