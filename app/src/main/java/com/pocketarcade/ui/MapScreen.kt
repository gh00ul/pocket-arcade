package com.pocketarcade.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal
import com.pocketarcade.games.MiniGame
import com.pocketarcade.hub.CafeLayout
import com.pocketarcade.hub.HubLayout
import com.pocketarcade.hub.HubMap
import com.pocketarcade.hub.HubWorld
import com.pocketarcade.hub.PropKind
import com.pocketarcade.hub.Spot
import com.pocketarcade.hub.SpotType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** What a pin on the quick-travel map is. */
enum class PinKind { MACHINE, TOKENS, PRIZES, CAFE, DOORS, PHOTO }

/**
 * A place you can travel to from the map, drawn at ([x], [z]) on the floor plan. Going there
 * walks to [spot] (the standing point of a machine, the token kiosk or the prize counter) or,
 * for the café and the doors, to the floor point ([goalX], [goalZ]). Of a bank's cabinets one
 * pin is [labelled], carrying the marquee text; the others are dots.
 */
class MapPin(
    val kind: PinKind,
    val text: String,
    /** ARGB, the machine's glow. */
    val color: Int,
    val x: Float,
    val z: Float,
    val spot: Spot?,
    val goalX: Float = x,
    val goalZ: Float = z,
    val labelled: Boolean = true,
) {
    /** Sets the hall's kid walking there (either camera). False if there is no way. */
    fun go(world: HubWorld): Boolean = if (spot != null) world.walkTo(spot) else world.walkToPoint(goalX, goalZ)
}

/** The quick-travel map's content, built from the floor plan alone (so the JVM tests can check it). */
object HallMap {
    /** The floor plan's size in world units: the map is drawn as large as fits, whole. */
    const val WIDTH = HubLayout.WIDTH.toFloat()
    const val DEPTH = HubLayout.DEPTH.toFloat()

    /**
     * Every place worth travelling to: each machine (a pin per cabinet, one carrying its
     * marquee), the token kiosk, the prize counter, the café and the doors.
     */
    fun pins(map: HubMap, games: List<MiniGame>): List<MapPin> {
        val out = ArrayList<MapPin>()
        for ((i, g) in games.withIndex()) {
            val copies = map.spots.filter { it.type == SpotType.MACHINE && it.machine == i }
            if (copies.isEmpty()) continue
            // The label sits on the cabinet nearest the middle of the bank.
            val mid = copies.map { it.area.centerX }.average().toFloat()
            val lead = copies.minByOrNull { abs(it.area.centerX - mid) }
            for (s in copies) {
                out += MapPin(PinKind.MACHINE, g.marquee, g.look.glow, s.area.centerX, s.area.centerY, s, labelled = s === lead)
            }
        }
        for (s in map.spots) {
            when (s.type) {
                SpotType.TOKENS -> out += MapPin(PinKind.TOKENS, "TOKENS", Pal.GOLD, s.area.centerX, s.area.centerY, s)
                SpotType.PRIZES -> out += MapPin(PinKind.PRIZES, "PRIZES", Pal.PINK, s.area.centerX, s.area.centerY, s)
                SpotType.PHOTO -> out += MapPin(PinKind.PHOTO, "PHOTOS", Pal.PURPLE, s.area.centerX, s.area.centerY, s)
                // Machines have their own pins above, and the café has one below. The rest only
                // earn a pin once their prop does something worth walking over for.
                SpotType.MACHINE, SpotType.TROPHY, SpotType.TANK, SpotType.CAFE,
                SpotType.RIDE, SpotType.JUKEBOX, SpotType.VENDING -> Unit
            }
        }
        // The café: labelled over its tables, walking to the front of the till queue.
        val till = map.cafeQueue.firstOrNull()
        out += MapPin(
            PinKind.CAFE, "CAFE", Pal.GREEN,
            (CafeLayout.FLOOR_X0 + CafeLayout.FLOOR_X1) / 2f, CafeLayout.FLOOR_Z0 + 150f, null,
            goalX = till?.x ?: CafeLayout.TILL_X, goalZ = till?.z ?: (CafeLayout.FLOOR_Z0 + 86f),
        )
        // The doors, just outside the wall; you start hall visits just inside them.
        out += MapPin(PinKind.DOORS, "DOORS", Pal.SKY, (HubLayout.DOOR_X0 + HubLayout.DOOR_X1) / 2f, HubLayout.FRONT_WALL + 8f, null, map.spawnX, map.spawnY)
        return out
    }

