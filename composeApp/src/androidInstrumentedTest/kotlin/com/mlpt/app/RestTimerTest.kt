package com.mlpt.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RestTimerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "timer_test_${UUID.randomUUID()}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun cleanup() { scope.cancel(); context.deleteSharedPreferences(name) }
    @Test fun pausedRestSurvivesRecreationAndCanResumeOrReset() = runBlocking(Dispatchers.Main) {
        val timer = RestTimer(context, scope, name)
        timer.start(90); timer.pause()
        assertEquals(90, timer.state.value.remaining); assertFalse(timer.state.value.running)
        val reopened = RestTimer(context, scope, name)
        assertEquals(90, reopened.state.value.remaining)
        reopened.resume(); assertTrue(reopened.state.value.running)
        reopened.reset(); assertEquals(RestState(), reopened.state.value)
    }
    @Test fun elapsedDeadlineSurvivesRecreationAndFinishesWithoutGoingNegative() = runBlocking(Dispatchers.Main) {
        val first = RestTimer(context, scope, name); first.start(1)
        val reopened = RestTimer(context, scope, name)
        assertTrue(reopened.state.value.running)
        delay(1500)
        assertEquals(0, reopened.state.value.remaining); assertTrue(reopened.state.value.finished)
        assertFalse(reopened.state.value.running)
    }
}
