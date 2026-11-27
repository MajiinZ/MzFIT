package com.mlpt.app.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.mlpt.data.*
import com.mlpt.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun createStudioTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS saved_routines (id TEXT PRIMARY KEY NOT NULL, document TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS active_workout (slot INTEGER PRIMARY KEY CHECK(slot=1), document TEXT NOT NULL)")
    db.execSQL("""CREATE TABLE IF NOT EXISTS workout_details (
        draft_id TEXT PRIMARY KEY NOT NULL, workout_id INTEGER NOT NULL UNIQUE REFERENCES completed_workouts(id) ON DELETE CASCADE,
        elapsed_seconds INTEGER NOT NULL CHECK(elapsed_seconds>=0), planned_sets INTEGER NOT NULL CHECK(planned_sets>0),
        completed_sets INTEGER NOT NULL CHECK(completed_sets>0))""")
    db.execSQL("""CREATE TABLE IF NOT EXISTS timed_sets (
        workout_id INTEGER NOT NULL REFERENCES completed_workouts(id) ON DELETE CASCADE,
        exercise_position INTEGER NOT NULL CHECK(exercise_position>=0), set_number INTEGER NOT NULL CHECK(set_number>0),
        actual_seconds INTEGER NOT NULL CHECK(actual_seconds BETWEEN 1 AND 86400),
        PRIMARY KEY(workout_id,exercise_position,set_number))""")
}

internal object StudioJson {
    private fun exercise(e: Exercise) = JSONObject().put("id", e.id).put("name", e.name).put("sets", e.sets).put("reps", e.reps)
    private fun exercise(j: JSONObject) = Exercise(j.getString("id"), j.getString("name"), j.getInt("sets"), j.getString("reps"))
    private fun plan(p: WorkoutPlan) = JSONObject().put("id", p.id).put("title", p.title).put("category", p.category).put("minutes", p.minutes)
        .put("description", p.description).put("bodyPart", p.bodyPart).put("exercises", JSONArray().apply { p.exercises.forEach { put(exercise(it)) } })
    private fun plan(j: JSONObject) = WorkoutPlan(j.getString("id"), j.getString("title"), j.getString("category"), j.getInt("minutes"),
        j.getString("description"), j.getJSONArray("exercises").objects().map(::exercise), j.getString("bodyPart"))
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    fun routine(r: SavedRoutine): String = JSONObject().put("version", 1).put("plan", plan(r.plan)).put("days", JSONArray(r.days.sorted())).toString()
    fun routine(raw: String): SavedRoutine = JSONObject(raw).let { j ->
        require(j.getInt("version") == 1)
        val days = j.getJSONArray("days")
        SavedRoutine(plan(j.getJSONObject("plan")), (0 until days.length()).map { days.getInt(it) }.toSet()).also(::validateRoutine)
    }
    fun draft(d: WorkoutDraft): String = JSONObject().put("version", 1).put("id", d.id).put("plan", plan(d.plan)).put("startedAt", d.startedAt)
        .put("pounds", d.pounds).put("notes", d.notes).put("exercises", JSONArray().apply { d.exercises.forEach { e ->
            put(JSONObject().put("prescription", exercise(e.prescription)).put("metric", e.metric.name).put("sets", JSONArray().apply { e.sets.forEach { s ->
                put(JSONObject().put("amount", s.amount).put("load", s.load).put("complete", s.complete))
            } }))
        } }).toString()
    fun draft(raw: String): WorkoutDraft = JSONObject(raw).let { j ->
        require(j.getInt("version") == 1)
        WorkoutDraft(j.getString("id"), plan(j.getJSONObject("plan")), j.getLong("startedAt"), j.getBoolean("pounds"),
            j.getJSONArray("exercises").objects().map { e -> LiveExercise(exercise(e.getJSONObject("prescription")), SetMetric.valueOf(e.getString("metric")),
                e.getJSONArray("sets").objects().map { LiveSet(it.getString("amount"), it.getString("load"), it.getBoolean("complete")) }) }, j.getString("notes")).also(::validateDraft)
    }
}

