package com.example.scanapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved scan in the library, shown as a single row in "Recents". A document
 * can have multiple pages (e.g. a multi-page scanned letter), all stored as
 * separate JPEG files referenced by DocumentPageEntity.
 *
 * filesDir-relative storage: pages live under filesDir/scans/<documentId>/page_N.jpg.
 * This is private app storage (not the public Documents folder) — it is the
 * SOURCE LIBRARY the app manages directly, separate from "Export to Documents"
 * which is a one-way copy-out action triggered by the user.
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
    val accessedAtMillis: Long,
    /**
     * User-defined manual ordering position, used when the Home list's sort
     * mode is DocumentSortBy.MANUAL (drag-to-reorder after multi-select).
     * Defaults to 0 for freshly-migrated rows; backfilled to match insertion
     * order by the MIGRATION_1_2 Room migration so existing libraries start
     * with a sensible order rather than everything tied at 0.
     */
    val manualOrder: Long = 0L
)

/**
 * One page image belonging to a DocumentEntity. pageIndex defines display order
 * within the document (0-based). filePath is an absolute path under the app's
 * private files directory.
 */
@Entity(
    tableName = "document_pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val filePath: String,
    /** Rotation to apply at render/export time, in degrees clockwise (0/90/180/270). */
    val rotationDegrees: Int = 0,
    /** Filter applied to this page: "none", "grayscale", "blackAndWhite". */
    val filter: String = "none"
)
