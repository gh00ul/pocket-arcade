class_name GameHostScreen
extends Control
## ui/GameHostScreen.kt: hosts one mini-game: the intro card, countdown, round clock, pause/quit,
## and the results screen where tickets print out of the machine with a counter ticking up. The
## round's logic is [GameRound]; this draws it (the cabinet bezel and chase bulbs, the top bar, the
## countdown and GO!, TIME'S UP, the results card, grade stamp and ticket printer), steps it on the
## fixed 120 Hz loop, routes touches and Back, follows the app's pauses, the tilt sensor and the
## edge-swipe exclusion, and positions the game's 3D picture (GameViewport).
##
## The cards (intro, pause, results buttons) and the close button are drawn here in build-13's
## layout until the shared UI kit's widgets take them over.

signal exited(refund_token: bool)

var round: GameRound
var game: MiniGame
var services: ArcadeServices
var touch_input := TouchInput.new()
var loop := GameLoop.new()
var tilt := TiltSteer.new()

var _intro_t := 0.0
var _pause_t := 0.0
var _controls_t := 0.0
var _buttons: Array = []
var _pressed := -1
var _app_active := true
var _top_inset := 0.0
var _bottom_inset := 0.0


## Opens machine [param p_game] (its token already spent) with the app's [param p_services].
static func create(p_game: MiniGame, p_services: ArcadeServices) -> GameHostScreen:
	var s := GameHostScreen.new()
	s.game = p_game
	s.services = p_services
	s.round = GameRound.new(p_game, p_services.audio, p_services.haptics, p_services.repo)
	s.round.exit_requested.connect(s._on_exit_requested)
	return s


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	touch_input.set_capturing(true)
	_update_gesture_exclusion()
	if Main.instance != null:
		Main.instance.app_active_changed.connect(_on_app_active)
	_update_tilt()


func _exit_tree() -> void:
	touch_input.set_capturing(false)
	touch_input.set_unbuffered(false)
	tilt.stop()
	AndroidBridge.set_gesture_exclusion(PackedInt32Array())
	Gfx.remove(GameViewport.SLOT)
	GameViewport.take_punch()
	if services != null and services.audio != null:
		RoundMusic.play(services.audio)


## Android Back (through Main's back handler): the same as the close button.
func handle_back() -> bool:
	round.on_exit_pressed()
	return true


func _on_exit_requested(refund: bool) -> void:
	exited.emit(refund)


func _on_app_active(active: bool) -> void:
	_app_active = active
	if not active:
		round.pause()
	_update_tilt()


## Tilt steering (the racer, while its option is on): the sensor is listened to only while this
## round is on screen and the app is in front.
func _update_tilt() -> void:
	var wants: bool = _app_active and ("tilt_steering" in game) and bool(game.get("tilt_steering"))
	if wants and not tilt.listening():
		tilt.start(game)
	elif not wants and tilt.listening():
		tilt.stop()


## Thumbs rest low on both sides (pinball flippers, racer steering): no Back swipes there.
func _update_gesture_exclusion() -> void:
	var px := Vector2(get_window().size) if is_inside_tree() else Vector2.ZERO
	if px.x > 0.0:
		AndroidBridge.set_gesture_exclusion(TouchInput.thumb_zones(Rect2(Vector2.ZERO, px), Display.density))


func _process(delta: float) -> void:
	var steps := loop.steps_for(delta)
	for i in steps:
		round.frame(GameLoop.FIXED_DT)
	tilt.poll()
	touch_input.set_unbuffered(round.playing())
	for s: Array in touch_input.poll(TouchInput.window_to_local(self)):
		_route_touch(s[0], s[1], s[2], s[3], s[4])
	if round.phase == GameRound.Phase.INTRO:
		_intro_t += delta
	if round.phase == GameRound.Phase.PAUSED:
		_pause_t += delta
	else:
		_pause_t = 0.0
	if round.phase == GameRound.Phase.RESULTS and round.reveal_stage >= 4:
		_controls_t += delta
	else:
		_controls_t = 0.0
	queue_redraw()


func _gui_input(event: InputEvent) -> void:
	if touch_input.handle_event(event):
		accept_event()


