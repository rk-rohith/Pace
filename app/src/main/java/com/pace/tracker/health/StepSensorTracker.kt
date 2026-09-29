package com.pace.tracker.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.pace.tracker.data.PaceRepository
import com.pace.tracker.data.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Counts steps with Sensor.TYPE_STEP_COUNTER while the app is in the foreground.
 * The sensor reports steps since boot, so we track the delta between readings and add it to today.
 */
class StepSensorTracker(
    private val context: Context,
    private val repository: PaceRepository,
    private val scope: CoroutineScope,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var lastValue: Float? = null
    private var pending = 0
    private var pendingDay = today()
    private var registered = false

    val isSupported: Boolean get() = sensor != null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (registered || sensor == null || !hasPermission()) return
        lastValue = null
        registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
        flush()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.firstOrNull() ?: return
        val last = lastValue
        lastValue = value
        if (last == null) return
        val delta = (value - last).toInt()
        // Reject resets (reboot) and absurd jumps.
        if (delta <= 0 || delta > 5_000) return
        val day = today()
        if (day != pendingDay) {
            flush()
            pendingDay = day
        }
        pending += delta
        if (pending >= FLUSH_EVERY) flush()
    }

    private fun flush() {
        val steps = pending
        val day = pendingDay
        pending = 0
        if (steps > 0) scope.launch { repository.addSensorSteps(day, steps) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val FLUSH_EVERY = 20
    }
}
