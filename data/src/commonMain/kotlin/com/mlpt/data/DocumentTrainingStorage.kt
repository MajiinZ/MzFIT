package com.mlpt.data

import com.mlpt.shared.*

/** Platform adapter must atomically replace a value and throw on read/write failure. */
interface LocalDocuments {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
}

/** Versioned, length-prefixed records preserve arbitrary Unicode and separator characters. */
internal class DocumentWriter {
    private val out = StringBuilder()
    fun put(value: Any?) { val s = value?.toString(); if (s == null) out.append("-1:") else out.append(s.length).append(':').append(s) }
    fun <T> list(values: List<T>, write: (T) -> Unit) { put(values.size); values.forEach(write) }
    override fun toString() = out.toString()
}
internal class DocumentReader(private val source: String) {
    private var offset = 0
    fun next(): String? {
        val colon = source.indexOf(':', offset); require(colon >= offset) { "Invalid local record" }
        val size = source.substring(offset, colon).toInt(); offset = colon + 1
        if (size == -1) return null
        require(size >= 0 && size <= source.length - offset) { "Truncated local record" }
        return source.substring(offset, offset + size).also { offset += size }
    }
    fun string() = requireNotNull(next())
    fun int() = string().toInt()
    fun long() = string().toLong()
    fun float() = string().toFloat()
    fun bool() = string().toBooleanStrict()
    fun <T> list(read: () -> T): List<T> { val size = int(); require(size in 0..1_000_000); return List(size) { read() } }
    fun end() { require(offset == source.length) { "Unexpected local record fields" } }
}

class DocumentTrainingStorage(private val documents: LocalDocuments) : TrainingStorage {
    private val key = "training-v1"
    override suspend fun load(): SavedTrainingData {
        val value = documents.read(key) ?: return SavedTrainingData(null, emptyList())
        val r = DocumentReader(value); require(r.string() == "training-1")
        val profile = if (r.bool()) ClientProfile(r.string(), r.string(), r.int()) else null
        val sessions = r.list {
            TrainingSession(r.int(), r.string(), r.string(), r.int(), r.next()?.toLong(), r.next(), r.next()?.toInt(),
                r.list { Exercise(r.string(), r.string(), r.int(), r.string()) }, r.bool())
        }
        r.end(); return SavedTrainingData(profile, sessions)
    }
    private suspend fun save(data: SavedTrainingData) {
        val w = DocumentWriter(); w.put("training-1"); w.put(data.profile != null)
        data.profile?.let { w.put(it.name); w.put(it.goal); w.put(it.weeklyTarget) }
        w.list(data.sessions) { s ->
            w.put(s.id); w.put(s.planId); w.put(s.title); w.put(s.exerciseCount); w.put(s.completedAtEpochMillis)
            w.put(s.bodyPart); w.put(s.plannedMinutes)
            w.list(s.exercises) { w.put(it.id); w.put(it.name); w.put(it.sets); w.put(it.reps) }; w.put(s.importedLegacy)
        }
        documents.write(key, w.toString())
    }
    override suspend fun insertCompletedWorkout(workout: TrainingSession): TrainingSession {
        val data = load(); val saved = workout.copy(id = (data.sessions.maxOfOrNull { it.id } ?: 0) + 1)
        save(data.copy(sessions = data.sessions + saved)); return saved
    }
    override suspend fun saveProfile(profile: ClientProfile) { save(load().copy(profile = profile)) }
}

class DocumentPoseStorage(private val documents: LocalDocuments) : PoseLogStorage {
    private val key = "poses-v1"
    private suspend fun load(): List<PoseSessionLog> {
        val value = documents.read(key) ?: return emptyList()
        val r = DocumentReader(value); require(r.string() == "poses-1")
        val logs = r.list {
            PoseSessionLog(r.string(), r.string(), r.string(), r.long(), r.next()?.toLong(), r.string(), r.string(), r.int(), r.int(),
                r.list { PoseRep(r.int(), r.long(), r.long(), r.bool(), r.float(), r.float(), r.float(), r.float(), r.list { r.string() }) },
                r.list { PoseObservation(r.int(), r.long(), r.string(), r.string(), r.next()?.toFloat(), r.next()?.toFloat(), r.next()?.toFloat(), r.list { r.string() }) })
        }
        r.end(); return logs
    }
    private suspend fun save(logs: List<PoseSessionLog>) {
        val w = DocumentWriter(); w.put("poses-1")
        w.list(logs) { s ->
            w.put(s.id); w.put(s.exerciseKey); w.put(s.exerciseName); w.put(s.startedAt); w.put(s.endedAt); w.put(s.status)
            w.put(s.algorithmVersion); w.put(s.analyzedSamples); w.put(s.usableSamples)
            w.list(s.reps) { r ->
                w.put(r.number); w.put(r.elapsedMs); w.put(r.durationMs); w.put(r.completedRange)
                w.put(r.minAngle); w.put(r.maxAngle); w.put(r.minSecondary); w.put(r.maxSecondary); w.list(r.cues) { w.put(it) }
            }
            w.list(s.observations) { o ->
                w.put(o.number); w.put(o.elapsedMs); w.put(o.quality); w.put(o.phase); w.put(o.angle); w.put(o.secondary)
                w.put(o.confidence); w.list(o.cues) { w.put(it) }
            }
        }
        documents.write(key, w.toString())
    }
    override suspend fun recoverInterruptedSessions() {
        val logs = load()
        if (logs.any { it.status == "active" }) save(logs.map { s -> if (s.status != "active") s else s.copy(status = "interrupted",
            endedAt = s.startedAt + maxOf(s.observations.lastOrNull()?.elapsedMs ?: 0, s.reps.lastOrNull()?.elapsedMs ?: 0)) })
    }
    override suspend fun checkpoint(log: PoseSessionLog) { save(load().filterNot { it.id == log.id } + log) }
    override suspend fun history(limit: Int) = load().sortedByDescending { it.startedAt }.take(limit).map {
        PoseSessionSummary(it.id, it.exerciseName, it.startedAt, it.endedAt, it.status, it.fullReps, it.matchedReps, it.partialAttempts, it.analyzedSamples, it.usableSamples)
    }
    override suspend fun details(id: String) = load().find { it.id == id }?.let { it.copy(observations = it.observations.takeLast(100)) }
}
