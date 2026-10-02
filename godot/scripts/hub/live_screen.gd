class_name LiveScreen
extends RefCounted
## hub/MachineArt.kt LiveScreen: one cabinet's live screen: the game's attract loop, drawn with
## smooth shapes and real text, and a "HIGH SCORE" card every few seconds, with CRT scanlines. The
## hall only repaints it while the cabinet is in view (MachineUnit.refresh). Map
## `texture.full()` onto a quad (emissive about 1.1).

const SCALE := 10.0

var art: MachineArt
var seed_v: int
## The screen in painter units (what the game's draw_attract gets as w × h).
var units: Vector2i
## The live picture (its pixels are the painter's viewport, repainted in place).
var texture: PaTexture
var _tp: TexPaint = null
var _painter: CanvasPainter = null
## Screens repaint on alternate frames; which ones is set by the seed so the whole hall doesn't do
## it on the same frame.
var _frame: int
var _best_text := ""
var _best_shown := -1


func _init(p_art: MachineArt, p_seed: int) -> void:
	art = p_art
	seed_v = p_seed
	var u: Variant = art.screen_units()
	units = u if u != null else Vector2i(24, 18)
	texture = PaTexture.new(int(units.x * SCALE), int(units.y * SCALE))
	_frame = p_seed & 1


func _painter_ready() -> void:
	if _tp == null:
		_tp = TexPaint.new(int(units.x * SCALE), int(units.y * SCALE))
		_painter = CanvasPainter.new(_tp, SCALE)


## Paints the first picture now (a loading step), so the painter, the texture and whatever the
## game's attract loop paints on first use are ready before the cabinet is first in view, rather
## than one after another as the camera pans over the hall. Keeps the alternate-frame stagger the
## seed set.
func warm(best: int) -> void:
	var keep := _frame
	_frame = 1
	paint(best, 0.0)
	_frame = keep


func paint(best: int, t: float) -> void:
	# Screens refresh at 30 fps; that's plenty for attract loops.
	_frame += 1
	if _frame % 2 != 0:
		return
	_painter_ready()
	var tp := _tp
	var cycle := fmod(t + seed_v * 1.7, 9.0)
	# The painter records what is drawn: start the picture over (Kotlin painted over its bitmap).
	tp.clear(0)
	if cycle > 7.0:
		tp.fill(0xFF05040A)
		tp.glow_text("HIGH SCORE", tp.w / 2.0, tp.h * 0.36, tp.h * 0.13, 0xFFFFE14D, 0xFFFF9A3C, 5.0, Fonts.display())
		if best != _best_shown:
			_best_shown = best
			_best_text = str(best)
		tp.glow_text(_best_text, tp.w / 2.0, tp.h * 0.72, tp.h * 0.26, 0xFFFFFFFF, art.glow, 8.0, Fonts.display())
	else:
		tp.fill(0xFF000000)
		art.game.draw_attract(_painter, units.x, units.y, t + seed_v * 3.1)
	# Scanlines and a soft glass sheen: one draw of a pre-made overlay, not a rect per line.
	tp.image(MachineArt.screen_glass(tp.w, tp.h), 0.0, 0.0)
	tp.update(texture)
