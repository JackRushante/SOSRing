package com.lorenzomarci.sosring

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class LocationPoint(
    val id: Long,
    val contactNumber: String,
    val sessionId: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val timestamp: Long
)

class SosRingDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "sosring.db"
        private const val DB_VERSION = 1
        private const val TABLE_POINTS = "location_points"

        @Volatile private var INSTANCE: SosRingDatabase? = null
        fun getInstance(context: Context): SosRingDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SosRingDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_POINTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_number TEXT NOT NULL,
                session_id TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                accuracy REAL NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX idx_session ON $TABLE_POINTS(session_id)")
        db.execSQL("CREATE INDEX idx_timestamp ON $TABLE_POINTS(timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_POINTS")
        onCreate(db)
    }

    fun insertPoint(contactNumber: String, sessionId: String, lat: Double, lon: Double, accuracy: Float, timestamp: Long) {
        val values = ContentValues().apply {
            put("contact_number", contactNumber)
            put("session_id", sessionId)
            put("lat", lat)
            put("lon", lon)
            put("accuracy", accuracy)
            put("timestamp", timestamp)
        }
        writableDatabase.insertWithOnConflict(TABLE_POINTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getPointsForSession(sessionId: String): List<LocationPoint> {
        val points = mutableListOf<LocationPoint>()
        readableDatabase.query(TABLE_POINTS, null, "session_id = ?", arrayOf(sessionId), null, null, "timestamp ASC").use {
            while (it.moveToNext()) {
                points.add(LocationPoint(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    contactNumber = it.getString(it.getColumnIndexOrThrow("contact_number")),
                    sessionId = it.getString(it.getColumnIndexOrThrow("session_id")),
                    lat = it.getDouble(it.getColumnIndexOrThrow("lat")),
                    lon = it.getDouble(it.getColumnIndexOrThrow("lon")),
                    accuracy = it.getFloat(it.getColumnIndexOrThrow("accuracy")),
                    timestamp = it.getLong(it.getColumnIndexOrThrow("timestamp"))
                ))
            }
        }
        return points
    }

    fun clearSession(sessionId: String) {
        writableDatabase.delete(TABLE_POINTS, "session_id = ?", arrayOf(sessionId))
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_POINTS, null, null)
    }
}