class StudioDatabase(private val helper: WorkoutDatabase) : StudioStorage {
    override suspend fun load(): StudioSnapshot = withContext(Dispatchers.IO) {
        val db = helper.readableDatabase
        transaction(db) {
            val routines = mutableListOf<SavedRoutine>()
            db.rawQuery("SELECT document FROM saved_routines ORDER BY id", null).use { while (it.moveToNext()) routines += StudioJson.routine(it.getString(0)) }
            val draft = db.rawQuery("SELECT document FROM active_workout WHERE slot=1", null).use { if (it.moveToFirst()) StudioJson.draft(it.getString(0)) else null }
            val sets = mutableListOf<PerformanceSet>()
            db.rawQuery("""SELECT s.workout_id,e.name,w.completed_at,s.set_number,s.actual_reps,s.weight_kg FROM workout_sets s
                JOIN completed_exercises e ON e.workout_id=s.workout_id AND e.position=s.exercise_position
                JOIN completed_workouts w ON w.id=s.workout_id ORDER BY s.workout_id,s.exercise_position,s.set_number""", null).use {
                while (it.moveToNext()) sets += PerformanceSet(it.getInt(0), it.getString(1), if (it.isNull(2)) null else it.getLong(2), it.getInt(3), it.getInt(4), null, if (it.isNull(5)) null else it.getDouble(5))
            }
            db.rawQuery("""SELECT s.workout_id,e.name,w.completed_at,s.set_number,s.actual_seconds FROM timed_sets s
                JOIN completed_exercises e ON e.workout_id=s.workout_id AND e.position=s.exercise_position
                JOIN completed_workouts w ON w.id=s.workout_id ORDER BY s.workout_id,s.exercise_position,s.set_number""", null).use {
                while (it.moveToNext()) sets += PerformanceSet(it.getInt(0), it.getString(1), if (it.isNull(2)) null else it.getLong(2), it.getInt(3), null, it.getInt(4), null)
            }
            StudioSnapshot(routines, draft, sets)
        }
    }
    override suspend fun saveRoutine(routine: SavedRoutine) = withContext(Dispatchers.IO) {
        validateRoutine(routine)
        helper.writableDatabase.insertWithOnConflict("saved_routines", null, ContentValues().apply { put("id", routine.plan.id); put("document", StudioJson.routine(routine)) }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
        Unit
    }
    override suspend fun deleteRoutine(id: String) = withContext(Dispatchers.IO) { helper.writableDatabase.delete("saved_routines", "id=?", arrayOf(id)); Unit }
    override suspend fun saveDraft(draft: WorkoutDraft) = withContext(Dispatchers.IO) {
        validateDraft(draft)
        val db = helper.writableDatabase
        transaction(db) {
            check(completedId(db, draft.id) == null) { "Workout already completed" }
            val existing = db.rawQuery("SELECT document FROM active_workout WHERE slot=1", null).use { if (it.moveToFirst()) StudioJson.draft(it.getString(0)).id else null }
            check(existing == null || existing == draft.id) { "Another workout is active" }
            db.insertWithOnConflict("active_workout", null, ContentValues().apply { put("slot", 1); put("document", StudioJson.draft(draft)) }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
        }; Unit
    }
    override suspend fun discardDraft(id: String) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        transaction(db) {
            val existing = db.rawQuery("SELECT document FROM active_workout WHERE slot=1", null).use { if (it.moveToFirst()) StudioJson.draft(it.getString(0)).id else null }
            check(existing == null || existing == id)
            db.delete("active_workout", "slot=1", null)
        }; Unit
    }
    override suspend fun finish(draft: WorkoutDraft, endedAt: Long): Int = withContext(Dispatchers.IO) {
        validateDraft(draft); require(draft.completedSets > 0 && endedAt >= draft.startedAt)
        val db = helper.writableDatabase
        transaction(db) {
            completedId(db, draft.id)?.let { return@transaction it }
            val existing = db.rawQuery("SELECT document FROM active_workout WHERE slot=1", null).use { if (it.moveToFirst()) StudioJson.draft(it.getString(0)).id else null }
            check(existing == null || existing == draft.id) { "Workout draft has been replaced" }
            val completed = draft.exercises.filter { e -> e.sets.any { it.complete } }
            val saved = helper.insertCompletedWorkout(TrainingSession(0, draft.plan.id, draft.plan.title, completed.size, endedAt,
                draft.plan.bodyPart, draft.plan.minutes, completed.map { it.prescription }))
            db.insertOrThrow("workout_journal", null, ContentValues().apply { put("workout_id", saved.id); put("notes", draft.notes.trim()); put("updated_at", endedAt) })
            completed.forEachIndexed { position, e -> e.sets.filter { it.complete }.forEachIndexed { index, s ->
                val values = ContentValues().apply { put("workout_id", saved.id); put("exercise_position", position); put("set_number", index + 1) }
                if (e.metric == SetMetric.Seconds) {
                    values.put("actual_seconds", s.amount.toInt()); db.insertOrThrow("timed_sets", null, values)
                } else {
                    values.put("actual_reps", s.amount.toInt())
                    values.put("weight_kg", s.load.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDouble()?.div(if (draft.pounds) 2.20462262185 else 1.0))
                    db.insertOrThrow("workout_sets", null, values)
                }
            } }
            db.insertOrThrow("workout_details", null, ContentValues().apply {
                put("draft_id", draft.id); put("workout_id", saved.id); put("elapsed_seconds", (endedAt - draft.startedAt) / 1000)
                put("planned_sets", draft.totalSets); put("completed_sets", draft.completedSets)
            })
            db.delete("active_workout", "slot=1", null)
            saved.id
        }
    }
    private fun completedId(db: SQLiteDatabase, draft: String): Int? = db.rawQuery("SELECT workout_id FROM workout_details WHERE draft_id=?", arrayOf(draft)).use { if (it.moveToFirst()) it.getInt(0) else null }
    private inline fun <T> transaction(db: SQLiteDatabase, block: () -> T): T {
        db.beginTransaction()
        return try { block().also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
    }
}