## One touch sample (dp): the cards' buttons first, then the round (the game while playing, the
## results' skip tap).
func _route_touch(type: int, id: int, x: float, y: float, t: int) -> void:
	if type == TouchType.DOWN:
		_pressed = _button_at(Vector2(x, y))
		if _pressed >= 0:
			return
	elif type == TouchType.UP and _pressed >= 0:
		var b: Array = _buttons[_pressed] if _pressed < _buttons.size() else []
		_pressed = -1
		if not b.is_empty() and (b[0] as Rect2).grow(8.0).has_point(Vector2(x, y)):
			var action: Callable = b[1]
			action.call()
		return
	elif _pressed >= 0:
		return
	# The intro and pause cards' backdrops swallow every other tap.
	if round.phase == GameRound.Phase.INTRO or round.phase == GameRound.Phase.PAUSED:
		return
	round.touch(type, id, x, y, t)


func _button_at(p: Vector2) -> int:
	for i in range(_buttons.size() - 1, -1, -1):
		if (_buttons[i][0] as Rect2).has_point(p):
			return i
	return -1


func _motion() -> bool:
	return ScreenShake.intensity > 0.0


# ------------------------------------------------------------------ drawing

func _draw() -> void:
	_buttons.clear()
	var ins := Display.insets_dp(get_window())
	_top_inset = ins.y
	_bottom_inset = ins.w
	var ds := DrawScope.new(self, size)
	_draw_host(ds)
	match round.phase:
		GameRound.Phase.INTRO:
			_draw_intro_card(ds)
		GameRound.Phase.PAUSED:
			_draw_pause_card(ds)
		GameRound.Phase.RESULTS:
			if round.reveal_stage >= 4:
				_draw_results_controls(ds)
	_draw_flights(ds)
	# The exit button, last so it sits above the cards.
	_draw_close_button(ds)


## build-13's drawHost layout for a screen of [param screen] dp with safe-area [param insets]
## (left, top, right, bottom, dp) at [param px_per_dp]: the top bar's height, then the field's left,
## top and scale (dp per field unit) as (bar_h, gx, gy, gs). The field fills the width or the height
## left under the bar, whichever is smaller, centred: at any aspect the rest is bezel.
static func field_layout(screen: Vector2, insets: Vector4, px_per_dp: float) -> Vector4:
	var unit := maxf(floorf(2.0 * px_per_dp), 2.0) / px_per_dp
	var bar_h := insets.y + 42.0 * unit
	var avail_h := screen.y - bar_h - insets.w - 6.0 * unit
	var gs := minf(screen.x / MiniGame.GAME_W, avail_h / MiniGame.GAME_H)
	var gx := (screen.x - MiniGame.GAME_W * gs) / 2.0
	var gy := bar_h + maxf((avail_h - MiniGame.GAME_H * gs) / 2.0, 0.0)
	return Vector4(bar_h, gx, gy, gs)


