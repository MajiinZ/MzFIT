package com.mlpt.app.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class JournalSet(val exercisePosition: Int, val number: Int, val reps: Int, val weightKg: Double?)
data class TimedJournalSet(val exercisePosition: Int, val number: Int, val seconds: Int)
data class WorkoutJournal(val workoutId: Int, val notes: String = "", val sets: List<JournalSet> = emptyList(), val timedSets: List<TimedJournalSet> = emptyList())

internal fun createJournalTables(db: SQLiteDatabase) {
    db.execSQL("""CREATE TABLE IF NOT EXISTS workout_journal (
        workout_id INTEGER PRIMARY KEY REFERENCES completed_workouts(id) ON DELETE CASCADE,
        notes TEXT NOT NULL, updated_at INTEGER NOT NULL)""")
    db.execSQL("""CREATE TABLE IF NOT EXISTS workout_sets (
        workout_id INTEGER NOT NULL REFERENCES workout_journal(workout_id) ON DELETE CASCADE,
        exercise_position INTEGER NOT NULL CHECK(exercise_position >= 0),
        set_number INTEGER NOT NULL CHECK(set_number > 0),
        actual_reps INTEGER NOT NULL CHECK(actual_reps BETWEEN 1 AND 999),
        weight_kg REAL CHECK(weight_kg BETWEEN 0 AND 2000),
        PRIMARY KEY(workout_id, exercise_position, set_number))""")
}

class JournalDatabase(private val helper: WorkoutDatabase) {
    suspend fun load(id: Int): WorkoutJournal = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        db.beginTransaction()
        try {
            val notes = db.rawQuery("SELECT notes FROM workout_journal WHERE workout_id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else "" }
            val sets = mutableListOf<JournalSet>()
            db.rawQuery("SELECT exercise_position,set_number,actual_reps,weight_kg FROM workout_sets WHERE workout_id=? ORDER BY exercise_position,set_number", arrayOf(id.toString())).use {
                while (it.moveToNext()) sets += JournalSet(it.getInt(0), it.getInt(1), it.getInt(2), if (it.isNull(3)) null else it.getDouble(3))
            }
            val timed = mutableListOf<TimedJournalSet>()
            db.rawQuery("SELECT exercise_position,set_number,actual_seconds FROM timed_sets WHERE workout_id=? ORDER BY exercise_position,set_number", arrayOf(id.toString())).use {
                while (it.moveToNext()) timed += TimedJournalSet(it.getInt(0), it.getInt(1), it.getInt(2))
            }
            db.setTransactionSuccessful(); WorkoutJournal(id, notes, sets, timed)
        } finally { db.endTransaction() }
    }
    suspend fun save(journal: WorkoutJournal) = withContext(Dispatchers.IO) {
        require(journal.notes.length <= 5000)
        require(journal.sets.size + journal.timedSets.size <= 500)
        require((journal.sets.map { it.exercisePosition to it.number } + journal.timedSets.map { it.exercisePosition to it.number }).distinct().size == journal.sets.size + journal.timedSets.size)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val count = db.rawQuery("SELECT COUNT(*) FROM completed_exercises WHERE workout_id=?", arrayOf(journal.workoutId.toString())).use { it.moveToFirst(); it.getInt(0) }
            require(journal.sets.all { it.exercisePosition in 0 until count && it.number in 1..100 && it.reps in 1..999 && (it.weightKg == null || it.weightKg.isFinite() && it.weightKg in 0.0..2000.0) })
            require(journal.timedSets.all { it.exercisePosition in 0 until count && it.number in 1..100 && it.seconds in 1..86400 })
            val values = ContentValues().apply { put("workout_id", journal.workoutId); put("notes", journal.notes); put("updated_at", System.currentTimeMillis()) }
            if (db.update("workout_journal", values, "workout_id=?", arrayOf(journal.workoutId.toString())) == 0) db.insertOrThrow("workout_journal", null, values)
            db.delete("workout_sets", "workout_id=?", arrayOf(journal.workoutId.toString()))
            db.delete("timed_sets", "workout_id=?", arrayOf(journal.workoutId.toString()))
            journal.sets.forEach { set -> db.insertOrThrow("workout_sets", null, ContentValues().apply {
                put("workout_id", journal.workoutId); put("exercise_position", set.exercisePosition); put("set_number", set.number)
                put("actual_reps", set.reps); put("weight_kg", set.weightKg)
            }) }
            journal.timedSets.forEach { set -> db.insertOrThrow("timed_sets", null, ContentValues().apply {
                put("workout_id", journal.workoutId); put("exercise_position", set.exercisePosition); put("set_number", set.number); put("actual_seconds", set.seconds)
            }) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    /** A consistent export includes every observation, not the UI's 100-row detail limit. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        db.beginTransaction()
        try {
            val result = JSONObject().put("format", "mlpt-export-1").put("schemaVersion", 4).put("exportedAt", System.currentTimeMillis())
            listOf("profile", "completed_workouts", "completed_exercises", "workout_journal", "workout_sets", "pose_sessions", "pose_reps", "pose_observations", "saved_routines", "active_workout", "workout_details", "timed_sets").forEach { table ->
                val rows = JSONArray()
                db.rawQuery("SELECT * FROM $table", null).use { c ->
                    while (c.moveToNext()) {
                        val row = JSONObject()
                        c.columnNames.forEachIndexed { i, name -> row.put(name, when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            else -> c.getString(i)
                        }) }; rows.put(row)
                    }
                }; result.put(table, rows)
            }
            db.setTransactionSuccessful(); result.toString(2)
        } finally { db.endTransaction() }
    }
}