    /** The tint each kind of solid prop is drawn in, as ARGB, or 0 for the ones left off the plan. */
    fun propColor(kind: PropKind): Int = when (kind) {
        PropKind.COUNTER, PropKind.PRIZE_WALL -> Pal.PINK
        PropKind.TOKENS, PropKind.CHANGE -> Pal.GOLD
        PropKind.VENDING -> Pal.TEAL
        PropKind.CAFE_BAR, PropKind.CAFE_COUNTER, PropKind.CAFE_TABLE, PropKind.BOOTH -> Pal.ORANGE
        PropKind.PILLAR, PropKind.TRASH -> Pal.GRAY
        PropKind.PLANT -> Pal.GREEN
        PropKind.PHOTO_BOOTH -> Pal.PURPLE
        PropKind.KIDDIE_RIDE -> Pal.HOTPINK
        PropKind.BENCH -> Pal.TAN
        PropKind.DECOR -> Pal.LAVENDER
        else -> 0
    }

    /**
     * Where the plan sits in a [width] × [height] pixel area: as large as fits, centred.
     * [sx] and [sy] turn a world x and z into pixels.
     */
    class Geometry(val width: Float, val height: Float) {
        val scale = if (width > 0f && height > 0f) minOf(width / WIDTH, height / DEPTH) else 1f
        val left = (width - WIDTH * scale) / 2f
        val top = (height - DEPTH * scale) / 2f
        fun sx(x: Float) = left + x * scale
        fun sy(z: Float) = top + z * scale
    }

    /**
     * Finds room for the pin labels. Label [i] is [w]`[i]` wide and [h] tall and would like its
     * centre at ([ax]`[i]`, [ay]`[i]`); it stays inside 0..[width] × 0..[height] and, when it would
     * touch a label already placed (closer than [gap]), slides up or down until it doesn't.
     * Writes the centres to [outX] and [outY].
     */
    fun placeLabels(
        n: Int, ax: FloatArray, ay: FloatArray, w: FloatArray, h: Float, gap: Float,
        width: Float, height: Float, outX: FloatArray, outY: FloatArray,
    ) {
        for (i in 0 until n) {
            val x = ax[i].coerceIn(w[i] / 2f, maxOf(width - w[i] / 2f, w[i] / 2f))
            var placed = false
            var k = 0
            while (!placed && k < 12) {
                val slide = ((k + 1) / 2) * (h + gap)
                val y = (ay[i] + if (k == 0) 0f else if (k % 2 == 1) -slide else slide).coerceIn(h / 2f, maxOf(height - h / 2f, h / 2f))
                var clear = true
                for (j in 0 until i) {
                    if (abs(x - outX[j]) < (w[i] + w[j]) / 2f + gap && abs(y - outY[j]) < h + gap) {
                        clear = false
                        break
                    }
                }
                if (clear) {
                    outX[i] = x
                    outY[i] = y
                    placed = true
                }
                k++
            }
            if (!placed) {
                outX[i] = x
                outY[i] = ay[i]
            }
        }
    }

    /**
     * The pin a tap at ([tx], [ty]) is for: the one whose hit box (its label, or a square
     * [dot] across for a dot, both grown by [slop]) the tap is nearest, or -1 if none is within
     * [slop]. [hx], [hy] are the pins' centres, [hw] and [hh] their sizes.
     */
    fun hit(n: Int, hx: FloatArray, hy: FloatArray, hw: FloatArray, hh: FloatArray, tx: Float, ty: Float, slop: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until n) {
            val dx = maxOf(abs(tx - hx[i]) - hw[i] / 2f, 0f)
            val dy = maxOf(abs(ty - hy[i]) - hh[i] / 2f, 0f)
            val d = dx * dx + dy * dy
            if (d <= slop * slop && d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }
}

/**
 * The hall's floor plan as a map: props as tinted boxes, a marker for every machine in its glow
 * colour with its marquee text, the café, the token kiosk, the prize counter, the doors and a
 * pulsing dot where you stand. Tapping a place calls [onGo] with it; the caller closes the map
 * and walks there ([MapPin.go]).
 */
@Composable
fun MapScreen(world: HubWorld, onGo: (MapPin) -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current.density
    val map = world.map
    val pins = remember(map) { HallMap.pins(map, world.games) }
    // Where you are as the map opens: nothing walks while it is up.
    val youX = remember { world.player.x }
    val youZ = remember { world.player.y }
    val youYaw = remember { world.player.yaw }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val pulse by rememberInfiniteTransition(label = "you").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "pulse",
    )

