class_name Widgets
extends RefCounted
## ui/Widgets.kt: the menus' design system. Tokens live in [UiTheme] (and [UiColors]...), the
## vector icons and currency art in [UiIcons], chips / bars / toggles in [UiParts]; the components
## every screen is assembled from are classes of their own: [ArcadeText], [ArcadeButton],
## [RoundButton], [ArcadePanel], [GlassBox], [CurrencyRow], [ArcadeBanner]. This file holds
## Widgets.kt's top-level functions: colour lifting and shading, what a screen reader says,
## shrink-to-fit, the press, the button cap, the glass bevel and the entrance helpers.

## How hard a button shrinks when fully pressed (a fraction of its size), and how much it brightens.
const PRESS_SHRINK := 0.06
const PRESS_BRIGHTEN := 0.16
const PRESS_STIFFNESS := 1600.0
const RELEASE_STIFFNESS := 700.0
const RELEASE_DAMPING := 0.42

## A resting button's glow (alpha at its edge) and how far it reaches (dp); pressing dims it.
const BUTTON_GLOW := 0.28
const BUTTON_GLOW_REACH := 9.0

## At this many tokens or fewer the token counter pulses, nudging the player toward the token machine.
const LOW_TOKENS := 2
## The low-token pulse: one breath in and out, each way this long.
const LOW_PULSE_MILLIS := 820.0

## Compose's Constraints.Infinity (an unbounded width).
const CONSTRAINTS_INFINITY := 0x7FFFFFFF


## Color.shade: multiplies the RGB channels by [param f] (Pal.shade on the colour's ARGB).
static func shade(c: Color, f: float) -> Color:
	return Pal.c(Pal.shade(Pal.to_argb(c), f))


## Color.lift: mixes towards white by [param f].
static func lift(c: Color, f: float) -> Color:
	return Color(c.r + (1.0 - c.r) * f, c.g + (1.0 - c.g) * f, c.b + (1.0 - c.b) * f, c.a)


## What a screen reader should say for arcade text: the symbols the font draws inline (play, star,
## token...) named or dropped, line breaks as spaces and the capitals lowered, so TalkBack reads
## words rather than spelling out shouted letters.
static func spoken_text(text: String) -> String:
	var out := PackedStringArray()
	for ch in text:
		# The play triangle only points at the word beside it, and a note says nothing.
		if ch == ArcadeFont.PLAY or ch == ArcadeFont.NOTE:
			out.append(" ")
		elif ch == ArcadeFont.STAR:
			out.append(" star ")
		elif ch == ArcadeFont.HEART:
			out.append(" heart ")
		elif ch == ArcadeFont.TOKEN:
			out.append(" token ")
		elif ch == ArcadeFont.TICKET:
			out.append(" tickets ")
		elif ch == ArcadeFont.LEFT:
			out.append(" left ")
		elif ch == ArcadeFont.RIGHT:
			out.append(" right ")
		elif ch == ArcadeFont.UP:
			out.append(" up ")
		elif ch == ArcadeFont.DOWN:
			out.append(" down ")
		elif ch == "\n":
			out.append(" ")
		else:
			out.append(ch)
	var s := "".join(out).strip_edges()
	while s.contains("  "):
		s = s.replace("  ", " ")
	return s.to_lower()


## The scale that makes something [param natural] pixels wide fit in [param limit]: 1 when it
## already fits or there is no limit ([constant CONSTRAINTS_INFINITY]).
static func shrink_scale(natural: int, limit: int) -> float:
	if limit == CONSTRAINTS_INFINITY or natural <= 0 or natural <= limit:
		return 1.0
	return float(limit) / natural


## How far the cap has sunk into the skirt at [param press] (1 = fully down); the release overshoot
## lifts it a little above rest.
static func press_offset(lip: float, press: float) -> float:
	return lip * 0.8 * press if press >= 0.0 else lip * 0.5 * press


## The shared look of every button: a glow of its colour, a drop shadow, the skirt, a gradient cap
## with a darker lower bevel, gloss and a lit rim, at press depth [param press] (0 rest, 1 down),
## filling [param area] with [param lip] dp of skirt below the cap. A button that isn't
## [param enabled] is slate, flat and dark.
static func button_cap(ds: DrawScope, area: Vector2, color: Color, enabled: bool, lip: float, press: float, is_round: bool) -> void:
	var w := area.x
	var h := area.y - lip
	var r := h / 2.0 if is_round else minf(UiRadius.button, h / 2.0)
	var face_y := press_offset(lip, press)
	var pr := clampf(press, 0.0, 1.0)
	# The cap brightens as it is pressed, as if the light behind it came up.
	var base := lift(color, PRESS_BRIGHTEN * pr) if enabled and press > 0.0 else color
	if enabled:
		UiTheme.glow_round_rect(ds, color, Vector2(0.0, face_y + lip * 0.4), Vector2(w, h), r, BUTTON_GLOW_REACH, BUTTON_GLOW * (1.0 - 0.6 * pr))
	ds.draw_round_rect(Color(0, 0, 0, 0.38), Vector2(0.0, lip + 2.0), Vector2(w, h), r)
	ds.draw_round_rect(shade(base, 0.42) if enabled else UiColors.off_skirt, Vector2(0.0, lip), Vector2(w, h), r)
	var top := lift(base, 0.3) if enabled else UiColors.off_cap_top
	var mid := base if enabled else shade(UiColors.off_cap_top, 0.9)
	var bottom := shade(base, 0.74) if enabled else UiColors.off_cap_bottom
	ds.draw_round_rect(PaBrush.vertical([top, mid, bottom], face_y, face_y + h), Vector2(0.0, face_y), Vector2(w, h), r)
	if enabled:
		# The cap's lower bevel: its bottom edge turns away from the light.
		var dark := shade(base, 0.5)
		UiDraw.stroke_round_rect_vgradient(ds, Vector2(0.0, face_y), Vector2(w, h), r, 2.0,
			PackedColorArray([Color(0, 0, 0, 0), Color(dark, 0.6)]), face_y + h * 0.68, face_y + h)
	var inset := 3.0
	var gh := h * 0.46
	ds.draw_round_rect(PaBrush.vertical([Color(1, 1, 1, 0.42 if enabled else 0.1), Color(1, 1, 1, 0.03)], face_y + inset, face_y + inset + gh),
		Vector2(inset, face_y + inset * 0.7), Vector2(w - inset * 2.0, gh), r * 0.85)
	UiDraw.stroke_round_rect_vgradient(ds, Vector2(0.0, face_y), Vector2(w, h), r, 1.0,
		PackedColorArray([Color(1, 1, 1, 0.55 if enabled else 0.14), Color(1, 1, 1, 0.05)]), face_y, face_y + h)