func _draw_host(ds: DrawScope) -> void:
	var w := size.x
	var h := size.y
	var unit := maxf(floorf(2.0 * Display.density), 2.0) / Display.density
	var lay := field_layout(size, Vector4(0.0, _top_inset, 0.0, _bottom_inset), Display.density)
	var bar_h := lay.x
	var gs := lay.w
	var gw := MiniGame.GAME_W * gs
	var gh := MiniGame.GAME_H * gs
	var gx := lay.y
	var gy := lay.z
	round.gx = gx
	round.gy = gy
	round.gs = gs
	var look := game.look
	var t := round.host_time

	# Cabinet bezel around the field (the field itself shows the GPU picture underneath).
	var bezel := Pal.c(Pal.shade(look.body, 0.28))
	ds.draw_rect(bezel, Vector2.ZERO, Vector2(w, gy))
	ds.draw_rect(bezel, Vector2(0, gy + gh), Vector2(w, h - gy - gh))
	ds.draw_rect(bezel, Vector2(0, gy), Vector2(gx, gh))
	ds.draw_rect(bezel, Vector2(gx + gw, gy), Vector2(w - gx - gw, gh))
	GameViewport.x = gx + round.shake.offset_x * gs
	GameViewport.y = gy + round.shake.offset_y * gs
	GameViewport.scale = gs
	GameViewport.clip_x0 = gx
	GameViewport.clip_y0 = gy
	GameViewport.clip_x1 = gx + gw
	GameViewport.clip_y1 = gy + gh
	for i in 20:
		var y := bar_h + i * (h - bar_h) / 20.0
		var on := (int(t * 5.0) + i) % 4 == 0
		var c := Pal.c(Pal.YELLOW if on else Pal.shade(look.trim, 0.4))
		if gx > unit * 6.0:
			ds.draw_circle(c, unit * 2.0, Vector2(gx / 2.0, y + unit * 6.0))
			ds.draw_circle(c, unit * 2.0, Vector2(w - gx / 2.0, y + unit * 6.0))

	# The game itself, clipped to its screen.
	ds.push()
	ds.clip_rect(gx, gy, gx + gw, gy + gh)
	ds.translate(gx + round.shake.offset_x * gs, gy + round.shake.offset_y * gs)
	ds.scale_by(gs, gs, Vector2.ZERO)
	game.draw(ds)
	# A punch no 3D stage took (a flat game) has nothing to move: drop it, don't let it linger.
	GameViewport.take_punch()
	_draw_overlays(ds)
	round.particles.draw(ds)
	ds.pop()
	ds.draw_rect(Pal.c(look.trim), Vector2(gx - unit * 2.0, gy - unit * 2.0), Vector2(gw + unit * 4.0, gh + unit * 4.0), 1.0, PaStroke.new(unit * 2.0))

	# Top bar: score, time, best.
	ds.draw_rect(Pal.c(Pal.NIGHT), Vector2.ZERO, Vector2(w, bar_h))
	ds.draw_rect(Pal.c(look.trim), Vector2(0, bar_h - unit * 2.0), Vector2(w, unit * 2.0))
	ds.draw_rect(Pal.c(look.glow), Vector2(0, bar_h), Vector2(w, unit * 3.0), 0.25)
	var label_y := _top_inset + unit * 6.0
	var value_y := label_y + unit * 10.0
	var score_x := w * 0.40
	ArcadeFont.draw_centered(ds, "SCORE", score_x, label_y, unit, Pal.LAVENDER)
	ArcadeFont.draw_centered(ds, str(game.score), score_x, value_y, unit * 2.5, Pal.WHITE)
	var time_x := w * 0.66
	var secs := ceili(round.time_left)
	var low := secs <= 10 and round.phase == GameRound.Phase.PLAYING
	var time_alpha := 0.4 if low and int(t * 4.0) % 2 == 0 else 1.0
	ArcadeFont.draw_centered(ds, "TIME", time_x, label_y, unit, Pal.LAVENDER)
	ArcadeFont.draw_centered(ds, str(secs), time_x, value_y, unit * 2.5, Pal.RED if low else Pal.CYAN, time_alpha)
	var best_x := w * 0.88
	ArcadeFont.draw_centered(ds, "BEST", best_x, label_y, unit, Pal.LAVENDER)
	ArcadeFont.draw_centered(ds, str(round.live_best()), best_x, value_y + unit * 2.0, unit * 1.5, Pal.YELLOW)
	# Time bar.
	var frac := MathUtil.clamp01(round.time_left / game.round_seconds)
	ds.draw_rect(Pal.c(Pal.DEEP), Vector2(w * 0.27, bar_h - unit * 6.0), Vector2(w * 0.7, unit * 2.0))
	ds.draw_rect(Pal.c(Pal.RED if low else look.glow), Vector2(w * 0.27, bar_h - unit * 6.0), Vector2(w * 0.7 * frac, unit * 2.0))


