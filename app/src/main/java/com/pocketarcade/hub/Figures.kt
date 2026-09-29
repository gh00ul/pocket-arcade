package com.pocketarcade.hub

import com.pocketarcade.data.HatStyle
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.Xform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * How a kid looks: skin, hair (colour and style 0 short, 1 spiky, 2 long), clothes and hat, and
 * an [apron] colour for staff (0 for none).
 */
data class CharacterLook(
    val skin: Int,
    val hair: Int,
    val hairStyle: Int,
    val shirt: Int,
    val pants: Int,
    val shoes: Int = Pal.DARKGRAY,
    val hat: HatStyle? = null,
    val apron: Int = 0,
)

/** The cast: the player, the prize clerk, the café barista and a deterministic crowd of kids. */
object Looks {
    private val skins = intArrayOf(0xFFFFD9B8.toInt(), 0xFFF1C27D.toInt(), 0xFFE0A87A.toInt(), 0xFFC68642.toInt(), 0xFF8D5524.toInt(), 0xFF5C3A21.toInt())
    private val hairs = intArrayOf(0xFF2B1B10.toInt(), 0xFF5A3418.toInt(), 0xFF8B5A2B.toInt(), 0xFFE8C170.toInt(), 0xFF141018.toInt(), 0xFFB5462E.toInt(), 0xFFFF77C8.toInt(), 0xFF4DA6FF.toInt())
    private val shirts = intArrayOf(0xFFFF4D4D.toInt(), 0xFF3DDC84.toInt(), 0xFF4DA6FF.toInt(), 0xFFFFE14D.toInt(), 0xFF8A4FFF.toInt(), 0xFFFF9A3C.toInt(), 0xFFFFFFFF.toInt(), 0xFF3DF5FF.toInt(), 0xFFFF77C8.toInt())
    private val pants = intArrayOf(0xFF1A2A6C.toInt(), 0xFF3A3A55.toInt(), 0xFF5A3418.toInt(), 0xFF2E1F5E.toInt(), 0xFF2F5BE0.toInt(), 0xFF22222A.toInt())
    private val shoes = intArrayOf(0xFFFFFFFF.toInt(), 0xFF22222A.toInt(), 0xFFFF4D4D.toInt(), 0xFF4DA6FF.toInt())

    fun randomKid(seed: Int): CharacterLook {
        fun pick(arr: IntArray, salt: Int) = arr[((seed * 7919 + salt * 104729) and 0x7FFFFFFF) % arr.size]
        val hats = arrayOf(null, null, null, HatStyle.CAP, HatStyle.BEANIE, HatStyle.HEADPHONES, null)
        return CharacterLook(
            skin = pick(skins, 1), hair = pick(hairs, 2), hairStyle = (seed * 13 + 5) % 3,
            shirt = pick(shirts, 3), pants = pick(pants, 4), shoes = pick(shoes, 5),
            hat = hats[(seed * 31 + 7) % hats.size],
        )
    }

    fun player(shirt: Int, pants: Int, hat: HatStyle?) = CharacterLook(
        skin = 0xFFF1C27D.toInt(), hair = 0xFF5A3418.toInt(), hairStyle = 1,
        shirt = shirt, pants = pants, shoes = 0xFFFFFFFF.toInt(), hat = hat,
    )

    val clerk = CharacterLook(
        skin = 0xFFC68642.toInt(), hair = 0xFF141018.toInt(), hairStyle = 0,
        shirt = 0xFFE8323C.toInt(), pants = 0xFF1A2A6C.toInt(), hat = HatStyle.CAP,
    )

    val barista = CharacterLook(
        skin = 0xFFE0A87A.toInt(), hair = 0xFF5A3418.toInt(), hairStyle = 2,
        shirt = 0xFFF4EEE2.toInt(), pants = 0xFF2A2A34.toInt(), shoes = 0xFF22222A.toInt(),
        hat = HatStyle.CAP, apron = 0xFF1F8A70.toInt(),
    )
}

