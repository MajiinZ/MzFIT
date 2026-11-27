package com.mlpt.app.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.mlpt.data.PoseLogStorage
import com.mlpt.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal fun createPoseTables(db: SQLiteDatabase) {
    db.execSQL("""CREATE TABLE pose_sessions (
        id TEXT PRIMARY KEY NOT NULL, exercise_key TEXT NOT NULL, exercise_name TEXT NOT NULL,
        started_at INTEGER NOT NULL, ended_at INTEGER, status TEXT NOT NULL CHECK(status IN ('active','completed','interrupted')),
        algorithm_version TEXT NOT NULL, analyzed_samples INTEGER NOT NULL, usable_samples INTEGER NOT NULL,
        full_reps INTEGER NOT NULL, matched_reps INTEGER NOT NULL, partial_attempts INTEGER NOT NULL,
        CHECK(usable_samples BETWEEN 0 AND analyzed_samples), CHECK(matched_reps BETWEEN 0 AND full_reps),
        CHECK(partial_attempts >= 0), CHECK((status = 'active' AND ended_at IS NULL) OR (status != 'active' AND ended_at >= started_at))
    )""")
    db.execSQL("CREATE INDEX pose_sessions_date ON pose_sessions(started_at)")
    db.execSQL("""CREATE TABLE pose_reps (
        session_id TEXT NOT NULL REFERENCES pose_sessions(id) ON DELETE CASCADE,
        number INTEGER NOT NULL, elapsed_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL,
        completed_range INTEGER NOT NULL CHECK(completed_range IN (0,1)), min_angle REAL NOT NULL, max_angle REAL NOT NULL,
        min_secondary REAL NOT NULL, max_secondary REAL NOT NULL, cues TEXT NOT NULL,
        PRIMARY KEY(session_id, number), CHECK(number > 0), CHECK(duration_ms >= 0), CHECK(elapsed_ms >= 0)
    )""")
    db.execSQL("""CREATE TABLE pose_observations (
        session_id TEXT NOT NULL REFERENCES pose_sessions(id) ON DELETE CASCADE,
        number INTEGER NOT NULL, elapsed_ms INTEGER NOT NULL, quality TEXT NOT NULL, phase TEXT NOT NULL,
        angle REAL, secondary REAL, confidence REAL, cues TEXT NOT NULL,
        PRIMARY KEY(session_id, number), CHECK(number > 0), CHECK(elapsed_ms >= 0)
    )""")
}

class PoseLogDatabase(private val database: WorkoutDatabase) : PoseLogStorage {
    override suspend fun recoverInterruptedSessions() = withContext(Dispatchers.IO) {
        database.writableDatabase.execSQL("""UPDATE pose_sessions SET status='interrupted', ended_at=started_at +
            COALESCE((SELECT MAX(elapsed_ms) FROM pose_observations WHERE session_id=pose_sessions.id),0) WHERE status='active'""")
    }
    override suspend fun checkpoint(log: PoseSessionLog) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", log.id); put("exercise_key", log.exerciseKey); put("exercise_name", log.exerciseName)
                put("started_at", log.startedAt); put("ended_at", log.endedAt); put("status", log.status); put("algorithm_version", log.algorithmVersion)
                put("analyzed_samples", log.analyzedSamples); put("usable_samples", log.usableSamples)
                put("full_reps", log.fullReps); put("matched_reps", log.matchedReps); put("partial_attempts", log.partialAttempts)
            }
            if (db.update("pose_sessions", values, "id = ?", arrayOf(log.id)) == 0) db.insertOrThrow("pose_sessions", null, values)
            fun latest(table: String) = db.rawQuery("SELECT COALESCE(MAX(number),0) FROM $table WHERE session_id=?", arrayOf(log.id)).use { it.moveToFirst(); it.getInt(0) }
            val lastRep = latest("pose_reps")
            log.reps.filter { it.number > lastRep }.forEach { rep -> db.insertOrThrow("pose_reps", null, ContentValues().apply {
                put("session_id", log.id); put("number", rep.number); put("elapsed_ms", rep.elapsedMs); put("duration_ms", rep.durationMs)
                put("completed_range", if (rep.completedRange) 1 else 0); put("min_angle", rep.minAngle); put("max_angle", rep.maxAngle)
                put("min_secondary", rep.minSecondary); put("max_secondary", rep.maxSecondary); put("cues", JSONArray(rep.cues).toString())
            }) }
            val lastObservation = latest("pose_observations")
            log.observations.filter { it.number > lastObservation }.forEach { event -> db.insertOrThrow("pose_observations", null, ContentValues().apply {
                put("session_id", log.id); put("number", event.number); put("elapsed_ms", event.elapsedMs); put("quality", event.quality); put("phase", event.phase)
                put("angle", event.angle); put("secondary", event.secondary); put("confidence", event.confidence); put("cues", JSONArray(event.cues).toString())
            }) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    override suspend fun history(limit: Int): List<PoseSessionSummary> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PoseSessionSummary>()
        database.readableDatabase.rawQuery("SELECT id,exercise_name,started_at,ended_at,status,full_reps,matched_reps,partial_attempts,analyzed_samples,usable_samples FROM pose_sessions ORDER BY started_at DESC,id DESC LIMIT ?", arrayOf(limit.toString())).use { c ->
            while(c.moveToNext()) result += PoseSessionSummary(c.getString(0),c.getString(1),c.getLong(2),if(c.isNull(3)) null else c.getLong(3),c.getString(4),c.getInt(5),c.getInt(6),c.getInt(7),c.getInt(8),c.getInt(9))
        }
        result
    }
    override suspend fun details(id: String): PoseSessionLog? = withContext(Dispatchers.IO) {
        val db = database.readableDatabase
        val log = db.rawQuery("SELECT exercise_key,exercise_name,started_at,ended_at,status,algorithm_version,analyzed_samples,usable_samples FROM pose_sessions WHERE id=?", arrayOf(id)).use { c ->
            if(!c.moveToFirst()) null else PoseSessionLog(id,c.getString(0),c.getString(1),c.getLong(2),if(c.isNull(3)) null else c.getLong(3),c.getString(4),c.getString(5),c.getInt(6),c.getInt(7))
        } ?: return@withContext null
        fun cues(raw: String): List<String> { val array = JSONArray(raw); return (0 until array.length()).map { array.getString(it) } }
        val reps = mutableListOf<PoseRep>()
        db.rawQuery("SELECT number,elapsed_ms,duration_ms,completed_range,min_angle,max_angle,min_secondary,max_secondary,cues FROM pose_reps WHERE session_id=? ORDER BY number",arrayOf(id)).use { c ->
            while(c.moveToNext()) reps += PoseRep(c.getInt(0),c.getLong(1),c.getLong(2),c.getInt(3)==1,c.getFloat(4),c.getFloat(5),c.getFloat(6),c.getFloat(7),cues(c.getString(8)))
        }
        val events = mutableListOf<PoseObservation>()
        db.rawQuery("SELECT number,elapsed_ms,quality,phase,angle,secondary,confidence,cues FROM pose_observations WHERE session_id=? ORDER BY number DESC LIMIT 100",arrayOf(id)).use { c ->
            while(c.moveToNext()) events += PoseObservation(c.getInt(0),c.getLong(1),c.getString(2),c.getString(3),if(c.isNull(4)) null else c.getFloat(4),if(c.isNull(5)) null else c.getFloat(5),if(c.isNull(6)) null else c.getFloat(6),cues(c.getString(7)))
        }
        log.copy(reps = reps, observations = events.asReversed())
    }
}