    // Pin boxes in pixels: labels as wide as their text, dots a fixed size, labels spread apart.
    val boxes = remember(pins, size, density) {
        val g = HallMap.Geometry(size.width.toFloat(), size.height.toFloat())
        val lu = 1.7f * density
        val padX = 5f * density
        val lh = ArcadeFont.height(lu, true) + 8f * density
        val dot = 9f * density
        val n = pins.size
        val cx = FloatArray(n)
        val cy = FloatArray(n)
        val w = FloatArray(n)
        val h = FloatArray(n)
        val labelled = ArrayList<Int>()
        for (i in 0 until n) {
            val p = pins[i]
            cx[i] = g.sx(p.x)
            cy[i] = g.sy(p.z)
            if (p.labelled) {
                w[i] = ArcadeFont.width(p.text, lu, true) + padX * 2f
                h[i] = lh
                labelled += i
            } else {
                w[i] = dot
                h[i] = dot
            }
        }
        val m = labelled.size
        val ax = FloatArray(m) { cx[labelled[it]] }
        val ay = FloatArray(m) { cy[labelled[it]] }
        val lw = FloatArray(m) { w[labelled[it]] }
        val ox = FloatArray(m)
        val oy = FloatArray(m)
        HallMap.placeLabels(m, ax, ay, lw, lh, 3f * density, size.width.toFloat(), size.height.toFloat(), ox, oy)
        for (k in 0 until m) {
            cx[labelled[k]] = ox[k]
            cy[labelled[k]] = oy[k]
        }
        MapBoxes(g, cx, cy, w, h, lu, padX)
    }

    ArcadePanel("MAP", Color(Pal.SKY), onClose, fillHeight = true) {
        ArcadeText("TAP A PLACE TO WALK THERE", unit = 2.dp, tiny = true, color = Color(Pal.LAVENDER))
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size = it },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(boxes, pins) {
                        detectTapGestures { at ->
                            val i = HallMap.hit(pins.size, boxes.cx, boxes.cy, boxes.w, boxes.h, at.x, at.y, 16f * density)
                            if (i >= 0) onGo(pins[i])
                        }
                    },
            ) {
                drawPlan(world, boxes.geometry, youX, youZ, youYaw, pulse, density)
                drawPins(pins, boxes, density)
            }
        }
    }
}

/** The pins' pixel boxes (centres and sizes), with the label text size and padding they were made for. */
private class MapBoxes(
    val geometry: HallMap.Geometry,
    val cx: FloatArray,
    val cy: FloatArray,
    val w: FloatArray,
    val h: FloatArray,
    val labelUnit: Float,
    val labelPad: Float,
)

/** The floor, the walls, the café's floor and every solid prop. */
private fun DrawScope.drawPlan(world: HubWorld, g: HallMap.Geometry, youX: Float, youZ: Float, youYaw: Float, pulse: Float, density: Float) {
    val map = world.map
    val s = g.scale
    val lw = 1.5f * density
    // The floor, inside the walls, with a gap where the doors are.
    val fx0 = g.sx(HubLayout.WALL)
    val fz0 = g.sy(HubLayout.BACK_WALL)
    val fx1 = g.sx(HubLayout.WIDTH - HubLayout.WALL)
    val fz1 = g.sy(HubLayout.FRONT_WALL)
    val corner = CornerRadius(6f * density)
    drawRoundRect(Color(0xFF1A1236), Offset(fx0, fz0), Size(fx1 - fx0, fz1 - fz0), corner)
    drawRoundRect(
        Color(Pal.LAVENDER), Offset(fx0, fz0), Size(fx1 - fx0, fz1 - fz0), corner, alpha = 0.55f, style = Stroke(lw * 2f),
    )
    // The café's tiled floor, and the door gap through the wall.
    drawRect(
        Color(Pal.ORANGE), Offset(g.sx(CafeLayout.FLOOR_X0), g.sy(CafeLayout.FLOOR_Z0)),
        Size((CafeLayout.FLOOR_X1 - CafeLayout.FLOOR_X0) * s, (CafeLayout.FLOOR_Z1 - CafeLayout.FLOOR_Z0) * s), alpha = 0.1f,
    )
    drawLine(
        Color(0xFF1A1236), Offset(g.sx(HubLayout.DOOR_X0), fz1), Offset(g.sx(HubLayout.DOOR_X1), fz1), lw * 4f,
    )
    drawLine(
        Color(Pal.SKY), Offset(g.sx(HubLayout.DOOR_X0), fz1), Offset(g.sx(HubLayout.DOOR_X1), fz1), lw * 1.6f, StrokeCap.Round,
    )
    for (p in map.props) {
        if (!p.solid) continue
        val argb = if (p.kind == PropKind.MACHINE) world.games[p.machine].look.glow else HallMap.propColor(p.kind)
        if (argb == 0) continue
        val c = Color(argb)
        val tl = Offset(g.sx(p.x0), g.sy(p.z0))
        val sz = Size((p.x1 - p.x0) * s, (p.z1 - p.z0) * s)
        val r = CornerRadius(1.5f * density)
        drawRoundRect(c, tl, sz, r, alpha = 0.32f)
        drawRoundRect(c, tl, sz, r, alpha = 0.85f, style = Stroke(lw))
    }
    drawYou(g.sx(youX), g.sy(youZ), youYaw, pulse, density)
}