/**
 * What a figure is doing, which sets its pose. With a café treat: [CARRY] walking with it, [HOLD]
 * standing with it, [SIP] sitting and sipping; [WIPE] is the barista wiping the counter.
 */
enum class Pose { STAND, WALK, PLAY, CHEER, SIT, WIPE, CARRY, HOLD, SIP }

/**
 * A 3D kid: sculpted body, painted face, hair and hat, with limbs that swing as they walk.
 * Stands on its origin facing +z at yaw 0; about 44 units tall.
 */
class Figure(val look: CharacterLook) {
    companion object {
        const val HIP_Y = 14f
        const val SHOULDER_Y = 25.2f
        const val SHOULDER_X = 7.3f
        const val HEAD_Y = 35.5f
        const val HEAD_R = 8f

        private val paints = HashMap<Int, Texture>()
        fun paint(color: Int): Region = paints.getOrPut(color) { HallArt.paint(color, 0.2f, 0.72f) }.full

        private val hatModels = HashMap<HatStyle, Model>()
        private val propeller by lazy {
            val b = ModelBuilder()
            val c = paint(0xFFFF4D4D.toInt())
            b.box(-6f, 0f, -0.8f, 6f, 0.5f, 0.8f, BoxFaces.all(c, 0.5f))
            b.box(-0.8f, 0f, -6f, 0.8f, 0.5f, 6f, BoxFaces.all(paint(0xFF4DA6FF.toInt()), 0.5f))
            b.build()
        }

        /** Café treats a figure can hold: none, a slushie cup or a soft-serve cone. */
        const val ITEM_NONE = 0
        const val ITEM_CUP = 1
        const val ITEM_CONE = 2

        /** What [look] always buys at the café: most kids a slushie, some a cone. */
        fun heldItem(look: CharacterLook): Int = if ((look.hashCode() and 0x7FFFFFFF) % 3 == 0) ITEM_CONE else ITEM_CUP

        /** A slushie cup with a domed lid and a straw, gripped at its middle. */
        private val cupModels = HashMap<Int, Model>()
        private val slushColors = intArrayOf(0xFF2FB8FF.toInt(), 0xFFFF3D6E.toInt(), 0xFF7CF25A.toInt(), 0xFFB070FF.toInt())
        private fun cup(color: Int): Model = cupModels.getOrPut(color) {
            val b = ModelBuilder()
            b.lathe(0f, -3.2f, 0f, floatArrayOf(1.35f, 0f, 1.6f, 2.4f, 1.9f, 6.4f), 12, paint(color), gloss = 0.5f)
            b.cylinder(0f, 0f, -1.1f, 1.3f, 1.72f, 12, HallArt.solid(-1).full, gloss = 0.3f)
            b.sphere(0f, 3.2f, 0f, 1.95f, HallArt.solid(0xFFE8F4FF.toInt()).full, slices = 12, stacks = 4, yFrom = 0f, sy = 0.45f, gloss = 0.9f)
            b.capsule(0.3f, 3.6f, 0f, 0.9f, 7.2f, -0.3f, 0.3f, paint(0xFFFF4FA8.toInt()), slices = 5)
            b.build()
        }
        private val cone by lazy {
            val b = ModelBuilder()
            val waffle = TexPaint(32, 32).let { tp ->
                tp.fill(0xFFD89A4A.toInt())
                for (k in -32 until 64 step 8) {
                    tp.line(k.toFloat(), 0f, k + 32f, 32f, 1.4f, 0xFFA8692A.toInt())
                    tp.line(k + 32f, 0f, k.toFloat(), 32f, 1.4f, 0xFFA8692A.toInt())
                }
                tp.toTexture().also { tp.recycle() }
            }
            b.lathe(0f, -4.5f, 0f, floatArrayOf(0.1f, 0f, 1.3f, 3.5f, 2.1f, 6f), 12, waffle.full, gloss = 0.1f)
            val cream = HallArt.paint(0xFFFFF4E4.toInt(), 0.3f, 0.85f).full
            b.sphere(0f, 2.1f, 0f, 2.2f, cream, slices = 12, stacks = 6, sy = 0.7f, gloss = 0.5f)
            b.sphere(0f, 3.6f, 0f, 1.6f, HallArt.paint(0xFFFF9EC8.toInt(), 0.3f, 0.85f).full, slices = 10, stacks = 5, sy = 0.8f, gloss = 0.5f)
            b.cylinder(0f, 0f, 4.6f, 6f, 0.9f, 8, cream, topRadius = 0.05f)
            b.build()
        }

        /** The model for café treat [item] (a cup's colour picked by [seed]), or null for none. */
        fun itemModel(item: Int, seed: Int): Model? = when (item) {
            ITEM_CUP -> cup(slushColors[(seed and 0x7FFFFFFF) % slushColors.size])
            ITEM_CONE -> cone
            else -> null
        }

        /** A hat model in head coordinates (head centre at the origin, radius [HEAD_R]). */
        fun hat(style: HatStyle): Model = hatModels.getOrPut(style) { buildHat(style) }

        private fun buildHat(style: HatStyle): Model {
            val b = ModelBuilder()
            val r = HEAD_R
            when (style) {
                HatStyle.CAP -> {
                    val c = paint(0xFFE8323C.toInt())
                    b.sphere(0f, 0f, 0f, r + 0.6f, c, slices = 18, stacks = 8, yFrom = 0.28f, gloss = 0.2f)
                    b.box(-5.5f, 2.2f, 4f, 5.5f, 2.8f, 12.5f, BoxFaces.all(c, 0.2f))
                    b.sphere(0f, r + 0.4f, 0f, 1f, c)
                }
                HatStyle.BEANIE -> {
                    val tp = TexPaint(64, 64)
                    tp.fill(0xFF3DDC84.toInt())
                    for (y in 0 until 64 step 12) tp.rect(0f, y.toFloat(), 64f, 5f, 0xFFFFE14D.toInt())
                    tp.grain(0.12f, 5)
                    val stripes = tp.toTexture().also { tp.recycle() }
                    b.sphere(0f, 0.5f, 0f, r + 0.8f, stripes.full, slices = 18, stacks = 8, yFrom = 0.18f, sy = 1.08f)
                    b.cylinder(0f, 0f, 1f, 3.6f, r + 0.9f, 18, paint(0xFF3DDC84.toInt()))
                    b.sphere(0f, r + 2.6f, 0f, 2.4f, paint(0xFFFFE14D.toInt()))
                }
                HatStyle.PARTY -> {
                    val tp = TexPaint(128, 64)
                    tp.fill(0xFF39E6F2.toInt())
                    for (x in 0 until 128 step 16) tp.rect(x.toFloat(), 0f, 8f, 64f, 0xFFFF4FA8.toInt())
                    for (k in 0 until 20) tp.circle((k * 37 % 128).toFloat(), (k * 23 % 64).toFloat(), 2f, 0xFFFFE14D.toInt())
                    val tex = tp.toTexture().also { tp.recycle() }
                    b.cylinder(0f, 0f, 5.5f, 17f, 4.8f, 14, tex.full, topRadius = 0.2f, gloss = 0.3f)
                    b.sphere(0f, 17.2f, 0f, 1.8f, paint(0xFFFFE14D.toInt()))
                }
                HatStyle.HEADPHONES -> {
                    val band = paint(0xFF22222A.toInt())
                    val band3 = ModelBuilder().torus(0f, 0f, 0f, r + 1.1f, 0.8f, band, segments = 20, sides = 6, gloss = 0.5f).build()
                    b.add(band3, Xform().set(0f, 0.5f, 0f, roll = PI.toFloat() / 2f))
                    val cup = ModelBuilder().cylinder(0f, 0f, -1.4f, 1.4f, 3.3f, 14, paint(0xFFFF4FA8.toInt()), top = paint(0xFF22222A.toInt()), bottom = paint(0xFF22222A.toInt()), gloss = 0.6f).build()
                    b.add(cup, Xform().set(-r - 0.6f, -0.5f, 0f, roll = PI.toFloat() / 2f))
                    b.add(cup, Xform().set(r + 0.6f, -0.5f, 0f, roll = PI.toFloat() / 2f))
                }
                HatStyle.COWBOY -> {
                    val c = paint(0xFFB5763C.toInt())
                    b.lathe(0f, 2.4f, 0f, floatArrayOf(r + 6.5f, 1.8f, r + 5f, 0.4f, r - 0.5f, 0f, r - 1.5f, 0.2f), 20, c, gloss = 0.2f)
                    b.lathe(0f, 2.4f, 0f, floatArrayOf(r - 1.2f, 0f, r - 1.2f, 1f, r - 1.6f, 6f, r - 2.4f, 8.5f, 0.1f, 9f), 18, c, gloss = 0.2f)
                    b.cylinder(0f, 0f, 2.6f, 4f, r - 1.1f, 18, paint(0xFF3A2414.toInt()))
                }
                HatStyle.PROPELLER -> {
                    val tp = TexPaint(128, 32)
                    val cs = intArrayOf(0xFFFF4D4D.toInt(), 0xFFFFE14D.toInt(), 0xFF3DDC84.toInt(), 0xFF4DA6FF.toInt())
                    for (k in 0 until 8) tp.rect(k * 16f, 0f, 16f, 32f, cs[k % 4])
                    val tex = tp.toTexture().also { tp.recycle() }
                    b.sphere(0f, 0f, 0f, r + 0.6f, tex.full, slices = 16, stacks = 8, yFrom = 0.3f, gloss = 0.3f)
                    b.capsule(0f, r + 0.3f, 0f, 0f, r + 3f, 0f, 0.4f, paint(0xFF888888.toInt()))
                }
                HatStyle.WIZARD -> {
                    val tp = TexPaint(128, 128)
                    tp.vgrad(0f, 0f, 128f, 128f, 0xFF4A2A9A.toInt(), 0xFF2A1060.toInt())
                    for (k in 0 until 18) {
                        val x = (k * 53 % 128).toFloat()
                        val y = (k * 29 % 128).toFloat()
                        tp.text("★", x, y, 16f, 0xFFFFE14D.toInt(), Fonts.heavy)
                    }
                    val tex = tp.toTexture().also { tp.recycle() }
                    b.cylinder(0f, 0f, 3.5f, 22f, r - 0.5f, 16, tex.full, topRadius = 0.3f, gloss = 0.2f)
                    b.lathe(0f, 3.4f, 0f, floatArrayOf(r + 5f, 0.3f, r + 4.5f, 0f, r - 1f, 0.2f), 18, tex.full)
                }
                HatStyle.TOPHAT -> {
                    val c = paint(0xFF1A1820.toInt())
                    b.cylinder(0f, 0f, 3.5f, 15f, r - 1.5f, 18, c, top = c, gloss = 0.5f)
                    b.cylinder(0f, 0f, 3.5f, 5f, r - 1.4f, 18, paint(0xFFE8323C.toInt()))
                    b.lathe(0f, 3.3f, 0f, floatArrayOf(r + 3f, 0.5f, r + 3f, 0f, r - 1.5f, 0.1f), 18, c, gloss = 0.5f)
                }
                HatStyle.CROWN -> {
                    val gold = HallArt.paint(0xFFFFC83D.toInt(), 0.35f, 0.7f).full
                    b.cylinder(0f, 0f, 4f, 7.5f, r - 1f, 18, gold, gloss = 0.9f, topRadius = r - 0.6f)
                    for (k in 0 until 6) {
                        val a = k * PI.toFloat() / 3f
                        val x = kotlin.math.cos(a) * (r - 0.8f)
                        val z = kotlin.math.sin(a) * (r - 0.8f)
                        b.cylinder(x, z, 7.4f, 11f, 1.3f, 6, gold, topRadius = 0.1f, gloss = 0.9f)
                        b.sphere(x, 11.3f, z, 0.6f, gold, slices = 6, stacks = 4, gloss = 0.9f)
                    }
                    b.sphere(0f, 5.8f, r - 0.2f, 1.2f, paint(0xFFE8323C.toInt()), gloss = 1f)
                }
                HatStyle.HALO -> {
                    val gold = HallArt.solid(0xFFFFE99A.toInt()).full
                    b.torus(0f, 13.5f, 0f, 6f, 0.7f, gold, segments = 22, sides = 6, emissive = 1.6f)
                }
            }
            return b.build()
        }
    }

