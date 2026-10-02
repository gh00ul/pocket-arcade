class_name FishingGame
extends BaseMiniGame
## games/fishing/FishingGame.kt: Gone Fishing: hold on the pond to wind up a cast (the power meter
## swings up and down, the finger's position aims), let go to cast. Fish swim about the pond,
## notice the lure, nibble and then bite: crank the reel within the strike window to set the hook.
## Then wind the fish in by drawing circles on the reel, easing off when it runs so the line doesn't
## snap and keeping it tight when it rests so it doesn't throw the hook. A landed fish scores
## weight × species value. The knobs are in [FishingTuning]; the reel is a [Crank].

const K := preload("res://scripts/games/fishing/fishing_tuning.gd")

## What the player's line is doing.
enum CastPhase { IDLE, CHARGE, FLIGHT, WAIT, FIGHT, LANDING, RECOVER }
## What one fish is doing.
enum FishMode { SWIM, APPROACH, NIBBLE, BITE, HOOKED, LANDED, GONE }
## The rhythm of a hooked fish: resting, thrashing (a warning), then running with the line.
enum Pull { REST, WARN, RUN }

# The pond, in world units (y up, z towards the viewer; the water is at y = 0).
const POND_CX := 180.0
const POND_CZ := 270.0
const POND_RX := 200.0
const POND_RZ := 290.0
const BED_Y := -46.0
## Where the line meets the water below the rod: the dock's edge.
const DOCK_X := 180.0
const DOCK_Z := 548.0
## Casts land at least this far inside the bank.
const EDGE_MARGIN := 14.0
# The reel on screen, in field units.
const REEL_CX := 266.0
const REEL_CY := 552.0
const REEL_R := 58.0
## A finger landing this near the reel's centre takes the crank.
const REEL_GRAB_R := 92.0
const REEL_HUB_R := 16.0
## The old boot's slot, after the fish.
const BOOT := 10
const BOOT_SPECIES := 4
const SLOTS := 11
const RIPPLES := 16
const PADS := 8
## Radians of crank per reel click.
const REEL_TICK := 0.9
const SEP_RADIUS := 28.0
const FLOCK_RADIUS := 70.0

# Look (presentation only): the low sun, and how many of each little effect there are.
const SUN_X := 330.0
const SUN_Y := 178.0
const SUN_Z := -440.0
const SPLASHES := 3
const SPLASH_LIFE := 0.8
const DROPLETS := 9
const FIREFLIES := 12
const GLITTER := 26
## Strengths of the sun's halo, the beams of light and the mist over the far water.
const HALO_ALPHA := 0.5
const SHAFT_ALPHA := 0.11
const MIST_ALPHA := 0.17

const SPLASH_COLORS := [Pal.WHITE, Pal.CYAN, Pal.SKY, 0xFFBFF4FF]
const GOLD_COLORS := [Pal.GOLD, Pal.YELLOW, Pal.WHITE, Pal.ORANGE]
const ATTRACT_FISH: Array[int] = [Pal.ORANGE, 0xFF6E8A2A, Pal.GOLD]
## The grass square the pond is cut out of.
const BANK_X0 := -480.0
const BANK_X1 := 840.0
const BANK_Z0 := -370.0
const BANK_Z1 := 910.0

# Scenery (Kotlin's private object Scenery).
## Trees on the far bank: x, z, height.
const TREES: Array[float] = [
	-150.0, -90.0, 150.0, -60.0, -140.0, 175.0, 40.0, -110.0, 140.0, 120.0, -150.0, 190.0, 215.0, -120.0, 150.0,
	300.0, -95.0, 170.0, 400.0, -130.0, 185.0, 500.0, -80.0, 150.0, -40.0, 20.0, 120.0, 430.0, 30.0, 130.0,
]
## Reed clumps round the far rim: angles (radians) on the pond's ellipse.
const REEDS: Array[float] = [3.3, 3.6, 3.95, 4.3, 4.75, 5.1, 5.5, 5.85, 6.1, 2.9, 0.3]


## One fish (or the boot) in the pond's fixed pool.
class Fish:
	extends RefCounted
	var species := 0
	var mode := 6  # FishMode.GONE
	var x := 0.0
	var y := -20.0
	var z := 0.0
	var vx := 0.0
	var vz := 0.0
	var heading := 0.0
	var weight := 1.0
	var scale := 1.0
	var wx := 0.0
	var wz := 0.0
	var wander_t := 0.0
	var spook_t := 0.0
	var flee_x := 0.0
	var flee_z := 0.0
	## Timer for the current mode (nibble gaps, the bite window, approach, respawn).
	var mode_t := 0.0
	var nibbles := 0
	var depth_phase := 0.0
	var wiggle := 0.0


var _fish: Array[Fish] = []

var _phase: int = CastPhase.IDLE
var _phase_t := 0.0

# The cast being wound up.
var _cast_id := -1
var _aim_fx := GAME_W / 2.0
var _charge_t := 0.0
var _power := 0.0

# The lure: in flight, floating, or dragged about by a fish.
var _lure_x := DOCK_X
var _lure_y := 0.0
var _lure_z := DOCK_Z
var _from_x := 0.0
var _from_y := 0.0
var _from_z := 0.0
var _to_x := 0.0
var _to_z := 0.0
var _flight_dur := 1.0
var _suitor := -1
var _strike_acc := 0.0
var _early_acc := 0.0
var _boot_ignored := false
var _bob_dip := 0.0

# The fight.
var _hooked := -1
var _tension := 0.0
var _pull: int = Pull.REST
var _pull_t := 0.0
var _run_dir := 1.0
var _stamina := 1.0
var _line_d := 0.0
var _line_ang := 0.0
var _strain_t := 0.0
var _slack_t := 0.0
var _fight_t := 0.0
var _land_x := 0.0
var _land_y := 0.0
var _land_z := 0.0

var _crank := Crank.new(REEL_CX, REEL_CY, REEL_HUB_R)
var _tick_acc := 0.0
var _snap_flash := 0.0
var _settle_t := 0.0
var _rise_t := 0.0
var _last_catch := 0
var _catch_name := ""
var _catch_show_t := 0.0

# Tallies for the tests and the round's story.
var _casts := 0
var _landed := 0
var _snaps := 0
var _thrown := 0
var _missed := 0
var _too_soon := 0
var _failsafe_trips := 0

# Ripples on the water.
var _rx := PackedFloat64Array()
var _rz := PackedFloat64Array()
var _r_age := PackedFloat64Array()
var _r_life := PackedFloat64Array()
var _r_size := PackedFloat64Array()
var _r_on := PackedByteArray()

# Presentation only: splash sprays. Written by the game's splash events, read by render(); a spray's
# droplets fly from hashes of its seed, never from the game's random numbers.
var _splash_x := PackedFloat64Array()
var _splash_z := PackedFloat64Array()
var _splash_age := PackedFloat64Array()
var _splash_power := PackedFloat64Array()
var _splash_seed := PackedInt32Array()
var _splash_next := 0
var _splash_count := 0

# Lily pads (placed per round; they only decorate).
var _pad_x := PackedFloat64Array()
var _pad_z := PackedFloat64Array()
var _pad_s := PackedFloat64Array()
var _pad_a := PackedFloat64Array()

# ---------------------------------------------------------------- camera and fixed points

var _stage := Stage3D.new(int(GAME_W), int(GAME_H))
## Kotlin's shared `pt` scratch: the last point projected to the field (kept when a projection fails,
## as the Kotlin array kept its old values).
var _pt := Vector3.ZERO

## The rod: butt near the viewer's hands at the bottom right, tip up over the dock.
var _butt_x := 0.0
var _butt_y := 196.0
var _butt_z := 0.0
var _tip_x := 0.0
var _tip_y := 214.0
var _tip_z := 0.0
## Where a landed fish is held up for the camera.
var _show_x := 0.0
var _show_y := 120.0
var _show_z := 0.0


func _init() -> void:
	id = "fishing"
	title = "GONE FISHING"
	marquee = "FISH"
	instructions = PackedStringArray([
		"HOLD ON THE POND TO CAST",
		"LET GO AT THE RIGHT POWER",
		"BOBBER DIVES? CRANK FAST!",
		"CIRCLE THE REEL TO REEL IN",
		"EASE OFF WHEN IT PULLS!",
	])
	look = MiniGame.CabinetLook.new(Pal.TEAL, Pal.YELLOW, Pal.SKY, MiniGame.CabinetShape.FISHING)
	round_seconds = K.ROUND_SECONDS
	for i in SLOTS:
		_fish.append(Fish.new())
	# (Packed arrays held in an Array are copies: each is resized by name.)
	_rx.resize(RIPPLES)
	_rz.resize(RIPPLES)
	_r_age.resize(RIPPLES)
	_r_life.resize(RIPPLES)
	_r_size.resize(RIPPLES)
	_r_on.resize(RIPPLES)
	_splash_x.resize(SPLASHES)
	_splash_z.resize(SPLASHES)
	_splash_age.resize(SPLASHES)
	_splash_power.resize(SPLASHES)
	_splash_age.fill(SPLASH_LIFE)
	_splash_seed.resize(SPLASHES)
	_pad_x.resize(PADS)
	_pad_z.resize(PADS)
	_pad_s.resize(PADS)
	_pad_a.resize(PADS)
	_stage.look(180.0, 290.0, 800.0, 180.0, 0.0, 250.0, 48.0)
	var o: Vector2 = _stage.touch_to_plane(330.0, 700.0, _butt_y)
	_butt_x = o.x
	_butt_z = o.y
	o = _stage.touch_to_plane(204.0, 236.0, _tip_y)
	_tip_x = o.x
	_tip_z = o.y
	o = _stage.touch_to_plane(180.0, 250.0, _show_y)
	_show_x = o.x
	_show_z = o.y


## build-13's custom hall cabinet (FishingCabinet, the tub) is a later step: until the hall's
## cabinet kit is in, the hall builds the built-in FISHING shape.
func cabinet() -> Object:
	return null


## Projects a world point to the field into _pt; false (and _pt unchanged) when it is behind the eye.
func _to_field(x: float, y: float, z: float) -> bool:
	var p: Variant = _stage.to_field(x, y, z)
	if p == null:
		return false
	_pt = p
	return true


# ---------------------------------------------------------------- round flow

func reset() -> void:
	_phase = CastPhase.IDLE
	_phase_t = 0.0
	_cast_id = -1
	_aim_fx = GAME_W / 2.0
	_charge_t = 0.0
	_power = 0.0
	_lure_x = DOCK_X
	_lure_y = 0.0
	_lure_z = DOCK_Z
	_splash_age.fill(SPLASH_LIFE)
	_suitor = -1
	_strike_acc = 0.0
	_early_acc = 0.0
	_boot_ignored = false
	_bob_dip = 0.0
	_hooked = -1
	_tension = 0.0
	_pull = Pull.REST
	_pull_t = 0.0
	_stamina = 1.0
	_line_d = 0.0
	_line_ang = 0.0
	_strain_t = 0.0
	_slack_t = 0.0
	_fight_t = 0.0
	_crank.cancel()
	_tick_acc = 0.0
	_snap_flash = 0.0
	_settle_t = 0.0
	_rise_t = 1.0
	_last_catch = 0
	_catch_show_t = 0.0
	_casts = 0
	_landed = 0
	_snaps = 0
	_thrown = 0
	_missed = 0
	_too_soon = 0
	_failsafe_trips = 0
	_r_on.fill(0)
	for i in K.FISH_SLOTS:
		_spawn_fish(_fish[i], true)
	_place_boot(_fish[BOOT])
	for i in PADS:
		# Pads float round the edges, clear of the dock.
		var a := rng.range_f(PI * 1.05, PI * 1.95)
		if i % 2 != 0:
			a += rng.range_f(-1.2, 1.2)
		var d := rng.range_f(0.72, 0.9)
		_pad_x[i] = POND_CX + cos(a) * POND_RX * d
		_pad_z[i] = minf(POND_CZ + sin(a) * POND_RZ * d, DOCK_Z - 90.0)
		_pad_s[i] = rng.range_f(18.0, 30.0)
		_pad_a[i] = rng.range_f(0.0, TAU)


