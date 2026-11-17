package com.mlpt.app

import android.content.Context
import com.mlpt.app.storage.AndroidTrainingStorage
import com.mlpt.app.storage.WorkoutDatabase
import com.mlpt.data.PersistentTrainingRepository
import com.mlpt.shared.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AndroidTrainingRepository(context: Context, database: WorkoutDatabase = WorkoutDatabase(context.applicationContext)) : TrainingRepository by PersistentTrainingRepository(
    storage = AndroidTrainingStorage(context.applicationContext, database),
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    clockMillis = System::currentTimeMillis,
)