    private val body: Model
    private val leg: Model
    private val arm: Model
    private val hatModel: Model? = look.hat?.let { hat(it) }

    init {
        val skin = paint(look.skin)
        val shirt = paint(look.shirt)
        val pants = paint(look.pants)
        val shoe = paint(look.shoes)

        // Torso: a rounded, slightly flattened shape from the hips to the shoulders.
        val torso = ModelBuilder().lathe(
            0f, 0f, 0f,
            floatArrayOf(0f, 12f, 5.4f, 12.3f, 6.3f, 14f, 6.4f, 18f, 6.5f, 22f, 6.1f, 24.6f, 4.8f, 26.4f, 2.4f, 27.4f),
            16, shirt, gloss = 0.05f,
        ).build()
        val b = ModelBuilder()
        b.addScaled(torso, 1f, 1f, 0.76f)
        b.cylinder(0f, 0f, 26.5f, 29.5f, 2.1f, 10, skin)
        // Staff wear an apron with a bib and a pocket over the shirt.
        if (look.apron != 0) {
            val apron = paint(look.apron)
            val trim = paint(lift(look.apron, 0.45f))
            b.box(-5.2f, 7.5f, 4.1f, 5.2f, 20.5f, 5.5f, BoxFaces(front = apron, left = apron, right = apron, gloss = 0.1f))
            b.box(-3.4f, 20.5f, 3.9f, 3.4f, 25.2f, 5.2f, BoxFaces(front = apron, left = apron, right = apron, top = apron, gloss = 0.1f))
            b.box(-2.6f, 12.5f, 5.5f, 2.6f, 15.8f, 5.8f, BoxFaces(front = trim, top = trim, left = trim, right = trim))
            b.box(-6.5f, 18.6f, -5f, 6.5f, 19.8f, 4.2f, BoxFaces(left = apron, right = apron, back = apron, top = apron))
        }
        // Head with the painted face, and hair over it.
        b.sphere(0f, HEAD_Y, 0f, HEAD_R, face(look.skin, look.hair), slices = 20, stacks = 14, gloss = 0.08f)
        val hair = hairTexture(look.hair, look.hairStyle)
        b.sphere(0f, HEAD_Y + 0.4f, -0.2f, HEAD_R + 0.55f, hair, slices = 20, stacks = 12, yFrom = -0.35f, gloss = 0.25f)
        when (look.hairStyle) {
            1 -> for (k in 0 until 5) {
                val a = -0.9f + k * 0.45f
                b.cylinder(kotlin.math.sin(a) * 4f, -1f - kotlin.math.cos(a) * 2f, HEAD_Y + 6.5f, HEAD_Y + 11f, 2f, 6, hair, topRadius = 0.2f)
            }
            2 -> b.capsule(0f, HEAD_Y + 2f, -7.5f, 0f, HEAD_Y - 9f, -7f, 3f, hair)
        }
        body = b.build()

        leg = ModelBuilder()
            .capsule(0f, 0f, 0f, 0f, -11.2f, 0f, 2.7f, pants)
            .capsule(0f, -12.2f, -0.8f, 0f, -12.2f, 3.2f, 2.1f, shoe)
            .build()
        arm = ModelBuilder()
            .capsule(0f, 0f, 0f, 0f, -5.5f, 0f, 2.2f, shirt)
            .capsule(0f, -5.5f, 0f, 0f, -10.5f, 0f, 1.7f, skin)
            .sphere(0f, -11.8f, 0f, 2f, skin)
            .build()
    }