func tickets_for(p_score: int) -> int:
	@warning_ignore("integer_division")
	return K.BASE_TICKETS + p_score / K.POINTS_PER_TICKET


func is_settled() -> bool:
	return _phase == CastPhase.IDLE


## Time's up: nothing new is cast or hooked. A fish already being lifted out finishes and scores; a
## fish still fighting gets away and the line is wound in.
func on_time_up() -> void:
	_cast_id = -1
	if _phase == CastPhase.CHARGE:
		_phase = CastPhase.IDLE
	elif _phase == CastPhase.FLIGHT or _phase == CastPhase.WAIT:
		_release_suitor()
		_recover()
	elif _phase == CastPhase.FIGHT:
		var f := _fish[_hooked]
		_spook(f, DOCK_X, DOCK_Z, K.SPOOK_SECONDS)
		_hooked = -1
		_to_field(f.x, 0.0, f.z)
		popups.add("TIME!", _pt.x, _pt.y - 20.0, Pal.GRAY, 3.0)
		_recover()


## Lets go of the cast being wound up (no cast is made) and of the reel.
func cancel_input() -> void:
	_cast_id = -1
	if _phase == CastPhase.CHARGE:
		_phase = CastPhase.IDLE
		_power = 0.0
	_crank.cancel()


func on_touch(type: int, pid: int, x: float, y: float, time_ms: int) -> void:
	if type == TouchType.DOWN:
		var dx := x - REEL_CX
		var dy := y - REEL_CY
		if dx * dx + dy * dy <= REEL_GRAB_R * REEL_GRAB_R:
			_crank.grab(pid, x, y, time_ms)
		elif _phase == CastPhase.IDLE and _cast_id < 0 and not time_up:
			_cast_id = pid
			_aim_fx = x
			_charge_t = 0.0
			_power = 0.0
			_phase = CastPhase.CHARGE
			play(Sfx.BLIP, 0.35, 0.8)
	elif type == TouchType.MOVE:
		if pid == _cast_id:
			_aim_fx = x
		_crank.move(pid, x, y, time_ms)
	elif type == TouchType.UP:
		if pid == _cast_id:
			_aim_fx = x
			_cast_id = -1
			_release_cast()
		_crank.release(pid)


func _release_cast() -> void:
	if _phase != CastPhase.CHARGE:
		return
	if time_up:
		_phase = CastPhase.IDLE
		return
	var spot := _landing_spot(_aim_fx, _power)
	_to_x = spot.x
	_to_z = spot.y
	_from_x = _tip_x
	_from_y = _tip_y
	_from_z = _tip_z
	_lure_x = _from_x
	_lure_y = _from_y
	_lure_z = _from_z
	var dist := _hypot(_to_x - DOCK_X, _to_z - DOCK_Z)
	_flight_dur = K.FLIGHT_BASE + dist * K.FLIGHT_PER_UNIT
	_phase = CastPhase.FLIGHT
	_phase_t = 0.0
	_casts += 1
	play(Sfx.CAST, 0.85, 0.85 + _power * 0.4)
	fx.haptics.tick()


## Where a cast aimed by a finger at field x [param fx_] with [param pow] power lands: world (x, z).
func _landing_spot(fx_: float, pow: float) -> Vector2:
	var a := _aim_angle(fx_)
	var dist := K.MIN_CAST + MathUtil.clamp01(pow) * (K.MAX_CAST - K.MIN_CAST)
	var dx := sin(a)
	var dz := -cos(a)
	var d := minf(dist, _reach(dx, dz) - EDGE_MARGIN)
	return Vector2(DOCK_X + dx * d, DOCK_Z + dz * d)


func _aim_angle(fx_: float) -> float:
	return clampf((fx_ - GAME_W / 2.0) / 160.0, -1.0, 1.0) * K.MAX_AIM_DEG * (PI / 180.0)


## Distance from the dock to the bank along direction ([param dx], [param dz]) (a unit vector).
func _reach(dx: float, dz: float) -> float:
	var px := (DOCK_X - POND_CX) / POND_RX
	var pz := (DOCK_Z - POND_CZ) / POND_RZ
	var ux := dx / POND_RX
	var uz := dz / POND_RZ
	var a := ux * ux + uz * uz
	var b := 2.0 * (px * ux + pz * uz)
	var c := px * px + pz * pz - 1.0
	var disc := b * b - 4.0 * a * c
	if disc <= 0.0 or a <= 0.0:
		return 0.0
	return maxf((-b + sqrt(disc)) / (2.0 * a), 0.0)


# ---------------------------------------------------------------- simulation

func step(dt: float) -> void:
	_crank.age(dt)
	var turn := _crank.take()
	_reel_clicks(turn)
	_bob_dip = maxf(_bob_dip - dt * 2.5, 0.0)
	_snap_flash = maxf(_snap_flash - dt * 2.0, 0.0)
	_catch_show_t = maxf(_catch_show_t - dt, 0.0)
	_step_ripples(dt)
	for i in SPLASHES:
		if _splash_age[i] < SPLASH_LIFE:
			_splash_age[i] += dt
	if time_up:
		_settle_t += dt
		if _settle_t > K.SETTLE_FAILSAFE and _phase != CastPhase.IDLE:
			_failsafe_trips += 1
			if _hooked >= 0 and _fish[_hooked].mode != FishMode.GONE:
				_spook(_fish[_hooked], DOCK_X, DOCK_Z, 1.0)
			_hooked = -1
			_release_suitor()
			_phase = CastPhase.IDLE
	match _phase:
		CastPhase.IDLE:
			pass
		CastPhase.CHARGE:
			_charge_t += dt
			var f := _charge_t / K.CHARGE_PERIOD
			_power = 1.0 - absf(1.0 - 2.0 * (f - floorf(f)))
		CastPhase.FLIGHT:
			_step_flight(dt)
		CastPhase.WAIT:
			_step_wait(dt, turn)
		CastPhase.FIGHT:
			_step_fight(dt, turn)
		CastPhase.LANDING:
			_phase_t += dt
			if _phase_t >= K.LAND_SECONDS:
				_score_catch()
		CastPhase.RECOVER:
			_phase_t += dt
			var k := MathUtil.clamp01(_phase_t / K.RECOVER_SECONDS)
			_lure_x = _from_x + (_tip_x - _from_x) * k
			_lure_y = _from_y + (_tip_y - 30.0 - _from_y) * k
			_lure_z = _from_z + (_tip_z - _from_z) * k
			if _phase_t >= K.RECOVER_SECONDS:
				_phase = CastPhase.IDLE
	_step_fish(dt)
	# Now and then a fish rises and rings the surface, showing where they are.
	_rise_t -= dt
	if _rise_t <= 0.0:
		_rise_t = rng.range_f(0.8, 1.8)
		var i := rng.next_int_until(K.FISH_SLOTS)
		var f := _fish[i]
		if f.mode == FishMode.SWIM:
			_add_ripple(f.x, f.z, 16.0 + f.scale * 6.0, 1.3)


## The reel clicks as it turns, faster clicks pitched higher.
func _reel_clicks(turn: float) -> void:
	_tick_acc += absf(turn)
	if _tick_acc >= REEL_TICK:
		_tick_acc = fmod(_tick_acc, REEL_TICK)
		var busy := _phase == CastPhase.WAIT or _phase == CastPhase.FIGHT
		var speed := minf(absf(_crank.rate) / K.CRANK_REF, 2.0)
		play(Sfx.REEL, 0.45 if busy else 0.25, 0.7 + speed * 0.45)
		# The click under your thumb, while a lure is out (a spinning empty reel stays quiet).
		if busy:
			fx.haptics.soft()


func _step_flight(dt: float) -> void:
	_phase_t += dt
	var k := MathUtil.clamp01(_phase_t / _flight_dur)
	var dist := _hypot(_to_x - DOCK_X, _to_z - DOCK_Z)
	var arc := 50.0 + dist * 0.3
	_lure_x = _from_x + (_to_x - _from_x) * k
	_lure_z = _from_z + (_to_z - _from_z) * k
	_lure_y = _from_y * (1.0 - k) + 4.0 * k * (1.0 - k) * arc
	if k >= 1.0:
		_splash_down()


func _splash_down() -> void:
	_phase = CastPhase.WAIT
	_phase_t = 0.0
	_lure_x = _to_x
	_lure_y = 0.0
	_lure_z = _to_z
	_suitor = -1
	_strike_acc = 0.0
	_early_acc = 0.0
	_boot_ignored = false
	_add_ripple(_lure_x, _lure_z, 44.0, 1.2)
	_add_ripple(_lure_x, _lure_z, 24.0, 0.8)
	_splash_at(_lure_x, _lure_z, 1.0)
	if _to_field(_lure_x, 0.0, _lure_z):
		particles.burst(_pt.x, _pt.y, 16, 40.0, 150.0, SPLASH_COLORS, 0.55, 3.5, 380.0, 2.0, Particles.SQUARE, PI * 1.1, PI * 1.9)
	play(Sfx.SPLASH, 0.75, 1.1)
	var r2 := K.SPLASH_SPOOK_RADIUS * K.SPLASH_SPOOK_RADIUS
	for i in K.FISH_SLOTS:
		var f := _fish[i]
		if f.mode != FishMode.SWIM:
			continue
		var dx := f.x - _lure_x
		var dz := f.z - _lure_z
		if dx * dx + dz * dz < r2:
			_spook(f, _lure_x, _lure_z, 1.2)
	# Landing next to the old boot snags it.
	var b := _fish[BOOT]
	if b.mode == FishMode.SWIM and _hypot(b.x - _lure_x, b.z - _lure_z) < K.BOOT_SNAG_RADIUS:
		_suitor = BOOT
		b.mode = FishMode.NIBBLE
		b.nibbles = 0
		b.mode_t = K.BOOT_SNAG_DELAY


func _step_wait(dt: float, turn: float) -> void:
	_phase_t += dt
	_early_acc = maxf(_early_acc - dt * 0.8, 0.0)
	var s := _suitor
	var biting := s >= 0 and _fish[s].mode == FishMode.BITE
	if biting:
		var f := _fish[s]
		if turn > 0.0:
			_strike_acc += turn
		if _strike_acc >= K.STRIKE_ANGLE:
			_hook(s)
			return
		f.mode_t -= dt
		if f.mode_t <= 0.0:
			_miss_strike(s)
		return
	if turn > 0.0:
		# Winding in with nothing on: the lure comes back (too soon if a fish is nibbling).
		if s >= 0 and _fish[s].mode == FishMode.NIBBLE:
			_early_acc += turn
			if _early_acc >= K.STRIKE_ANGLE:
				_strike_too_soon(s)
		var dx := DOCK_X - _lure_x
		var dz := DOCK_Z - _lure_z
		var d := _hypot(dx, dz)
		var stp := turn * K.RETRIEVE_PER_RAD
		if d - stp <= K.LAND_DIST * 0.8:
			_lure_out()
			return
		_lure_x += dx / d * stp
		_lure_z += dz / d * stp
	if _suitor < 0:
		_find_suitor(dt)
		return
	var f := _fish[_suitor]
	if f.mode == FishMode.APPROACH:
		f.mode_t += dt
		if _hypot(f.x - _lure_x, f.z - _lure_z) < 9.0:
			f.mode = FishMode.NIBBLE
			f.nibbles = K.NIBBLES_MIN + rng.next_int_until(K.NIBBLES_MAX - K.NIBBLES_MIN + 1)
			f.mode_t = rng.range_f(K.NIBBLE_GAP_MIN, K.NIBBLE_GAP_MAX)
		elif f.mode_t > K.APPROACH_TIMEOUT:
			f.mode = FishMode.SWIM
			_suitor = -1
	elif f.mode == FishMode.NIBBLE:
		f.mode_t -= dt
		if f.mode_t <= 0.0:
			if f.nibbles > 0:
				f.nibbles -= 1
				f.mode_t = rng.range_f(K.NIBBLE_GAP_MIN, K.NIBBLE_GAP_MAX)
				_bob_dip = 0.45
				_add_ripple(_lure_x, _lure_z, 18.0, 0.7)
				play(Sfx.BITE, 0.3, 1.6)
			else:
				_bite(f)


