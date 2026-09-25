package com.pocketarcade.hub

import androidx.compose.ui.graphics.Color
import com.pocketarcade.data.DecorStyle
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.FloorCaster
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.RasterPainter
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.MiniGame
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The 3D arcade hall built from a [HubMap]: lit floor and walls, cabinet models with live
 * attract screens and marquees, furniture, decorations, billboarded characters, glow pools,
 * contact shadows and sparkles. [render] draws one frame into a [Renderer3D].
 */
class HubScene3D(val map: HubMap, private val games: List<MiniGame>) {
    companion object {
        private val skins = HashMap<String, CabinetSkin>()
        fun skinFor(game: MiniGame): CabinetSkin = skins.getOrPut(game.id) { CabinetSkin(game.look, game.marquee) }
        val brokenSkin: CabinetSkin by lazy { CabinetSkin(CabinetLook(Pal.DARKGRAY, Pal.GRAY, Pal.GRAY), "SOON") }

        private var floorKey = ""
        private var floorTexture: Texture? = null

        private fun floorFor(map: HubMap): Texture {
            val key = "${map.rows}:" + map.mats.joinToString(",") { "${it.centerX},${it.frontZ},${it.glow}" }
            if (key != floorKey || floorTexture == null) {
                floorTexture = HubTextures.floor(map)
                floorKey = key
            }
            return floorTexture!!
        }
    }

    private class Item(
        val prop: Prop,
        val model: Model? = null,
        val skin: CabinetSkin? = null,
        val game: MiniGame? = null,
        val billboard: Region? = null,
        val bbW: Float = 0f,
        val bbH: Float = 0f,
        val bbY: Float = 0f,
        val light: PointLight? = null,
        val glowColor: Int = 0,
        val index: Int = 0,
    )

    private val floor = floorFor(map).full
    private val caster = FloorCaster(16f)
    private val backWall = HubTextures.backWall.full
    private val sideWall = HubTextures.sideWall.full
    private val neon = HubTextures.neonStrip.region(wrap = true)
    private val glow = HubTextures.glow.full
    private val shadow = HubTextures.shadow.full
    private val spark = HubTextures.spark.full
    private val sign = HubTextures.sign.full
    private val entrance = HubModels.entranceWall(map.widthPx.toFloat(), (map.heightPx - 16).toFloat())

    private val allLights = ArrayList<PointLight>()
    private val lightBase = HashMap<PointLight, Float>()
    private val items = ArrayList<Item>()
    private val painter = RasterPainter()

    // Animated decoration faces.
    private var fishBase: IntArray? = null
    private var fishTex: Texture? = null
    private var jukeBase: IntArray? = null
    private var jukeTex: Texture? = null

    private val sparkX = FloatArray(160)
    private val sparkY = FloatArray(160)
    private val sparkZ = FloatArray(160)
    private val sparkVY = FloatArray(160)
    private val sparkLife = FloatArray(160)
    private val sparkColor = IntArray(160)
    private var sparkCount = 0
    private var lastTime = -1f
    private var sparkTimer = 0f
    private val rng = Random(9)
    private val tmp = FloatArray(2)

