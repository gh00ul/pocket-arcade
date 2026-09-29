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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

    /**
     * The room a label needs beyond its text's width, for a label [h] tall at screen [density]: padding
     * either side and the marker dot at its left.
     */
    fun labelPad(h: Float, density: Float): Float = 12f * density + h * 0.6f
}

/** How often the "you are here" ring pulses, in milliseconds. */
private const val YOU_PULSE_MILLIS = 1400

/** The map's floor: a violet gradient with a faint blueprint grid every [GRID_STEP] world units. */
private val FLOOR_TOP = Color(0xFF231A4C)
private val FLOOR_BOTTOM = Color(0xFF130D2A)
private const val GRID_STEP = 60f
private const val GRID_ALPHA = 0.055f

/** Props are washed in their tint at this alpha and outlined at [PROP_EDGE_ALPHA]: soft enough that the pins stay the loudest thing on the plan. */
private const val PROP_FILL_ALPHA = 0.24f
private const val PROP_EDGE_ALPHA = 0.7f

/** The legend under the plan: what each colour of marker means. */
private class LegendEntry(val label: String, val color: Color)

private val LEGEND = listOf(
    LegendEntry("YOU", Color(Pal.CYAN)),
    LegendEntry("MACHINE", Color(Pal.PURPLE)),
    LegendEntry("TOKENS", Color(Pal.GOLD)),
    LegendEntry("PRIZES", Color(Pal.PINK)),
    LegendEntry("CAFE", Color(Pal.GREEN)),
    LegendEntry("DOORS", Color(Pal.SKY)),
)

/**
 * The hall's floor plan as a map: a blueprint-style plan with props as soft tinted boxes, a
 * glowing marker for every machine in its glow colour with its marquee text, the café, the token
 * kiosk, the prize counter, the doors and a pulsing dot where you stand, and a legend under it.
 * Tapping a place calls [onGo] with it; the caller closes the map and walks there ([MapPin.go]).
 *
 * The plan and pins are drawn once (they never move); only the pulsing "you" layer redraws each
 * frame, so the map costs next to nothing to keep open.
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
    // Held as State and read only in the "you" layer's draw, so the pulse redraws that layer and nothing else.
    // With reduce motion the ring rests part-way out instead of pulsing.
    val pulse = if (UiMotion.enabled) {
        rememberInfiniteTransition(label = "you").animateFloat(
            0f, 1f, infiniteRepeatable(tween(YOU_PULSE_MILLIS, easing = LinearEasing), RepeatMode.Restart), label = "pulse",
        )
    } else {
        remember { mutableFloatStateOf(0.35f) }
    }

    // Pin boxes in pixels: labels as wide as their text, dots a fixed size, labels spread apart.
    val boxes = remember(pins, size, density) {
        val g = HallMap.Geometry(size.width.toFloat(), size.height.toFloat())
        val lu = 1.7f * density
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
                w[i] = ArcadeFont.width(p.text, lu, true) + HallMap.labelPad(lh, density)
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
        MapBoxes(g, cx, cy, w, h, lu, lh)
    }

    ArcadePanel("MAP", Color(Pal.SKY), onClose, fillHeight = true) {
        ArcadeText("TAP A PLACE TO WALK THERE", UiText.CAPTION, color = UiColors.textMid, centered = true)
        Spacer(Modifier.height(UiSpace.sm))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size = it }
                .pointerInput(boxes, pins) {
                    detectTapGestures { at ->
                        val i = HallMap.hit(pins.size, boxes.cx, boxes.cy, boxes.w, boxes.h, at.x, at.y, 16f * density)
                        if (i >= 0) onGo(pins[i])
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawPlan(world, boxes.geometry, density)
                drawPins(pins, boxes, density)
            }
            Canvas(Modifier.fillMaxSize()) {
                drawYou(boxes.geometry.sx(youX), boxes.geometry.sy(youZ), youYaw, pulse.value, density)
            }
        }
        Spacer(Modifier.height(UiSpace.sm))
        MapLegend()
    }
}

/** What each marker colour means, in a row of dots and words that shrink to fit a narrow phone. */
@Composable
private fun MapLegend() {
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Legend: you, machines, tokens, prizes, cafe, doors" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (e in LEGEND) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Canvas(Modifier.size(9.dp)) {
                    val r = this.size.minDimension / 2f
                    glowCircle(e.color, center, r * 0.8f, r * 0.9f, 0.4f)
                    drawCircle(e.color, r * 0.8f, center)
                    drawCircle(Color.White.copy(alpha = 0.7f), r * 0.28f, center + Offset(-r * 0.2f, -r * 0.25f))
                }
                Spacer(Modifier.width(3.dp))
                ArcadeText(e.label, UiText.CAPTION, color = UiColors.textMid)
            }
        }
    }
}

/** The pins' pixel boxes (centres and sizes), with the label text size and the height labels were made for. */
private class MapBoxes(
    val geometry: HallMap.Geometry,
    val cx: FloatArray,
    val cy: FloatArray,
    val w: FloatArray,
    val h: FloatArray,
    val labelUnit: Float,
    val labelHeight: Float,
)

