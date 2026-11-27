package com.mlpt.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ToolPreferences(val restSeconds: Int = 90, val pounds: Boolean = false, val keepAwake: Boolean = false)
data class RestState(val remaining: Int = 0, val running: Boolean = false, val finished: Boolean = false)

class TrainingPreferences(private val context: Context) {
    private val disk = context.getSharedPreferences("training_tools", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(ToolPreferences(disk.getInt("rest", 90).coerceIn(15, 600), disk.getBoolean("pounds", false), disk.getBoolean("awake", false)))
    val state = mutable.asStateFlow()
    fun update(value: ToolPreferences) {
        require(value.restSeconds in 15..600)
        disk.edit().putInt("rest", value.restSeconds).putBoolean("pounds", value.pounds).putBoolean("awake", value.keepAwake).apply()
        mutable.value = value
    }
}

/** App-owned timer uses elapsed time, so navigation and device sleep do not stretch a rest. */
class RestTimer(context: Context, scope: CoroutineScope, storageName: String = "rest_timer") {
    private val disk = context.getSharedPreferences(storageName, Context.MODE_PRIVATE)
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private var deadline = if (boot >= 0 && disk.getInt("boot", -2) == boot) disk.getLong("deadline", 0) else 0
    private var paused = disk.getInt("paused", 0).coerceIn(0, 600)
    private val mutable = MutableStateFlow(RestState(paused))
    val state = mutable.asStateFlow()
    init { tick(); scope.launch { while (isActive) { delay(250); tick() } } }
    private fun tick() {
        if (deadline == 0L) return
        val remaining = ((deadline - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceIn(0, 600)
        if (remaining == 0) { deadline = 0; persist(); mutable.value = RestState(finished = true) }
        else mutable.value = RestState(remaining, running = true)
    }
    fun start(seconds: Int) { require(seconds in 1..600); paused = 0; deadline = SystemClock.elapsedRealtime() + seconds * 1000; persist(); tick() }
    fun pause() { paused = mutable.value.remaining; deadline = 0; persist(); mutable.value = RestState(paused) }
    fun resume() { if (paused > 0) start(paused) }
    fun reset() { deadline = 0; paused = 0; persist(); mutable.value = RestState() }
    private fun persist() { disk.edit().putLong("deadline", deadline).putInt("paused", paused).putInt("boot", boot).apply() }
}