    private val root = Xform()
    private val part = Xform()
    private val local = Xform()
    private val held = Xform()
    private val ownItem = heldItem(look)
    private val itemSeed = look.shirt xor look.hair

    /**
     * Draws the figure standing at ([x], [y], [z]) facing [yaw]. [phase] advances the walk cycle;
     * [time] drives idle motions. In the holding poses the hand carries café treat [item] (by
     * default the one this look always buys).
     */
    fun draw(r: Renderer3D, x: Float, y: Float, z: Float, yaw: Float, pose: Pose, phase: Float, time: Float, scale: Float = 1f, item: Int = -1) {
        val walking = pose == Pose.WALK || pose == Pose.CARRY
        val swing = if (walking) sin(phase) else 0f
        val bob = when (pose) {
            Pose.WALK, Pose.CARRY -> abs(sin(phase)) * 0.9f
            Pose.CHEER -> abs(sin(time * 9f)) * 2.5f
            else -> sin(time * 2f) * 0.15f
        }
        val sit = pose == Pose.SIT || pose == Pose.SIP
        root.set(x, y + (bob + if (sit) -5f else 0f) * scale, z, yaw = yaw, scale = scale)
        body.draw(r, Blend.OPAQUE, xf = root)
        hatModel?.let {
            local.set(0f, HEAD_Y, 0f)
            part.setProduct(root, local)
            it.draw(r, xf = part)
            if (look.hat == HatStyle.PROPELLER) {
                local.set(0f, HEAD_Y + HEAD_R + 3f, 0f, yaw = time * 14f)
                part.setProduct(root, local)
                propeller.draw(r, Blend.OPAQUE, xf = part)
            }
        }
        // Legs swing from the hips (or stick out forwards when sitting).
        for (s in SIDES) {
            val legPitch = if (sit) -1.45f else swing * 0.6f * s
            local.set(s * 3.1f, HIP_Y, 0f, pitch = legPitch)
            part.setProduct(root, local)
            leg.draw(r, Blend.OPAQUE, xf = part)
        }
        // Arms: opposite to the legs when walking, reaching forward at a machine, up when cheering.
        for (s in SIDES) {
            val armPitch: Float
            val armRoll: Float
            when (pose) {
                Pose.WALK -> {
                    armPitch = -swing * 0.5f * s; armRoll = 0.12f * s
                }
                Pose.PLAY -> {
                    armPitch = -1.05f + sin(time * 11f + s) * 0.12f; armRoll = 0.05f * s
                }
                Pose.CHEER -> {
                    armPitch = -2.6f + sin(time * 9f + s) * 0.25f; armRoll = 0.2f * s
                }
                Pose.SIT -> {
                    armPitch = -0.6f; armRoll = 0.1f * s
                }
                Pose.STAND -> {
                    armPitch = sin(time * 1.3f + s) * 0.05f; armRoll = 0.1f * s
                }
                Pose.WIPE -> if (s > 0) {
                    // Circles with a cloth on the counter top.
                    armPitch = -1.15f + sin(time * 7f) * 0.1f; armRoll = 0.05f + cos(time * 7f) * 0.28f
                } else {
                    armPitch = -0.75f; armRoll = -0.12f
                }
                Pose.CARRY, Pose.HOLD -> if (s > 0) {
                    armPitch = -1.25f + sin(time * 2f) * 0.04f; armRoll = 0.02f
                } else if (pose == Pose.CARRY) {
                    armPitch = swing * 0.5f; armRoll = -0.12f
                } else {
                    armPitch = sin(time * 1.3f) * 0.05f; armRoll = -0.1f
                }
                Pose.SIP -> if (s > 0) {
                    // Every few seconds the cup comes up for a sip.
                    val c = time % 4.5f
                    val lift = if (c < 1.4f) sin(c / 1.4f * PI.toFloat()) else 0f
                    armPitch = -0.95f - lift * 1.35f; armRoll = 0.05f + lift * 0.2f
                } else {
                    armPitch = -0.6f; armRoll = -0.1f
                }
            }
            local.set(s * SHOULDER_X, SHOULDER_Y, 0f, pitch = armPitch, roll = armRoll)
            part.setProduct(root, local)
            arm.draw(r, Blend.OPAQUE, xf = part)
            // The treat stays upright in the hand.
            if (s > 0 && (pose == Pose.CARRY || pose == Pose.HOLD || pose == Pose.SIP)) {
                val what = if (item >= 0) item else ownItem
                val m = itemModel(what, itemSeed)
                if (m != null) {
                    held.set(part.x(0f, -12.6f, 1.2f), part.y(0f, -12.6f, 1.2f), part.z(0f, -12.6f, 1.2f), yaw = yaw, scale = scale)
                    m.draw(r, Blend.OPAQUE, xf = held)
                }
            }
        }
    }