## Some fish near the lure decides to go for it.
func _find_suitor(dt: float) -> void:
	var r2 := K.NOTICE_RADIUS * K.NOTICE_RADIUS
	for i in K.FISH_SLOTS:
		var f := _fish[i]
		if f.mode != FishMode.SWIM or f.spook_t > 0.0:
			continue
		var dx := f.x - _lure_x
		var dz := f.z - _lure_z
		if dx * dx + dz * dz > r2:
			continue
		if rng.next_float() < K.APPETITE[f.species] * dt:
			_suitor = i
			f.mode = FishMode.APPROACH
			f.mode_t = 0.0
			return


func _bite(f: Fish) -> void:
	f.mode = FishMode.BITE
	f.mode_t = K.BOOT_WINDOW if f.species == BOOT_SPECIES else K.STRIKE_WINDOW
	_strike_acc = 0.0
	_bob_dip = 1.0
	_add_ripple(_lure_x, _lure_z, 36.0, 1.0)
	play(Sfx.BITE, 1.0, 0.7 if f.species == BOOT_SPECIES else 1.0)
	fx.haptics.tick()
	if _to_field(_lure_x, 0.0, _lure_z):
		popups.add("STRIKE!", _pt.x, _pt.y - 34.0, Pal.YELLOW, 3.5, 0.8)
		particles.burst(_pt.x, _pt.y, 10, 30.0, 110.0, SPLASH_COLORS, 0.45, 3.0, 300.0)


func _miss_strike(s: int) -> void:
	_missed += 1
	var f := _fish[s]
	if f.species == BOOT_SPECIES:
		f.mode = FishMode.SWIM
		_boot_ignored = true
	else:
		_spook(f, _lure_x, _lure_z, K.SPOOK_SECONDS)
	_suitor = -1
	_add_ripple(_lure_x, _lure_z, 30.0, 0.9)
	play(Sfx.SPLASH, 0.35, 1.4)
	if _to_field(_lure_x, 0.0, _lure_z):
		popups.add("MISSED!", _pt.x, _pt.y - 30.0, Pal.LIGHTGRAY, 3.0)


func _strike_too_soon(s: int) -> void:
	_too_soon += 1
	var f := _fish[s]
	if f.species == BOOT_SPECIES:
		f.mode = FishMode.SWIM
		_boot_ignored = true
	else:
		_spook(f, _lure_x, _lure_z, K.SPOOK_SECONDS)
	_suitor = -1
	_early_acc = 0.0
	_add_ripple(_lure_x, _lure_z, 26.0, 0.8)
	if _to_field(_lure_x, 0.0, _lure_z):
		popups.add("TOO SOON!", _pt.x, _pt.y - 30.0, Pal.LIGHTGRAY, 3.0)


## The lure is wound all the way back in: ready to cast again.
func _lure_out() -> void:
	_release_suitor()
	_phase = CastPhase.IDLE
	play(Sfx.BLIP, 0.3, 1.3)


func _release_suitor() -> void:
	var s := _suitor
	if s >= 0:
		var f := _fish[s]
		if f.mode == FishMode.APPROACH or f.mode == FishMode.NIBBLE or f.mode == FishMode.BITE:
			f.mode = FishMode.SWIM
	_suitor = -1


## Winds the lure back to the rod from wherever it is.
func _recover() -> void:
	_from_x = _lure_x
	_from_y = _lure_y
	_from_z = _lure_z
	_phase = CastPhase.RECOVER
	_phase_t = 0.0
	_tension = 0.0
	_strain_t = 0.0
	_slack_t = 0.0


func _spook(f: Fish, px: float, pz: float, seconds: float) -> void:
	f.mode = FishMode.SWIM
	f.spook_t = seconds
	f.flee_x = px
	f.flee_z = pz


func _hook(s: int) -> void:
	var f := _fish[s]
	_hooked = s
	_suitor = -1
	f.mode = FishMode.HOOKED
	_phase = CastPhase.FIGHT
	_phase_t = 0.0
	_fight_t = 0.0
	_line_d = maxf(_hypot(f.x - DOCK_X, f.z - DOCK_Z), K.LAND_DIST + 1.0)
	_line_ang = clampf(atan2(f.x - DOCK_X, DOCK_Z - f.z), -K.MAX_SWAY, K.MAX_SWAY)
	_tension = 0.45
	_pull = Pull.REST
	_pull_t = K.FIRST_REST
	_stamina = 1.0
	_strain_t = 0.0
	_slack_t = 0.0
	play(Sfx.SPLASH, 0.8, 0.9)
	fx.haptics.hit()
	shake.add(0.15)
	_add_ripple(f.x, f.z, 40.0, 1.0)
	if _to_field(f.x, 0.0, f.z):
		popups.add("SNAGGED!" if f.species == BOOT_SPECIES else "HOOKED!", _pt.x, _pt.y - 36.0, Pal.LIME, 3.5)
		particles.burst(_pt.x, _pt.y, 18, 50.0, 170.0, SPLASH_COLORS, 0.6, 3.5, 380.0)


func _step_fight(dt: float, turn: float) -> void:
	_phase_t += dt
	_fight_t += dt
	var f := _fish[_hooked]
	var sp := f.species
	var boot := sp == BOOT_SPECIES
	var pull_k: float = K.PULL[sp]
	if turn > 0.0:
		_line_d -= turn * K.REEL_PER_RAD / (1.0 + f.weight * K.REEL_WEIGHT_K)
	if turn < 0.0:
		_line_d -= turn * K.LET_OUT_PER_RAD
	var c := maxf(_crank.rate / K.CRANK_REF, 0.0)
	var c_out := maxf(-_crank.rate / K.CRANK_REF, 0.0)
	var target: float
	if boot:
		target = K.BOOT_TENSION + K.CRANK_TENSION * c * 1.3
	else:
		var rest_target := K.REST_TENSION + K.CRANK_TENSION * c * (1.0 + f.weight * 0.05)
		if _pull == Pull.REST:
			_line_d += K.REST_DRIFT * pull_k * dt
			target = rest_target
			_pull_t -= dt
			if _pull_t <= 0.0:
				_pull = Pull.WARN
				_pull_t = K.WARN_SECONDS
		elif _pull == Pull.WARN:
			target = rest_target + 0.08
			_pull_t -= dt
			if rng.next_float() < dt * 10.0 and _to_field(f.x, 0.0, f.z):
				particles.burst(_pt.x, _pt.y, 4, 30.0, 120.0, SPLASH_COLORS, 0.4, 3.0, 400.0, 2.0, Particles.SQUARE, PI * 1.15, PI * 1.85)
			if _pull_t <= 0.0:
				_start_run(f)
		else:
			_line_d += K.RUN_SPEED[sp] * _stamina * dt
			_line_ang += _run_dir * K.RUN_SWAY * dt
			target = K.RUN_TENSION + pull_k * K.RUN_PULL_TENSION * _stamina + K.CRANK_TENSION * c * (1.6 + pull_k)
			_pull_t -= dt
			if _pull_t <= 0.0:
				_pull = Pull.REST
				_pull_t = rng.range_f(K.REST_MIN, K.REST_MAX)
				_stamina *= K.STAMINA_DECAY
	target -= K.LET_OUT_RELIEF * c_out
	_line_ang = clampf(_line_ang, -K.MAX_SWAY, K.MAX_SWAY)
	var dx := sin(_line_ang)
	var dz := -cos(_line_ang)
	var max_d := _reach(dx, dz) - EDGE_MARGIN
	if _line_d > max_d:
		_line_d = max_d
		if _pull == Pull.RUN and not boot:
			target += 0.25
	_tension = MathUtil.damp(_tension, maxf(target, 0.0), K.TENSION_RATE, dt)
	# The fish (and the float, dragged along the surface just this side of it).
	f.x = DOCK_X + dx * _line_d
	f.z = DOCK_Z + dz * _line_d
	if not boot:
		f.y = MathUtil.damp(f.y, -3.0 if _pull == Pull.WARN else -9.0 - 8.0 * MathUtil.clamp01(_line_d / 300.0), 4.0, dt)
		f.heading = atan2(dx, dz) + _run_dir * 0.4 if _pull == Pull.RUN else atan2(-dx, -dz) + sin(_fight_t * 7.0) * 0.5
	else:
		f.y = MathUtil.damp(f.y, -12.0, 1.5, dt)
	_lure_x = f.x - dx * 10.0
	_lure_z = f.z - dz * 10.0
	_lure_y = 0.0
	if _tension > K.SNAP_TENSION:
		_strain_t += dt
		if _strain_t > K.SNAP_GRACE:
			_snap_line(f)
			return
	else:
		_strain_t = maxf(_strain_t - dt, 0.0)
	if not boot and _tension < K.SLACK_TENSION:
		_slack_t += dt
		if _slack_t > K.SLACK_GRACE:
			_throw_hook(f)
			return
	else:
		_slack_t = maxf(_slack_t - dt * 2.0, 0.0)
	if _line_d <= K.LAND_DIST:
		_start_landing(f)
		return
	if _fight_t > K.FIGHT_TIMEOUT:
		_failsafe_trips += 1
		_throw_hook(f)


func _start_run(f: Fish) -> void:
	_pull = Pull.RUN
	_pull_t = rng.range_f(K.RUN_MIN, K.RUN_MAX) * (0.7 + K.PULL[f.species])
	# Mostly away from the side it's already on, so it stays out in open water.
	_run_dir = -1.0 if rng.next_float() < 0.5 + _line_ang else 1.0
	_tension += K.YANK * K.PULL[f.species]
	play(Sfx.SPLASH, 0.55, 1.3)
	fx.haptics.tick()
	shake.add(0.08)
	_add_ripple(f.x, f.z, 30.0, 0.9)
	_splash_at(f.x, f.z, 0.55)


func _snap_line(f: Fish) -> void:
	_snaps += 1
	_spook(f, DOCK_X, DOCK_Z, K.SPOOK_SECONDS)
	_hooked = -1
	play(Sfx.LINE_SNAP, 1.0)
	fx.haptics.heavy()
	shake.add(0.45)
	_snap_flash = 1.0
	popups.add("SNAP!", GAME_W / 2.0, 300.0, Pal.RED, 5.0, 1.0)
	# The lure's gone with the fish: a fresh one is tied on at the rod.
	_lure_x = _tip_x
	_lure_y = _tip_y - 30.0
	_lure_z = _tip_z
	_recover()


func _throw_hook(f: Fish) -> void:
	_thrown += 1
	_spook(f, DOCK_X, DOCK_Z, K.SPOOK_SECONDS)
	_hooked = -1
	play(Sfx.SPLASH, 0.6, 1.2)
	fx.haptics.tick()
	_add_ripple(f.x, f.z, 34.0, 1.0)
	_splash_at(f.x, f.z, 0.8)
	if _to_field(f.x, 0.0, f.z):
		popups.add("IT GOT AWAY!", _pt.x, _pt.y - 30.0, Pal.LIGHTGRAY, 3.0)
	_recover()


func _start_landing(f: Fish) -> void:
	_phase = CastPhase.LANDING
	_phase_t = 0.0
	f.mode = FishMode.LANDED
	_land_x = f.x
	_land_y = f.y
	_land_z = f.z
	_tension = 0.0
	play(Sfx.SPLASH, 0.9, 0.8)
	_add_ripple(f.x, f.z, 50.0, 1.2)
	_splash_at(f.x, f.z, 1.5)
	if _to_field(f.x, 0.0, f.z):
		particles.burst(_pt.x, _pt.y, 26, 60.0, 220.0, SPLASH_COLORS, 0.7, 4.0, 420.0, 2.0, Particles.SQUARE, PI * 1.05, PI * 1.95)


