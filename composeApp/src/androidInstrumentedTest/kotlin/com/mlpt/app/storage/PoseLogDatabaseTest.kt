package com.mlpt.app.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mlpt.shared.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PoseLogDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "pose_test_${UUID.randomUUID()}.db"
    private var database = WorkoutDatabase(context,name)
    @After fun cleanup() { database.close();context.deleteDatabase(name) }
    private fun sample() = PoseSessionLog("test-session","bodyweight_squat","Bodyweight squat",1_800_000_000_000L,
        analyzedSamples=20,usableSamples=18,
        reps=listOf(PoseRep(1,3000,1500,true,85f,171f,0f,20f,emptyList())),
        observations=listOf(PoseObservation(1,4000,"WithinChecks","Ready",170f,5f,0.95f,emptyList())))

    @Test fun checkpointsAreIdempotentAndRestoreAllRepMeasurements() = runBlocking {
        var store = PoseLogDatabase(database)
        val log = sample().copy(endedAt=1_800_000_005_000L,status="completed")
        store.checkpoint(log);store.checkpoint(log)
        database.close();database=WorkoutDatabase(context,name);store=PoseLogDatabase(database)
        assertEquals(log,store.details(log.id))
        assertEquals(1,store.history(20).size)
        assertEquals(1,store.history(20).single().matchedReps)
        assertTrue(database.readSaved().sessions.isEmpty())
    }
    @Test fun interruptedSessionsRecoverAtTheLastLoggedSample() = runBlocking {
        val store = PoseLogDatabase(database)
        store.checkpoint(sample())
        store.recoverInterruptedSessions()
        val recovered = store.details("test-session")!!
        assertEquals("interrupted",recovered.status)
        assertEquals(recovered.startedAt+4000,recovered.endedAt)
        assertEquals(1,recovered.fullReps)
    }
    @Test fun failedChildInsertDoesNotOverwriteThePreviouslyCommittedLog() = runBlocking {
        val store = PoseLogDatabase(database)
        val original=sample();store.checkpoint(original)
        var failed=false
        try { store.checkpoint(original.copy(reps=original.reps+original.reps.single().copy(number=2,durationMs=-1))) } catch (_:Exception) { failed=true }
        assertTrue(failed)
        assertEquals(original,store.details(original.id))
    }
    @Test fun schemaOneMigrationPreservesExistingWorkoutAndProfile() = runBlocking {
        database.migrateLegacy("""{"profile":{"name":"Test","goal":"Strength","target":3},"sessions":[{"id":7,"planId":"old","title":"Preserved workout","count":3}]}""")
        val original=database.readSaved()
        // Build an exact v1 workout schema by removing only new tables from this isolated test DB.
        database.writableDatabase.apply { execSQL("DROP TABLE timed_sets");execSQL("DROP TABLE workout_details");execSQL("DROP TABLE active_workout");execSQL("DROP TABLE saved_routines");execSQL("DROP TABLE workout_sets");execSQL("DROP TABLE workout_journal");execSQL("DROP TABLE pose_observations");execSQL("DROP TABLE pose_reps");execSQL("DROP TABLE pose_sessions");version=1 }
        database.close();database=WorkoutDatabase(context,name)
        assertEquals(original,database.readSaved())
        val store=PoseLogDatabase(database);store.checkpoint(sample())
        assertEquals(1,store.history(20).size)
        assertEquals(original,database.readSaved())
    }
}
