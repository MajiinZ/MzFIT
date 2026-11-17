package com.mlpt.app.storage

import android.content.Context
import com.mlpt.data.TrainingStorage
import com.mlpt.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidTrainingStorage(
    context: Context,
    private val database: WorkoutDatabase = WorkoutDatabase(context.applicationContext),
    private val legacyState: () -> String? = { context.getSharedPreferences("training", Context.MODE_PRIVATE).getString("state", null) },
) : TrainingStorage {
    override suspend fun load() = withContext(Dispatchers.IO) {
        database.migrateLegacy(legacyState())
        database.readSaved()
    }
    override suspend fun insertCompletedWorkout(workout: TrainingSession) = withContext(Dispatchers.IO) { database.insertCompletedWorkout(workout) }
    override suspend fun saveProfile(profile: ClientProfile) = withContext(Dispatchers.IO) { database.saveProfile(profile) }
}