/** "You are here": a pulsing ring, a bright dot and a wedge pointing the way you face. */
private fun DrawScope.drawYou(x: Float, y: Float, yaw: Float, pulse: Float, density: Float) {
    val c = Offset(x, y)
    val cyan = Color(Pal.CYAN)
    val r = 6f * density
    drawCircle(cyan, r * (1.2f + 1.8f * pulse), c, alpha = 0.5f * (1f - pulse), style = Stroke(2f * density))
    // On the plan z runs down the screen, so a heading of yaw points (sin, cos).
    val fx = sin(yaw)
    val fz = cos(yaw)
    val wedge = Path().apply {
        moveTo(x + fx * r * 2.3f, y + fz * r * 2.3f)
        lineTo(x - fz * r * 0.9f - fx * r * 0.2f, y + fx * r * 0.9f - fz * r * 0.2f)
        lineTo(x + fz * r * 0.9f - fx * r * 0.2f, y - fx * r * 0.9f - fz * r * 0.2f)
        close()
    }
    drawPath(wedge, cyan)
    drawCircle(Color.Black, r * 1.15f, c, alpha = 0.5f)
    drawCircle(cyan, r, c)
    drawCircle(Color.White, r * 0.4f, c)
}

/** A dot for every cabinet that doesn't carry its bank's label, a dark glass pill for every one that does. */
private fun DrawScope.drawPins(pins: List<MapPin>, b: MapBoxes, density: Float) {
    for (i in pins.indices) {
        val p = pins[i]
        val c = Color(p.color)
        val at = Offset(b.cx[i], b.cy[i])
        if (!p.labelled) {
            drawCircle(Color.Black, b.w[i] * 0.5f, at, alpha = 0.5f)
            drawCircle(c, b.w[i] * 0.34f, at)
            continue
        }
        val tl = Offset(at.x - b.w[i] / 2f, at.y - b.h[i] / 2f)
        val sz = Size(b.w[i], b.h[i])
        val r = CornerRadius(b.h[i] / 2f)
        drawRoundRect(Color(0xF0120C22), tl, sz, r)
        drawRoundRect(c, tl, sz, r, style = Stroke(1.6f * density))
        ArcadeFont.drawCentered(this, p.text, at.x, at.y - ArcadeFont.height(b.labelUnit, true) / 2f, b.labelUnit, c.lift(0.35f), tiny = true, shadow = false)
    }
}

/** A folded paper map with a place marked on it, [s] across, centred on [c]. */
fun DrawScope.drawMapIcon(c: Offset, s: Float, color: Color) {
    val l = c.x - s * 0.42f
    val r = c.x + s * 0.42f
    val t = c.y - s * 0.34f
    val b = c.y + s * 0.34f
    val a = c.x - s * 0.14f
    val m = c.x + s * 0.14f
    val dip = s * 0.07f
    val paper = Path().apply {
        moveTo(l, t + dip)
        lineTo(a, t)
        lineTo(m, t + dip)
        lineTo(r, t)
        lineTo(r, b - dip)
        lineTo(m, b)
        lineTo(a, b - dip)
        lineTo(l, b)
        close()
    }
    drawPath(paper, color, style = Stroke(s * 0.09f))
    drawLine(color, Offset(a, t), Offset(a, b - dip), s * 0.07f)
    drawLine(color, Offset(m, t + dip), Offset(m, b), s * 0.07f)
    drawCircle(color, s * 0.07f, Offset(c.x + s * 0.28f, c.y - s * 0.06f))
    drawCircle(color, s * 0.07f, Offset(c.x - s * 0.28f, c.y + s * 0.08f))
}