## Countdown, time-up banner and the results screen, drawn in game units over the field.
func _draw_overlays(ds: DrawScope) -> void:
	var cx := MiniGame.GAME_W / 2.0
	var motion := _motion()
	match round.phase:
		GameRound.Phase.COUNTDOWN:
			var step := clampi(int(round.phase_t / GameRound.COUNT_STEP), 0, 2)
			var frac := fmod(round.phase_t, GameRound.COUNT_STEP) / GameRound.COUNT_STEP
			ds.draw_rect(Color.BLACK, Vector2.ZERO, Vector2(MiniGame.GAME_W, MiniGame.GAME_H), 0.35 * MathUtil.clamp01(round.phase_t / 0.3))
			var label := str(3 - step)
			var color: int = GameRound.COUNT_COLORS[step]
			var settle := MathUtil.clamp01(frac / 0.28)
			var leave := MathUtil.clamp01((frac - 0.72) / 0.28)
			# Each numeral drops in big, overshoots a touch small, settles, then eases away.
			var scale := (lerpf(1.9, 1.0, MathUtil.ease_out_back(settle)) if motion else 1.0) * (1.0 - 0.12 * leave)
			var alpha := MathUtil.clamp01(frac / 0.1) * (1.0 - leave)
			var size_u := 16.0 * scale
			var mid_y := 262.0
			if motion:
				ds.draw_circle(Pal.c(color), 36.0 + 120.0 * MathUtil.ease_out_cubic(frac), Vector2(cx, mid_y), (1.0 - frac) * 0.45, PaStroke.new(3.0))
			ArcadeFont.draw_centered(ds, label, cx, mid_y - 3.5 * size_u, size_u, color, alpha)
			ArcadeFont.draw_centered(ds, "GET READY", cx, 380.0, 3.0, Pal.WHITE, MathUtil.clamp01(round.phase_t / 0.3) * (0.65 + 0.35 * sin(round.host_time * 5.0)))
		GameRound.Phase.PLAYING:
			if round.go_t < 0.75:
				var p := round.go_t / 0.75
				var settle := MathUtil.clamp01(round.go_t / 0.16)
				# GO! slams in, swells as it fades, with a ring rolling outward.
				var scale := (lerpf(2.0, 1.0, MathUtil.ease_out_back(settle)) if motion else 1.0) * (1.0 + 0.35 * p)
				var a := 1.0 if p < 0.55 else 1.0 - (p - 0.55) / 0.45
				var size_u := 12.0 * scale
				var mid_y := 262.0
				if motion:
					ds.draw_circle(Pal.c(Pal.LIME), 30.0 + 170.0 * MathUtil.ease_out_cubic(p), Vector2(cx, mid_y), (1.0 - p) * 0.5, PaStroke.new(4.0))
				ArcadeFont.draw_centered(ds, "GO!", cx, mid_y - 3.5 * size_u, size_u, Pal.LIME, a)
		GameRound.Phase.ENDING:
			var pop := MathUtil.ease_out_back(MathUtil.clamp01(round.phase_t / 0.35)) if motion else MathUtil.clamp01(round.phase_t / 0.2)
			ds.draw_rect(Color.BLACK, Vector2.ZERO, Vector2(MiniGame.GAME_W, MiniGame.GAME_H), 0.4 * MathUtil.clamp01(round.phase_t / 0.3))
			ArcadeFont.draw_centered(ds, "ALL DONE!" if round.ended_early else "TIME'S UP!", cx, 280.0, 6.0 * pop, Pal.YELLOW, MathUtil.clamp01(round.phase_t / 0.12))
		GameRound.Phase.RESULTS:
			_draw_results(ds)
	# The white flash of the biggest moments: motion, so reduce motion turns it off.
	var flash_alpha := round.flash.value * 0.5 * clampf(ScreenShake.intensity, 0.0, 1.0)
	if flash_alpha > 0.0:
		ds.draw_rect(Color.WHITE, Vector2.ZERO, Vector2(MiniGame.GAME_W, MiniGame.GAME_H), flash_alpha)


