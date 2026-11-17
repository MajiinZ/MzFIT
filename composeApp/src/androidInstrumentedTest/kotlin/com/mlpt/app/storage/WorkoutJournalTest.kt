package com.mlpt.app.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mlpt.shared.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkoutJournalTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "journal_test_${UUID.randomUUID()}.db"
    private var database = WorkoutDatabase(context, name)
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }
    private fun workout() = database.insertCompletedWorkout(TrainingSession(0, "plan", "Test", 1, 1_800_000_000_000L, "Chest", 30, listOf(Exercise("bench", "Bench press", 3, "8–12"))))

    @Test fun actualSetsAndNotesSurviveReopenAndReplacementWithoutChangingPrescription() = runBlocking {
        val workout = workout(); val original = database.readSaved()
        val journal = WorkoutJournal(workout.id, "Felt stronger 🏋️", listOf(JournalSet(0, 1, 10, 42.5), JournalSet(0, 2, 8, null)))
        JournalDatabase(database).save(journal)
        database.close(); database = WorkoutDatabase(context, name)
        assertEquals(journal, JournalDatabase(database).load(workout.id))
        val replacement = journal.copy(notes = "Updated", sets = journal.sets.take(1))
        JournalDatabase(database).save(replacement); JournalDatabase(database).save(replacement)
        assertEquals(replacement, JournalDatabase(database).load(workout.id))
        assertEquals(original, database.readSaved())
    }
    @Test fun badSetOrMissingWorkoutCannotOverwriteJournal() = runBlocking {
        val id = workout().id; val store = JournalDatabase(database)
        val original = WorkoutJournal(id, "Keep me", listOf(JournalSet(0, 1, 8, 20.0))); store.save(original)
        var failed = false
        try { store.save(original.copy(notes = "Discard", sets = listOf(JournalSet(2, 1, 8, 20.0)))) } catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(original, store.load(id))
        failed = false
        try { store.save(WorkoutJournal(999, "Orphan")) } catch (_: Exception) { failed = true }
        assertTrue(failed)
    }
    @Test fun upgradeFromTwoRetainsExistingWorkoutAndPoseHistory() = runBlocking {
        val id = workout().id
        val pose = PoseSessionLog("pose", "biceps_curl", "Curl", 1000, 2000, "completed")
        PoseLogDatabase(database).checkpoint(pose)
        val original = database.readSaved()
        database.writableDatabase.apply { execSQL("DROP TABLE timed_sets"); execSQL("DROP TABLE workout_details"); execSQL("DROP TABLE active_workout"); execSQL("DROP TABLE saved_routines"); execSQL("DROP TABLE workout_sets"); execSQL("DROP TABLE workout_journal"); version = 2 }
        database.close(); database = WorkoutDatabase(context, name)
        assertEquals(original, database.readSaved()); assertEquals(pose, PoseLogDatabase(database).details("pose"))
        JournalDatabase(database).save(WorkoutJournal(id, "New journal"))
        assertEquals("New journal", JournalDatabase(database).load(id).notes)
    }
    @Test fun childInsertFailureRollsBackNotesAndDeletedSetsTogether() = runBlocking {
        val id = workout().id; val store = JournalDatabase(database)
        val original = WorkoutJournal(id, "Original", listOf(JournalSet(0, 1, 8, 20.0))); store.save(original)
        database.writableDatabase.execSQL("CREATE TRIGGER fail_second_set BEFORE INSERT ON workout_sets WHEN NEW.set_number=2 BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        var failed = false
        try { store.save(original.copy(notes = "Uncommitted", sets = original.sets + JournalSet(0, 2, 6, 22.5))) } catch (_: Exception) { failed = true }
        assertTrue(failed); assertEquals(original, store.load(id))
    }
    @Test fun exportContainsFullPoseObservationsAndJournalWithoutUiTruncation() = runBlocking {
        val id = workout().id
        val store = JournalDatabase(database); store.save(WorkoutJournal(id, "Export me", listOf(JournalSet(0, 1, 5, 50.0))))
        PoseLogDatabase(database).checkpoint(PoseSessionLog("pose", "push_up", "Push-up", 1000,
            analyzedSamples = 120, usableSamples = 120,
            observations = (1..120).map { PoseObservation(it, it * 1000L, "WithinChecks", "Ready", 170f, 175f, .95f, emptyList()) }))
        val json = JSONObject(store.exportJson())
        assertEquals(4, json.getInt("schemaVersion")); assertEquals(120, json.getJSONArray("pose_observations").length())
        assertEquals("Export me", json.getJSONArray("workout_journal").getJSONObject(0).getString("notes"))
        assertEquals(50.0, json.getJSONArray("workout_sets").getJSONObject(0).getDouble("weight_kg"), .00001)
        assertEquals("8–12", json.getJSONArray("completed_exercises").getJSONObject(0).getString("reps"))
    }
}
