package com.mlpt.data

import com.mlpt.shared.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DocumentStorageTest {
    private class Documents : LocalDocuments {
        val records = mutableMapOf<String, String>()
        var fail = false
        override suspend fun read(key: String) = records[key]
        override suspend fun write(key: String, value: String) { check(!fail); records[key] = value }
    }
    @Test fun workoutAndProfileSurviveNewAdapterWithUnicodeAndNullFields() = runTest {
        val db = Documents(); val store = DocumentTrainingStorage(db)
        val profile = ClientProfile("Zak 🏋️\n:0\u0000", "Get stronger", 4)
        store.saveProfile(profile)
        val exercise = Exercise("1:", "Press 🏋️", 3, "8–12")
        val saved = store.insertCompletedWorkout(TrainingSession(0, "p", "Push\n: day", 1, 42, "Chest", 30, listOf(exercise)))
        val legacy = store.insertCompletedWorkout(TrainingSession(0, "old", "Legacy", 2, importedLegacy = true))
        val loaded = DocumentTrainingStorage(db).load()
        assertEquals(profile, loaded.profile); assertEquals(listOf(saved, legacy), loaded.sessions)
        assertEquals(listOf(1, 2), loaded.sessions.map { it.id })
    }
    @Test fun failedWritePreservesPreviousCommittedDocument() = runTest {
        val db = Documents(); val store = DocumentTrainingStorage(db)
        val profile = ClientProfile("Zak", "Strength", 3); store.saveProfile(profile)
        db.fail = true
        assertFailsWith<IllegalStateException> { store.saveProfile(profile.copy(name = "Changed")) }
        assertEquals(profile, DocumentTrainingStorage(db).load().profile)
    }
    private fun log() = PoseSessionLog("id", "push_up", "Push-up", 1000, analyzedSamples = 30, usableSamples = 25,
        reps = listOf(PoseRep(1, 3000, 1500, true, 90f, 165f, 160f, 179f, listOf("HipAlignment"))),
        observations = listOf(PoseObservation(1, 3200, "Adjust", "Ready", 163f, null, .8f, listOf("HipAlignment"))))
    @Test fun poseCheckpointIsIdempotentAndRetainsEveryMetric() = runTest {
        val db = Documents(); val store = DocumentPoseStorage(db); val log = log()
        store.checkpoint(log); store.checkpoint(log)
        val reopened = DocumentPoseStorage(db)
        assertEquals(log, reopened.details(log.id)); assertEquals(1, reopened.history(20).size)
        assertEquals(1, reopened.history(20).single().fullReps)
        assertEquals(0, reopened.history(20).single().matchedReps)
        assertEquals(emptyList(), DocumentTrainingStorage(db).load().sessions)
    }
    @Test fun interruptionUsesLastEventAndLeavesCompletedRecordsAlone() = runTest {
        val db = Documents(); val store = DocumentPoseStorage(db)
        val done = log().copy(id = "done", status = "completed", endedAt = 5000)
        store.checkpoint(log()); store.checkpoint(done)
        DocumentPoseStorage(db).recoverInterruptedSessions()
        assertEquals("interrupted", store.details("id")?.status)
        assertEquals(4200L, store.details("id")?.endedAt)
        assertEquals(done, store.details("done"))
    }
    @Test fun malformedOrUnsupportedRecordsFailWithoutOverwriting() = runTest {
        val db = Documents(); db.records["training-v1"] = "10:training-9"
        val store = DocumentTrainingStorage(db)
        assertFailsWith<IllegalArgumentException> { store.load() }
        assertFailsWith<IllegalArgumentException> { store.saveProfile(ClientProfile("Zak", "Strength", 3)) }
        assertEquals("10:training-9", db.records["training-v1"])
        db.records["poses-v1"] = "7:poses-1" // Missing session count.
        assertFailsWith<IllegalArgumentException> { DocumentPoseStorage(db).recoverInterruptedSessions() }
    }
}