## The fish is out of the water: it scores its weight times its species' value.
func _score_catch() -> void:
	var f := _fish[_hooked]
	var sp := f.species
	var pts := _catch_points(sp, f.weight)
	_landed += 1
	_last_catch = pts
	_to_field(_show_x, _show_y, _show_z)
	var sx := _pt.x
	var sy := _pt.y
	var tenths := MathUtil.round_to_int(f.weight * 10.0)
	@warning_ignore("integer_division")
	_catch_name = K.NAMES[sp] + "  " + str(tenths / 10) + "." + str(tenths % 10) + " LB"
	_catch_show_t = 1.8
	if sp == BOOT_SPECIES:
		add_score(pts, sx, sy - 40.0, Pal.TAN)
		popups.add("JUNK!", sx, sy + 10.0, Pal.TAN, 4.0)
		play(Sfx.THUD, 0.9)
		fx.haptics.hit()
	elif sp == 3:
		add_score(pts, sx, sy - 40.0, Pal.GOLD)
		popups.add("GOLDEN!!", sx, sy + 10.0, Pal.GOLD, 5.0, 1.4)
		play(Sfx.JACKPOT)
		fx.haptics.jackpot()
		shake.add(0.5)
		flash.trigger(0.7)
		particles.confetti(0.0, 0.0, GAME_W, 80)
		particles.burst(sx, sy, 40, 80.0, 300.0, GOLD_COLORS, 0.9, 5.0, 0.0, 2.0, Particles.SPARKLE)
	elif pts >= K.BIG_CATCH:
		add_score(pts, sx, sy - 40.0, Pal.LIME)
		popups.add("WHOPPER!", sx, sy + 10.0, Pal.LIME, 4.5, 1.2)
		play(Sfx.CATCH)
		play(Sfx.WIN, 0.7)
		fx.haptics.win()
		shake.add(0.3)
		flash.trigger(0.4)
		particles.confetti(0.0, 0.0, GAME_W, 50)
	else:
		add_score(pts, sx, sy - 40.0, Pal.CYAN)
		play(Sfx.CATCH, 0.9)
		fx.haptics.hit()
		particles.burst(sx, sy, 24, 60.0, 220.0, SPLASH_COLORS, 0.7, 4.0, 200.0)
	f.mode = FishMode.GONE
	f.mode_t = K.RESPAWN_SECONDS * 3.0 if sp == BOOT_SPECIES else K.RESPAWN_SECONDS
	_hooked = -1
	_phase = CastPhase.IDLE
	_lure_x = _tip_x
	_lure_y = _tip_y - 30.0
	_lure_z = _tip_z


static func _catch_points(species: int, weight: float) -> int:
	return maxi(MathUtil.round_to_int(weight * K.VALUE[species]), 1)


# ---------------------------------------------------------------- the fish

func _roll_species() -> int:
	var total := 0.0
	for w: float in K.SPAWN_ODDS:
		total += w
	var r := rng.next_float() * total
	for i in K.SPAWN_ODDS.size():
		r -= K.SPAWN_ODDS[i]
		if r < 0.0:
			return i
	return 0


## A new fish anywhere in the pond ([param anywhere]) or swimming in from the far side.
func _spawn_fish(f: Fish, anywhere: bool) -> void:
	var sp := _roll_species()
	f.species = sp
	f.weight = rng.range_f(K.WEIGHT_MIN[sp], K.WEIGHT_MAX[sp])
	var mid: float = (K.WEIGHT_MIN[sp] + K.WEIGHT_MAX[sp]) / 2.0
	f.scale = 0.8 + 0.4 * clampf(f.weight / mid - 0.5, 0.0, 1.0)
	var a := rng.range_f(0.0, TAU) if anywhere else rng.range_f(PI * 1.15, PI * 1.85)
	var d := sqrt(rng.next_float()) * 0.8 if anywhere else rng.range_f(0.75, 0.85)
	f.x = POND_CX + cos(a) * POND_RX * d
	f.z = minf(POND_CZ + sin(a) * POND_RZ * d, DOCK_Z - 110.0)
	f.y = -18.0
	var h := rng.range_f(0.0, TAU)
	f.vx = sin(h) * K.SPEED[sp]
	f.vz = cos(h) * K.SPEED[sp]
	f.heading = h
	f.wander_t = 0.0
	f.spook_t = 0.0
	f.mode_t = 0.0
	f.mode = FishMode.SWIM
	f.depth_phase = rng.range_f(0.0, TAU)
	f.wiggle = rng.range_f(0.0, TAU)


## The old boot lies somewhere on the bottom, away from the dock.
func _place_boot(f: Fish) -> void:
	f.species = BOOT_SPECIES
	f.weight = K.WEIGHT_MIN[BOOT_SPECIES]
	f.scale = 1.0
	var a := rng.range_f(0.0, TAU)
	var d := sqrt(rng.next_float()) * 0.6
	f.x = POND_CX + cos(a) * POND_RX * d
	f.z = minf(POND_CZ + sin(a) * POND_RZ * d, DOCK_Z - 160.0)
	f.y = BED_Y + 1.5
	f.vx = 0.0
	f.vz = 0.0
	f.heading = rng.range_f(0.0, TAU)
	f.mode = FishMode.SWIM
	f.spook_t = 0.0
	f.mode_t = 0.0


func _step_fish(dt: float) -> void:
	for i in SLOTS:
		var f := _fish[i]
		var m := f.mode
		if m == FishMode.GONE:
			f.mode_t -= dt
			if f.mode_t <= 0.0:
				if i == BOOT:
					_place_boot(f)
				else:
					_spawn_fish(f, false)
		elif m == FishMode.HOOKED or m == FishMode.LANDED:
			f.wiggle += dt * 16.0
		elif i != BOOT:
			_swim(f, i, dt)


## Boids-lite: wander, keep apart, school loosely with the same kind, stay in the pond.
func _swim(f: Fish, i: int, dt: float) -> void:
	var speed: float = K.SPEED[f.species]
	var dx: float
	var dz: float
	var pace := 1.0
	var seeking := f.mode == FishMode.APPROACH or f.mode == FishMode.NIBBLE or f.mode == FishMode.BITE
	if seeking:
		# Nose up to the lure; circle it a little while nibbling.
		var jig := 0.0 if f.mode == FishMode.APPROACH else 3.0
		var tx := _lure_x + sin(time * 3.0 + i) * jig
		var tz := _lure_z + cos(time * 3.0 + i) * jig
		dx = tx - f.x
		dz = tz - f.z
		var d := _hypot(dx, dz)
		pace = 1.1 * MathUtil.clamp01(d / 20.0 + 0.25) if f.mode == FishMode.APPROACH else MathUtil.clamp01(d / 12.0)
		f.y = MathUtil.damp(f.y, -5.0, 2.0, dt)
	else:
		f.spook_t -= dt
		f.wander_t -= dt
		if f.wander_t <= 0.0 or _hypot(f.wx - f.x, f.wz - f.z) < 15.0:
			var a := rng.range_f(0.0, TAU)
			var d := sqrt(rng.next_float()) * 0.78
			f.wx = POND_CX + cos(a) * POND_RX * d
			f.wz = minf(POND_CZ + sin(a) * POND_RZ * d, DOCK_Z - 60.0)
			f.wander_t = rng.range_f(2.0, 5.0)
		dx = f.wx - f.x
		dz = f.wz - f.z
		var l := maxf(_hypot(dx, dz), 1e-3)
		dx /= l
		dz /= l
		if f.spook_t > 0.0:
			var ex := f.x - f.flee_x
			var ez := f.z - f.flee_z
			var el := _hypot(ex, ez)
			if el < 160.0:
				var k := 2.0 / maxf(el, 1e-3)
				dx += ex * k
				dz += ez * k
				pace = 1.8
		# Separation, alignment and cohesion.
		var ax := 0.0
		var az := 0.0
		var cx := 0.0
		var cz := 0.0
		var mates := 0
		var fx0 := f.x
		var fz0 := f.z
		var sp := f.species
		for j in K.FISH_SLOTS:
			if j == i:
				continue
			var o := _fish[j]
			var om := o.mode
			if om == FishMode.GONE or om == FishMode.LANDED:
				continue
			var ox := fx0 - o.x
			var oz := fz0 - o.z
			var d2 := ox * ox + oz * oz
			if d2 < SEP_RADIUS * SEP_RADIUS and d2 > 1e-4:
				var d := sqrt(d2)
				var k := (SEP_RADIUS - d) / SEP_RADIUS * 1.5 / d
				dx += ox * k
				dz += oz * k
			if o.species == sp and d2 < FLOCK_RADIUS * FLOCK_RADIUS:
				ax += o.vx
				az += o.vz
				cx += o.x
				cz += o.z
				mates += 1
		if mates > 0:
			var al := _hypot(ax, az)
			if al > 1e-3:
				dx += ax / al * 0.3
				dz += az / al * 0.3
			dx += (cx / mates - f.x) * 0.004
			dz += (cz / mates - f.z) * 0.004
		var e := _ellipse(f.x, f.z)
		if e > 0.72:
			var bx := POND_CX - f.x
			var bz := POND_CZ - f.z
			l = maxf(_hypot(bx, bz), 1e-3)
			var k := (e - 0.72) * 6.0
			dx += bx / l * k
			dz += bz / l * k
		if f.z > DOCK_Z - 70.0:
			dz -= 1.5
		f.y = -10.0 - 16.0 * (0.5 + 0.5 * sin(time * 0.5 + f.depth_phase))
	var ll := _hypot(dx, dz)
	var want := speed * pace
	var tvx := dx / ll * want if ll > 1e-4 else 0.0
	var tvz := dz / ll * want if ll > 1e-4 else 0.0
	f.vx = MathUtil.damp(f.vx, tvx, 2.5, dt)
	f.vz = MathUtil.damp(f.vz, tvz, 2.5, dt)
	f.x += f.vx * dt
	f.z += f.vz * dt
	# Hard edge: never through the bank or under the dock.
	var e2 := _ellipse(f.x, f.z)
	if e2 > 0.9:
		var k := sqrt(0.9 / e2)
		f.x = POND_CX + (f.x - POND_CX) * k
		f.z = POND_CZ + (f.z - POND_CZ) * k
	if f.z > DOCK_Z - 30.0:
		f.z = DOCK_Z - 30.0
	var sp2 := f.vx * f.vx + f.vz * f.vz
	if sp2 > 1.0:
		f.heading = atan2(f.vx, f.vz)
	f.wiggle += dt * (4.0 + sqrt(sp2) * 0.15)


static func _ellipse(x: float, z: float) -> float:
	var u := (x - POND_CX) / POND_RX
	var v := (z - POND_CZ) / POND_RZ
	return u * u + v * v


static func _hypot(x: float, z: float) -> float:
	return sqrt(x * x + z * z)


# ---------------------------------------------------------------- ripples

func _add_ripple(x: float, z: float, size: float, life: float) -> void:
	var slot := 0
	var oldest := -1.0
	for i in RIPPLES:
		if _r_on[i] == 0:
			slot = i
			break
		var left := _r_life[i] - _r_age[i]
		if oldest < 0.0 or left < oldest:
			oldest = left
			slot = i
	_r_on[slot] = 1
	_rx[slot] = x
	_rz[slot] = z
	_r_age[slot] = 0.0
	_r_life[slot] = life
	_r_size[slot] = size


## Starts a spray of droplets at ([param x], [param z]) on the water, [param power] times a normal splash.
func _splash_at(x: float, z: float, power: float) -> void:
	var i := _splash_next
	_splash_next = (_splash_next + 1) % SPLASHES
	_splash_x[i] = x
	_splash_z[i] = z
	_splash_age[i] = 0.0
	_splash_power[i] = power
	_splash_count += 1
	_splash_seed[i] = _splash_count


func _step_ripples(dt: float) -> void:
	for i in RIPPLES:
		if _r_on[i] == 0:
			continue
		_r_age[i] += dt
		if _r_age[i] >= _r_life[i]:
			_r_on[i] = 0


