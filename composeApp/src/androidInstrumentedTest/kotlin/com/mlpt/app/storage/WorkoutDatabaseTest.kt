package com.mlpt.app.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mlpt.shared.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Every test uses a unique database; production workout history is never edited. */
@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "workout_test_${UUID.randomUUID()}.db"
    private var database = WorkoutDatabase(context, name)
    @After fun cleanUp() { database.close(); context.deleteDatabase(name) }
    private fun workout() = TrainingSession(0, "test-plan", "Original title", 2, 1_800_000_000_000L, "Legs", 30,
        listOf(Exercise("squat", "Original squat", 3, "8–10"), Exercise("calf", "Calf raise", 2, "12")))

    @Test fun committedWorkoutsAndProfilesSurviveClosingTheDatabase() = runBlocking {
        var store = AndroidTrainingStorage(context, database) { null }
        store.load()
        val profile = ClientProfile("Test client", "Strength", 3)
        store.saveProfile(profile)
        val saved = store.insertCompletedWorkout(workout())
        database.close()
        database = WorkoutDatabase(context, name)
        store = AndroidTrainingStorage(context, database) { null }
        val restored = store.load()
        assertEquals(profile, restored.profile)
        assertEquals(listOf(saved), restored.sessions)
        assertEquals("Original squat", restored.sessions.single().exercises.first().name)
        assertTrue(store.insertCompletedWorkout(workout()).id > saved.id)
    }

    @Test fun legacyImportPreservesIdsAndRunsOnceWithoutInventingDetails() = runBlocking {
        val legacy = """{"profile":{"name":"Legacy client","goal":"Strength","target":4},"sessions":[{"id":4,"planId":"retired","title":"Old plan","count":5},{"id":9,"planId":"strength","title":"Another plan","count":3}]}"""
        val store = AndroidTrainingStorage(context, database) { legacy }
        val imported = store.load()
        assertEquals(listOf(4, 9), imported.sessions.map { it.id })
        assertTrue(imported.sessions.all { it.importedLegacy && it.completedAtEpochMillis == null && it.exercises.isEmpty() })
        assertEquals(imported, store.load())
        assertEquals("Legacy client", imported.profile!!.name)
        assertTrue(store.insertCompletedWorkout(workout()).id > 9)
        assertEquals(3, store.load().sessions.size)
    }

    @Test fun aFailedExerciseInsertRollsBackTheWholeWorkout() = runBlocking {
        val store = AndroidTrainingStorage(context, database) { null }
        store.load()
        val invalid = workout().copy(exercises = listOf(Exercise("good", "Valid exercise", 3, "8"), Exercise("bad", "", 2, "8")))
        var failed = false
        try { store.insertCompletedWorkout(invalid) } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertTrue(store.load().sessions.isEmpty())
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM completed_exercises", null).use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
        assertEquals(2, store.insertCompletedWorkout(workout()).exercises.size)
    }

    @Test fun malformedLegacyDataRollsBackAndCanBeRetried() = runBlocking {
        var legacy = """{"profile":{"name":"Test","goal":"Strength","target":3},"sessions":[{"id":1,"planId":"old","title":"Valid","count":2},{"id":2}]}"""
        val store = AndroidTrainingStorage(context, database) { legacy }
        var failed = false
        try { store.load() } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertTrue(database.readSaved().sessions.isEmpty())
        assertNull(database.readSaved().profile)
        legacy = """{"sessions":[{"id":1,"planId":"old","title":"Recovered workout","count":2}]}"""
        assertEquals("Recovered workout", store.load().sessions.single().title)
    }
}
