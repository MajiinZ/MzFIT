package com.mlpt.app

import android.app.Application
import android.os.SystemClock
import com.mlpt.app.storage.*
import com.mlpt.data.PoseSessionRecorder
import com.mlpt.data.TrainingStudio
import kotlinx.coroutines.*
import java.util.UUID
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrainingApplication : Application() {
    private val database by lazy { WorkoutDatabase(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val preferences by lazy { TrainingPreferences(this) }
    val restTimer by lazy { RestTimer(this, applicationScope) }
    val journal by lazy { JournalDatabase(database) }
    val studio by lazy { TrainingStudio(StudioDatabase(database), applicationScope, System::currentTimeMillis,
        { UUID.randomUUID().toString() }, { trainingRepository.refreshHistory() }) }
    private val mutableExport = MutableStateFlow(ExportState())
    val exportState = mutableExport.asStateFlow()
    fun exportHistory(uri: Uri) {
        if (mutableExport.value.busy) return
        mutableExport.value = ExportState(busy = true)
        applicationScope.launch {
            try {
                val json = journal.exportJson()
                withContext(Dispatchers.IO) {
                    checkNotNull(contentResolver.openOutputStream(uri, "wt")) { "Cannot open export file" }
                        .bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                }
                mutableExport.value = ExportState(message = "History exported. Keep the file somewhere private; it contains your profile and workout measurements.")
            } catch (_: Exception) {
                mutableExport.value = ExportState(message = "Export failed. Choose a destination and try again. A partial file may need deleting.")
            }
        }
    }
    val trainingRepository by lazy { AndroidTrainingRepository(this, database) }
    val poseRecorder by lazy { PoseSessionRecorder(PoseLogDatabase(database), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        System::currentTimeMillis, SystemClock::elapsedRealtime, { UUID.randomUUID().toString() }) }
}
data class ExportState(val busy: Boolean = false, val message: String? = null)
