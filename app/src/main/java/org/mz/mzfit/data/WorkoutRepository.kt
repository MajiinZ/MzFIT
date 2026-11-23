package org.mz.mzfit.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One completed set, either tracked by the AI Coach or logged manually. */
data class WorkoutLog(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val timestamp: Long,
    val reps: Int,
    val goodReps: Int,
    /** 0–100, or -1 when the set was logged manually. */
    val formScore: Int,
    val durationSec: Int,
    val weightKg: Float = 0f,
    val aiTracked: Boolean,
) {
    val exercise: Exercise? get() = ExerciseLibrary.get(exerciseId)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("exerciseId", exerciseId).put("timestamp", timestamp)
        .put("reps", reps).put("goodReps", goodReps).put("formScore", formScore)
        .put("durationSec", durationSec).put("weightKg", weightKg.toDouble()).put("aiTracked", aiTracked)

    companion object {
        fun fromJson(o: JSONObject) = WorkoutLog(
            id = o.optString("id", UUID.randomUUID().toString()),
            exerciseId = o.getString("exerciseId"),
            timestamp = o.getLong("timestamp"),
            reps = o.optInt("reps"),
            goodReps = o.optInt("goodReps"),
            formScore = o.optInt("formScore", -1),
            durationSec = o.optInt("durationSec"),
            weightKg = o.optDouble("weightKg", 0.0).toFloat(),
            aiTracked = o.optBoolean("aiTracked"),
        )
    }
}

data class WeeklySummary(val sets: Int, val reps: Int, val activeDays: Int, val avgForm: Int?)

class WorkoutRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mzfit_workouts", Context.MODE_PRIVATE)
    private val _logs = MutableStateFlow(load())
    val logs: StateFlow<List<WorkoutLog>> = _logs.asStateFlow()

    private fun load(): List<WorkoutLog> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { WorkoutLog.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList()).sortedByDescending { it.timestamp }

    private fun persist(list: List<WorkoutLog>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _logs.value = list.sortedByDescending { it.timestamp }
    }

    fun add(log: WorkoutLog) = persist(_logs.value + log)

    fun delete(id: String) = persist(_logs.value.filterNot { it.id == id })

    fun clear() = persist(emptyList())

    companion object {
        private const val KEY = "logs"
        private val WEEK_MS = TimeUnit.DAYS.toMillis(7)

        /** Effective weekly sets per muscle at which the map reads "fully trained". */
        const val TARGET_WEEKLY_SETS = 10f

        /**
         * Training load per muscle over the last 7 days, normalized 0..1.
         * Primary movers count as one full set, secondary movers as half a set.
         */
        fun muscleLoad(logs: List<WorkoutLog>, now: Long = System.currentTimeMillis()): Map<Muscle, Float> {
            val raw = mutableMapOf<Muscle, Float>()
            logs.filter { now - it.timestamp <= WEEK_MS }.forEach { log ->
                val ex = log.exercise ?: return@forEach
                ex.primary.forEach { raw[it] = (raw[it] ?: 0f) + 1f }
                ex.secondary.forEach { raw[it] = (raw[it] ?: 0f) + 0.5f }
            }
            return raw.mapValues { (it.value / TARGET_WEEKLY_SETS).coerceIn(0f, 1f) }
        }

        fun weeklySummary(logs: List<WorkoutLog>, now: Long = System.currentTimeMillis()): WeeklySummary {
            val week = logs.filter { now - it.timestamp <= WEEK_MS }
            val days = week.map { TimeUnit.MILLISECONDS.toDays(it.timestamp) }.toSet().size
            val scored = week.filter { it.formScore >= 0 }
            return WeeklySummary(
                sets = week.size,
                reps = week.sumOf { it.reps },
                activeDays = days,
                avgForm = if (scored.isEmpty()) null else scored.map { it.formScore }.average().toInt(),
            )
        }
    }
}

enum class CameraFacing { FRONT, BACK }

data class AppSettings(
    val voiceCoaching: Boolean = true,
    val haptics: Boolean = true,
    val highAccuracyModel: Boolean = false,
    val showJointAngles: Boolean = true,
    val cameraFacing: CameraFacing = CameraFacing.FRONT,
    val countdownSeconds: Int = 3,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("mzfit_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        voiceCoaching = prefs.getBoolean("voice", true),
        haptics = prefs.getBoolean("haptics", true),
        highAccuracyModel = prefs.getBoolean("accurate", false),
        showJointAngles = prefs.getBoolean("angles", true),
        cameraFacing = if (prefs.getString("facing", "FRONT") == "BACK") CameraFacing.BACK else CameraFacing.FRONT,
        countdownSeconds = prefs.getInt("countdown", 3),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putBoolean("voice", s.voiceCoaching)
            .putBoolean("haptics", s.haptics)
            .putBoolean("accurate", s.highAccuracyModel)
            .putBoolean("angles", s.showJointAngles)
            .putString("facing", s.cameraFacing.name)
            .putInt("countdown", s.countdownSeconds)
            .apply()
        _settings.value = s
    }
}

/** Minimal manual DI container, created once in [org.mz.mzfit.MzFitApplication]. */
class AppContainer(context: Context) {
    val workouts = WorkoutRepository(context)
    val settings = SettingsRepository(context)
}