## The results card, revealed in stages (see ResultsPlan). Reduce motion fades instead of sliding and stamping.
func _draw_results(ds: DrawScope) -> void:
	var cx := MiniGame.GAME_W / 2.0
	var t := round.phase_t
	var look := game.look
	var motion := _motion()
	var stage := round.reveal_stage
	ds.draw_rect(Color.BLACK, Vector2.ZERO, Vector2(MiniGame.GAME_W, MiniGame.GAME_H), 0.7 * MathUtil.clamp01(t / 0.25))

	# The card drops in with a bounce (a plain fade with reduce motion).
	var slide := MathUtil.ease_out_back(MathUtil.clamp01(t / ResultsPlan.CARD_IN)) if motion else 1.0
	var card_alpha := MathUtil.clamp01(t / (0.18 if motion else 0.3))
	var top := GameRound.CARD_TOP - (1.0 - slide) * 300.0
	ds.draw_round_rect(Pal.c(Pal.NIGHT), Vector2(GameRound.CARD_X, top), Vector2(GameRound.CARD_W, GameRound.CARD_H), 10.0, card_alpha)
	var hot := round.new_high and stage >= 2
	if hot:
		# A gold rim that breathes for a new high score.
		var glow := 0.5 + 0.5 * sin(round.host_time * 6.0)
		ds.draw_round_rect(Pal.c(Pal.GOLD), Vector2(GameRound.CARD_X - 3.0, top - 3.0), Vector2(GameRound.CARD_W + 6.0, GameRound.CARD_H + 6.0), 12.0, 0.18 + 0.22 * glow, PaStroke.new(6.0))
	ds.draw_round_rect(Pal.c(Pal.GOLD if hot else look.trim), Vector2(GameRound.CARD_X, top), Vector2(GameRound.CARD_W, GameRound.CARD_H), 10.0, card_alpha, PaStroke.new(4.0))
	ArcadeFont.draw_centered(ds, "ROUND OVER", cx, top + 14.0, 3.0, look.glow, MathUtil.clamp01((t - 0.15) / 0.2))

	# Score: counts up, then gives one small pop as it lands on the total.
	if stage >= 1:
		var fade_in := MathUtil.clamp01((t - ResultsPlan.SCORE_AT) / 0.15)
		ArcadeFont.draw_centered(ds, "SCORE", cx, top + 46.0, 2.0, Pal.LAVENDER, fade_in)
		var shown := ResultsPlan.counted_score(round.result_score, t)
		var settle_t := MathUtil.clamp01((t - (ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS)) / 0.25)
		var pop := 1.0
		if motion and settle_t < 1.0 and t > ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS:
			pop = 1.0 + 0.16 * (1.0 - MathUtil.ease_out_cubic(settle_t))
		var size_u := 6.0 * pop
		ArcadeFont.draw_centered(ds, str(shown), cx, top + 64.0 + (42.0 - 7.0 * size_u) / 2.0, size_u, Pal.WHITE, fade_in)

	# Best line, or the NEW HIGH SCORE slam.
	if stage >= 2:
		var since := t - ResultsPlan.BEST_AT
		var land := MathUtil.clamp01(since / 0.28)
		if round.new_high:
			var c: int = GameRound.RAINBOW[int(round.host_time * 10.0) % GameRound.RAINBOW.size()]
			var slam := lerpf(2.4, 1.0, MathUtil.ease_out_back(land)) if motion else 1.0
			var pulse := 1.0 + 0.06 * sin(round.host_time * 10.0)
			var size_u := 2.5 * slam * pulse
			ArcadeFont.draw_centered(ds, "%s NEW HIGH SCORE! %s" % [ArcadeFont.STAR, ArcadeFont.STAR], cx, top + 118.0 + (17.5 - 7.0 * size_u) / 2.0, size_u, c, MathUtil.clamp01(since / 0.1))
		else:
			ArcadeFont.draw_centered(ds, "BEST %d" % round.best, cx, top + 118.0, 2.0, Pal.GRAY, MathUtil.clamp01(since / 0.25))

	# Grade stamp (left) and the ticket count (right).
	if stage >= 3:
		var gt := t - ResultsPlan.GRADE_AT
		ArcadeFont.draw_centered(ds, "GRADE", cx + GameRound.BADGE_DX, top + 150.0, 2.0, Pal.LAVENDER, MathUtil.clamp01(gt / 0.15))
		_draw_grade_stamp(ds, round.grade, Vector2(cx + GameRound.BADGE_DX, top + GameRound.BADGE_DY), gt, motion)
	if stage >= 4:
		var pt := t - ResultsPlan.PRINT_AT
		var a := MathUtil.clamp01(pt / 0.2)
		ArcadeFont.draw_centered(ds, "TICKETS", cx - GameRound.BADGE_DX, top + 150.0, 2.0, Pal.LAVENDER, a)
		ArcadeFont.draw_centered(ds, "%s %d" % [ArcadeFont.TICKET, round.printed], cx - GameRound.BADGE_DX, top + GameRound.BADGE_DY - 17.5, 5.0, Pal.ORANGE, a)
		if game.bonus_tickets > 0:
			ArcadeFont.draw_centered(ds, "INCLUDES +%d BONUS" % game.bonus_tickets, cx, top + 246.0, 2.0, Pal.YELLOW, a)

	# Ticket printer with the strip feeding out of it: it rises into place with the card.
	var slot_y := ResultsPlan.SLOT_Y + ((1.0 - MathUtil.clamp01((t - 0.15) / 0.4)) * 90.0 if motion else 0.0)
	var seg_h := 24.0
	var seg_w := 60.0
	var sx := cx - seg_w / 2.0
	var printing := not round.printing_done and round.printed < round.result_tickets and stage >= 4
	var frac := clampf(round.print_acc, 0.0, 1.0) if printing else 0.0
	var count := round.printed + (1 if printing else 0)
	ds.push()
	ds.clip_rect(0.0, slot_y, MiniGame.GAME_W, MiniGame.GAME_H)
	for k in mini(count, 12):
		var index := k if printing else k + 1
		var y := slot_y + (frac + index - 1.0) * seg_h
		if y > MiniGame.GAME_H:
			break
		var wobble := sin(k * 0.9 + round.host_time * 2.0) * (k * 0.4)
		_ticket(ds, sx + wobble, y, seg_w, seg_h)
	ds.pop()
	var housing_alpha := MathUtil.clamp01((t - 0.1) / 0.3)
	ds.draw_round_rect(Pal.c(Pal.DARKGRAY), Vector2(cx - 80.0, slot_y - 26.0), Vector2(160.0, 30.0), 6.0, housing_alpha)
	ds.draw_rect(Pal.c(Pal.BLACK), Vector2(cx - 38.0, slot_y - 4.0), Vector2(76.0, 6.0), housing_alpha)
	var lamp_on := printing and int(round.host_time * 10.0) % 2 == 0
	ds.draw_circle(Pal.c(Pal.LIME if lamp_on else Pal.DARKGREEN), 5.0, Vector2(cx + 64.0, slot_y - 12.0), housing_alpha)
	ArcadeFont.draw_centered(ds, "TICKETS", cx - 10.0, slot_y - 20.0, 2.0, Pal.LIGHTGRAY, housing_alpha, false, false)
	if (stage < 4 and t > GameRound.SKIP_AFTER) or printing:
		ArcadeFont.draw_centered(ds, "TAP TO SKIP", cx, 610.0, 2.0, Pal.WHITE, 0.5 + 0.5 * sin(round.host_time * 6.0))