# ---------------------------------------------------------------- 3D presentation

## The low sun's warm light over the pond (golden hour: long, warm, a little dim).
var _sun := PointLight.new(360.0, 260.0, 120.0, 1.0, 0.72, 0.42, 900.0, 0.42)
var _gold_light := PointLight.new(0.0, 20.0, 0.0, 1.0, 0.8, 0.3, 90.0, 0.0)
var _xf := Xform.new()
var _bank_tex: Region = null
var _line_end := PackedFloat64Array([0.0, 0.0, 0.0])
var _tip_now := PackedFloat64Array([0.0, 0.0, 0.0])


func render(scope: DrawScope) -> void:
	var r := _stage.begin()
	var l := r.lighting
	# Golden hour: soft violet shade, warm low sun. Bright enough that the fish still read.
	l.amb_r = 0.70
	l.amb_g = 0.68
	l.amb_b = 0.76
	l.set_direction(-0.4, 1.0, 0.5)
	l.dir_r = 0.62
	l.dir_g = 0.49
	l.dir_b = 0.35
	l.points.clear()
	l.points.append(_sun)
	r.vignette = 0.3
	r.gradient(0xFF3D68AE, 0xFFF3B27A)
	_draw_sky(r)
	_draw_scenery(r)
	_draw_fish(r)
	_draw_pads(r)
	_rod_tip(_tip_now)
	_draw_rod(r)
	_draw_bobber(r)
	_draw_water(r)
	_draw_mist(r)
	_draw_glitter(r)
	_draw_ripples(r)
	_draw_splashes(r)
	_draw_line(r)
	_draw_aim(r)
	_draw_glints(r)
	_draw_fireflies(r)
	_stage.present()
	_draw_hud(scope)


## The low sun behind the far bank: a wide warm halo, its core, a band of horizon haze over the tree
## line and a few slow beams of light slanting down between the trees.
func _draw_sky(r: Renderer3D) -> void:
	var glow := TexKit.glow().full()
	var breathe := 1.0 + 0.05 * sin(time * 0.7)
	r.sprite(SUN_X, SUN_Y, SUN_Z, 640.0, 470.0, glow, 0.0, Blend.ADD, 1.0, HALO_ALPHA * breathe, 1.0, 0xFFFF9A50)
	r.sprite(SUN_X, SUN_Y, SUN_Z + 1.0, 240.0, 240.0, glow, 0.0, Blend.ADD, 1.0, 0.7, 1.0, 0xFFFFD890)
	r.sprite(SUN_X, SUN_Y, SUN_Z + 2.0, 72.0, 72.0, TexKit.dot().full(), 0.0, Blend.ADD, 1.2, 0.95, 1.0, 0xFFFFF4D0)
	var haze := FishingArt.haze()
	r.quad(-620.0, 200.0, -418.0, 980.0, 200.0, -418.0, 980.0, 40.0, -418.0, -620.0, 40.0, -418.0, haze, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, 0.32, false, 0xFFFFB068)
	# Beams: each slants from near the sun down and away to the left, swelling and fading.
	var shaft := FishingArt.shaft()
	for i in 4:
		var xt := SUN_X - 30.0 + i * 26.0
		var xb := xt - 210.0 - i * 60.0
		var hw_t := 14.0
		var hw_b := 62.0 + i * 12.0
		var a := SHAFT_ALPHA * (0.65 + 0.35 * sin(time * 0.5 + i * 1.9))
		r.quad(xt - hw_t, 178.0, -200.0, xt + hw_t, 178.0, -200.0, xb + hw_b, 0.0, -200.0, xb - hw_b, 0.0, -200.0, shaft, 0.0, 0.0, 1.0,
			0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, a, false, 0xFFFFC880)


func _draw_scenery(r: Renderer3D) -> void:
	# Distant tree line and hills.
	var tl := FishingArt.treeline()
	r.quad(-620.0, 190.0, -420.0, 980.0, 190.0, -420.0, 980.0, -10.0, -420.0, -620.0, -10.0, -420.0, tl, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.85, 1.0, true, 0xFFFFDDB4)
	# Grass round the pond, with the pond cut out of it.
	if _bank_tex == null:
		_bank_tex = FishingArt.bank(BANK_X0, BANK_Z0, BANK_X1, BANK_Z1, POND_CX, POND_CZ, POND_RX, POND_RZ).full()
	r.quad(BANK_X0, 0.4, BANK_Z0, BANK_X1, 0.4, BANK_Z0, BANK_X1, 0.4, BANK_Z1, BANK_X0, 0.4, BANK_Z1, _bank_tex, 0.0, 1.0, 0.0)
	# The pond bed.
	var bed := FishingArt.bed()
	var bw := float(bed.w)
	r.quad(
		POND_CX - POND_RX - 20.0, BED_Y, POND_CZ - POND_RZ - 20.0, POND_CX + POND_RX + 20.0, BED_Y, POND_CZ - POND_RZ - 20.0,
		POND_CX + POND_RX + 20.0, BED_Y, POND_CZ + POND_RZ + 20.0, POND_CX - POND_RX - 20.0, BED_Y, POND_CZ + POND_RZ + 20.0,
		bed, 0.0, 1.0, 0.0, 0.0, 0.0, bw * 4.0, bw * 5.5)
	# Trees and reeds.
	var tree := FishingArt.tree()
	var k := 0
	while k < TREES.size():
		var h: float = TREES[k + 2]
		r.billboard(TREES[k], 0.0, TREES[k + 1], h * 0.75, h, tree, k % 2 == 0, 0.2, Blend.OPAQUE, 0.0, 1.0, 1.03, 0xFFFFE6C4)
		k += 3
	var reeds := FishingArt.reeds()
	for i in REEDS.size():
		var a: float = REEDS[i]
		var x := POND_CX + cos(a) * (POND_RX + 4.0)
		var z := POND_CZ + sin(a) * (POND_RZ + 4.0)
		r.billboard(x, 0.0, z, 34.0, 34.0, reeds, i % 2 == 1, 0.3)
	# The dock: planks running towards the viewer, its edge and two posts.
	var pl := FishingArt.planks()
	var pw := float(pl.w)
	r.quad(92.0, 8.0, DOCK_Z + 10.0, 268.0, 8.0, DOCK_Z + 10.0, 268.0, 8.0, 900.0, 92.0, 8.0, 900.0, pl, 0.0, 1.0, 0.0, 0.0, 0.0, pw * 1.4, pw * 2.7)
	r.quad(92.0, 8.0, DOCK_Z + 10.0, 268.0, 8.0, DOCK_Z + 10.0, 268.0, -6.0, DOCK_Z + 10.0, 92.0, -6.0, DOCK_Z + 10.0, pl, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, 12.0)
	r.beam(98.0, 14.0, DOCK_Z + 12.0, 98.0, -30.0, DOCK_Z + 12.0, 9.0, pl)
	r.beam(262.0, 14.0, DOCK_Z + 12.0, 262.0, -30.0, DOCK_Z + 12.0, 9.0, pl)


func _draw_fish(r: Renderer3D) -> void:
	var shadow := TexKit.shadow().full()
	for i in SLOTS:
		var f := _fish[i]
		if f.mode == FishMode.GONE:
			continue
		var length: float = K.LENGTH[f.species] * f.scale
		if f.mode == FishMode.LANDED:
			_draw_landed_fish(r, f)
			continue
		if f.species == BOOT_SPECIES:
			FishingArt.boot().draw(r, Blend.OPAQUE, 1.0, _xf.set_xf(f.x, f.y, f.z, f.heading, 0.0, PI / 2.0))
			continue
		# Soft shadow on the bed, then the fish itself swishing its tail.
		r.flat(f.x, f.z, BED_Y + 0.5, length * 0.45, length, shadow, -f.heading, Blend.ALPHA, 0.0, 0.3)
		var swish := sin(f.wiggle) * (0.35 if f.mode == FishMode.HOOKED else 0.14)
		FishingArt.fish(f.species).draw(r, Blend.OPAQUE, 1.0, _xf.set_xf(f.x, f.y, f.z, f.heading + swish, 0.0, 0.0, f.scale))


func _draw_landed_fish(r: Renderer3D, f: Fish) -> void:
	var k := MathUtil.ease_out_cubic(MathUtil.clamp01(_phase_t / (K.LAND_SECONDS * 0.7)))
	var x := _land_x + (_show_x - _land_x) * k
	var z := _land_z + (_show_z - _land_z) * k
	var y := _land_y + (_show_y - _land_y) * k + sin(k * PI) * 30.0
	var wig := sin(f.wiggle) * 0.35
	if f.species == BOOT_SPECIES:
		FishingArt.boot().draw(r, Blend.OPAQUE, 1.0, _xf.set_xf(x, y, z, 1.2 + wig * 0.3, 0.0, wig, 1.6))
	else:
		# Held up sideways by the mouth, tail flapping.
		FishingArt.fish(f.species).draw(r, Blend.OPAQUE, 1.0, _xf.set_xf(x, y, z, PI / 2.0 + wig, -1.2, 0.0, f.scale * 1.7))


func _draw_pads(r: Renderer3D) -> void:
	var pad := FishingArt.lily_pad()
	var flower := FishingArt.flower()
	for i in PADS:
		var a := _pad_a[i] + sin(time * 0.4 + i) * 0.08
		r.flat(_pad_x[i], _pad_z[i], 0.7, _pad_s[i], _pad_s[i], pad, a)
		if i % 3 == 0:
			r.sprite(_pad_x[i] + 3.0, 4.0, _pad_z[i] - 2.0, 12.0, 12.0, flower)


## Where the rod tip is this frame: bent towards the line by the tension, drawn back while charging.
func _rod_tip(out: PackedFloat64Array) -> void:
	var bend := 0.03
	if _phase == CastPhase.FIGHT:
		bend = _tension * 1.1
	elif _phase == CastPhase.WAIT:
		bend = 0.06 + _bob_dip * 0.25
	elif _phase == CastPhase.LANDING:
		bend = 0.5
	var x := _tip_x
	var y := _tip_y
	var z := _tip_z
	if _phase == CastPhase.CHARGE:
		y += _power * 26.0
		z += _power * 40.0
	if _phase == CastPhase.FLIGHT:
		var k := MathUtil.clamp01(_phase_t / 0.25)
		y -= (1.0 - k) * 20.0
	if _phase == CastPhase.FIGHT and _pull != Pull.REST:
		bend += sin(time * 38.0) * 0.04
	var tx := _lure_x - x
	var ty := _lure_y - y
	var tz := _lure_z - z
	var tl := maxf(sqrt(tx * tx + ty * ty + tz * tz), 1.0)
	x += tx / tl * bend * 40.0
	y += ty / tl * bend * 40.0 - bend * 26.0
	z += tz / tl * bend * 40.0
	out[0] = x
	out[1] = y
	out[2] = z


func _draw_rod(r: Renderer3D) -> void:
	var n := 10
	# Quadratic curve: butt, a control point on the straight rod, and the (bent) tip.
	var cx := _butt_x + (_tip_x - _butt_x) * 0.55
	var cy := _butt_y + (_tip_y - _butt_y) * 0.55
	var cz := _butt_z + (_tip_z - _butt_z) * 0.55
	var px := _butt_x
	var py := _butt_y
	var pz := _butt_z
	var cork := FishingArt.cork()
	var rod := FishingArt.rod()
	var line := FishingArt.line()
	var dot := TexKit.dot().full()
	for s in range(1, n + 1):
		var t := s / float(n)
		var u := 1.0 - t
		var x := u * u * _butt_x + 2.0 * u * t * cx + t * t * _tip_now[0]
		var y := u * u * _butt_y + 2.0 * u * t * cy + t * t * _tip_now[1]
		var z := u * u * _butt_z + 2.0 * u * t * cz + t * t * _tip_now[2]
		var w := 4.2 - 3.2 * t
		r.beam(px, py, pz, x, y, z, w + 1.6 if s <= 2 else w, cork if s <= 2 else rod)
		# A warm sheen of low sun along the top of the blank.
		if s > 2:
			r.beam(px, py + w * 0.3, pz, x, y + w * 0.3, z, w * 0.3, line, Blend.ADD, 1.0, 0.32 * (1.0 - t * 0.7), 0xFFFFE0B0)
		if s >= 3 and s <= 9 and s % 2 == 1:
			r.sprite(x, y + w * 0.6, z, w * 0.9, w * 0.9, dot, 0.0, Blend.OPAQUE, 0.0, 1.0, 1.0, Pal.LIGHTGRAY)
		px = x
		py = y
		pz = z
	# The tip glints, and burns hotter as the rod bends under a hard-pulling fish.
	var hot := clampf((_tension - 0.6) / 0.4, 0.0, 1.0) if _phase == CastPhase.FIGHT else 0.0
	r.sprite(_tip_now[0], _tip_now[1] + 1.0, _tip_now[2], 9.0 + 12.0 * hot, 9.0 + 12.0 * hot, TexKit.glow().full(), 0.0,
		Blend.ADD, 1.0, 0.3 + 0.5 * hot, 1.0, Pal.mix(0xFFFFE0B0, Pal.RED, hot))