    init {
        val entranceFront = ((map.entranceRow + 3) * HubLayout.TILE).toFloat()
        for ((i, p) in map.props.withIndex()) {
            when (p.kind) {
                PropKind.MACHINE -> {
                    val g = games[p.machine]
                    val skin = skinFor(g)
                    val light = light(p.centerX, 26f, p.frontZ + 8f, g.look.glow, 95f, 1f)
                    items += Item(p, HubModels.cabinet(skin, p.x0, p.z0, p.x1, p.z1), skin, g, light = light, glowColor = g.look.glow, index = i)
                }
                PropKind.BROKEN -> items += Item(p, HubModels.cabinet(brokenSkin, p.x0, p.z0, p.x1, p.z1), brokenSkin, index = i)
                PropKind.COUNTER -> items += Item(p, HubModels.counter(), glowColor = Pal.PINK, index = i)
                PropKind.TOKENS -> {
                    val l = light(p.centerX, 30f, p.frontZ + 8f, Pal.GOLD, 70f, 0.9f)
                    items += Item(p, HubModels.tokenMachine(p), light = l, glowColor = Pal.GOLD, index = i)
                }
                PropKind.SODA -> {
                    val l = light(p.centerX, 26f, p.frontZ + 6f, Pal.RED, 55f, 0.7f)
                    items += Item(p, HubModels.soda(p), light = l, glowColor = Pal.RED, index = i)
                }
                PropKind.BENCH -> items += Item(p, HubModels.bench(p), index = i)
                PropKind.TRASH -> items += Item(p, HubModels.trash(p), index = i)
                PropKind.PLANT -> items += Item(p, billboard = DecorArt.plantHd.full, bbW = DecorArt.plantHd.width / 2f, bbH = DecorArt.plantHd.height / 2f, index = i)
                PropKind.DECOR -> items += decorItem(p, i)
            }
        }
        light(112f, 66f, HubLayout.WALL_Z + 8f, Pal.PINK, 120f, 0.8f)
        light(112f, 44f, 104f, 0xFFFFC8E0.toInt(), 90f, 0.8f)
        light(112f, 30f, map.heightPx - 24f, 0xFF88B4FF.toInt(), 90f, 0.7f)
        var z = 160f
        while (z < entranceFront + 40f) {
            light(112f, 110f, z, 0xFFB0A8E0.toInt(), 170f, 0.7f)
            z += 128f
        }
    }

    private fun light(x: Float, y: Float, z: Float, argb: Int, radius: Float, intensity: Float): PointLight {
        val l = PointLight(
            x, y, z,
            (argb shr 16 and 255) / 255f, (argb shr 8 and 255) / 255f, (argb and 255) / 255f,
            radius, intensity,
        )
        allLights += l
        lightBase[l] = intensity
        return l
    }

    private fun decorItem(p: Prop, i: Int): Item {
        val style = p.decor!!
        return when (style) {
            DecorStyle.TROPHY_CASE -> Item(p, HubModels.decorBox(style, p, HubModels.boxFront(style)), index = i)
            DecorStyle.FISH_TANK -> {
                val base = HubModels.boxFront(style)
                fishBase = base.pixels.copyOf()
                fishTex = base
                val l = light(p.centerX, 20f, p.frontZ + 6f, Pal.SKY, 60f, 0.7f)
                Item(p, HubModels.decorBox(style, p, base), light = l, glowColor = Pal.SKY, index = i)
            }
            DecorStyle.JUKEBOX -> {
                val base = HubModels.boxFront(style)
                jukeBase = base.pixels.copyOf()
                jukeTex = base
                val l = light(p.centerX, 24f, p.frontZ + 8f, Pal.PURPLE, 80f, 1f)
                Item(p, HubModels.decorBox(style, p, base), light = l, glowColor = Pal.PURPLE, index = i)
            }
            DecorStyle.DISCO_BALL -> {
                val t = DecorArt.hd(style)
                light(p.centerX, 50f, (p.z0 + p.z1) / 2f, Pal.WHITE, 90f, 0.5f)
                Item(p, billboard = t.full, bbW = t.width / 2f, bbH = t.height / 2f, bbY = 54f, index = i)
            }
            else -> {
                val t = DecorArt.hd(style)
                val (lightColor, has) = when (style) {
                    DecorStyle.LAVA_LAMP -> Pal.ORANGE to true
                    DecorStyle.FLAMINGO -> Pal.PINK to true
                    else -> 0 to false
                }
                val l = if (has) light(p.centerX, 20f, p.frontZ + 6f, lightColor, 60f, 0.8f) else null
                Item(p, billboard = t.full, bbW = t.width / 2f, bbH = t.height / 2f, light = l, glowColor = if (has) lightColor else 0, index = i)
            }
        }
    }

    // ------------------------------------------------------------------ frame