## The grade badge, [param t] seconds after the stamp began: it slams down from big (a plain fade
## with reduce motion), a ring rolls outward where it lands, and an S shimmers gold.
func _draw_grade_stamp(ds: DrawScope, g: int, c: Vector2, t: float, motion: bool) -> void:
	var p := MathUtil.clamp01(t / ResultsPlan.STAMP_SECONDS)
	var s := lerpf(2.8, 1.0, MathUtil.ease_out_cubic(p)) if motion else 1.0
	var a := MathUtil.clamp01(t / 0.08)
	var base := Pal.mix(Pal.GOLD, Pal.WHITE, 0.5 + 0.5 * sin(round.host_time * 7.0)) if g == ResultsPlan.Grade.S else ResultsPlan.grade_argb(g)
	if motion and t > ResultsPlan.STAMP_SECONDS:
		var r := (t - ResultsPlan.STAMP_SECONDS) / 0.5
		if r < 1.0:
			ds.draw_circle(Pal.c(base), GameRound.BADGE_R + 50.0 * MathUtil.ease_out_cubic(r), c, (1.0 - r) * 0.55, PaStroke.new(3.0))
	ds.push()
	ds.rotate_deg(-10.0 if motion else 0.0, c)
	ds.draw_circle(Pal.c(Pal.NIGHT), GameRound.BADGE_R * s, c, a)
	ds.draw_circle(Pal.c(base), GameRound.BADGE_R * s, c, a, PaStroke.new(4.0 * s))
	ds.draw_circle(Pal.c(base), (GameRound.BADGE_R - 7.0) * s, c, a * 0.45, PaStroke.new(1.5 * s))
	ArcadeFont.draw_centered(ds, ResultsPlan.grade_letter(g), c.x, c.y - 17.5 * s, 5.0 * s, base, a)
	ds.pop()


func _ticket(ds: DrawScope, x: float, y: float, w: float, h: float) -> void:
	ds.draw_rect(Pal.c(Pal.ORANGE), Vector2(x, y), Vector2(w, h - 2.0))
	ds.draw_rect(Pal.c(Pal.GOLD), Vector2(x + 4.0, y + 3.0), Vector2(w - 8.0, h - 8.0), 1.0, PaStroke.new(2.0))
	ds.draw_rect(Pal.c(Pal.NIGHT), Vector2(x - 2.0, y + h / 2.0 - 4.0), Vector2(5.0, 6.0))
	ds.draw_rect(Pal.c(Pal.NIGHT), Vector2(x + w - 3.0, y + h / 2.0 - 4.0), Vector2(5.0, 6.0))
	for i in 6:
		ds.draw_rect(Pal.c(Pal.shade(Pal.ORANGE, 0.6)), Vector2(x + 3.0 + i * 10.0, y + h - 3.0), Vector2(5.0, 2.0))
	ArcadeFont.draw_centered(ds, ArcadeFont.STAR, x + w / 2.0, y + 6.0, 1.6, Pal.DARKRED, 1.0, false, false)


# ------------------------------------------------------------------ cards and buttons (until the UI kit's widgets)

