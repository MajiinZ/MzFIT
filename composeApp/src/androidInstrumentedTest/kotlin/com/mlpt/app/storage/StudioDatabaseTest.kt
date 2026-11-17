package com.mlpt.app.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mlpt.data.*
import com.mlpt.shared.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StudioDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "studio_test_${UUID.randomUUID()}.db"
    private var database = WorkoutDatabase(context, name)
    @After fun cleanup() { database.close(); context.deleteDatabase(name) }
    private val plan = WorkoutPlan("routine", "My workout 🏋️", "Strength", 30, "Test", listOf(
        Exercise("bench", "Bench press", 2, "8–12"), Exercise("plank", "Plank", 1, "30 seconds"), Exercise("row", "Dumbbell row", 2, "10")))
    private fun draft(id: String = "draft") = WorkoutDraft(id, plan, 1000, true, listOf(
        LiveExercise(plan.exercises[0], SetMetric.Reps, listOf(LiveSet("8", "110.231131", true), LiveSet("6", "100", true))),
        LiveExercise(plan.exercises[1], SetMetric.Seconds, listOf(LiveSet("45", complete = true))),
        LiveExercise(plan.exercises[2], SetMetric.Reps, listOf(LiveSet(), LiveSet()))), "Felt strong")

    @Test fun routinesAndRawDraftSurviveDatabaseReopen() = runBlocking {
        val routine = SavedRoutine(plan, setOf(1, 3, 5)); val d = draft().copy(exercises = draft().exercises.mapIndexed { i, e -> if (i != 0) e else e.copy(sets = listOf(LiveSet("", "42,"), LiveSet("8", "0", true))) })
        val store = StudioDatabase(database); store.saveRoutine(routine); store.saveDraft(d)
        database.close(); database = WorkoutDatabase(context, name)
        val reopened = StudioDatabase(database).load()
        assertEquals(listOf(routine), reopened.routines); assertEquals(d, reopened.draft)
        assertTrue(database.readSaved().sessions.isEmpty())
    }
    @Test fun finishIsAtomicIdempotentAndStoresOnlyCompletedExercisesAndSets() = runBlocking {
        val store = StudioDatabase(database); val draft = draft(); store.saveDraft(draft)
        val id = store.finish(draft, 61_000)
        assertEquals(id, store.finish(draft, 62_000))
        assertNull(store.load().draft)
        val saved = database.readSaved().sessions.single()
        assertEquals(2, saved.exerciseCount); assertEquals(listOf("Bench press", "Plank"), saved.exercises.map { it.name })
        val journal = JournalDatabase(database).load(id)
        assertEquals(2, journal.sets.size); assertEquals(50.0, journal.sets.first().weightKg!!, .000001)
        assertEquals(listOf(TimedJournalSet(1, 1, 45)), journal.timedSets)
        val export = JSONObject(JournalDatabase(database).exportJson())
        assertEquals(60, export.getJSONArray("workout_details").getJSONObject(0).getInt("elapsed_seconds"))
        assertEquals(3, export.getJSONArray("workout_details").getJSONObject(0).getInt("completed_sets"))
        assertEquals(1, export.getJSONArray("completed_workouts").length())
    }
    @Test fun failedSetInsertRollsBackEntireCompletionAndRetainsDraftForRetry() = runBlocking {
        val store = StudioDatabase(database); val d = draft(); store.saveDraft(d)
        database.writableDatabase.execSQL("CREATE TRIGGER reject_timed BEFORE INSERT ON timed_sets BEGIN SELECT RAISE(ABORT,'test failure'); END")
        var failed = false
        try { store.finish(d, 61_000) } catch (_: Exception) { failed = true }
        assertTrue(failed); assertTrue(database.readSaved().sessions.isEmpty()); assertEquals(d, store.load().draft)
        assertTrue(store.load().performances.isEmpty())
        database.writableDatabase.execSQL("DROP TRIGGER reject_timed")
        store.finish(d, 61_000); assertEquals(1, database.readSaved().sessions.size)
    }
    @Test fun lateCheckpointCannotReviveCompletedDraftAndOldFinishCannotDeleteNewDraft() = runBlocking {
        val store = StudioDatabase(database); val d = draft(); store.saveDraft(d); val id = store.finish(d, 61_000)
        var rejected = false
        try { store.saveDraft(d) } catch (_: Exception) { rejected = true }
        assertTrue(rejected); assertNull(store.load().draft)
        val next = draft("next"); store.saveDraft(next)
        assertEquals(id, store.finish(d, 62_000)); assertEquals(next, store.load().draft)
    }
    @Test fun schemaThreeMigrationPreservesWorkoutProfileJournalAndPoseRecords() = runBlocking {
        database.migrateLegacy("""{"profile":{"name":"Test","goal":"Strength","target":3},"sessions":[{"id":7,"planId":"old","title":"Keep this","count":2}]}""")
        val journal = WorkoutJournal(7, "Keep notes"); JournalDatabase(database).save(journal)
        val pose = PoseSessionLog("pose", "push_up", "Push-up", 1000, 2000, "completed"); PoseLogDatabase(database).checkpoint(pose)
        val original = database.readSaved()
        database.writableDatabase.apply { execSQL("DROP TABLE timed_sets"); execSQL("DROP TABLE workout_details"); execSQL("DROP TABLE active_workout"); execSQL("DROP TABLE saved_routines"); version = 3 }
        database.close(); database = WorkoutDatabase(context, name)
        assertEquals(original, database.readSaved()); assertEquals(journal, JournalDatabase(database).load(7)); assertEquals(pose, PoseLogDatabase(database).details("pose"))
        StudioDatabase(database).saveRoutine(SavedRoutine(plan)); assertEquals(1, StudioDatabase(database).load().routines.size)
    }
    @Test fun journalCorrectionsPreserveEditableTimedMetricsAndUpdateLiftInsights() = runBlocking {
        val store = StudioDatabase(database); val d = draft(); store.saveDraft(d); val id = store.finish(d, 61_000)
        val journals = JournalDatabase(database); val journal = journals.load(id)
        journals.save(journal.copy(sets = journal.sets.take(1).map { it.copy(reps = 10, weightKg = 55.0) }, timedSets = listOf(TimedJournalSet(1, 1, 60))))
        val performance = store.load().performances
        assertEquals(60, performance.single { it.seconds != null }.seconds)
        assertEquals(550.0, liftRecords(performance).single().externalVolumeKg, .000001)
    }
}