/** The floor with its blueprint grid, the walls, the café's floor and every solid prop. */
private fun DrawScope.drawPlan(world: HubWorld, g: HallMap.Geometry, density: Float) {
    val map = world.map
    val s = g.scale
    val lw = 1.5f * density
    // The floor, inside the walls, with a gap where the doors are.
    val fx0 = g.sx(HubLayout.WALL)
    val fz0 = g.sy(HubLayout.BACK_WALL)
    val fx1 = g.sx(HubLayout.WIDTH - HubLayout.WALL)
    val fz1 = g.sy(HubLayout.FRONT_WALL)
    val corner = CornerRadius(8f * density)
    val floor = Size(fx1 - fx0, fz1 - fz0)
    glowRoundRect(Color(Pal.SKY), Offset(fx0, fz0), floor, corner.x, 10f * density, 0.16f)
    drawRoundRect(Brush.verticalGradient(listOf(FLOOR_TOP, FLOOR_BOTTOM), fz0, fz1), Offset(fx0, fz0), floor, corner)
    // A faint grid, so the plan reads as drawn rather than filled.
    var gx = GRID_STEP
    while (gx < HubLayout.WIDTH) {
        val x = g.sx(gx)
        if (x > fx0 && x < fx1) drawLine(Color(Pal.LAVENDER), Offset(x, fz0 + corner.x), Offset(x, fz1 - corner.x), 1f, alpha = GRID_ALPHA)
        gx += GRID_STEP
    }
    var gz = GRID_STEP
    while (gz < HubLayout.DEPTH) {
        val y = g.sy(gz)
        if (y > fz0 && y < fz1) drawLine(Color(Pal.LAVENDER), Offset(fx0 + corner.x, y), Offset(fx1 - corner.x, y), 1f, alpha = GRID_ALPHA)
        gz += GRID_STEP
    }
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(Pal.LAVENDER).copy(alpha = 0.7f), Color(Pal.LAVENDER).copy(alpha = 0.3f)), fz0, fz1),
        Offset(fx0, fz0), floor, corner, style = Stroke(lw * 2f),
    )
    // The café's tiled floor, and the door gap through the wall.
    drawRect(
        Color(Pal.ORANGE), Offset(g.sx(CafeLayout.FLOOR_X0), g.sy(CafeLayout.FLOOR_Z0)),
        Size((CafeLayout.FLOOR_X1 - CafeLayout.FLOOR_X0) * s, (CafeLayout.FLOOR_Z1 - CafeLayout.FLOOR_Z0) * s), alpha = 0.1f,
    )
    drawLine(
        Color(0xFF130D2A), Offset(g.sx(HubLayout.DOOR_X0), fz1), Offset(g.sx(HubLayout.DOOR_X1), fz1), lw * 4.5f,
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
        val r = CornerRadius(2f * density)
        drawRoundRect(c, tl, sz, r, alpha = PROP_FILL_ALPHA)
        drawRoundRect(c, tl, sz, r, alpha = PROP_EDGE_ALPHA, style = Stroke(lw * 0.8f))
    }
}

/** "You are here": a pulsing ring, a bright dot with a soft glow and a wedge pointing the way you face. */
private fun DrawScope.drawYou(x: Float, y: Float, yaw: Float, pulse: Float, density: Float) {
    val c = Offset(x, y)
    val cyan = Color(Pal.CYAN)
    val r = 6f * density
    drawCircle(cyan, r * (1.2f + 1.8f * pulse), c, alpha = 0.5f * (1f - pulse), style = Stroke(2f * density))
    glowCircle(cyan, c, r * 1.15f, r * 1.2f, 0.4f)
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

/** A glowing dot for every cabinet that doesn't carry its bank's label, a dark glass pill with a marker dot for every one that does. */
private fun DrawScope.drawPins(pins: List<MapPin>, b: MapBoxes, density: Float) {
    for (i in pins.indices) {
        val p = pins[i]
        val c = Color(p.color)
        val at = Offset(b.cx[i], b.cy[i])
        if (!p.labelled) {
            val r = b.w[i] * 0.5f
            glowCircle(c, at, r, r * 0.9f, 0.4f)
            drawCircle(Color.Black, r, at, alpha = 0.55f)
            drawCircle(c, r * 0.68f, at)
            drawCircle(Color.White, r * 0.2f, at + Offset(-r * 0.18f, -r * 0.22f), alpha = 0.75f)
            continue
        }
        val tl = Offset(at.x - b.w[i] / 2f, at.y - b.h[i] / 2f)
        val sz = Size(b.w[i], b.h[i])
        val r = CornerRadius(b.h[i] / 2f)
        glowRoundRect(c, tl, sz, r.x, 5f * density, 0.32f)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xF02A2052), Color(0xF0120C22)), tl.y, tl.y + sz.height), tl, sz, r,
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(c.lift(0.2f), c.shade(0.8f)), tl.y, tl.y + sz.height), tl, sz, r, style = Stroke(1.6f * density),
        )
        // A marker dot at the left, then the name in the space that is left.
        val dotR = b.h[i] * 0.16f
        val dotC = Offset(tl.x + b.h[i] * 0.5f, at.y)
        drawCircle(c, dotR, dotC)
        drawCircle(Color.White, dotR * 0.35f, dotC + Offset(-dotR * 0.2f, -dotR * 0.25f), alpha = 0.8f)
        val textLeft = dotC.x + dotR + b.h[i] * 0.18f
        val textMid = (textLeft + tl.x + sz.width - b.h[i] * 0.3f) / 2f
        ArcadeFont.drawCentered(this, p.text, textMid, at.y - ArcadeFont.height(b.labelUnit, true) / 2f, b.labelUnit, c.lift(0.35f), tiny = true, shadow = false)
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