## The float: at the line's end (hanging from the rod when nothing's cast).
func _draw_bobber(r: Renderer3D) -> void:
	_line_target(_line_end)
	if _phase == CastPhase.LANDING:
		return
	var y := _line_end[1]
	var biting := _phase == CastPhase.WAIT and _suitor >= 0 and _fish[_suitor].mode == FishMode.BITE
	if _phase == CastPhase.WAIT:
		y = -5.0 + sin(time * 30.0) * 0.8 if biting else sin(time * 2.4) * 0.7 - _bob_dip * 4.0
	if _phase == CastPhase.FIGHT:
		y = sin(time * 9.0) * 1.0
	var bx := _lure_x if _phase == CastPhase.FIGHT else _line_end[0]
	var bz := _lure_z if _phase == CastPhase.FIGHT else _line_end[2]
	FishingArt.bobber().draw(r, Blend.OPAQUE, 1.0, _xf.set_xf(bx, y, bz, 0.0, 0.0, sin(time * 5.0) * 0.5 if _phase == CastPhase.FIGHT else 0.0))
	if _phase == CastPhase.WAIT:
		if biting:
			# The dive is the cue to strike: rings pulse out from the bobber, and it glows.
			var k := fmod(time * 3.2, 1.0)
			r.flat(bx, bz, 0.9, 16.0 + 46.0 * k, 16.0 + 46.0 * k, FishingArt.ripple(), 0.0, Blend.ADD, 1.0, (1.0 - k) * 0.9, Pal.YELLOW)
			r.sprite(bx, y + 4.0, bz, 24.0, 24.0, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, 0.4 + 0.3 * sin(time * 18.0), 1.0, Pal.YELLOW)
		else:
			r.sprite(bx, y + 3.5, bz, 12.0, 12.0, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, 0.22 + 0.08 * sin(time * 2.4), 1.0, 0xFFFF6A50)


## Where the line ends: the lure, the hooked fish, or dangling under the rod tip.
func _line_target(out: PackedFloat64Array) -> void:
	if _phase == CastPhase.IDLE or _phase == CastPhase.CHARGE:
		out[0] = _tip_now[0]
		out[1] = _tip_now[1] - 34.0
		out[2] = _tip_now[2]
	elif _phase == CastPhase.FIGHT:
		var f := _fish[_hooked]
		out[0] = f.x
		out[1] = f.y
		out[2] = f.z
	elif _phase == CastPhase.LANDING:
		var k := MathUtil.ease_out_cubic(MathUtil.clamp01(_phase_t / (K.LAND_SECONDS * 0.7)))
		out[0] = _land_x + (_show_x - _land_x) * k
		out[1] = _land_y + (_show_y - _land_y) * k + sin(k * PI) * 30.0 + 14.0
		out[2] = _land_z + (_show_z - _land_z) * k
	else:
		out[0] = _lure_x
		out[1] = _lure_y
		out[2] = _lure_z


func _draw_water(r: Renderer3D) -> void:
	var x0 := POND_CX - POND_RX - 10.0
	var x1 := POND_CX + POND_RX + 10.0
	var z0 := POND_CZ - POND_RZ - 10.0
	var z1 := POND_CZ + POND_RZ + 10.0
	var w := FishingArt.water()
	var tw := float(w.w)
	var su := time * 5.0
	var sv := time * 3.0
	r.quad(x0, 0.0, z0, x1, 0.0, z0, x1, 0.0, z1, x0, 0.0, z1, w, 0.0, 1.0, 0.0, su, sv, su + tw * 4.2, sv + tw * 6.0, Blend.ALPHA, 0.0, 0.72, false)
	var c := FishingArt.caustics()
	var cw := float(c.w)
	var cu := -time * 7.0
	var cv := time * 4.0
	r.quad(x0, 0.1, z0, x1, 0.1, z0, x1, 0.1, z1, x0, 0.1, z1, c, 0.0, 1.0, 0.0, cu, cv, cu + cw * 3.0, cv + cw * 4.4, Blend.ADD, 1.0, 0.16, false, 0xFFB8F4FF)
	r.quad(x0, 0.2, z0, x1, 0.2, z0, x1, 0.2, z1, x0, 0.2, z1, c, 0.0, 1.0, 0.0, time * 3.0, -time * 6.0, time * 3.0 + cw * 5.0, -time * 6.0 + cw * 7.0, Blend.ADD, 1.0, 0.1, false)


## A bank of mist drifting over the far water (a horizontal sheet that fades out at its edges).
func _draw_mist(r: Renderer3D) -> void:
	var m := time * 4.0
	r.quad(
		POND_CX - POND_RX, 7.0, POND_CZ - POND_RZ - 10.0, POND_CX + POND_RX, 7.0, POND_CZ - POND_RZ - 10.0,
		POND_CX + POND_RX, 7.0, POND_CZ + 10.0, POND_CX - POND_RX, 7.0, POND_CZ + 10.0, FishingArt.fog(), 0.0, 1.0, 0.0,
		m, 0.0, m + 256.0, 32.0, Blend.ALPHA, 0.9, MIST_ALPHA, false, 0xFFFFE2C8)


