package com.pocketarcade.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.math.abs

/** Shared checks for the animation tests: every joint is finite and inside what a body can do. */
internal object RigChecks {
    const val DT = 1f / 120f

    val JOINT_NAMES = arrayOf(
        "yaw", "rootY", "sway", "lean", "leanRoll", "twist", "breath", "breathLift", "headYaw", "headPitch", "headRoll",
        "hatPitch", "hatRoll", "hatLift", "tailPitch", "tailRoll", "blink", "item",
        "armPitchL", "armRollL", "legPitchL", "legLiftL", "armPitchR", "armRollR", "legPitchR", "legLiftR",
    )
    val JOINTS = JOINT_NAMES.size

    /** Every joint value the renderer reads, in the order of [JOINT_NAMES]. */
    fun joints(a: FigureAnim, out: FloatArray) {
        var i = 0
        out[i++] = a.yaw; out[i++] = a.rootY; out[i++] = a.sway
        out[i++] = a.lean; out[i++] = a.leanRoll; out[i++] = a.twist
        out[i++] = a.breath; out[i++] = a.breathLift
        out[i++] = a.headYaw; out[i++] = a.headPitch; out[i++] = a.headRoll
        out[i++] = a.hatPitch; out[i++] = a.hatRoll; out[i++] = a.hatLift
        out[i++] = a.tailPitch; out[i++] = a.tailRoll
        out[i++] = a.blink; out[i++] = a.itemAmount
        for (s in 0..1) {
            out[i++] = a.armPitch[s]; out[i++] = a.armRoll[s]
            out[i++] = a.legPitch[s]; out[i++] = a.legLift[s]
        }
    }

    /** Fails if any joint is NaN or outside the range a body could plausibly reach. */
    fun assertSane(a: FigureAnim, tag: String) {
        val v = FloatArray(JOINTS)
        joints(a, v)
        for (i in v.indices) assertFalse("$tag: ${JOINT_NAMES[i]} is not finite", v[i].isNaN() || v[i].isInfinite())
        for (s in 0..1) {
            // Arms: never past straight up or well behind the back; never crossing through the body sideways.
            assertTrue("$tag: arm pitch ${a.armPitch[s]}", a.armPitch[s] in -3.1f..1.3f)
            assertTrue("$tag: arm roll ${a.armRoll[s]}", a.armRoll[s] in -0.9f..0.9f)
            // Legs: a sitting kid's legs reach horizontal at most; walking legs stay inside a stride.
            assertTrue("$tag: leg pitch ${a.legPitch[s]}", a.legPitch[s] in -1.62f..1.0f)
            assertTrue("$tag: leg lift ${a.legLift[s]}", a.legLift[s] in -0.01f..2.6f)
        }
        assertTrue("$tag: body height ${a.rootY}", a.rootY in -6.5f..5f)
        assertTrue("$tag: sway ${a.sway}", abs(a.sway) < 2f)
        assertTrue("$tag: lean ${a.lean}", a.lean in -0.5f..0.5f)
        assertTrue("$tag: bank ${a.leanRoll}", abs(a.leanRoll) < 0.3f)
        assertTrue("$tag: twist ${a.twist}", abs(a.twist) < 0.5f)
        assertTrue("$tag: head yaw ${a.headYaw}", abs(a.headYaw) < 1.2f)
        assertTrue("$tag: head pitch ${a.headPitch}", a.headPitch in -0.6f..0.7f)
        assertTrue("$tag: head roll ${a.headRoll}", abs(a.headRoll) < 0.4f)
        assertTrue("$tag: hat pitch ${a.hatPitch}", abs(a.hatPitch) < 0.3f)
        assertTrue("$tag: hat roll ${a.hatRoll}", abs(a.hatRoll) < 0.3f)
        assertTrue("$tag: hat lift ${a.hatLift}", abs(a.hatLift) < 1.5f)
        assertTrue("$tag: tail ${a.tailPitch}/${a.tailRoll}", abs(a.tailPitch) < 0.9f && abs(a.tailRoll) < 0.9f)
        assertTrue("$tag: blink ${a.blink}", a.blink in 0f..1f)
        assertTrue("$tag: item ${a.itemAmount}", a.itemAmount in 0f..1f)
        assertTrue("$tag: breath ${a.breath}", abs(a.breath) < 0.05f)
    }
}