    fun render(r: Renderer3D, world: HubWorld, save: SaveState) {
        val t = world.time
        val dt = if (lastTime < 0f) 0f else (t - lastTime).coerceIn(0f, 0.1f)
        lastTime = t
        val cam = r.camera

        // Visible band of the hall along z.
        val zFar = if (cam.rayToPlaneY(r.width / 2f, 0f, 0f, tmp)) tmp[1] - 40f else world.camera.targetZ - 520f
        val zNear = if (cam.rayToPlaneY(r.width / 2f, r.height.toFloat(), 0f, tmp)) tmp[1] + 30f else world.camera.targetZ + 200f

        // Light setup: only lights near the visible band, with neon pulsing.
        r.lighting.ambR = 0.42f; r.lighting.ambG = 0.38f; r.lighting.ambB = 0.52f
        r.lighting.setDirection(0.3f, 1f, 0.6f)
        r.lighting.dirR = 0.28f; r.lighting.dirG = 0.26f; r.lighting.dirB = 0.3f
        r.lighting.points.clear()
        for ((k, l) in allLights.withIndex()) {
            if (l.z + l.radius < zFar || l.z - l.radius > zNear) continue
            val base = lightBase[l] ?: 1f
            l.intensity = base * (0.88f + 0.12f * sin(t * 2.2f + k * 1.7f))
            r.lighting.points += l
        }
        r.fogNear = world.camera.distance + 120f
        r.fogFar = world.camera.distance + 620f
        r.fogFloor = 0.12f
        r.clear(0xFF0A0614.toInt())

        drawFloor(r, zFar, zNear)
        drawWalls(r, zFar, zNear, t)

        // Props.
        for (it in items) {
            val p = it.prop
            if (p.z1 < zFar - 30f || p.z0 > zNear + 10f) continue
            it.skin?.let { skin -> paintScreen(skin, it.game, save, t, it.index) }
            if (it.prop.decor == DecorStyle.FISH_TANK) animateFish(t)
            if (it.prop.decor == DecorStyle.JUKEBOX) animateJukebox(t)
            it.model?.draw(r, Blend.OPAQUE)
            it.billboard?.let { bb ->
                val bob = if (p.decor == DecorStyle.DISCO_BALL) sin(t * 1.5f) * 1f else 0f
                r.billboard(p.centerX, it.bbY + bob, (p.z0 + p.z1) / 2f, it.bbW, it.bbH, bb, lean = 0.45f)
            }
        }
        if (map.heightPx - 16f < zNear + 20f) entrance.draw(r)

        // Characters.
        val pl = world.player
        pl.sheet?.let { s -> r.billboard(pl.x, 0f, pl.y, 18f, 30f, s.frames[pl.dir][pl.frame], lean = 0.5f) }
        for (n in world.npcs) {
            if (n.y < zFar - 10f || n.y > zNear + 10f) continue
            r.billboard(n.x, n.hop, n.y, 18f, 30f, n.sheet.frames[n.dir][n.frame], lean = 0.5f)
        }
        if (map.clerkY > zFar - 30f) {
            val bob = if ((t % 3f) < 0.2f) 1f else 0f
            r.billboard(map.clerkX, bob, map.clerkY, 18f, 30f, world.clerk.frames[CharacterArt.DOWN][0], lean = 0.5f)
        }

        // Transparent pass: glow pools, shadows, glass, neon halos, disco spots and sparkles.
        for (it in items) {
            val p = it.prop
            if (p.z1 < zFar - 30f || p.z0 > zNear + 10f) continue
            if (it.glowColor != 0) {
                val pulse = 0.3f + 0.08f * sin(t * 2.2f + it.index)
                val w = (p.x1 - p.x0) * 0.9f + 20f
                r.decal(p.centerX - w, p.frontZ - 8f, p.centerX + w, p.frontZ + 34f, 0.2f, glow, Blend.ADD, emissive = 1f, alpha = pulse, tint = it.glowColor)
            }
            it.model?.draw(r, Blend.ALPHA)
            val skin = it.skin
            if (skin != null && it.game != null) {
                val mY = when (skin.shape) {
                    com.pocketarcade.games.CabinetShape.UPRIGHT -> 48f
                    com.pocketarcade.games.CabinetShape.WIDE -> 51f
                    com.pocketarcade.games.CabinetShape.LANE -> 45f
                }
                val mZ = if (skin.shape == com.pocketarcade.games.CabinetShape.LANE) p.z0 + 6f else p.frontZ - 3f
                r.billboard(p.centerX, mY - 12f, mZ, (p.x1 - p.x0) * 1.9f, 24f, glow, lean = 0f, blend = Blend.ADD, emissive = 1f, alpha = 0.28f, depthBias = 1f, tint = skin.look.trim)
            }
            if (p.decor == DecorStyle.FLAMINGO) {
                val on = hash01((t * 6f).toInt(), 17) > 0.06f
                if (on) r.billboard(p.centerX, 7f, (p.z0 + p.z1) / 2f + 0.5f, 20f, 23f, DecorArt.flamingoNeon.full, lean = 0.45f, blend = Blend.ADD, emissive = 1.4f)
            }
            if (p.decor == DecorStyle.LAVA_LAMP) {
                val blob = 0.5f + 0.5f * sin(t * 1.3f)
                r.billboard(p.centerX, 8f + blob * 8f, (p.z0 + p.z1) / 2f + 0.5f, 8f, 8f, glow, lean = 0.45f, blend = Blend.ADD, emissive = 1f, alpha = 0.8f, tint = Pal.ORANGE)
            }
            if (p.decor == DecorStyle.DISCO_BALL) drawDiscoSpots(r, p, t)
        }
        drawShadow(r, pl.x, pl.y)
        for (n in world.npcs) if (n.y > zFar && n.y < zNear) drawShadow(r, n.x, n.y)
        r.quad(
            112f - 110f, 76f, HubLayout.WALL_Z + 0.6f, 112f + 110f, 76f, HubLayout.WALL_Z + 0.6f,
            112f + 110f, 50f, HubLayout.WALL_Z + 0.6f, 112f - 110f, 50f, HubLayout.WALL_Z + 0.6f,
            glow, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f, alpha = 0.15f, tint = Pal.PINK,
        )
        drawSign(r, t)
        updateSparks(dt, zFar, zNear)
        drawSparks(r)
    }