## The sun's glitter on the water: a broad warm sheen and a scatter of twinkling streaks along the
## path to the sun.
func _draw_glitter(r: Renderer3D) -> void:
	# The glowing sky reflected in the far water.
	r.quad(
		POND_CX - POND_RX, 0.6, POND_CZ - POND_RZ, POND_CX + POND_RX, 0.6, POND_CZ - POND_RZ,
		POND_CX + POND_RX, 0.6, 200.0, POND_CX - POND_RX, 0.6, 200.0, FishingArt.haze(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, 0.16, false, 0xFFFF9A58)
	r.flat(246.0, 240.0, 0.8, 130.0, 520.0, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, 0.10, 0xFFFFB060)
	var dot := TexKit.dot().full()
	for i in GLITTER:
		var z := 20.0 + MathUtil.hash01(i, 301) * 500.0
		var t := (800.0 - z) / 1230.0
		var spread := 10.0 + maxf(560.0 - z, 0.0) * 0.05 + 30.0 * (1.0 - t)
		var x := 180.0 + 150.0 * t + (MathUtil.hash01(i, 302) - 0.5) * 2.0 * spread
		var tw := sin(time * (1.6 + MathUtil.hash01(i, 303) * 3.0) + MathUtil.hash01(i, 304) * 9.0)
		var a := tw * tw * tw * tw if tw > 0.0 else 0.0
		if a < 0.02:
			continue
		var w := 6.0 + MathUtil.hash01(i, 305) * 12.0
		r.flat(x, z, 0.7, w, w * 0.34, dot, 0.0, Blend.ADD, 1.0, 0.85 * a, 0xFFFFF0D0)


func _draw_ripples(r: Renderer3D) -> void:
	var ring := FishingArt.ripple()
	for i in RIPPLES:
		if _r_on[i] == 0:
			continue
		var k := _r_age[i] / _r_life[i]
		var s := _r_size[i] * (0.3 + 0.9 * MathUtil.ease_out_cubic(k))
		r.flat(_rx[i], _rz[i], 0.5, s, s, ring, 0.0, Blend.ADD, 1.0, 0.55 * (1.0 - k), 0xFFDFF8FF)
		# A second, smaller wave following the first.
		var k2 := (k - 0.18) / 0.82
		if k2 > 0.0:
			var s2 := _r_size[i] * (0.3 + 0.75 * MathUtil.ease_out_cubic(k2)) * 0.7
			r.flat(_rx[i], _rz[i], 0.5, s2, s2, ring, 0.0, Blend.ADD, 1.0, 0.3 * (1.0 - k2), 0xFFDFF8FF)


## Sprays: droplets thrown up from a splash, each on its own arc, plus a bright column at the start.
## build-13 drew each spray's column then its droplets; all are additive, so the columns go first and
## then every droplet (the same picture, fewer draws).
func _draw_splashes(r: Renderer3D) -> void:
	var dot := TexKit.dot().full()
	var glow := TexKit.glow().full()
	for i in SPLASHES:
		var t := _splash_age[i]
		if t >= SPLASH_LIFE or t >= 0.25:
			continue
		var pw := _splash_power[i]
		var c := 1.0 - t / 0.25
		r.sprite(_splash_x[i], 8.0 + t * 60.0 * pw, _splash_z[i], 10.0 * pw, 26.0 * pw * (1.0 - 0.3 * (1.0 - c)), glow, 0.0, Blend.ADD, 1.0, 0.6 * c, 1.0, 0xFFE8FFFF)
	for i in SPLASHES:
		var t := _splash_age[i]
		if t >= SPLASH_LIFE:
			continue
		var k := t / SPLASH_LIFE
		var pw := _splash_power[i]
		var seed_v := _splash_seed[i] * 7
		var x := _splash_x[i]
		var z := _splash_z[i]
		for n in DROPLETS:
			var ang := MathUtil.hash01(n, seed_v + 1) * 6.2832
			var out := (14.0 + MathUtil.hash01(n, seed_v + 2) * 30.0) * pw
			var up := (46.0 + MathUtil.hash01(n, seed_v + 3) * 70.0) * pw
			var y := up * t - 0.5 * 300.0 * t * t
			if y < 0.0:
				continue
			var sz := 2.4 + MathUtil.hash01(n, seed_v + 4) * 2.0
			r.sprite(x + cos(ang) * out * t, y + 1.0, z + sin(ang) * out * t * 0.6, sz, sz, dot, 0.0, Blend.ADD, 1.0, 0.9 * (1.0 - k), 1.0, 0xFFEAFBFF)


## Fireflies drifting over the far bank and the reeds, blinking on and off. build-13 drew each one's
## dot then its glow; both are additive, so the dots go first and then the glows.
func _draw_fireflies(r: Renderer3D) -> void:
	var dot := TexKit.dot().full()
	var glow := TexKit.glow().full()
	for pass_i in 2:
		for i in FIREFLIES:
			var a := PI * 0.9 + MathUtil.hash01(i, 201) * PI * 1.2
			var ring := 8.0 + MathUtil.hash01(i, 202) * 70.0
			var x := POND_CX + cos(a) * (POND_RX + ring) + sin(time * 0.5 + i) * 10.0
			var z := POND_CZ + sin(a) * (POND_RZ + ring * 0.6) + cos(time * 0.4 + i * 1.3) * 10.0
			var y := 10.0 + MathUtil.hash01(i, 203) * 44.0 + sin(time * 0.8 + i * 2.0) * 6.0
			var b := 0.5 + 0.5 * sin(time * 2.2 + i * 1.9)
			var a3 := b * b * b
			if pass_i == 0:
				r.sprite(x, y, z, 3.6, 3.6, dot, 0.0, Blend.ADD, 1.0, 0.15 + 0.85 * a3, 1.0, 0xFFDFFF80)
			else:
				r.sprite(x, y, z, 17.0, 17.0, glow, 0.0, Blend.ADD, 1.0, 0.5 * a3, 1.0, 0xFFC8FF60)


func _draw_line(r: Renderer3D) -> void:
	var sx := _tip_now[0]
	var sy := _tip_now[1]
	var sz := _tip_now[2]
	var ex := _line_end[0]
	var ey := _line_end[1]
	var ez := _line_end[2]
	var tight := 0.15
	if _phase == CastPhase.FIGHT:
		tight = MathUtil.clamp01(_tension * 1.4)
	elif _phase == CastPhase.FLIGHT or _phase == CastPhase.LANDING or _phase == CastPhase.IDLE or _phase == CastPhase.CHARGE:
		tight = 1.0
	var len := sqrt((ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) + (ez - sz) * (ez - sz))
	var sag := (1.0 - tight) * len * 0.12
	var color := Pal.WHITE
	if _phase == CastPhase.FIGHT and _tension > K.SAFE_HIGH:
		color = Pal.mix(Pal.WHITE, Pal.RED, (_tension - K.SAFE_HIGH) * 5.0)
	# A glow along the line as it comes under strain: cool, then warm, then hot, flickering at the snap.
	var strain := 0.0
	if _phase == CastPhase.FIGHT:
		var st := clampf((_tension - 0.55) / 0.45, 0.0, 1.0)
		strain = st * st * (0.7 + 0.3 * sin(time * 50.0) if _tension > K.SNAP_TENSION - 0.05 else 1.0)
	var glow_col := Pal.mix(Pal.YELLOW, Pal.RED, clampf((_tension - 0.85) * 6.0, 0.0, 1.0))
	var segs := 8
	var px := sx
	var py := sy
	var pz := sz
	var line := FishingArt.line()
	for s in range(1, segs + 1):
		var t := s / float(segs)
		var x := sx + (ex - sx) * t
		var y := sy + (ey - sy) * t - sag * 4.0 * t * (1.0 - t)
		var z := sz + (ez - sz) * t
		r.beam(px, py, pz, x, y, z, 0.9, line, Blend.ALPHA, 1.0, 0.85, color)
		if strain > 0.02:
			r.beam(px, py, pz, x, y, z, 3.4, line, Blend.ADD, 1.0, strain * 0.45, glow_col)
		px = x
		py = y
		pz = z


## While a cast is wound up: its landing spot on the water and the arc to it.
func _draw_aim(r: Renderer3D) -> void:
	if _phase != CastPhase.CHARGE:
		return
	var spot := _landing_spot(_aim_fx, _power)
	var tx := spot.x
	var tz := spot.y
	var pulse := 0.75 + 0.25 * sin(time * 10.0)
	r.flat(tx, tz, 0.6, 40.0, 40.0, FishingArt.target(), time, Blend.ADD, 1.0, 0.8 * pulse, Pal.YELLOW)
	var dist := _hypot(tx - DOCK_X, tz - DOCK_Z)
	var arc := 50.0 + dist * 0.3
	var dot := TexKit.dot().full()
	for k in range(1, 10):
		var t := k / 10.0
		var x := _tip_now[0] + (tx - _tip_now[0]) * t
		var z := _tip_now[2] + (tz - _tip_now[2]) * t
		var y := _tip_now[1] * (1.0 - t) + 4.0 * t * (1.0 - t) * arc
		r.sprite(x, y, z, 5.0, 5.0, dot, 0.0, Blend.ADD, 1.0, 0.7, 1.0, Pal.YELLOW)


## Golden carp glint under the water; a hooked golden one lights the water. build-13 drew each
## carp's glow then its glint; all are additive, so the glows go first, then the glints, then the
## catch's light and sparkles.
func _draw_glints(r: Renderer3D) -> void:
	var glow := TexKit.glow().full()
	var dot := TexKit.dot().full()
	for i in K.FISH_SLOTS:
		var f := _fish[i]
		if f.species != 3 or f.mode == FishMode.GONE or f.mode == FishMode.LANDED:
			continue
		var a := 0.35 + 0.25 * sin(time * 6.0 + i)
		r.sprite(f.x, f.y + 4.0, f.z, 46.0, 46.0, glow, 0.0, Blend.ADD, 1.0, a, 1.0, Pal.GOLD)
	for i in K.FISH_SLOTS:
		var f := _fish[i]
		if f.species != 3 or f.mode == FishMode.GONE or f.mode == FishMode.LANDED:
			continue
		if fmod(time * 3.0 + i, 1.0) < 0.25:
			r.sprite(f.x + sin(time * 7.0 + i) * 8.0, 2.0, f.z + cos(time * 5.0) * 6.0, 8.0, 8.0, dot, 0.0, Blend.ADD, 1.0, 0.9, 1.0, Pal.YELLOW)
	if _phase == CastPhase.LANDING:
		var k := MathUtil.clamp01(_phase_t / K.LAND_SECONDS)
		var gold := _hooked >= 0 and _fish[_hooked].species == 3
		r.sprite(_show_x, _show_y, _show_z, 120.0 * k, 120.0 * k, glow, 0.0, Blend.ADD, 1.0, 0.5 * k, 1.0, Pal.GOLD if gold else Pal.CREAM)
		# Sparkles circling the catch as it is held up.
		for n in 8:
			var ang := time * 2.4 + n * 0.7854
			var rad := 30.0 + 26.0 * k
			r.sprite(_show_x + cos(ang) * rad, _show_y + sin(ang) * rad * 0.55 + 6.0, _show_z + 4.0, 6.0, 6.0, dot, 0.0, Blend.ADD, 1.0,
				0.9 * k * (0.5 + 0.5 * sin(time * 9.0 + n)), 1.0, Pal.YELLOW if gold else Pal.WHITE)


# ---------------------------------------------------------------- HUD overlay (field units)

var _rim_stroke := PaStroke.new(4.0)
var _arrow_stroke := PaStroke.new(6.0, true)
var _thin_stroke := PaStroke.new(2.0)


func _draw_hud(scope: DrawScope) -> void:
	_draw_reel(scope)
	_draw_gauge(scope)
	_draw_status(scope)
	if _catch_show_t > 0.0:
		var a := MathUtil.clamp01(_catch_show_t / 0.3)
		ArcadeFont.draw_centered(scope, _catch_name, GAME_W / 2.0, 92.0, 2.4, Pal.CREAM, a)
	if _snap_flash > 0.0:
		scope.draw_rect(Pal.RED, Vector2(0.0, 0.0), Vector2(GAME_W, GAME_H), _snap_flash * 0.25)


func _draw_reel(scope: DrawScope) -> void:
	var c := Vector2(REEL_CX, REEL_CY)
	var biting := _phase == CastPhase.WAIT and _suitor >= 0 and _fish[_suitor].mode == FishMode.BITE
	if _crank.held():
		scope.draw_circle(Pal.CYAN, REEL_R + 14.0, c, 0.18 + 0.08 * sin(time * 8.0))
	scope.draw_circle(0xFF0E1622, REEL_R + 8.0, c, 0.88)
	scope.draw_circle(0xFF8A96AA, REEL_R + 6.0, c, 1.0, _rim_stroke)
	# A knurled edge (a tick every 15 degrees) and a sheen catching the low sun.
	for k in 24:
		var ang := k * TAU / 24.0
		scope.draw_line(0xFF56647E, Vector2(REEL_CX + cos(ang) * (REEL_R + 2.0), REEL_CY + sin(ang) * (REEL_R + 2.0)),
			Vector2(REEL_CX + cos(ang) * (REEL_R + 9.0), REEL_CY + sin(ang) * (REEL_R + 9.0)), 2.0)
	scope.draw_arc(Pal.WHITE, 195.0, 75.0, false, Vector2(REEL_CX - REEL_R - 6.0, REEL_CY - REEL_R - 6.0),
		Vector2((REEL_R + 6.0) * 2.0, (REEL_R + 6.0) * 2.0), 0.3, _rim_stroke)
	scope.draw_circle(0xFF26344A, REEL_R * 0.66, c)
	scope.draw_circle(0xFF3E5270, REEL_R * 0.66, c, 1.0, _thin_stroke)
	var a := _crank.angle
	for k in 3:
		var s := a + k * TAU / 3.0
		scope.draw_line(0xFF6A7C98, c, Vector2(REEL_CX + cos(s) * REEL_R * 0.62, REEL_CY + sin(s) * REEL_R * 0.62), 5.0)
	# The handle arm and its knob.
	var kx := REEL_CX + cos(a) * REEL_R * 0.84
	var ky := REEL_CY + sin(a) * REEL_R * 0.84
	scope.draw_line(0xFFB8C4D8, c, Vector2(kx, ky), 8.0)
	scope.draw_circle(0xFF1A1A22, 14.0, Vector2(kx + 1.5, ky + 2.0), 0.6)
	scope.draw_circle(Pal.CREAM, 13.0, Vector2(kx, ky))
	scope.draw_circle(Pal.TAN, 7.0, Vector2(kx, ky))
	scope.draw_circle(0xFFC8D0E0, 7.0, c)
	var hint := biting or (_phase == CastPhase.FIGHT and not _crank.held()) \
		or ((_phase == CastPhase.WAIT or _phase == CastPhase.FLIGHT) and not _crank.held())
	if hint:
		# A clockwise arrow round the reel.
		var pulse := 0.55 + 0.45 * sin(time * (16.0 if biting else 6.0))
		var col := Pal.YELLOW if biting else Pal.CYAN
		var rr := REEL_R + 20.0
		var sweep_from := fmod(time * (400.0 if biting else 120.0), 360.0)
		scope.draw_arc(col, sweep_from, 250.0, false, Vector2(REEL_CX - rr, REEL_CY - rr), Vector2(rr * 2.0, rr * 2.0), pulse, _arrow_stroke)
		var end := (sweep_from + 250.0) * (PI / 180.0)
		var hx := REEL_CX + cos(end) * rr
		var hy := REEL_CY + sin(end) * rr
		var tx := -sin(end)
		var ty := cos(end)
		scope.draw_line(col, Vector2(hx, hy), Vector2(hx - tx * 12.0 + cos(end) * 8.0, hy - ty * 12.0 + sin(end) * 8.0), 6.0, false, pulse)
		scope.draw_line(col, Vector2(hx, hy), Vector2(hx - tx * 12.0 - cos(end) * 8.0, hy - ty * 12.0 - sin(end) * 8.0), 6.0, false, pulse)


func _draw_gauge(scope: DrawScope) -> void:
	var x := 12.0
	var top := 440.0
	var w := 20.0
	var h := 180.0
	var charging := _phase == CastPhase.CHARGE
	var fighting := _phase == CastPhase.FIGHT
	var a := 1.0 if charging or fighting else 0.45
	scope.draw_rect(0xFF0E1622, Vector2(x - 4.0, top - 4.0), Vector2(w + 8.0, h + 8.0), 0.85 * a)
	if charging:
		scope.draw_rect(Pal.DARKGRAY, Vector2(x, top), Vector2(w, h), a)
		var fy := top + h - _power * h
		scope.draw_rect(Pal.mix(Pal.YELLOW, Pal.ORANGE, _power), Vector2(x, fy), Vector2(w, top + h - fy))
		ArcadeFont.draw_centered(scope, "POWER", x + w / 2.0 + 8.0, top + h + 8.0, 1.3, Pal.YELLOW)
	else:
		# Zones: slack (blue), safe (green), snap (red).
		var y_slack := _y_for(K.SLACK_TENSION, top, h)
		var y_safe := _y_for(K.SAFE_HIGH, top, h)
		scope.draw_rect(Pal.SKY, Vector2(x, y_slack), Vector2(w, top + h - y_slack), 0.45 * a)
		scope.draw_rect(Pal.GREEN, Vector2(x, y_safe), Vector2(w, y_slack - y_safe), 0.4 * a)
		scope.draw_rect(Pal.RED, Vector2(x, top), Vector2(w, y_safe - top), 0.5 * a)
		if fighting:
			var ty := _y_for(_tension, top, h)
			var col := Pal.LIME
			if _tension > K.SAFE_HIGH:
				col = Pal.RED
			elif _tension < K.SLACK_TENSION:
				col = Pal.SKY
			scope.draw_rect(col, Vector2(x + 4.0, ty), Vector2(w - 8.0, top + h - ty))
			scope.draw_rect(col, Vector2(x - 2.0, ty - 6.0), Vector2(w + 4.0, 12.0), 0.22)
			scope.draw_rect(Pal.WHITE, Vector2(x - 5.0, ty - 2.0), Vector2(w + 10.0, 4.0))
			if _strain_t > 0.0 and int(time * 12.0) % 2 == 0:
				scope.draw_rect(Pal.RED, Vector2(x - 4.0, top - 4.0), Vector2(w + 8.0, h + 8.0), 1.0, _rim_stroke)
		ArcadeFont.draw_centered(scope, "LINE", x + w / 2.0 + 4.0, top + h + 8.0, 1.3, Pal.LAVENDER, a)


static func _y_for(v: float, top: float, h: float) -> float:
	return top + h - MathUtil.clamp01(v / 1.1) * h


func _draw_status(scope: DrawScope) -> void:
	if time_up:
		return
	var blink := 0.6 + 0.4 * sin(time * 6.0)
	var biting := _phase == CastPhase.WAIT and _suitor >= 0 and _fish[_suitor].mode == FishMode.BITE
	var text: String
	var color := Pal.WHITE
	var size := 2.0
	var alpha := 1.0
	if biting:
		text = "STRIKE! CRANK!"
		color = Pal.YELLOW
		size = 3.2
		alpha = 1.0 if int(time * 10.0) % 2 == 0 else 0.5
	elif _phase == CastPhase.IDLE:
		text = "HOLD ON THE POND TO CAST"
		alpha = blink
	elif _phase == CastPhase.CHARGE:
		text = "LET GO TO CAST!"
		color = Pal.YELLOW
	elif _phase == CastPhase.FLIGHT or _phase == CastPhase.WAIT:
		text = "WAIT FOR A BITE..." if _crank.held() else "HOLD THE REEL"
		color = Pal.CREAM
	elif _phase == CastPhase.FIGHT:
		if _tension > K.SAFE_HIGH or _pull == Pull.RUN:
			text = "IT'S PULLING! EASE OFF!"
			color = Pal.RED
			alpha = blink
		elif _pull == Pull.WARN:
			text = "WATCH IT..."
			color = Pal.ORANGE
		elif _tension < K.SLACK_TENSION:
			text = "KEEP REELING!"
			color = Pal.SKY
			alpha = blink
		else:
			text = "REEL IT IN!"
			color = Pal.LIME
	else:
		return
	ArcadeFont.draw_centered(scope, text, GAME_W / 2.0, 40.0, size, color, alpha)


# ---------------------------------------------------------------- simulation-test hooks

func bot_phase() -> int:
	return _phase


func bot_power() -> float:
	return _power


func bot_tension() -> float:
	return _tension


func bot_pull() -> int:
	return _pull


func bot_line_dist() -> float:
	return _line_d


func bot_hooked() -> int:
	return _hooked


func bot_suitor() -> int:
	return _suitor


func bot_biting() -> bool:
	return _phase == CastPhase.WAIT and _suitor >= 0 and _fish[_suitor].mode == FishMode.BITE


func bot_lure_x() -> float:
	return _lure_x


func bot_lure_z() -> float:
	return _lure_z


func bot_crank_rate() -> float:
	return _crank.rate


func bot_crank_held() -> bool:
	return _crank.held()


func bot_casts() -> int:
	return _casts


func bot_landed() -> int:
	return _landed


func bot_snaps() -> int:
	return _snaps


func bot_thrown() -> int:
	return _thrown


func bot_missed() -> int:
	return _missed


func bot_too_soon() -> int:
	return _too_soon


func bot_failsafe_trips() -> int:
	return _failsafe_trips


func bot_last_catch() -> int:
	return _last_catch


func bot_slots() -> int:
	return _fish.size()


func bot_fish_x(i: int) -> float:
	return _fish[i].x


func bot_fish_y(i: int) -> float:
	return _fish[i].y


func bot_fish_z(i: int) -> float:
	return _fish[i].z


func bot_fish_species(i: int) -> int:
	return _fish[i].species


func bot_fish_mode(i: int) -> int:
	return _fish[i].mode


func bot_fish_weight(i: int) -> float:
	return _fish[i].weight


func bot_points(species: int, weight: float) -> int:
	return _catch_points(species, weight)


## Where a cast held at field x [param fx_] and let go at [param pow] lands: world (x, z).
func bot_landing(fx_: float, pow: float) -> Vector2:
	return _landing_spot(fx_, pow)


## The field x to hold and the power to let go at to land a cast on world ([param x], [param z]).
func bot_aim_for(x: float, z: float) -> Vector2:
	var a := atan2(x - DOCK_X, DOCK_Z - z)
	return Vector2(GAME_W / 2.0 + a / (K.MAX_AIM_DEG * (PI / 180.0)) * 160.0,
		MathUtil.clamp01((_hypot(x - DOCK_X, z - DOCK_Z) - K.MIN_CAST) / (K.MAX_CAST - K.MIN_CAST)))


## Test setup: makes fish [param i] a swimming [param species] of [param weight] pounds at world
## ([param x], [param z]).
func bot_place_fish(i: int, species: int, weight: float, x: float, z: float) -> void:
	var f := _fish[i]
	f.species = species
	f.weight = weight
	f.scale = 1.0
	f.x = x
	f.z = z
	f.vx = 0.0
	f.vz = 0.0
	f.mode = FishMode.SWIM
	f.spook_t = 0.0


## Test setup: hooks fish [param i] as if the strike had just gone in (the line out to it).
func bot_hook_now(i: int) -> void:
	_cast_id = -1
	_release_suitor()
	_lure_x = _fish[i].x
	_lure_z = _fish[i].z
	_hook(i)


# ---------------------------------------------------------------- attract mode

func draw_attract(p: Painter, w: int, h: int, at: float) -> void:
	# A pond at golden hour in miniature: a warm sky and low sun over a tree line, the water
	# glittering along the path to the sun, fish circling under the surface, and a rod whose float
	# dives every few seconds (rings, a splash and a jumping fish). Fireflies blink.
	var wf := float(w)
	var hf := float(h)
	var horizon := hf * 0.3
	# Sky in warm bands, the sun with its halo, and the tree line.
	p.fill(0.0, 0.0, wf, horizon, 0xFF3D68AE)
	p.fill(0.0, hf * 0.09, wf, hf * 0.08, 0xFF8A5A98)
	p.fill(0.0, hf * 0.17, wf, hf * 0.08, 0xFFE0905E)
	p.fill(0.0, hf * 0.25, wf, horizon - hf * 0.25 + 0.1, 0xFFF3B27A)
	var sun_x := wf * 0.74
	var sun_y := hf * 0.2
	p.disc(sun_x, sun_y, 4.4, 0xFFFFB068, 0.16)
	p.disc(sun_x, sun_y, 2.9, 0xFFFFD08C, 0.30)
	p.disc(sun_x, sun_y, 1.5, 0xFFFFF4D0)
	for k in 9:
		var tx := k * wf / 8.0
		var th := hf * (0.10 + 0.06 * MathUtil.hash01(k, 3))
		p.disc(tx, horizon - th * 0.2, 2.3 + MathUtil.hash01(k, 4), 0xFF1F4A38)
		p.fill(tx - 1.6, horizon - th, 3.2, th, 0xFF1F4A38, 0.9)
	p.fill(0.0, horizon - 0.6, wf, 0.6, 0xFFFFC080, 0.5)
	# The pond, a little darker towards the viewer, and a warm sheen under the sun.
	for y in int(hf - horizon) + 1:
		var t := y / (hf - horizon)
		p.fill(0.0, horizon + y, wf, 1.0, Pal.mix(0xFF2E8088, 0xFF135060, t))
	p.fill(sun_x - 2.2, horizon, 4.4, hf - horizon, 0xFFFFB060, 0.10)
	# Glitter down the sun's path, and drifting streaks of ripple.
	for i in 16:
		var gy := horizon + 0.8 + MathUtil.hash01(i, 21) * (hf - horizon - 1.6)
		var spread := 0.8 + (gy - horizon) * 0.22
		var gx := sun_x - (gy - horizon) * 0.3 + (MathUtil.hash01(i, 22) - 0.5) * 2.0 * spread
		var tw := sin(at * (1.5 + MathUtil.hash01(i, 23) * 2.5) + MathUtil.hash01(i, 24) * 9.0)
		if tw > 0.2:
			p.fill(gx, gy, 0.9 + MathUtil.hash01(i, 25) * 1.4, 0.28, 0xFFFFF0D0, tw)
	for k in 4:
		var y := hf * (0.42 + k * 0.14)
		var x := fmod(at * (3.0 + k) + k * 7.0, wf + 6.0) - 4.0
		p.fill(x, y, 3.0, 0.4, 0xFF8FE3E0, 0.5)
	# Fish circling under the surface.
	for k in 3:
		var a := at * (0.7 + k * 0.25) + k * 2.1
		var fxp := wf * 0.45 + cos(a) * wf * (0.18 + k * 0.07)
		var fyp := hf * 0.68 + sin(a) * hf * (0.12 + k * 0.03)
		var dir := 1.0 if -sin(a) >= 0.0 else -1.0
		p.disc(fxp, fyp, 1.3, ATTRACT_FISH[k], 0.85)
		p.disc(fxp - dir * 1.6, fyp, 0.8, ATTRACT_FISH[k], 0.85)
		p.disc(fxp, fyp + 0.9, 1.7, 0xFF000000, 0.10)
	# A rod from the corner, its line to a bobbing float that dives every few seconds.
	var cycle := fmod(at, 4.0)
	var dive := 1.2 if cycle > 3.0 else 0.0
	var bx := wf * 0.62
	var by := hf * 0.55 + sin(at * 3.0) * 0.3 + dive
	p.fill(wf - 5.0, 1.0, 0.6, hf * 0.4, 0xFF0E1838)
	var steps := 8
	for s in steps + 1:
		var t := s / float(steps)
		p.fill(wf - 4.7 + (bx - wf + 4.7) * t, 1.5 + (by - 1.5) * t + t * (1.0 - t) * 2.0, 0.35, 0.35, Pal.WHITE, 0.8)
	var ring_k := fmod(at * 0.5, 1.0)
	p.frame(bx - 1.0 - 2.4 * ring_k, by + 0.1 - 0.5 * ring_k, 2.0 + 4.8 * ring_k, 1.0 + 1.0 * ring_k, Pal.WHITE, 0.45 * (1.0 - ring_k))
	p.disc(bx, by, 0.9, Pal.RED)
	p.fill(bx - 0.9, by, 1.8, 0.6, Pal.WHITE)
	if cycle > 3.0:
		# Splash and a jumping fish.
		var j := cycle - 3.0
		p.disc(bx - 2.0 + j * 4.0, by - sin(j * PI) * 5.0, 1.4, Pal.GOLD)
		p.frame(bx - 3.0, by - 0.5, 6.0, 1.5, Pal.WHITE, 1.0 - j)
		for d in 5:
			p.px(bx - 1.5 + d * 0.8, by - sin(j * PI) * (1.5 + d * 0.6), 0xFFEAFBFF, 1.0 - j)
	# Fireflies over the far bank.
	for i in 6:
		var ffx := wf * MathUtil.hash01(i, 31) + sin(at * 0.6 + i) * 1.2
		var ffy := horizon - 1.2 - MathUtil.hash01(i, 32) * hf * 0.12 + cos(at * 0.5 + i * 1.7) * 0.6
		var b := sin(at * 2.2 + i * 1.9) * 0.5 + 0.5
		p.px(ffx, ffy, 0xFFDFFF80, b * b)
	if int(at * 1.5) % 2 == 0:
		p.text_centered("GONE FISHING", wf / 2.0, 1.0, Pal.YELLOW, true)