func _button(ds: DrawScope, label: String, center: Vector2, unit: float, color: int, action: Callable, enabled: bool = true, alpha: float = 1.0) -> void:
	var lines := label.split("\n")
	var tw := 0.0
	for line in lines:
		tw = maxf(tw, ArcadeFont.width(line, unit))
	var lh := ArcadeFont.height(unit)
	var bw := tw + unit * 10.0
	var bh := lh * lines.size() + unit * 2.5 * (lines.size() - 1) + unit * 7.0
	var r := Rect2(center - Vector2(bw, bh) / 2.0, Vector2(bw, bh))
	var lip := unit * 1.2
	var c := color if enabled else Pal.GRAY
	ds.draw_round_rect(Pal.c(Pal.shade(c, 0.55)), r.position + Vector2(0, lip), r.size, unit * 3.0, alpha)
	ds.draw_round_rect(PaBrush.vertical([Pal.c(Pal.mix(c, Pal.WHITE, 0.25)), Pal.c(c)], r.position.y, r.end.y), r.position, r.size, unit * 3.0, alpha)
	var y := r.position.y + unit * 3.5
	for line in lines:
		ArcadeFont.draw_centered(ds, line, center.x, y, unit, Pal.WHITE, alpha)
		y += lh + unit * 2.5
	if enabled:
		_buttons.append([r, action])


## The intro card: title, how to play, round length, best and START (build-13's IntroCard).
func _draw_intro_card(ds: DrawScope) -> void:
	var e := MathUtil.clamp01(_intro_t / 0.82)
	ds.draw_rect(Pal.c(0xAA07050E), Vector2.ZERO, size, MathUtil.clamp01(e * 4.0) * (0xAA / 255.0))
	var glow := game.look.glow
	var card_w := size.x - 40.0
	var lines := game.instructions
	var h := 20.0 + 7.0 * 4.0 + 16.0 + lines.size() * (14.0 + 8.0) + 8.0 + 14.0 + 6.0 + 21.0 + 18.0 + 4.0 * 7.0 + 28.0 + 20.0
	var top := (size.y - h) / 2.0
	var k := MathUtil.ease_out_back(MathUtil.clamp01(e / 0.55))
	var rise := (1.0 - k) * 40.0 if _motion() else 0.0
	var r := Rect2(20.0, top + rise, card_w, h)
	var a := MathUtil.clamp01(e * 5.0)
	ds.draw_round_rect(Pal.c(glow), r.position - Vector2(6, 6), r.size + Vector2(12, 12), 30.0, 0.12 * a)
	ds.draw_round_rect(PaBrush.vertical([Pal.c(Pal.PLUM), Pal.c(Pal.NIGHT)], r.position.y, r.end.y), r.position, r.size, 24.0, a)
	ds.draw_round_rect(PaBrush.vertical([Pal.c(glow), Pal.c(Pal.shade(glow, 0.45))], r.position.y, r.end.y), r.position, r.size, 24.0, a, PaStroke.new(2.0))
	var cx := size.x / 2.0
	var y := r.position.y + 20.0
	ArcadeFont.draw_centered(ds, game.title, cx, y, minf(4.0, (card_w - 40.0) / maxf(ArcadeFont.width(game.title, 1.0), 1.0)), glow, a)
	y += 7.0 * 4.0 + 16.0
	for line in lines:
		ArcadeFont.draw_centered(ds, line, cx, y, minf(2.0, (card_w - 40.0) / maxf(ArcadeFont.width(line, 1.0), 1.0)), Pal.WHITE, a)
		y += 14.0 + 8.0
	y += 8.0
	ArcadeFont.draw_centered(ds, "%d SECOND ROUND" % int(game.round_seconds), cx, y, 2.0, Pal.LAVENDER, a)
	y += 14.0 + 6.0
	ArcadeFont.draw_centered(ds, "BEST: %d" % round.best, cx, y, 3.0, Pal.YELLOW, a)
	y += 21.0 + 18.0
	_button(ds, "%s START" % ArcadeFont.PLAY, Vector2(cx, y + 28.0), 4.0, Pal.GREEN, round.start_round, true, a)


