package com.example.scanapp.ui

import android.net.Uri

/**
 * A single page shown on the document detail screen (and, from there, in the
 * full-screen preview/edit flow). [pageId] is the stable DB id used to key
 * deletes/reorders/edits back to the repository; [pageIndex] is its position
 * within the document at the time it was loaded.
 */
data class DetailPage(
    val pageId: Long,
    val pageIndex: Int,
    val uri: Uri
)
