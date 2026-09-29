package com.pocketarcade.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * A game that can be steered by tilting the phone. The host listens to the sensor (with a
 * [TiltSteer]) only while the round is on screen and [tiltSteering] is on, and feeds it readings.
 */
interface TiltControlled {
    /** Whether the player chose tilt steering. */
    val tiltSteering: Boolean

    /** A new reading: how far the phone leans right in radians (negative leans left), see [TiltMath.lean]. */
    fun onTilt(lean: Float)
}

/** The sums behind tilt steering, apart from the sensor so they can be tested. */
object TiltMath {
    /** Lean this far off the neutral (degrees) before anything happens: a hand is never still. */
    const val DEAD_DEG = 2.5f

    /**
     * Lean this far off the neutral (degrees) for full lock. Leaning reads a little smaller the
     * more upright the phone is held (see [lean]), so this is well under a comfortable wrist turn.
     */
    const val FULL_DEG = 20f

    /**
     * How far the device leans to the right, in radians: the angle of "up" out of the device's own
     * front-back plane, given as ([ux], [uy], [uz]) in device coordinates (x right, y up the
     * screen, z out of it). It reads the same for a phone rocked flat like a tray and one turned
     * upright like a wheel, and in between it reads a bit less, which the level the game
     * calibrates and [FULL_DEG] absorb. Right edge down (a clockwise wheel) is positive; any
     * length of vector works.
     */
    fun lean(ux: Float, uy: Float, uz: Float): Float = atan2(-ux, hypot(uy, uz))

    /**
     * Steering from -1 (full left) to 1 (full right) for a phone leaning [lean] radians when
     * level was [neutral]: nothing inside [DEAD_DEG], full at [FULL_DEG], and finer near the
     * middle than at the ends (the same shape as the hall's stick).
     */
    fun steer(lean: Float, neutral: Float, deadDeg: Float = DEAD_DEG, fullDeg: Float = FULL_DEG): Float {
        val off = (lean - neutral) * (180f / Math.PI.toFloat())
        val a = abs(off)
        if (!(a > deadDeg)) return 0f
        val t = ((a - deadDeg) / (fullDeg - deadDeg)).coerceAtMost(1f)
        return sign(off) * t * (0.35f + 0.65f * t)
    }
}

/**
 * Reads the phone's tilt and hands it to a [TiltControlled] game: the game rotation vector (a gyro
 * fused reading that needs no compass) where there is one, else the accelerometer, smoothed.
 * Readings arrive on the main thread, where the game runs. Call [start] when the round is on
 * screen and the option is on, and [stop] when it isn't: a listener left registered drains the
 * battery.
 */
class TiltSteer(context: Context) : SensorEventListener {
    private val sensors = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val matrix = FloatArray(9)
    private var target: TiltControlled? = null
    private var fused = false
    private var ax = 0f
    private var ay = 0f
    private var az = 0f
    private var primed = false

    /** Starts listening; false if the phone has no suitable sensor (the game then keeps its touch steering). */
    fun start(game: TiltControlled): Boolean {
        stop()
        val manager = sensors ?: return false
        val rotation = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val sensor = rotation ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        fused = rotation != null
        primed = false
        target = game
        return manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        target = null
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val game = target ?: return
        val v = event.values
        if (fused) {
            // The third row of the rotation matrix is "up" in the device's own coordinates.
            SensorManager.getRotationMatrixFromVector(matrix, v)
            game.onTilt(TiltMath.lean(matrix[6], matrix[7], matrix[8]))
        } else {
            val g = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            if (g < 1f) return
            // Smooth the accelerometer: a knock or the engine's rumble is no steering.
            val k = if (primed) ACCEL_SMOOTH else 1f
            primed = true
            ax += (v[0] / g - ax) * k
            ay += (v[1] / g - ay) * k
            az += (v[2] / g - az) * k
            game.onTilt(TiltMath.lean(ax, ay, az))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** Share of each accelerometer reading blended in (about 0.13 s of smoothing at 50 Hz). */
        const val ACCEL_SMOOTH = 0.15f
    }
}
