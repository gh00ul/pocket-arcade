package com.pocketarcade.godot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * build-13's TiltSteer sensor side: the game rotation vector (a gyro-fused reading that needs no
 * compass) where there is one, else the accelerometer, at SENSOR_DELAY_GAME. Every reading is kept
 * until [take]: the rotation vector as "up" in the device's own axes (the rotation matrix's third
 * row), the accelerometer raw (the Godot side smooths it as build-13 did).
 */
internal class TiltReader(context: Context) : SensorEventListener {
    companion object {
        const val NONE = 0
        const val ROTATION = 1
        const val ACCEL = 2
        private const val MAX_READINGS = 256
    }

    private val sensors = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val matrix = FloatArray(9)
    private val lock = Any()
    private var readings = FloatArray(MAX_READINGS * 4)
    private var count = 0
    private var kind = NONE

    /** Starts listening: [ROTATION], [ACCEL], or [NONE] when the phone has no suitable sensor. */
    fun start(): Int {
        stop()
        val manager = sensors ?: return NONE
        val rotation = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val sensor = rotation ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return NONE
        kind = if (rotation != null) ROTATION else ACCEL
        synchronized(lock) { count = 0 }
        return if (manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) kind else NONE
    }

    fun stop() {
        kind = NONE
        sensors?.unregisterListener(this)
        synchronized(lock) { count = 0 }
    }

    fun take(): FloatArray {
        synchronized(lock) {
            val out = readings.copyOf(count * 4)
            count = 0
            return out
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val k = kind
        if (k == NONE) return
        val v = event.values
        synchronized(lock) {
            if (count >= MAX_READINGS) count = 0
            val at = count * 4
            readings[at] = k.toFloat()
            if (k == ROTATION) {
                SensorManager.getRotationMatrixFromVector(matrix, v)
                readings[at + 1] = matrix[6]
                readings[at + 2] = matrix[7]
                readings[at + 3] = matrix[8]
            } else {
                readings[at + 1] = v[0]
                readings[at + 2] = v[1]
                readings[at + 3] = v[2]
            }
            count++
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