    // ------------------------------------------------------------------ textures

    /** Skin with eyes, brows, a smile and rosy cheeks, centred where the sphere faces +z. */
    private fun face(skin: Int, hair: Int): Region = faceCache.getOrPut(skin * 31 + hair) {
        val w = 256
        val h = 128
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), lift(skin, 0.08f), dim(skin, 0.88f))
        val cx = w * 0.25f
        val cy = h * 0.5f
        val ink = 0xFF1C140E.toInt()
        for (s in intArrayOf(-1, 1)) {
            val ex = cx + s * 13f
            tp.oval(ex, cy + 2f, 5.2f, 7f, -1)
            tp.oval(ex, cy + 3f, 4.2f, 5.6f, ink)
            tp.circle(ex - 1.5f, cy + 0.5f, 1.6f, -1)
            tp.line(ex - 5f, cy - 8f, ex + 5f, cy - 9.5f - s * 0.5f, 2.2f, dim(hair, 0.8f))
            tp.oval(cx + s * 21f, cy + 14f, 5f, 3f, 0x55FF5A7A)
        }
        tp.paint.reset()
        tp.paint.isAntiAlias = true
        tp.paint.style = android.graphics.Paint.Style.STROKE
        tp.paint.strokeWidth = 2.4f
        tp.paint.strokeCap = android.graphics.Paint.Cap.ROUND
        tp.paint.color = 0xFF7A2A20.toInt()
        tp.canvas.drawArc(cx - 7f, cy + 10f, cx + 7f, cy + 21f, 20f, 140f, false, tp.paint)
        tp.oval(cx, cy + 9f, 2f, 1.4f, dim(skin, 0.8f))
        tp.toTexture().also { tp.recycle() }
    }.full

    /** Hair colour with soft strands; the face area is left clear (cut out). */
    private fun hairTexture(hair: Int, style: Int): Region = hairCache.getOrPut(hair * 7 + style) {
        val w = 256
        val h = 128
        val tp = TexPaint(w, h)
        tp.vgrad(0f, 0f, w.toFloat(), h.toFloat(), lift(hair, 0.12f), dim(hair, 0.75f))
        for (k in 0 until 90) {
            val x = (k * 53 % w).toFloat()
            tp.line(x, 0f, x + 6f, h * 0.9f, 1.2f, alpha(lift(hair, 0.25f), 0.35f))
        }
        // Cut out the face: fringe line across the forehead, open down to the chin.
        val cx = w * 0.25f
        val halfW = if (style == 2) 32f else 42f
        val fringe = if (style == 1) 44f else 50f
        tp.paint.reset()
        tp.paint.isAntiAlias = true
        tp.paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        tp.canvas.drawRoundRect(cx - halfW, fringe, cx + halfW, h + 20f, 16f, 16f, tp.paint)
        tp.paint.xfermode = null
        tp.toTexture().also { tp.recycle() }
    }.full

    private val faceCache get() = FaceCache.faces
    private val hairCache get() = FaceCache.hairs
}

/** Left and right, for per-side limbs (a shared array, so drawing allocates nothing). */
private val SIDES = intArrayOf(-1, 1)

/** Shared face and hair textures (kids with the same colouring reuse them). */
private object FaceCache {
    val faces = HashMap<Int, Texture>()
    val hairs = HashMap<Int, Texture>()
}