## The pause card (build-13's PauseCard).
func _draw_pause_card(ds: DrawScope) -> void:
	var e := MathUtil.clamp01(_pause_t / 0.52)
	ds.draw_rect(Pal.c(0xCC08060F), Vector2.ZERO, size, MathUtil.clamp01(e * 4.0) * (0xCC / 255.0))
	var cx := size.x / 2.0
	var y := size.y / 2.0 - 110.0
	ArcadeFont.draw_centered(ds, "PAUSED", cx, y, 6.0, Pal.YELLOW, MathUtil.clamp01(e * 3.0))
	_button(ds, "%s RESUME" % ArcadeFont.PLAY, Vector2(cx, y + 42.0 + 24.0 + 28.0), 4.0, Pal.GREEN, round.resume, true, MathUtil.clamp01(e * 3.0))
	_button(ds, "QUIT ROUND", Vector2(cx, y + 42.0 + 24.0 + 56.0 + 16.0 + 24.0), 3.0, Pal.RED, round.quit_round, true, MathUtil.clamp01(e * 3.0))
	ArcadeFont.draw_centered(ds, "QUITTING FORFEITS THIS ROUND", cx, y + 42.0 + 24.0 + 56.0 + 16.0 + 48.0 + 10.0, 2.0, Pal.GRAY, MathUtil.clamp01(e * 2.0), true)


## Under the results card: the currency counter (climbing as tickets land), then PLAY AGAIN and EXIT
## popping in when printing is done.
func _draw_results_controls(ds: DrawScope) -> void:
	var e := MathUtil.clamp01(_controls_t / 0.52)
	var cx := size.x / 2.0
	var bottom := size.y - _bottom_inset - 16.0
	var ready := round.printing_done
	var tokens := services.repo.state().tokens if services != null else 0
	var row_y := bottom - 70.0 - 10.0 - 40.0
	var box := Rect2(cx - 110.0, row_y, 220.0, 40.0)
	ds.draw_round_rect(Pal.c(Pal.DEEP), box.position, box.size, 14.0, 0.8 * e)
	ds.draw_round_rect(Pal.c(Pal.LAVENDER), box.position, box.size, 14.0, 0.35 * e, PaStroke.new(1.0))
	ArcadeFont.draw(ds, "%s %d" % [ArcadeFont.TOKEN, tokens], box.position.x + 16.0, row_y + 12.0, 2.5, Pal.GOLD, e)
	ArcadeFont.draw(ds, "%s %d" % [ArcadeFont.TICKET, round.tickets_before + round.landed], cx + 16.0, row_y + 12.0, 2.5, Pal.ORANGE, e)
	if ready:
		var can := tokens > 0
		_button(ds, "PLAY AGAIN\n1 TOKEN" if can else "NO TOKENS\nLEFT", Vector2(cx - 62.0, bottom - 35.0), 3.0, Pal.GREEN, func() -> void: round.play_again(), can)
		_button(ds, "EXIT", Vector2(cx + 92.0, bottom - 35.0), 3.0, Pal.PURPLE, round.leave_results)


## Tickets flying from the printer's slot into the counter.
func _draw_flights(ds: DrawScope) -> void:
	if round.phase != GameRound.Phase.RESULTS:
		return
	var from := Vector2(round.gx + MiniGame.GAME_W / 2.0 * round.gs, round.gy + (ResultsPlan.SLOT_Y - 4.0) * round.gs)
	var to := Vector2(size.x / 2.0 + 24.0, size.y - _bottom_inset - 16.0 - 70.0 - 10.0 - 20.0)
	for f: Array in round.flights:
		var p := round.flight_progress(f)
		if p < 0.0 or p >= 1.0:
			continue
		var e := MathUtil.ease_out_cubic(p)
		var pos := from.lerp(to, e) + Vector2(0, -90.0 * sin(p * PI))
		ArcadeFont.draw_centered(ds, ArcadeFont.TICKET, pos.x, pos.y - 8.0, 2.5, Pal.ORANGE, 1.0 - 0.3 * p)


func _draw_close_button(ds: DrawScope) -> void:
	var ins := Display.insets_dp(get_window())
	var c := Vector2(ins.x + 8.0 + 24.0, ins.y + 8.0 + 24.0)
	ds.draw_circle(Pal.c(Pal.shade(Pal.RED, 0.55)), 24.0, c + Vector2(0, 3))
	ds.draw_circle(PaBrush.radial([Pal.c(Pal.mix(Pal.RED, Pal.WHITE, 0.3)), Pal.c(Pal.RED)], c - Vector2(6, 8), 30.0), 24.0, c)
	ds.draw_line(Color.WHITE, c - Vector2(8, 8), c + Vector2(8, 8), 4.0, true)
	ds.draw_line(Color.WHITE, c + Vector2(-8, 8), c + Vector2(8, -8), 4.0, true)
	_buttons.append([Rect2(c - Vector2(24, 24), Vector2(48, 48)), round.on_exit_pressed])