## A lit hairline just inside the top edge of a glass surface, fading toward its sides.
static func paint_glass_bevel(ds: DrawScope, area: Vector2, corner: float) -> void:
	var inset := 1.0
	UiDraw.stroke_round_rect_vgradient(ds, Vector2(inset, inset), area - Vector2(inset * 2.0, inset * 2.0), maxf(corner - inset, 0.0), 1.0,
		PackedColorArray([Color(1, 1, 1, 0.26), Color(0, 0, 0, 0)]), 0.0, area.y * 0.3)


## The 0..1 breathing of the low-token pulse at [param time_s] seconds into it (an infinite
## FastOutSlowIn tween of [constant LOW_PULSE_MILLIS], reversing).
static func low_pulse(time_s: float) -> float:
	return UiAnim.repeat_value(time_s, LOW_PULSE_MILLIS, UiAnim.Easing.FAST_OUT_SLOW_IN, true)


# ---------------------------------------------------------------- entrances

## How far through the slot [param from]..[param to] a progress [param p] is, 0..1: one step of a
## staggered sequence.
static func stage_of(p: float, from: float, to: float) -> float:
	return clampf((p - from) / maxf(to - from, 1e-4), 0.0, 1.0)


## rememberEntrance([param millis]): a 0..1 progress that runs once, linearly, from when [param owner]
## starts it (at once with reduce motion). Give it to [method enter_stage] for each piece of a
## card that should arrive in turn. [param owner] steps it.
static func remember_entrance(owner: UiView, millis: float = 700.0) -> UiEntrance:
	var e := UiEntrance.new(millis)
	owner.add_animator(e)
	e.start()
	return e


## Modifier.enterStage: brings [param view] in during [param from]..[param to] of [param entrance]:
## it fades in and rises [param rise] dp into place, or with [param pop] scales up from 70% with a
## little overshoot. Reduce motion only fades.
static func enter_stage(view: UiView, entrance: UiEntrance, from: float, to: float, rise: float = 16.0, pop: bool = false) -> void:
	entrance.stage(view, from, to, rise, pop)


## Modifier.staggerIn([param index]): [param view] arrives [param index] steps after the first piece
## of the panel it sits in, sliding up and fading in. Does nothing outside a panel.
static func stagger_in(view: UiView, index: int, rise: float = 14.0) -> UiView:
	view.stagger_index = index
	view.stagger_rise = rise
	if view.is_inside_tree():
		view.join_entrance()
	return view


## Modifier.popIn([param delay_ms], [param key]): [param view] fades up from 70% scale with a springy
## overshoot after the delay; it pops again whenever [param key] changes. Reduce motion shows it at once.
static func pop_in(view: UiView, delay_ms: float = 0.0, key: Variant = null) -> void:
	var pop: UiPopIn = null
	for a in view.animators:
		if a is UiPopIn:
			pop = a
	if pop == null:
		pop = UiPopIn.new(view)
		view.add_animator(pop)
		pop.restart(delay_ms, key)
	elif pop.key != key:
		pop.restart(delay_ms, key)


## Modifier.reserveSpace([param show]): [param view] keeps its space in the layout but draws and takes
## touches only while shown (buttons that arrive later must not move what is above them).
static func reserve_space(view: UiView, show: bool) -> void:
	view.reserve_space = true
	view.visible = show


# ---------------------------------------------------------------- surfaces

## Modifier.border([param width], [param color], RoundedCornerShape([param corner])): the stroke sits
## inside the bounds, as Compose draws a border.
static func border(ds: DrawScope, area: Vector2, width: float, color: Color, corner: float) -> void:
	var h := width / 2.0
	ds.draw_round_rect(color, Vector2(h, h), area - Vector2(width, width), maxf(corner - h, 0.0), 1.0, width)


## A border painted with a vertical gradient through [param colors] over the whole height.
static func border_vgradient(ds: DrawScope, area: Vector2, width: float, colors: PackedColorArray, corner: float) -> void:
	var h := width / 2.0
	UiDraw.stroke_round_rect_vgradient(ds, Vector2(h, h), area - Vector2(width, width), maxf(corner - h, 0.0), width, colors, 0.0, area.y)


## Insets forced in place of the window's safe area (left, top, right, bottom in dp; x < 0: use
## the window's). Captures set it to stand in for a phone's camera cutout.
static var forced_insets := Vector4(-1.0, -1.0, -1.0, -1.0)


## Compose's WindowInsets.safeDrawing for [param node]'s window, in dp (left, top, right, bottom).
static func safe_insets(node: Node) -> Vector4:
	if forced_insets.x >= 0.0:
		return forced_insets
	if node == null or not node.is_inside_tree():
		return Vector4.ZERO
	return Display.insets_dp(node.get_window())