    private fun drawShadow(r: Renderer3D, x: Float, z: Float) {
        r.decal(x - 8f, z - 3.5f, x + 8f, z + 3.5f, 0.3f, shadow, Blend.ALPHA, alpha = 0.75f)
    }

    private fun drawFloor(r: Renderer3D, zFar: Float, zNear: Float) {
        val zA = maxOf(HubLayout.WALL_Z, zFar)
        val zB = minOf(map.heightPx - 16f, zNear)
        if (caster.draw(r, floor.tex, TPU.toFloat(), 16f, map.widthPx - 16f, zA, zB)) return
        // Fallback for a yawed camera: 32-unit cells, lit per vertex.
        val tile = 32f
        var z = maxOf(HubLayout.WALL_Z, (zFar / tile).toInt() * tile)
        val zEnd = minOf(map.heightPx - 16f, zNear)
        while (z < zEnd) {
            val z2 = minOf(((z / tile).toInt() + 1) * tile, zEnd)
            var x = 16f
            while (x < map.widthPx - 16f) {
                r.quad(
                    x, 0f, z, x + tile, 0f, z, x + tile, 0f, z2, x, 0f, z2,
                    floor, 0f, 1f, 0f,
                    u0 = x * TPU, v0 = z * TPU, u1 = (x + tile) * TPU, v1 = z2 * TPU,
                )
                x += tile
            }
            z = z2
        }
    }

