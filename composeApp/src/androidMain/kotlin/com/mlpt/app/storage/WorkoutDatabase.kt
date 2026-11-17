package com.mlpt.app.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.mlpt.data.SavedTrainingData
import com.mlpt.shared.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, app-private SQLite store. Callers run these methods on an IO dispatcher. */
class WorkoutDatabase(context: Context, name: String = "training.db") : SQLiteOpenHelper(context, name, null, 4) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE completed_workouts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            plan_id TEXT NOT NULL CHECK(length(trim(plan_id)) > 0),
            title TEXT NOT NULL CHECK(length(trim(title)) > 0),
            exercise_count INTEGER NOT NULL CHECK(exercise_count > 0),
            completed_at INTEGER CHECK(completed_at > 0),
            body_part TEXT, planned_minutes INTEGER CHECK(planned_minutes > 0),
            imported_legacy INTEGER NOT NULL DEFAULT 0 CHECK(imported_legacy IN (0, 1))
        )""")
        db.execSQL("""CREATE TABLE completed_exercises (
            workout_id INTEGER NOT NULL REFERENCES completed_workouts(id) ON DELETE CASCADE,
            position INTEGER NOT NULL CHECK(position >= 0),
            exercise_id TEXT NOT NULL CHECK(length(trim(exercise_id)) > 0),
            name TEXT NOT NULL CHECK(length(trim(name)) > 0),
            set_count INTEGER NOT NULL CHECK(set_count > 0),
            reps TEXT NOT NULL CHECK(length(trim(reps)) > 0),
            PRIMARY KEY(workout_id, position)
        )""")
        db.execSQL("CREATE INDEX completed_workouts_date ON completed_workouts(completed_at)")
        db.execSQL("""CREATE TABLE profile (
            id INTEGER PRIMARY KEY CHECK(id = 1),
            name TEXT NOT NULL CHECK(length(trim(name)) > 0),
            goal TEXT NOT NULL CHECK(length(trim(goal)) > 0),
            weekly_target INTEGER NOT NULL CHECK(weekly_target BETWEEN 1 AND 7)
        )""")
        db.execSQL("CREATE TABLE app_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createPoseTables(db)
        createJournalTables(db)
        createStudioTables(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion in 1..3 && newVersion == 4) { "Unsupported database upgrade. Existing data has been preserved." }
        if (oldVersion < 2) createPoseTables(db)
        if (oldVersion < 3) createJournalTables(db)
        createStudioTables(db)
    }

    /** History and import marker commit together; malformed legacy data is never silently dropped. */
    fun migrateLegacy(raw: String?) {
        val db = writableDatabase
        transaction(db) {
            val imported = db.rawQuery("SELECT value FROM app_metadata WHERE key = ?", arrayOf("preferences-v1-imported")).use { it.moveToFirst() }
            if (!imported) {
                if (!raw.isNullOrBlank()) {
                    val root = JSONObject(raw)
                    if (root.has("profile")) root.getJSONObject("profile").let { profile ->
                        saveProfileRow(db, ClientProfile(profile.getString("name"), profile.getString("goal"), profile.getInt("target")))
                    }
                    val items = if (root.has("sessions")) root.getJSONArray("sessions") else JSONArray()
                    (0 until items.length()).forEach { index ->
                        val item = items.getJSONObject(index)
                        val session = TrainingSession(item.getInt("id"), item.getString("planId"), item.getString("title"), item.getInt("count"), importedLegacy = true)
                        require(session.id > 0)
                        insertWorkoutRow(db, session, preserveId = true)
                    }
                }
                db.insertOrThrow("app_metadata", null, ContentValues().apply { put("key", "preferences-v1-imported"); put("value", "1") })
            }
        }
    }

    fun readSaved(): SavedTrainingData {
        val db = readableDatabase
        return transaction(db) {
            val profile = db.rawQuery("SELECT name, goal, weekly_target FROM profile WHERE id = 1", null).use { cursor ->
                if (cursor.moveToFirst()) ClientProfile(cursor.getString(0), cursor.getString(1), cursor.getInt(2)) else null
            }
            val exercises = mutableMapOf<Int, MutableList<Exercise>>()
            db.rawQuery("SELECT workout_id, exercise_id, name, set_count, reps FROM completed_exercises ORDER BY workout_id, position", null).use { cursor ->
                while (cursor.moveToNext()) exercises.getOrPut(cursor.getInt(0)) { mutableListOf() }
                    .add(Exercise(cursor.getString(1), cursor.getString(2), cursor.getInt(3), cursor.getString(4)))
            }
            val sessions = mutableListOf<TrainingSession>()
            db.rawQuery("SELECT id, plan_id, title, exercise_count, completed_at, body_part, planned_minutes, imported_legacy FROM completed_workouts ORDER BY id", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(0)
                    sessions += TrainingSession(id, cursor.getString(1), cursor.getString(2), cursor.getInt(3),
                        if (cursor.isNull(4)) null else cursor.getLong(4), cursor.getString(5),
                        if (cursor.isNull(6)) null else cursor.getInt(6), exercises[id]?.toList() ?: emptyList(), cursor.getInt(7) == 1)
                }
            }
            SavedTrainingData(profile, sessions)
        }
    }

    fun insertCompletedWorkout(session: TrainingSession): TrainingSession {
        require(!session.importedLegacy && session.completedAtEpochMillis != null)
        require(session.exerciseCount == session.exercises.size)
        require(!session.bodyPart.isNullOrBlank() && (session.plannedMinutes ?: 0) > 0)
        return transaction(writableDatabase) { insertWorkoutRow(writableDatabase, session) }
    }
    fun saveProfile(profile: ClientProfile) { transaction(writableDatabase) { saveProfileRow(writableDatabase, profile) } }

    private fun saveProfileRow(db: SQLiteDatabase, profile: ClientProfile) {
        val values = ContentValues().apply { put("id", 1); put("name", profile.name); put("goal", profile.goal); put("weekly_target", profile.weeklyTarget) }
        db.insertWithOnConflict("profile", null, values, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }
    private fun insertWorkoutRow(db: SQLiteDatabase, session: TrainingSession, preserveId: Boolean = false): TrainingSession {
        val values = ContentValues().apply {
            if (preserveId) put("id", session.id)
            put("plan_id", session.planId); put("title", session.title); put("exercise_count", session.exerciseCount)
            put("completed_at", session.completedAtEpochMillis); put("body_part", session.bodyPart); put("planned_minutes", session.plannedMinutes)
            put("imported_legacy", if (session.importedLegacy) 1 else 0)
        }
        val id = db.insertOrThrow("completed_workouts", null, values).toInt()
        session.exercises.forEachIndexed { position, exercise ->
            db.insertOrThrow("completed_exercises", null, ContentValues().apply {
                put("workout_id", id); put("position", position); put("exercise_id", exercise.id)
                put("name", exercise.name); put("set_count", exercise.sets); put("reps", exercise.reps)
            })
        }
        return session.copy(id = id)
    }
    private inline fun <T> transaction(db: SQLiteDatabase, block: () -> T): T {
        db.beginTransaction()
        return try { block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
}
