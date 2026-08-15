package dev.daanyaal.truescreentime

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One app's usage on one archived day. */
data class ArchivedUsage(
    val packageName: String,
    val label: String,
    val totalMs: Long,
    val isSystem: Boolean,
)

/**
 * A permanent per-day, per-app record of usage, kept because the OS discards
 * detailed events after about a week. Days are stored raw — filters are
 * applied when the data is displayed, so changing them stays retroactive.
 *
 * Every method touches disk; call them off the main thread.
 */
class UsageArchive(context: Context) {

    private val helper = object : SQLiteOpenHelper(
        context.applicationContext, DB_NAME, null, DB_VERSION
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    day_start INTEGER NOT NULL,
                    package_name TEXT NOT NULL,
                    label TEXT NOT NULL,
                    total_ms INTEGER NOT NULL,
                    is_system INTEGER NOT NULL,
                    PRIMARY KEY (day_start, package_name)
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Only one schema version so far; nothing to migrate.
        }
    }

    /**
     * Replaces the stored record for [dayStart]. An empty list is ignored:
     * we cannot tell "the phone was untouched" from "the OS pruned it", and
     * wiping a good day would be the worse mistake.
     */
    fun saveDay(dayStart: Long, apps: List<AppUsage>) {
        if (apps.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE, "day_start = ?", arrayOf(dayStart.toString()))
            for (app in apps) {
                val values = ContentValues().apply {
                    put("day_start", dayStart)
                    put("package_name", app.packageName)
                    put("label", app.label)
                    put("total_ms", app.totalTimeMs)
                    put("is_system", if (app.isSystem) 1 else 0)
                }
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Stored usage for a day, longest first. Empty when nothing is kept. */
    fun loadDay(dayStart: Long): List<ArchivedUsage> {
        val results = mutableListOf<ArchivedUsage>()
        helper.readableDatabase.query(
            TABLE,
            arrayOf("package_name", "label", "total_ms", "is_system"),
            "day_start = ?",
            arrayOf(dayStart.toString()),
            null,
            null,
            "total_ms DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += ArchivedUsage(
                    packageName = cursor.getString(0),
                    label = cursor.getString(1),
                    totalMs = cursor.getLong(2),
                    isSystem = cursor.getInt(3) != 0,
                )
            }
        }
        return results
    }

    /** How many distinct days are on record — handy for a "history" readout. */
    fun archivedDayCount(): Int =
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT day_start) FROM $TABLE", null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    fun deleteBefore(cutoffDayStart: Long) {
        helper.writableDatabase.delete(TABLE, "day_start < ?", arrayOf(cutoffDayStart.toString()))
    }

    private companion object {
        const val DB_NAME = "usage_archive.db"
        const val DB_VERSION = 1
        const val TABLE = "daily_usage"
    }
}