    private fun drawWalls(r: Renderer3D, zFar: Float, zNear: Float, t: Float) {
        val wh = HubLayout.WALL_HEIGHT
        val wz = HubLayout.WALL_Z
        if (zFar < wz + 200f) {
            var x = 16f
            while (x < map.widthPx - 16f) {
                val u0 = (x - 16f) * TPU
                val u1 = u0 + 32f * TPU
                r.quad(x, wh, wz, x + 32f, wh, wz, x + 32f, wh / 2f, wz, x, wh / 2f, wz, backWall, 0f, 0f, 1f, u0 = u0, v0 = 0f, u1 = u1, v1 = wh / 2f * TPU)
                r.quad(x, wh / 2f, wz, x + 32f, wh / 2f, wz, x + 32f, 0f, wz, x, 0f, wz, backWall, 0f, 0f, 1f, u0 = u0, v0 = wh / 2f * TPU, u1 = u1, v1 = wh * TPU)
                x += 32f
            }
        }
        val zs = maxOf(wz, (zFar / 32f).toInt() * 32f)
        val ze = minOf(map.heightPx - 16f, zNear)
        var z = zs
        val right = map.widthPx - 16f
        while (z < ze) {
            val z2 = minOf(z + 32f, map.heightPx - 16f)
            val uw = (z2 - z) * TPU
            r.quad(16f, wh, z, 16f, wh, z2, 16f, 0f, z2, 16f, 0f, z, sideWall, 1f, 0f, 0f, u1 = uw, v1 = wh * TPU)
            r.quad(right, wh, z2, right, wh, z, right, 0f, z, right, 0f, z2, sideWall, -1f, 0f, 0f, u1 = uw, v1 = wh * TPU)
            z += 32f
        }
        // Neon tubes along both side walls (additive, drawn now but only over walls).
        val pulse = 0.75f + 0.25f * sin(t * 1.7f)
        val nz0 = zs
        val nz1 = ze
        if (nz1 > nz0) {
            r.quad(16.3f, 58f, nz0, 16.3f, 58f, nz1, 16.3f, 55f, nz1, 16.3f, 55f, nz0, neon, 1f, 0f, 0f, u1 = (nz1 - nz0) * TPU, blend = Blend.ADD, emissive = 1.2f, alpha = pulse, cull = false)
            r.quad(right - 0.3f, 58f, nz1, right - 0.3f, 58f, nz0, right - 0.3f, 55f, nz0, right - 0.3f, 55f, nz1, neon, -1f, 0f, 0f, u1 = (nz1 - nz0) * TPU, blend = Blend.ADD, emissive = 1.2f, alpha = pulse, cull = false)
        }
    }

    private fun drawSign(r: Renderer3D, t: Float) {
        val buzz = 0.85f + 0.15f * sin(t * 13f) * sin(t * 3.1f)
        val flicker = if (hash01((t * 20f).toInt(), 3) < 0.03f) 0.35f else 1f
        val w = sign.w / TPU.toFloat()
        val h = sign.h / TPU.toFloat()
        val z = HubLayout.WALL_Z + 0.8f
        val y1 = 74f
        r.quad(112f - w / 2f, y1, z, 112f + w / 2f, y1, z, 112f + w / 2f, y1 - h, z, 112f - w / 2f, y1 - h, z, sign, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1.3f * buzz * flicker)
    }

    private fun drawDiscoSpots(r: Renderer3D, p: Prop, t: Float) {
        val colors = intArrayOf(Pal.PINK, Pal.CYAN, Pal.YELLOW, Pal.LIME, Pal.WHITE, Pal.PURPLE)
        val cx = p.centerX
        val cz = (p.z0 + p.z1) / 2f
        for (k in 0 until 16) {
            val a = t * 0.5f + k * TAU / 16f
            val rad = 20f + 70f * hash01(k, 4)
            val x = cx + cos(a) * rad * 1.2f
            val z = cz + 30f + sin(a) * rad
            r.decal(x - 3f, z - 2f, x + 3f, z + 2f, 0.25f, glow, Blend.ADD, emissive = 1f, alpha = 0.7f, tint = colors[k % colors.size])
        }
    }

    // ------------------------------------------------------------------ live textures

    private fun paintScreen(skin: CabinetSkin, game: MiniGame?, save: SaveState, t: Float, index: Int) {
        skin.paint(game, game?.let { save.highScore(it.id) } ?: 0, t, index, painter)
    }

    private fun animateFish(t: Float) {
        val tex = fishTex ?: return
        val base = fishBase ?: return
        System.arraycopy(base, 0, tex.pixels, 0, base.size)
        val p = painter.begin(tex.full, 2f)
        val fishColors = intArrayOf(Pal.ORANGE, Pal.YELLOW, Pal.PINK)
        for (k in 0 until 3) {
            val speed = 5f + k * 3f
            val span = 30f
            val raw = (t * speed + k * 11f) % (span * 2f)
            val right = raw < span
            val fx = 2f + if (right) raw else span * 2f - raw
            val fy = 5f + k * 3f + sin(t * 2f + k) * 0.8f
            p.fill(fx, fy, 3f, 2f, Color(fishColors[k]))
            p.px(if (right) fx - 1f else fx + 3f, fy, Color(fishColors[k]))
            p.px(if (right) fx + 2f else fx, fy, Color(Pal.BLACK))
        }
        for (k in 0 until 4) {
            val by = 15f - ((t * 6f + k * 4f) % 11f)
            p.px(6f + k * 7f, by, Color(Pal.WHITE), 0.7f)
        }
    }

    private fun animateJukebox(t: Float) {
        val tex = jukeTex ?: return
        val base = jukeBase ?: return
        System.arraycopy(base, 0, tex.pixels, 0, base.size)
        val p = painter.begin(tex.full, 2f)
        val colors = intArrayOf(Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME)
        for (k in 0 until 8) {
            val h = 1f + (sin(t * 7f + k * 1.3f) * 0.5f + 0.5f) * 6f
            p.fill(5f + k * 2f, 26f - h, 1f, h, Color(colors[(k + (t * 2f).toInt()) % colors.size]))
        }
        val arc = colors[(t * 3f).toInt() % colors.size]
        p.disc(13f, 10f, 8f, Color(arc), 0.35f)
        p.disc(13f, 10f, 6f, Color(Pal.NIGHT))
    }

    // ------------------------------------------------------------------ sparkles

    private fun spawnSpark(x: Float, y: Float, z: Float, vy: Float, color: Int) {
        if (sparkCount >= sparkX.size) return
        val i = sparkCount++
        sparkX[i] = x; sparkY[i] = y; sparkZ[i] = z; sparkVY[i] = vy
        sparkLife[i] = 1.2f; sparkColor[i] = color
    }

    private fun updateSparks(dt: Float, zFar: Float, zNear: Float) {
        sparkTimer -= dt
        if (sparkTimer <= 0f) {
            sparkTimer = 0.1f
            for (it in items) {
                val p = it.prop
                if (p.z1 < zFar || p.z0 > zNear) continue
                when {
                    p.decor == DecorStyle.JUKEBOX && rng.nextFloat() < 0.4f ->
                        spawnSpark(p.centerX + (rng.nextFloat() - 0.5f) * 14f, 36f, p.frontZ, 10f, if (rng.nextBoolean()) Pal.PINK else Pal.CYAN)
                    p.decor == DecorStyle.DISCO_BALL && rng.nextFloat() < 0.6f ->
                        spawnSpark(p.centerX + (rng.nextFloat() - 0.5f) * 14f, 54f + rng.nextFloat() * 14f, (p.z0 + p.z1) / 2f, -2f, Pal.WHITE)
                    p.kind == PropKind.COUNTER && rng.nextFloat() < 0.15f ->
                        spawnSpark(p.x0 + rng.nextFloat() * (p.x1 - p.x0), 24f + rng.nextFloat() * 6f, p.z1 - 4f, 2f, Pal.WHITE)
                    p.kind == PropKind.TOKENS && rng.nextFloat() < 0.15f ->
                        spawnSpark(p.centerX + (rng.nextFloat() - 0.5f) * 16f, 10f + rng.nextFloat() * 24f, p.z1 + 1f, 4f, Pal.YELLOW)
                }
            }
        }
        var i = 0
        while (i < sparkCount) {
            sparkLife[i] -= dt
            if (sparkLife[i] <= 0f) {
                val last = --sparkCount
                sparkX[i] = sparkX[last]; sparkY[i] = sparkY[last]; sparkZ[i] = sparkZ[last]
                sparkVY[i] = sparkVY[last]; sparkLife[i] = sparkLife[last]; sparkColor[i] = sparkColor[last]
                continue
            }
            sparkY[i] += sparkVY[i] * dt
            i++
        }
    }

    private fun drawSparks(r: Renderer3D) {
        for (i in 0 until sparkCount) {
            val a = (sparkLife[i] / 1.2f).coerceIn(0f, 1f)
            val size = 2.5f * (0.5f + 0.5f * abs(sin(sparkLife[i] * 9f)))
            r.billboard(sparkX[i], sparkY[i], sparkZ[i], size, size, spark, lean = 1f, blend = Blend.ADD, emissive = 1.5f, alpha = a, depthBias = 1.05f, tint = sparkColor[i])
        }
    }
}
