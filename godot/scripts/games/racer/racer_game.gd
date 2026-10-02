class_name RacerGame
extends BaseMiniGame
## games/racer/RacerGame.kt: Turbo Racer, a three-lap race round a synthwave circuit against seven
## rivals. Drag to steer (the car accelerates by itself), hold the DRIFT button (or a second
## finger) to drift through corners and let go for a turbo. Optionally, tilt the phone to steer
## instead ([member tilt_steering]), with any touch then held to drift. The round ends at the flag
## (placed by finishing order) or when the clock runs out (placed by how far round the course
## everyone got).
##
## Tilt-steered (Kotlin's TiltControlled): the app sets [member tilt_steering] from
## GameSettings.tilt_steering and feeds the sensor through [method on_tilt].

const SEG := 40.0
const VIEW := 72
## Segments drawn behind the car, down to the bottom of the screen.
const BEHIND := 8
const ROAD_HALF := 150.0
const LANE := 100.0
const CAR_HALF_W := 22.0
const CAR_W := CAR_HALF_W * 2.0
const CAR_LEN := 80.0
const GROUND_HALF := 2400.0
const CAM_BACK := 250.0
const CAM_UP := 104.0
## How far off the road the player's car can wander.
const X_LIMIT := ROAD_HALF + 110.0
## How far off the road a shove can push a rival.
const AI_X_LIMIT := ROAD_HALF + 30.0
## Segments ahead the racing line looks at.
const LOOK := 14
## Progress gained on the inside of a bend (lost on the outside), per unit of offset and bend.
const INSIDE_GAIN := 0.0002
## Grid rows are far enough apart that a car launching at once can't reach the one ahead of it
## within half a second, even if that one hasn't moved yet (0.5 * ACCEL * 0.5 s^2 = 65).
const GRID_ROW := CAR_LEN + 70.0
const AVOID_RANGE := 260.0
const PASS_GAP := 58.0
const CARS := RacerTuning.CARS
const LAPS := RacerTuning.LAPS
## Where the how-to-play hints centre: left of the DRIFT button.
const HINT_X := 136.0
## Segments ahead of the car (past BEHIND) after which the ground is drawn in pairs.
const FAR_GROUND_FROM := 30
## Tyre smoke puffs and skid-mark pieces kept, and how long each lasts (seconds).
const PUFFS := 18
const PUFF_LIFE := 0.9
const SKIDS := 24
const SKID_LIFE := 3.5
## The camera's field of view (degrees) at rest and in a turbo.
const FOV_BASE := 56.0
const FOV_BOOST := 60.0

## The circuit, one entry per section: length in segments, peak bend (positive bends right) and hill height.
const SECTION_LEN := [66, 64, 28, 50, 40, 36, 36, 32, 72, 34, 44, 28, 40, 50]
const SECTION_BEND := [0.0, 1.4, 0.0, -2.4, 0.0, 1.9, -1.9, 0.0, -1.2, 0.0, 2.2, 0.0, -1.6, 0.0]
const SECTION_HILL := [0.0, 3.0, 0.0, 0.0, 7.0, 0.0, 0.0, -6.0, 4.0, 0.0, 0.0, 3.0, 0.0, 0.0]

const RIVAL_COLORS := [Pal.SKY, Pal.LIME, Pal.YELLOW, Pal.PURPLE, Pal.ORANGE, Pal.WHITE, Pal.TEAL]
const PLAYER_COLOR := Pal.RED
const SPARK_COLORS := [Pal.ORANGE, Pal.YELLOW, Pal.WHITE]
const CONFETTI_COLORS := [Pal.GOLD, Pal.PINK, Pal.CYAN, Pal.LIME, Pal.WHITE]
## The plain tyre-smoke grey (puffs tinted by the boost charge glow; this one doesn't).
const SMOKE_GREY := 0xFFB8B4D0

const PLACE_TEXT := ["1ST", "2ND", "3RD", "4TH", "5TH", "6TH", "7TH", "8TH"]
const OF_CARS := "/8"

const ATTRACT_RIVALS := 6
const ATTRACT_START := [0.4, 0.4, 1.4, 1.4, 2.4, 2.4]
const ATTRACT_PACE := [0.9, 0.75, 0.82, 0.95, 0.7, 0.86]

## "1/3", "2/3", "3/3".
static var LAP_TEXT := _laps("%d/%d")
## "LAP 1", "LAP 2", "LAP 3".
static var LAP_POPUPS := _laps("LAP %d")
## "0 KM/H", "5 KM/H", ... "315 KM/H".
static var SPEED_TEXT := _speed_text()
static var PASS_LABEL := "CLEAN PASS +%d" % RacerTuning.OVERTAKE_POINTS
## "1ST/8" ... "8TH/8".
static var ATTRACT_POS := _attract_pos()
## "LAP 1/3", "LAP 2/3", "LAP 3/3".
static var ATTRACT_LAP := _laps("LAP %d/%d")


## One label per lap from [param fmt], which takes the lap number (and, if it has a second %d, the lap count).
static func _laps(fmt: String) -> PackedStringArray:
	var out := PackedStringArray()
	for i in RacerTuning.LAPS:
		if fmt.count("%d") == 2:
			out.append(fmt % [i + 1, RacerTuning.LAPS])
		else:
			out.append(fmt % (i + 1))
	return out


static func _speed_text() -> PackedStringArray:
	var out := PackedStringArray()
	for i in 64:
		out.append("%d KM/H" % (i * 5))
	return out


static func _attract_pos() -> PackedStringArray:
	var out := PackedStringArray()
	for i in RacerTuning.CARS:
		out.append("%s/%d" % [PLACE_TEXT[i], RacerTuning.CARS])
	return out


# The circuit: per segment, how much the heading bends (curve), the slope (hill) and the racing
# line. Kotlin's FloatArrays: 32-bit, as build-13 stored them.
var _segs := 0
var _curve := PackedFloat32Array()
var _slope := PackedFloat32Array()
var _line_at := PackedFloat32Array()
var _lap_len := 0.0
var _race_len := 0.0

# The cars in the race, index 0 being the player's: Kotlin's Racer objects, laid out as parallel
# arrays (32-bit, like the Kotlin Float fields) because GDScript reads an array slot about four
# times faster than another object's field, and the race steps 120 times a second.
## Distance along the course from the start line (negative on the grid).
var _cd := PackedFloat32Array()
## Offset across the road from its middle.
var _cx := PackedFloat32Array()
var _cv := PackedFloat32Array()
var _color := PackedInt64Array()
## Rival's top speed as a share of the player's.
var _skill := PackedFloat32Array()
## Rival's personal offset from the racing line.
var _bias := PackedFloat32Array()
## Rival's reaction time to the green light.
var _launch := PackedFloat32Array()
## Where a rival is pulling out to pass, and for how much longer.
var _pass_x := PackedFloat32Array()
var _pass_t := PackedFloat32Array()
## Finishing order, 1-based; 0 while still racing.
var _finish := PackedInt32Array()
## How long a rival has been ahead of the player, and whether it was last step (1) or not (0).
var _ahead_t := PackedFloat32Array()
var _was_ahead := PackedByteArray()
var _yaw := PackedFloat32Array()
var _place := PackedInt32Array()
var _finish_count := 0
var _race_done := false
## The player's final place (at the flag, or by progress when the clock ran out).
var _final_place := 0
var _lap_shown := 0
var _last_place := 0

## Steer by tilting the phone instead of dragging (off by default; the host listens to the sensor
## only while this is on and a round is on screen). Touch then only drifts: any finger held drifts,
## and dragging steers nothing.
var tilt_steering := false:
	set(value):
		tilt_steering = value
		if not value:
			_tilt_seen = false
			_tilt_input = 0.0
## Latest lean of the phone (radians, right positive), the lean that counts as level, and the steering that makes.
var _tilt_lean := 0.0
var _tilt_neutral := 0.0
var _tilt_input := 0.0
## Set when the next reading should become level (a fresh round, or a pause that let go of the phone).
var _tilt_recenter := true
## Whether readings have arrived: without a sensor the option leaves touch steering as it was.
var _tilt_seen := false

var _steer_target := 0.0
var _steer_id := -1
var _last_steer_x := 0.0
var _drift_id := -1
var _drifting := false
var _ever_drifted := false
var _charge := 0.0
var _boost_t := 0.0
var _boost_full := 1.0
var _tilt := 0.0
var _drift_yaw := 0.0
var _points_carry := 0.0
var _passes := 0
var _contacts := 0
var _last_contact := -9.0
var _bump_fx_t := 0.0
var _engine_t := 0.0
var _skid_t := 0.0
var _rumble_t := 0.0

var _place_labels := PackedStringArray()
var _dnf_labels := PackedStringArray()

# ---------------------------------------------------------------- visual effects state

# Tyre smoke and skid marks live in track coordinates (distance along the course, offset across
# it), so they stay where they were made as the car drives on. They read the simulation but never
# change it, and use hash noise rather than the game's random numbers.
var _puff_d := PackedFloat32Array()
var _puff_x := PackedFloat32Array()
var _puff_age := PackedFloat32Array()
var _puff_tint := PackedInt64Array()
var _puff_next := 0
var _puff_t := 0.0
var _skid_d0 := PackedFloat32Array()
var _skid_x0 := PackedFloat32Array()
var _skid_d1 := PackedFloat32Array()
var _skid_x1 := PackedFloat32Array()
var _skid_age := PackedFloat32Array()
var _skid_next := 0
## Where each rear wheel's mark last reached (distance; negative when it has not started).
var _last_skid_d := PackedFloat32Array([-1.0, -1.0])
var _last_skid_x := PackedFloat32Array([0.0, 0.0])
var _fx_tick := 0
## The turbo's ramp in and out (0..1), and the camera's field of view easing towards it.
var _boost_k := 0.0
var _fov_now := FOV_BASE
var _last_render_t := 0.0

# ---------------------------------------------------------------- 3D presentation state

var _stage := Stage3D.new(int(GAME_W), int(GAME_H))
## Kotlin's shared `pt`: the last projected or track point.
var _pt0 := 0.0
var _pt1 := 0.0
var _pt2 := 0.0
# Segment start points, indexed from BEHIND segments behind the car to VIEW ahead.
var _seg_x := PackedFloat32Array()
var _seg_y := PackedFloat32Array()
var _seg_z := PackedFloat32Array()
var _car_y := 0.0
var _cam_y := CAM_UP
var _frac := 0.0

## Car models, the sky, flames and speed streaks.
var _scene := RacerScene.new()

var _ring_stroke := PaStroke.new(2.0)
var _charge_stroke := PaStroke.new(5.0)

var _headlight := PointLight.new(0.0, 40.0, -120.0, 0.8, 0.9, 1.0, 420.0, 0.9)
var _sun_light := PointLight.new(0.0, 300.0, -2600.0, 1.0, 0.4, 0.6, 2600.0, 0.6)
var _boost_light := PointLight.new(0.0, 30.0, 90.0, 0.4, 0.9, 1.0, 380.0, 0.0)

# ---------------------------------------------------------------- simulation-test hooks

## Runs the player alone (rivals parked on the grid), for tests of the car's own handling.
var solo_for_tests := false


func _init() -> void:
	id = "racer"
	title = "TURBO RACER"
	marquee = "RACER"
	instructions = PackedStringArray([
		"DRAG LEFT AND RIGHT TO STEER",
		"3 LAPS - BEAT 7 RIVALS",
		"HOLD DRIFT (OR A 2ND FINGER)",
		"LET GO FOR A TURBO BOOST",
		"CLEAN PASSES PAY EXTRA",
	])
	look = MiniGame.CabinetLook.new(Pal.DARKRED, Pal.WHITE, Pal.RED, MiniGame.CabinetShape.RACER)
	round_seconds = RacerTuning.ROUND_SECONDS
	for n: int in SECTION_LEN:
		_segs += n
	_curve.resize(_segs)
	_slope.resize(_segs)
	_line_at.resize(_segs)
	_lap_len = _segs * SEG
	_race_len = _lap_len * LAPS
	var i := 0
	for s in SECTION_LEN.size():
		var length: int = SECTION_LEN[s]
		var bend: float = SECTION_BEND[s]
		var hill: float = SECTION_HILL[s]
		for k in length:
			var t := k / float(length)
			_curve[i] = bend * sin(t * PI)
			_slope[i] = hill * sin(t * 2.0 * PI)
			i += 1
	# The racing line hugs the inside of the bends coming up.
	for s in _segs:
		var sum := 0.0
		for k in range(2, 2 + LOOK):
			sum += _curve[(s + k) % _segs]
		_line_at[s] = clampf(sum / LOOK * RacerTuning.LINE_GAIN, -RacerTuning.LINE_MAX, RacerTuning.LINE_MAX)
	_cd.resize(CARS)
	_cx.resize(CARS)
	_cv.resize(CARS)
	_color.resize(CARS)
	_skill.resize(CARS)
	_skill.fill(1.0)
	_bias.resize(CARS)
	_launch.resize(CARS)
	_pass_x.resize(CARS)
	_pass_t.resize(CARS)
	_finish.resize(CARS)
	_ahead_t.resize(CARS)
	_was_ahead.resize(CARS)
	_yaw.resize(CARS)
	for c in range(1, CARS):
		_color[c] = RIVAL_COLORS[c - 1]
	_color[0] = PLAYER_COLOR
	_place.resize(CARS)
	for k in CARS:
		_place[k] = k + 1
		_place_labels.append("%s PLACE! +%d" % [PLACE_TEXT[k], RacerTuning.PLACE_POINTS[k]])
		_dnf_labels.append("%s +%d" % [PLACE_TEXT[k], int(RacerTuning.PLACE_POINTS[k] * RacerTuning.DNF_SHARE)])
	_puff_d.resize(PUFFS)
	_puff_x.resize(PUFFS)
	_puff_age.resize(PUFFS)
	_puff_age.fill(9.0)
	_puff_tint.resize(PUFFS)
	_skid_d0.resize(SKIDS)
	_skid_x0.resize(SKIDS)
	_skid_d1.resize(SKIDS)
	_skid_x1.resize(SKIDS)
	_skid_age.resize(SKIDS)
	_skid_age.fill(99.0)
	_seg_x.resize(VIEW + BEHIND + 1)
	_seg_y.resize(VIEW + BEHIND + 1)
	_seg_z.resize(VIEW + BEHIND + 1)
	_stage.look(0.0, CAM_UP, CAM_BACK, 0.0, 20.0, -420.0, 56.0, 0.44)


## The racer's custom hall cabinet (RacerCabinet) is ported with the hall's cabinet kit; until
## then the hall builds its built-in RACER cabinet.
func cabinet() -> Object:
	return null


func reset() -> void:
	_finish_count = 0
	_race_done = false
	_final_place = 0
	_lap_shown = 0
	var rival := 0
	for slot in CARS:
		var c := 0
		if slot != RacerTuning.PLAYER_SLOT:
			rival += 1
			c = rival
		var row := slot / 2
		var col := slot % 2
		_cd[c] = -(30.0 + row * GRID_ROW + col * 25.0)
		_cx[c] = -52.0 if col == 0 else 52.0
		_cv[c] = 0.0
		_finish[c] = 0
		_pass_t[c] = 0.0
		_ahead_t[c] = 0.0
		_yaw[c] = 0.0
		if c != 0:
			# Faster cars start nearer the front.
			var rank := rival - 1
			_skill[c] = RacerTuning.AI_SKILL_MAX - (RacerTuning.AI_SKILL_MAX - RacerTuning.AI_SKILL_MIN) * rank / (CARS - 2) + rng.range_f(-0.012, 0.012)
			_bias[c] = rng.range_f(-18.0, 18.0)
			_launch[c] = rng.range_f(0.05, 0.35)
	_steer_target = _cx[0]
	_steer_id = -1
	_drift_id = -1
	_drifting = false
	_ever_drifted = false
	_charge = 0.0
	_boost_t = 0.0
	_tilt = 0.0
	_drift_yaw = 0.0
	_points_carry = 0.0
	_passes = 0
	_contacts = 0
	_last_contact = -9.0
	_bump_fx_t = 0.0
	_engine_t = 0.0
	_skid_t = 0.0
	_rumble_t = 0.0
	_tilt_recenter = true
	_tilt_input = 0.0
	_rank_cars()
	_last_place = _place[0]
	for i in range(1, CARS):
		_was_ahead[i] = 1 if _is_ahead(i, 0) else 0


func _wrap(seg: int) -> int:
	return posmod(seg, _segs)


func _seg_of(d: float) -> int:
	return posmod(floori(d / SEG), _segs)


func _lap_of(d: float) -> int:
	return clampi(floori(d / _lap_len), 0, LAPS - 1)


func tickets_for(p_score: int) -> int:
	return RacerTuning.BASE_TICKETS + p_score / RacerTuning.POINTS_PER_TICKET


func is_settled() -> bool:
	return _race_done or _cv[0] < 60.0


func on_time_up() -> void:
	cancel_input()
	if _race_done:
		return
	# Out of time before the flag: placed by how far round everyone got.
	_final_place = _place[0]
	var points := int(RacerTuning.PLACE_POINTS[_final_place - 1] * RacerTuning.DNF_SHARE)
	add_score(points, GAME_W / 2.0, 330.0, Pal.ORANGE, _dnf_labels[_final_place - 1])


## Forgets both fingers: the car holds its line, and a drift in progress ends without a boost.
func cancel_input() -> void:
	_steer_id = -1
	_drift_id = -1
	_drifting = false
	_charge = 0.0
	# Whoever picks the phone up again holds it differently.
	_tilt_recenter = true


func _tilt_live() -> bool:
	return tilt_steering and _tilt_seen


## A reading of the phone's lean (radians, right positive; see TiltMath.lean).
func on_tilt(lean: float) -> void:
	_tilt_seen = true
	_tilt_lean = lean
	if _tilt_recenter:
		_tilt_neutral = lean
		_tilt_recenter = false
	_tilt_input = TiltMath.steer(lean, _tilt_neutral)


func on_touch(type: int, pid: int, x: float, y: float, _time_ms: int) -> void:
	if type == TouchType.DOWN:
		if pid == _steer_id or pid == _drift_id:
			pass  # Already tracked: a repeated DOWN changes nothing.
		elif _drift_id < 0 and (_tilt_live() or DriftButton.hit(x, y)):
			# The button always drifts, even as the first finger; under tilt any finger does.
			_drift_id = pid
			_start_drift()
		elif _steer_id < 0 and not _tilt_live():
			_steer_id = pid
			_last_steer_x = x
		elif _drift_id < 0:
			_drift_id = pid
			_start_drift()
	elif type == TouchType.MOVE:
		if pid == _steer_id:
			# Relative steering, one finger delta at a time, so the curve's push (applied to the
			# target in step) is never thrown away by the finger moving.
			_steer_target = clampf(_steer_target + (x - _last_steer_x) * RacerTuning.STEER_GAIN, -X_LIMIT, X_LIMIT)
			_last_steer_x = x
	elif type == TouchType.UP:
		if pid == _steer_id:
			_steer_id = -1
		if pid == _drift_id:
			_drift_id = -1
			_release_drift()


func _start_drift() -> void:
	if _race_done or time_up:
		return
	_drifting = true
	_ever_drifted = true
	_charge = 0.0
	_skid_t = 0.0


func _release_drift() -> void:
	if not _drifting:
		return
	_drifting = false
	var t := 0.0
	if _charge >= RacerTuning.BOOST_CHARGE_2:
		t = RacerTuning.BOOST_TIME_2
	elif _charge >= RacerTuning.BOOST_CHARGE_1:
		t = RacerTuning.BOOST_TIME_1
	_charge = 0.0
	if t <= 0.0:
		return
	_boost_t = maxf(_boost_t, t)
	_boost_full = _boost_t
	play(Sfx.BOOST, 0.8, 1.15 if t > RacerTuning.BOOST_TIME_1 else 1.0)
	fx.haptics.hit()
	popups.add("SUPER TURBO!" if t > RacerTuning.BOOST_TIME_1 else "TURBO!", GAME_W / 2.0, 450.0, Pal.CYAN, 3.0)


func step(dt: float) -> void:
	_bump_fx_t -= dt
	if _boost_t > 0.0:
		_boost_t -= dt
	var racing := not time_up and not _race_done
	# The start of the race is the moment the phone's tilt counts as level.
	if time <= dt * 1.5:
		_tilt_recenter = true
	if racing and _tilt_live() and not _tilt_recenter:
		# Lean as a steering wheel: the further over, the faster the target slides that way.
		_steer_target = clampf(_steer_target + _tilt_input * RacerTuning.TILT_RATE * dt, -X_LIMIT, X_LIMIT)
	_step_player(dt, racing)
	if not solo_for_tests:
		_step_rivals(dt)
		_resolve_contacts(dt)
	_check_finishes()
	_rank_cars()
	if not time_up and not _race_done:
		_score_passes(dt)

	_engine_t -= dt
	if _engine_t <= 0.0 and _cv[0] > 40.0:
		_engine_t = 0.2
		play(Sfx.ENGINE, 0.3, 0.55 + _cv[0] / RacerTuning.MAX_SPEED * 0.9)
	if _drifting:
		_skid_t -= dt
		if _skid_t <= 0.0 and absf(_curve[_seg_of(_cd[0])]) > 0.3:
			_skid_t = 0.45
			play(Sfx.SKID, 0.35, 0.9 + MathUtil.clamp01(_charge / RacerTuning.BOOST_CHARGE_2) * 0.4)
	# A light hum through the phone: faint with the engine, firmer when a wheel is over the rumble
	# strips or the grass. (Haptics rate-limits it and never lets it cut off a hit.)
	_rumble_t -= dt
	if _rumble_t <= 0.0 and racing and _cv[0] > 150.0:
		var pace := MathUtil.clamp01(_cv[0] / RacerTuning.MAX_SPEED)
		if absf(_cx[0]) + CAR_HALF_W > ROAD_HALF:
			_rumble_t = 0.11
			fx.haptics.rumble(0.55 + 0.4 * pace)
		else:
			_rumble_t = 0.2
			fx.haptics.rumble(0.1 + 0.25 * pace)
	_step_fx(dt)


## Ages the smoke and marks and lays new ones while the car drifts.
func _step_fx(dt: float) -> void:
	_fx_tick += 1
	for i in PUFFS:
		if _puff_age[i] < PUFF_LIFE:
			_puff_age[i] += dt
	for i in SKIDS:
		if _skid_age[i] < SKID_LIFE:
			_skid_age[i] += dt
	if _drifting and _cv[0] > 250.0 and not _race_done and not time_up:
		_puff_t -= dt
		if _puff_t <= 0.0:
			_puff_t = 0.045
			var side := -16.0 if _fx_tick % 2 == 0 else 16.0
			_puff_d[_puff_next] = _cd[0] - 18.0
			_puff_x[_puff_next] = _cx[0] + side + (MathUtil.hash01(_fx_tick, 3) - 0.5) * 10.0
			_puff_age[_puff_next] = 0.0
			if _charge >= RacerTuning.BOOST_CHARGE_2:
				_puff_tint[_puff_next] = Pal.ORANGE
			elif _charge >= RacerTuning.BOOST_CHARGE_1:
				_puff_tint[_puff_next] = Pal.CYAN
			else:
				_puff_tint[_puff_next] = SMOKE_GREY
			_puff_next = (_puff_next + 1) % PUFFS
		# A mark behind each rear wheel, joined up as the car moves on.
		for w in 2:
			var d := _cd[0] - 14.0
			var x := _cx[0] + (-15.0 if w == 0 else 15.0)
			if _last_skid_d[w] >= 0.0 and d - _last_skid_d[w] > 9.0:
				_skid_d0[_skid_next] = _last_skid_d[w]
				_skid_x0[_skid_next] = _last_skid_x[w]
				_skid_d1[_skid_next] = d
				_skid_x1[_skid_next] = x
				_skid_age[_skid_next] = 0.0
				_skid_next = (_skid_next + 1) % SKIDS
				_last_skid_d[w] = d
				_last_skid_x[w] = x
			elif _last_skid_d[w] < 0.0:
				_last_skid_d[w] = d
				_last_skid_x[w] = x
	else:
		_last_skid_d[0] = -1.0
		_last_skid_d[1] = -1.0


## Projects a world point into the shared point (Kotlin's `stage.toField(x, y, z, pt)`): false, and
## the point left as it was, when it is behind the eye.
func _to_field(x: float, y: float, z: float) -> bool:
	var p: Variant = _stage.to_field(x, y, z)
	if p == null:
		return false
	var v: Vector3 = p
	_pt0 = v.x
	_pt1 = v.y
	_pt2 = v.z
	return true


func _step_player(dt: float, racing: bool) -> void:
	var bend := _curve[_seg_of(_cd[0])]
	var offroad := absf(_cx[0]) > ROAD_HALF + 4.0
	var top: float
	if _race_done:
		top = RacerTuning.COAST_SPEED
	elif time_up:
		top = 0.0
	elif _boost_t > 0.0:
		top = RacerTuning.BOOST_SPEED
	else:
		top = RacerTuning.MAX_SPEED
	# Bends scrub speed off; a drift carries most of it through.
	top *= 1.0 - minf(absf(bend), 2.5) * RacerTuning.CORNER_SCRUB * (RacerTuning.DRIFT_SCRUB if _drifting else 1.0)
	# ...but sliding sideways down a straight only scrubs speed off.
	if _drifting and absf(bend) < RacerTuning.DRIFT_MIN_BEND:
		top *= 1.0 - RacerTuning.DRIFT_DRAG
	if offroad:
		top = minf(top, RacerTuning.OFFROAD_SPEED)
	if _cv[0] < top:
		_cv[0] = minf(_cv[0] + (RacerTuning.ACCEL + (RacerTuning.BOOST_ACCEL if _boost_t > 0.0 else 0.0)) * dt, top)
	else:
		_cv[0] = MathUtil.approach(_cv[0], top, (900.0 if time_up and not _race_done else 600.0) * dt)
	# Past the flag the car eases back to the middle of the road by itself.
	if _race_done:
		_steer_target = MathUtil.approach(_steer_target, 0.0, 160.0 * dt)
	var prev_x := _cx[0]
	_cx[0] = MathUtil.approach(_cx[0], _steer_target, RacerTuning.STEER_SPEED * dt)
	# The curve pushes the car, and where the finger is steering it, towards the outside.
	var push := bend * (_cv[0] / RacerTuning.MAX_SPEED) * RacerTuning.CENTRIFUGAL * dt * (RacerTuning.DRIFT_GRIP if _drifting else 1.0)
	_cx[0] = clampf(_cx[0] - push, -X_LIMIT, X_LIMIT)
	_steer_target = clampf(_steer_target - push, -X_LIMIT, X_LIMIT)
	_tilt = MathUtil.damp(_tilt, clampf((_cx[0] - prev_x) / dt / 900.0, -0.25, 0.25), 8.0, dt)

	if _drifting:
		if _cv[0] > 300.0:
			_charge = minf(_charge + absf(bend) * (_cv[0] / RacerTuning.MAX_SPEED) * RacerTuning.DRIFT_CHARGE * dt, RacerTuning.BOOST_CHARGE_2 + 0.4)
		# The tail steps out: the nose points into the bend.
		var yaw_to := 0.0
		if bend > 0.05:
			yaw_to = -0.32
		elif bend < -0.05:
			yaw_to = 0.32
		_drift_yaw = MathUtil.damp(_drift_yaw, yaw_to, 6.0, dt)
		if _charge >= RacerTuning.BOOST_CHARGE_1 and rng.next_float() < 0.5:
			var c := Pal.ORANGE if _charge >= RacerTuning.BOOST_CHARGE_2 else Pal.CYAN
			var side := -18.0 if rng.next_boolean() else 18.0
			_to_field(_cx[0] + side, _car_y + 2.0, 34.0)
			particles.spawn(_pt0, _pt1, rng.range_f(-50.0, 50.0), rng.range_f(-90.0, -30.0), 0.3, 3.0, c)
	else:
		_drift_yaw = MathUtil.damp(_drift_yaw, 0.0, 8.0, dt)

	var before := _cd[0]
	_cd[0] += _cv[0] * dt * (1.0 + _cx[0] * bend * INSIDE_GAIN)
	# Distance only pays on the road: a car left to wander along the verge earns nothing.
	if racing and _cd[0] > 0.0 and not offroad:
		_points_carry += (_cd[0] - maxf(before, 0.0)) / RacerTuning.UNITS_PER_POINT
		var whole := int(_points_carry)
		if whole > 0:
			_points_carry -= whole
			score += whole
	if offroad and _cv[0] > 200.0 and rng.next_float() < 0.4:
		_to_field(_cx[0], 0.0, 0.0)
		particles.spawn(_pt0 + rng.range_f(-20.0, 20.0), _pt1, rng.range_f(-40.0, 40.0), rng.range_f(-80.0, -20.0), 0.4, 4.0, Pal.shade(Pal.PURPLE, 0.8))
	if _boost_t > 0.0 and rng.next_float() < 0.6:
		_to_field(_cx[0], _car_y + 10.0, 44.0)
		particles.spawn(_pt0 + rng.range_f(-6.0, 6.0), _pt1, rng.range_f(-30.0, 30.0), rng.range_f(20.0, 90.0), 0.25, 4.0, Pal.CYAN if rng.next_boolean() else Pal.WHITE)


func _step_rivals(dt: float) -> void:
	var lim := ROAD_HALF - CAR_HALF_W - 6.0
	for i in range(1, CARS):
		var d := _cd[i]
		var seg := _seg_of(d)
		var bend := _curve[seg]
		var line := _line_at[seg] + _bias[i]
		if _pass_t[i] > 0.0:
			_pass_t[i] -= dt
		else:
			# A slower car just ahead in our path: pull out and pass it.
			var nearest := AVOID_RANGE
			var found := -1
			var x := _cx[i]
			var v := _cv[i]
			for j in CARS:
				if j == i:
					continue
				var dd := _cd[j] - d
				if dd > 0.0 and dd < nearest and absf(_cx[j] - x) < CAR_W + 14.0 and _cv[j] < v + 40.0:
					nearest = dd
					found = j
			if found >= 0:
				var ox := _cx[found]
				var left := ox - PASS_GAP
				var right := ox + PASS_GAP
				var px: float
				if left < -lim:
					px = right
				elif right > lim:
					px = left
				elif absf(line - left) < absf(line - right):
					px = left
				else:
					px = right
				_pass_x[i] = clampf(px, -lim, lim)
				_pass_t[i] = 0.9
		var target := _pass_x[i] if _pass_t[i] > 0.0 else clampf(line, -lim, lim)
		var prev_x := _cx[i]
		_cx[i] = MathUtil.approach(prev_x, target, RacerTuning.AI_STEER * dt)
		_yaw[i] = MathUtil.damp(_yaw[i], -clampf((_cx[i] - prev_x) / dt / 900.0, -0.2, 0.2), 8.0, dt)
		var top := RacerTuning.MAX_SPEED * _skill[i] * (1.0 - minf(absf(bend), 2.5) * RacerTuning.AI_CORNER_SLOW)
		if _finish[i] > 0:
			top = RacerTuning.COAST_SPEED
		elif not _race_done and not time_up:
			# Mild rubber band: ease off when far ahead of the player, push on when far behind.
			top *= 1.0 - RacerTuning.RUBBER * clampf((_cd[i] - _cd[0]) / RacerTuning.RUBBER_RANGE, -1.0, 1.0)
		if time < _launch[i]:
			top = 0.0
		if _cv[i] < top:
			_cv[i] = minf(_cv[i] + RacerTuning.AI_ACCEL * dt, top)
		else:
			_cv[i] = MathUtil.approach(_cv[i], top, 500.0 * dt)
		_cd[i] += _cv[i] * dt * (1.0 + _cx[i] * bend * INSIDE_GAIN)


## Cars that touch push apart sideways; one running into the back of another is slowed to its pace.
func _resolve_contacts(dt: float) -> void:
	for i in CARS - 1:
		for j in range(i + 1, CARS):
			var dd := _cd[j] - _cd[i]
			if dd >= CAR_LEN or dd <= -CAR_LEN:
				continue
			var dx := _cx[j] - _cx[i]
			if dx >= CAR_W or dx <= -CAR_W:
				continue
			var side := 1.0 if dx >= 0.0 else -1.0
			var shove := minf((CAR_W - absf(dx)) * 0.5, RacerTuning.BUMP_SHOVE * dt)
			_nudge(i, -side * shove)
			_nudge(j, side * shove)
			if absf(dd) > CAR_LEN * 0.45:
				var rear := i if dd > 0.0 else j
				var front := j if dd > 0.0 else i
				if _cv[rear] > _cv[front]:
					_cv[front] += (_cv[rear] - _cv[front]) * 0.2
					_cv[rear] = maxf(_cv[front] - 10.0, 0.0)
			if i == 0:
				_touched(j)


func _nudge(i: int, dx: float) -> void:
	if i == 0:
		_cx[0] = clampf(_cx[0] + dx, -X_LIMIT, X_LIMIT)
		_steer_target = clampf(_steer_target + dx, -X_LIMIT, X_LIMIT)
	else:
		_cx[i] = clampf(_cx[i] + dx, -AI_X_LIMIT, AI_X_LIMIT)


func _touched(other: int) -> void:
	if time - _last_contact > 0.3:
		_contacts += 1
	_last_contact = time
	if _bump_fx_t > 0.0 or time_up:
		return
	_bump_fx_t = 0.4
	play(Sfx.CRASH, 0.35, 1.5)
	fx.haptics.hit()
	shake.add(0.2)
	_to_field((_cx[0] + _cx[other]) / 2.0, _car_y + 12.0, clampf((_cd[0] - _cd[other]) * 0.5, -40.0, 40.0))
	particles.burst(_pt0, _pt1, 10, 60.0, 200.0, SPARK_COLORS, 0.35, 3.0)


func _check_finishes() -> void:
	for i in CARS:
		if _finish[i] != 0 or _cd[i] < _race_len:
			continue
		if i == 0:
			# Rolling over the line after the clock ran out doesn't count.
			if time_up:
				continue
			_finish_count += 1
			_finish[0] = _finish_count
			_flag_fall()
		else:
			_finish_count += 1
			_finish[i] = _finish_count
	var lap := _lap_of(_cd[0])
	if lap > _lap_shown and not _race_done and not time_up:
		_lap_shown = lap
		play(Sfx.LAP, 0.8)
		fx.haptics.tick()
		var last := lap == LAPS - 1
		popups.add("FINAL LAP!" if last else LAP_POPUPS[lap], GAME_W / 2.0, 250.0, Pal.ORANGE if last else Pal.CYAN, 4.0)


func _flag_fall() -> void:
	_race_done = true
	ended_early = true
	_final_place = _finish[0]
	_drifting = false
	_charge = 0.0
	add_score(RacerTuning.PLACE_POINTS[_final_place - 1], GAME_W / 2.0, 300.0, Pal.GOLD, _place_labels[_final_place - 1])
	var bonus := int(time_left * RacerTuning.TIME_BONUS_PER_SECOND)
	if bonus > 0:
		add_score(bonus, GAME_W / 2.0, 345.0, Pal.CYAN, "TIME BONUS +%d" % bonus)
	play(Sfx.FINISH, 1.0)
	if _final_place == 1:
		play(Sfx.CHEER, 0.7)
	fx.haptics.win()
	flash.trigger(0.35)
	particles.burst(GAME_W / 2.0, 260.0, 60, 80.0, 320.0, CONFETTI_COLORS, 1.2, 5.0, 0.0, 2.0, Particles.SPARKLE)


## Whether car [param j] is placed ahead of car [param i]: finishers by order, the rest by distance.
func _is_ahead(j: int, i: int) -> bool:
	var af := _finish[j]
	var bf := _finish[i]
	if af > 0:
		return bf == 0 or af < bf
	return bf == 0 and _cd[j] > _cd[i]


## Every car's place: one plus the cars ahead of it (the test of _is_ahead, inlined: this runs every step).
func _rank_cars() -> void:
	for i in CARS:
		var bf := _finish[i]
		var bd := _cd[i]
		var r := 1
		for j in CARS:
			if j == i:
				continue
			var af := _finish[j]
			if af > 0:
				if bf == 0 or af < bf:
					r += 1
			elif bf == 0 and _cd[j] > bd:
				r += 1
		_place[i] = r


## Pays for clean passes: a rival that had been ahead for a while, overtaken without contact.
func _score_passes(dt: float) -> void:
	for i in range(1, CARS):
		var ahead := _is_ahead(i, 0)
		if ahead:
			_ahead_t[i] += dt
		else:
			if _was_ahead[i] == 1 and _ahead_t[i] >= 1.0 and time - _last_contact >= RacerTuning.CLEAN_SECONDS:
				_passes += 1
				add_score(RacerTuning.OVERTAKE_POINTS, GAME_W / 2.0, 380.0, Pal.CYAN, PASS_LABEL)
				play(Sfx.WHOOSH, 0.6, 1.2)
				fx.haptics.tick()
			_ahead_t[i] = 0.0
		_was_ahead[i] = 1 if ahead else 0
	if _place[0] < _last_place:
		play(Sfx.SELECT, 0.4, 1.3)
	_last_place = _place[0]


# ---------------------------------------------------------------- 3D presentation

func render(scope: DrawScope) -> void:
	_build_track()
	_cam_y = _car_y + CAM_UP
	var px := _cx[0]
	# The turbo's ramp (in over 0.12 s, out over its last 0.35 s) and the field of view easing with it.
	_boost_k = minf(MathUtil.clamp01(_boost_t / 0.35), MathUtil.clamp01((_boost_full - _boost_t) / 0.12)) if _boost_t > 0.0 else 0.0
	var dt_r := clampf(time - _last_render_t, 0.0, 0.1)
	_last_render_t = time
	_fov_now = MathUtil.damp(_fov_now, FOV_BOOST if _boost_t > 0.0 else FOV_BASE, 6.0, dt_r)
	# The camera follows the car most of the way across, so it stays in view off the road.
	_stage.look(px * 0.8, _cam_y, CAM_BACK, px * 0.5, _car_y + 20.0, -420.0, _fov_now, 0.44)
	var r := _stage.begin()
	var l := r.lighting
	l.amb_r = 0.55
	l.amb_g = 0.5
	l.amb_b = 0.7
	l.set_direction(0.0, 1.0, 0.3)
	l.dir_r = 0.3
	l.dir_g = 0.25
	l.dir_b = 0.35
	l.points.clear()
	_headlight.x = px
	_headlight.y = _car_y + 40.0
	l.points.append(_headlight)
	l.points.append(_sun_light)
	if _boost_k > 0.01:
		# The turbo's flames light the road round the car: cyan, or gold for a super turbo.
		_boost_light.x = px
		_boost_light.y = _car_y + 30.0
		if _boost_full > RacerTuning.BOOST_TIME_1:
			_boost_light.r = 1.0
			_boost_light.g = 0.6
			_boost_light.b = 0.2
		else:
			_boost_light.r = 0.4
			_boost_light.g = 0.9
			_boost_light.b = 1.0
		_boost_light.intensity = 1.3 * _boost_k * (0.9 + 0.1 * sin(time * 60.0))
		l.points.append(_boost_light)
	var half_h := float(int(r.height * 0.5))
	r.gradient(0xFF0A0420, 0xFF5A1850, 0.0, half_h)
	r.gradient(0xFF5A1850, 0xFF14082A, half_h, float(r.height))
	# The sky is unaffected by fog; everything on the ground fades into the night.
	r.fog_near = 1e8
	r.fog_far = 2e8
	_scene.draw_sky(r, -_seg_x[VIEW + BEHIND] * 0.08, time)
	r.fog_near = 900.0
	r.fog_far = 2900.0
	r.fog_floor = 0.12
	_draw_road(r)
	_draw_grid(r)
	_draw_skids(r)
	_draw_props(r)
	_draw_gantries(r)
	_draw_rivals(r)
	_draw_player(r)
	_draw_smoke(r)
	_scene.draw_speed_lines(r, px, _car_y, _cv[0] / RacerTuning.MAX_SPEED, _boost_k, time)
	_stage.present()
	_draw_hud(scope)


## Lays the road out ahead of the car: each segment bends and climbs a little more.
func _build_track() -> void:
	var pos := _cd[0] / SEG
	var base := floori(pos)
	_frac = pos - base
	var x := 0.0
	var dx := 0.0
	var y := 0.0
	for k in VIEW + 1:
		var i := _wrap(base + k)
		_seg_x[k + BEHIND] = x
		_seg_y[k + BEHIND] = y
		_seg_z[k + BEHIND] = (_frac - k) * SEG
		x += dx
		dx += _curve[i]
		y += _slope[i]
	# Behind the car the road runs straight back, following the hills.
	y = 0.0
	for k in range(-1, -BEHIND - 1, -1):
		y -= _slope[_wrap(base + k)]
		_seg_x[k + BEHIND] = 0.0
		_seg_y[k + BEHIND] = y
		_seg_z[k + BEHIND] = (_frac - k) * SEG
	_car_y = _slope[_wrap(base)] * _frac


## World position of a point [param d] along the course and [param x] across it (into the shared
## point); false if out of view.
func _track_point(d: float, x: float) -> bool:
	var k := (d - _cd[0]) / SEG + _frac + BEHIND
	if k < 0.0 or k >= VIEW + BEHIND:
		return false
	var i := int(k)
	var f := k - i
	_pt0 = _seg_x[i] + (_seg_x[i + 1] - _seg_x[i]) * f + x
	_pt1 = _seg_y[i] + (_seg_y[i + 1] - _seg_y[i]) * f
	_pt2 = _seg_z[i] + (_seg_z[i + 1] - _seg_z[i]) * f
	return true


func _draw_road(r: Renderer3D) -> void:
	var base := floori(_cd[0] / SEG)
	var white := RacerArt.white().full()
	var ground := RacerArt.ground().full()
	var asphalt_light := RacerArt.asphalt().full()
	var asphalt_dark := RacerArt.asphalt_dark().full()
	var checker := RacerArt.checker().full()
	var sx := _seg_x
	var sy := _seg_y
	var sz := _seg_z
	for j in VIEW + BEHIND:
		var i := _wrap(base + j - BEHIND)
		var x0 := sx[j]
		var y0 := sy[j]
		var z0 := sz[j]
		var x1 := sx[j + 1]
		var y1 := sy[j + 1]
		var z1 := sz[j + 1]
		# Far out, where fog has all but hidden it, the neon ground is laid in two-segment strips (a
		# little lower, so a hill can't push it through the road).
		var far_ground := j >= BEHIND + FAR_GROUND_FROM
		if not far_ground or j % 2 == 0:
			var jn := j + 2 if far_ground and j + 2 <= VIEW + BEHIND else j + 1
			var drop := 1.5 if far_ground else 0.5
			r.quad(sx[jn] - GROUND_HALF, sy[jn] - drop, sz[jn], sx[jn] + GROUND_HALF, sy[jn] - drop, sz[jn],
				x0 + GROUND_HALF, y0 - drop, z0, x0 - GROUND_HALF, y0 - drop, z0,
				ground, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.9)
		var asphalt := asphalt_light if (i / 2) % 2 == 0 else asphalt_dark
		r.quad(x1 - ROAD_HALF, y1, z1, x1 + ROAD_HALF, y1, z1, x0 + ROAD_HALF, y0, z0, x0 - ROAD_HALF, y0, z0,
			asphalt, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, true, -1, 1.0, RacerLook.ROAD_GLOSS)
		var near := j < BEHIND + RacerLook.DETAIL_SEGMENTS
		if near:
			# A bright edge line just inside each rumble strip.
			var e0 := ROAD_HALF - 5.0
			var e1 := ROAD_HALF - 2.5
			r.quad(x1 - e0, y1 + 0.35, z1, x1 - e1, y1 + 0.35, z1, x0 - e1, y0 + 0.35, z0, x0 - e0, y0 + 0.35, z0,
				white, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, RacerLook.EDGE_LINE_EMISSIVE, 1.0, true, 0xFFCFF4FF)
			r.quad(x1 + e1, y1 + 0.35, z1, x1 + e0, y1 + 0.35, z1, x0 + e0, y0 + 0.35, z0, x0 + e1, y0 + 0.35, z0,
				white, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, RacerLook.EDGE_LINE_EMISSIVE, 1.0, true, 0xFFCFF4FF)
		# Neon rumble strips.
		var rumble := Pal.CYAN if i % 2 == 0 else Pal.PINK
		var a := ROAD_HALF
		var b := ROAD_HALF + 16.0
		r.quad(x1 - b, y1 + 0.3, z1, x1 - a, y1 + 0.3, z1, x0 - a, y0 + 0.3, z0, x0 - b, y0 + 0.3, z0,
			white, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.1, 1.0, true, rumble)
		r.quad(x1 + a, y1 + 0.3, z1, x1 + b, y1 + 0.3, z1, x0 + b, y0 + 0.3, z0, x0 + a, y0 + 0.3, z0,
			white, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.1, 1.0, true, rumble)
		# Dashed lane lines.
		if near and i % 3 == 0:
			var h := LANE / 2.0
			r.quad(x1 - h - 2.5, y1 + 0.3, z1, x1 - h + 2.5, y1 + 0.3, z1, x0 - h + 2.5, y0 + 0.3, z0, x0 - h - 2.5, y0 + 0.3, z0,
				white, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.9)
			r.quad(x1 + h - 2.5, y1 + 0.3, z1, x1 + h + 2.5, y1 + 0.3, z1, x0 + h + 2.5, y0 + 0.3, z0, x0 + h - 2.5, y0 + 0.3, z0,
				white, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.9)
		# The chequered start/finish line.
		if i == 0:
			r.quad(x0 - ROAD_HALF, y0 + 0.4, z0 - 18.0, x0 + ROAD_HALF, y0 + 0.4, z0 - 18.0, x0 + ROAD_HALF, y0 + 0.4, z0, x0 - ROAD_HALF, y0 + 0.4, z0,
				checker, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.9)


## Painted grid boxes behind the start line.
func _draw_grid(r: Renderer3D) -> void:
	var lap_start := floorf(_cd[0] / _lap_len + 0.5) * _lap_len
	var white := RacerArt.white().full()
	for slot in CARS:
		var d := lap_start - (30.0 + (slot / 2) * GRID_ROW + (slot % 2) * 25.0) + CAR_LEN / 2.0 + 6.0
		if not _track_point(d, -52.0 if slot % 2 == 0 else 52.0):
			continue
		r.flat(_pt0, _pt2, _pt1 + 0.4, 56.0, 3.0, white, 0.0, Blend.ALPHA, 0.9, 0.8)


func _draw_props(r: Renderer3D) -> void:
	var base := floori(_cd[0] / SEG)
	var palm := RacerArt.palm().full()
	var white := RacerArt.white().full()
	var glow := TexKit.glow().full()
	var post := RacerArt.post().full()
	var signs := RacerArt.signs()
	for j in range(VIEW + BEHIND - 1, 0, -1):
		var i := _wrap(base + j - BEHIND)
		var sxj := _seg_x[j]
		var syj := _seg_y[j]
		var szj := _seg_z[j]
		if i % 5 == 0:
			var off_l := ROAD_HALF + 70.0 + MathUtil.hash01(i, 2) * 60.0
			var off_r := ROAD_HALF + 70.0 + MathUtil.hash01(i, 4) * 60.0
			r.billboard(sxj - off_l, syj, szj, 90.0, 140.0, palm, false, 0.0)
			r.billboard(sxj + off_r, syj, szj, 90.0, 140.0, palm, false, 0.0)
		if i % 5 == 2:
			# A neon pylon, alternating sides and colours, with a glow at its head.
			var side := 1.0 if (i / 5) % 2 == 0 else -1.0
			var px := sxj + side * (ROAD_HALF + 50.0)
			var tint := Pal.CYAN if (i / 10) % 2 == 0 else Pal.PINK
			r.billboard(px, syj, szj, 5.0, 130.0, white, false, 0.0, Blend.OPAQUE, 1.3, 1.0, 1.03, tint)
			r.sprite(px, syj + 132.0, szj, 46.0, 46.0, glow, 0.0, Blend.ADD, 1.0, 0.55, 1.0, tint)
		if i % 23 == 11:
			var side := 1.0 if MathUtil.hash01(i, 9) > 0.5 else -1.0
			var x := sxj + side * (ROAD_HALF + 110.0)
			var sign_region: Region = (signs[(i / 23) % signs.size()] as PaTexture).full()
			r.billboard(x, syj + 70.0, szj, 150.0, 55.0, sign_region, false, 0.0, Blend.OPAQUE, 1.1)
			r.billboard(x, syj, szj - 1.0, 6.0, 70.0, post, false, 0.0)


## The start/finish gantry (with its start lights) over each line in view.
func _draw_gantries(r: Renderer3D) -> void:
	var first := floorf(_cd[0] / _lap_len) * _lap_len
	var glow := TexKit.glow().full()
	var post := RacerArt.post().full()
	var banner := RacerArt.banner().full()
	var lamp_box := RacerArt.lamp_box().full()
	# Five start lights: red on the grid, green at the start, dark after that.
	var light_color := 0
	if time <= 0.0:
		light_color = Pal.RED
	elif time < 3.0:
		light_color = Pal.GREEN
	for k in 2:
		if not _track_point(first + k * _lap_len, 0.0):
			continue
		var x := _pt0
		var y := _pt1
		var z := _pt2
		var span := ROAD_HALF + 40.0
		r.billboard(x - span, y, z, 12.0, 160.0, post, false, 0.0)
		r.billboard(x + span, y, z, 12.0, 160.0, post, false, 0.0)
		r.quad(x - span, y + 160.0, z, x + span, y + 160.0, z, x + span, y + 124.0, z, x - span, y + 124.0, z,
			banner, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.1, 1.0, false)
		for n in range(-2, 3):
			var lx := x + n * 34.0
			r.quad(lx - 13.0, y + 124.0, z + 0.5, lx + 13.0, y + 124.0, z + 0.5, lx + 13.0, y + 102.0, z + 0.5, lx - 13.0, y + 102.0, z + 0.5,
				lamp_box, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, false)
			if light_color != 0:
				var lens := RacerArt.lamp_red().full() if light_color == Pal.RED else RacerArt.lamp_green().full()
				r.sprite(lx, y + 113.0, z + 1.5, 18.0, 18.0, lens, 0.0, Blend.OPAQUE, 1.0)
				r.sprite(lx, y + 113.0, z + 3.0, 56.0, 56.0, glow, 0.0, Blend.ADD, 1.0, 0.3, 1.0, light_color)
			else:
				r.sprite(lx, y + 113.0, z + 1.5, 18.0, 18.0, RacerArt.lamp_off().full(), 0.0, Blend.OPAQUE, 0.6)


## The road's slope at [param d] along the course (height gained per unit), eased between segments
## so a car's pitch doesn't tick.
func _grade_at(d: float) -> float:
	var pos := d / SEG
	var i := floori(pos)
	var f := pos - i
	return (_slope[_wrap(i)] + (_slope[_wrap(i + 1)] - _slope[_wrap(i)]) * f) / SEG


func _draw_rivals(r: Renderer3D) -> void:
	var glow := RacerArt.tail_glow().full()
	for i in range(1, CARS):
		if not _track_point(_cd[i], _cx[i]):
			continue
		_scene.draw_car(r, i, _color[i], _pt2 > -RacerLook.LOD_DISTANCE, _pt0, _pt1, _pt2, _yaw[i], 0.0, _grade_at(_cd[i]))
		r.sprite(_pt0, _pt1 + 11.0, _pt2 + 41.0, 70.0, 26.0, glow, 0.0, Blend.ADD, 1.0, 0.5, 1.0, Pal.RED)


func _draw_player(r: Renderer3D) -> void:
	var px := _cx[0]
	var bounce := sin(time * 30.0) * (_cv[0] / RacerTuning.MAX_SPEED) * 0.8
	_scene.draw_car(r, 0, _color[0], true, px, _car_y + bounce, 0.0, -_tilt * 0.6 + _drift_yaw, -_tilt, _grade_at(_cd[0]))
	var glow := RacerArt.tail_glow().full()
	r.sprite(px - 15.0, _car_y + 11.0, 41.0, 40.0, 24.0, glow, 0.0, Blend.ADD, 1.0, 0.7, 1.0, Pal.RED)
	r.sprite(px + 15.0, _car_y + 11.0, 41.0, 40.0, 24.0, glow, 0.0, Blend.ADD, 1.0, 0.7, 1.0, Pal.RED)
	_scene.draw_flames(r, px, _car_y, _boost_k, 1.0 if _boost_full > RacerTuning.BOOST_TIME_1 else 0.0, _cv[0] / RacerTuning.MAX_SPEED, time)


## Tyre smoke: soft puffs that swell and fade where the car drifted, tinted by the boost charge.
func _draw_smoke(r: Renderer3D) -> void:
	var tex := RacerArt.smoke().full()
	var calm := RacerLook.CALM_K if ScreenShake.intensity <= 0.0 else 1.0
	for i in PUFFS:
		var age := _puff_age[i]
		if age >= PUFF_LIFE or not _track_point(_puff_d[i], _puff_x[i]):
			continue
		var p := age / PUFF_LIFE
		var size := 14.0 + 46.0 * p
		var tint := _puff_tint[i]
		var coloured := tint != SMOKE_GREY
		r.sprite(_pt0, _pt1 + 6.0 + age * 14.0, _pt2, size, size * 0.8, tex, 0.0, Blend.ALPHA,
			0.9 if coloured else 0.55, RacerLook.SMOKE_ALPHA * (1.0 - p) * calm, 1.0, tint)


## Dark tyre marks laid behind the rear wheels while drifting, fading over a few seconds.
func _draw_skids(r: Renderer3D) -> void:
	var tex := RacerArt.skid().full()
	for i in SKIDS:
		var age := _skid_age[i]
		if age >= SKID_LIFE or not _track_point(_skid_d0[i], _skid_x0[i]):
			continue
		var ax := _pt0
		var ay := _pt1
		var az := _pt2
		if not _track_point(_skid_d1[i], _skid_x1[i]):
			continue
		var a := RacerLook.SKID_ALPHA * (1.0 - age / SKID_LIFE)
		r.quad(_pt0 - 2.6, _pt1 + 0.4, _pt2, _pt0 + 2.6, _pt1 + 0.4, _pt2, ax + 2.6, ay + 0.4, az, ax - 2.6, ay + 0.4, az,
			tex, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, a, false)


func _draw_hud(scope: DrawScope) -> void:
	var over := _race_done or time_up
	var p := clampi(_final_place if over and _final_place > 0 else _place[0], 1, CARS)
	var lap := _lap_of(_cd[0])
	# Position and lap.
	scope.draw_rect(Color.BLACK, Vector2(10.0, 10.0), Vector2(118.0, 50.0), 0.55)
	ArcadeFont.draw(scope, "POS", 18.0, 15.0, 1.4, Pal.LAVENDER)
	ArcadeFont.draw(scope, PLACE_TEXT[p - 1], 18.0, 29.0, 3.6, Pal.GOLD if p == 1 else Pal.WHITE)
	ArcadeFont.draw(scope, OF_CARS, 88.0, 38.0, 2.0, Pal.LAVENDER)
	scope.draw_rect(Color.BLACK, Vector2(250.0, 10.0), Vector2(100.0, 50.0), 0.55)
	ArcadeFont.draw(scope, "LAP", 258.0, 15.0, 1.4, Pal.LAVENDER)
	ArcadeFont.draw(scope, LAP_TEXT[lap], 258.0, 29.0, 3.6, Pal.ORANGE if lap == LAPS - 1 else Pal.CYAN)
	# Everyone's progress towards the flag.
	scope.draw_rect(Color.BLACK, Vector2(10.0, 66.0), Vector2(340.0, 14.0), 0.45)
	scope.draw_rect(Pal.LAVENDER, Vector2(16.0, 72.0), Vector2(328.0, 2.0), 0.6)
	for k in range(1, LAPS):
		scope.draw_rect(Pal.LAVENDER, Vector2(16.0 + 328.0 * k / LAPS, 68.0), Vector2(1.5, 10.0), 0.6)
	scope.draw_rect(Color.WHITE, Vector2(342.0, 67.0), Vector2(4.0, 12.0))
	for i in range(CARS - 1, -1, -1):
		var x := 16.0 + 328.0 * MathUtil.clamp01(_cd[i] / _race_len)
		if i == 0:
			scope.draw_circle(Color.WHITE, 6.0, Vector2(x, 73.0))
		scope.draw_circle(_color[i], 4.5 if i == 0 else 3.5, Vector2(x, 73.0))

	# Speed.
	var frac := MathUtil.clamp01(_cv[0] / RacerTuning.BOOST_SPEED)
	scope.draw_rect(Color.BLACK, Vector2(10.0, 592.0), Vector2(150.0, 36.0), 0.55)
	scope.draw_rect(Pal.CYAN if _boost_t > 0.0 else Pal.PINK, Vector2(16.0, 618.0), Vector2(138.0 * frac, 5.0))
	ArcadeFont.draw(scope, SPEED_TEXT[clampi(int(_cv[0] * 0.04), 0, SPEED_TEXT.size() - 1)], 16.0, 598.0, 2.0, Color.WHITE)
	# Drift charge, then the turbo burning.
	scope.draw_rect(Color.BLACK, Vector2(200.0, 592.0), Vector2(150.0, 36.0), 0.55)
	var label_color := Pal.GRAY
	if _boost_t > 0.0:
		label_color = Pal.CYAN
	elif _drifting:
		label_color = Pal.ORANGE
	ArcadeFont.draw(scope, "TURBO!" if _boost_t > 0.0 else "DRIFT", 206.0, 598.0, 2.0, label_color)
	scope.draw_rect(Pal.DEEP, Vector2(206.0, 618.0), Vector2(138.0, 5.0))
	if _boost_t > 0.0:
		scope.draw_rect(Pal.CYAN, Vector2(206.0, 618.0), Vector2(138.0 * MathUtil.clamp01(_boost_t / _boost_full), 5.0))
	else:
		var cc := Pal.LAVENDER
		if _charge >= RacerTuning.BOOST_CHARGE_2:
			cc = Pal.ORANGE
		elif _charge >= RacerTuning.BOOST_CHARGE_1:
			cc = Pal.CYAN
		scope.draw_rect(cc, Vector2(206.0, 618.0), Vector2(138.0 * MathUtil.clamp01(_charge / RacerTuning.BOOST_CHARGE_2), 5.0))
	scope.draw_rect(Color.WHITE, Vector2(206.0 + 138.0 * RacerTuning.BOOST_CHARGE_1 / RacerTuning.BOOST_CHARGE_2, 616.0), Vector2(1.5, 9.0))
	_draw_drift_button(scope, over)

	if not over:
		# The hints sit left of centre, clear of the DRIFT button.
		if time < 3.0:
			ArcadeFont.draw_centered(scope, "TILT TO STEER" if _tilt_live() else "DRAG TO STEER", HINT_X, 520.0, 2.0, Color.WHITE, 0.5 + 0.5 * sin(time * 6.0))
		elif not _ever_drifted and absf(_curve[_seg_of(_cd[0] + 6.0 * SEG)]) > 0.8:
			var a := 0.6 + 0.4 * sin(time * 8.0)
			ArcadeFont.draw_centered(scope, "HOLD DRIFT IN BENDS", HINT_X, 520.0, 2.0, Pal.ORANGE, a)
			ArcadeFont.draw_centered(scope, "LET GO: TURBO!", HINT_X, 544.0, 2.0, Pal.CYAN, a)
		if absf(_cx[0]) > ROAD_HALF + 4.0:
			ArcadeFont.draw_centered(scope, "OFF ROAD!", GAME_W / 2.0, 470.0, 3.0, Pal.ORANGE, 0.5 + 0.5 * sin(time * 12.0))
	elif _final_place > 0:
		ArcadeFont.draw_centered(scope, "FINISHED" if _race_done else "PLACED", GAME_W / 2.0, 392.0, 2.5, Pal.LAVENDER)
		ArcadeFont.draw_centered(scope, PLACE_TEXT[_final_place - 1], GAME_W / 2.0, 416.0, 7.0, Pal.GOLD if _final_place == 1 else Pal.WHITE)


## The DRIFT button: dim until held, then lit and ringed with the boost charge; faded once the race is over.
func _draw_drift_button(scope: DrawScope, over: bool) -> void:
	var c := Vector2(DriftButton.X, DriftButton.Y)
	var r := DriftButton.R
	var fade := 0.35 if over else 1.0
	var base := Pal.CYAN if _boost_t > 0.0 else Pal.ORANGE
	scope.draw_circle(Color.BLACK, r + 4.0, c, 0.55 * fade)
	scope.draw_circle(Pal.shade(base, 1.0 if _drifting else 0.5), r, c, (0.95 if _drifting else 0.7) * fade)
	scope.draw_circle(Color.WHITE, r, c, 0.4 * fade, _ring_stroke)
	if _drifting and _charge > 0.0:
		var ring := Pal.LAVENDER
		if _charge >= RacerTuning.BOOST_CHARGE_2:
			ring = Pal.ORANGE
		elif _charge >= RacerTuning.BOOST_CHARGE_1:
			ring = Pal.CYAN
		var out := r + 4.0
		scope.draw_arc(ring, -90.0, 360.0 * MathUtil.clamp01(_charge / RacerTuning.BOOST_CHARGE_2), false,
			Vector2(c.x - out, c.y - out), Vector2(out * 2.0, out * 2.0), 1.0, _charge_stroke)
	ArcadeFont.draw_centered(scope, "DRIFT", c.x, c.y - ArcadeFont.height(1.5) / 2.0, 1.5, Color.WHITE, fade)


# ---------------------------------------------------------------- simulation-test hooks

func bot_px() -> float:
	return _cx[0]


## Road position the car is steering for.
func bot_steer_target() -> float:
	return _steer_target


func bot_speed() -> float:
	return _cv[0]


func bot_progress() -> float:
	return _cd[0]


func bot_race_length() -> float:
	return _race_len


## The player's live position, or the final one once the race is over for them.
func bot_place() -> int:
	return _final_place if (_race_done or time_up) and _final_place > 0 else _place[0]


func bot_lap() -> int:
	return _lap_of(_cd[0]) + 1


func bot_race_done() -> bool:
	return _race_done


func bot_drifting() -> bool:
	return _drifting


## The steering the tilt asks for (-1..1).
func bot_tilt_input() -> float:
	return _tilt_input


## Whether tilt readings are steering the car.
func bot_tilt_live() -> bool:
	return _tilt_live()


func bot_boosting() -> bool:
	return _boost_t > 0.0


func bot_charge() -> float:
	return _charge


func bot_passes() -> int:
	return _passes


## Separate bumps the player's car has been in.
func bot_contacts() -> int:
	return _contacts


## The bend under the car (positive bends right and pushes the car left).
func bot_bend() -> float:
	return _curve[_seg_of(_cd[0])]


## The bend [param segments] ahead of the car.
func bot_bend_ahead(segments: int) -> float:
	return _curve[_seg_of(_cd[0] + segments * SEG)]


## Where the racing line runs at the car's position.
func bot_racing_line() -> float:
	return _line_at[_seg_of(_cd[0])]


func bot_car_d(i: int) -> float:
	return _cd[i]


func bot_car_x(i: int) -> float:
	return _cx[i]


func bot_car_v(i: int) -> float:
	return _cv[i]


func bot_finish_order(i: int) -> int:
	return _finish[i]


## Offset across the road of the nearest rival within [param p_range] ahead in the player's path, or NAN.
func bot_blocker_x(p_range: float) -> float:
	var best := p_range
	var x := NAN
	for i in range(1, CARS):
		var dd := _cd[i] - _cd[0]
		if dd > -CAR_LEN and dd < best and absf(_cx[i] - _cx[0]) < CAR_W + 34.0:
			best = dd
			x = _cx[i]
	return x


## Puts car [param i] at [param d] along the course and [param x] across it, doing [param v] (for rule tests).
func bot_place_car(i: int, d: float, x: float, v: float) -> void:
	_cd[i] = d
	_cx[i] = x
	_cv[i] = v
	if i == 0:
		_steer_target = x
		_lap_shown = _lap_of(d)
	_rank_cars()
	_last_place = _place[0]
	for k in range(1, CARS):
		_was_ahead[k] = 1 if _is_ahead(k, 0) else 0
		_ahead_t[k] = 0.0


# ---------------------------------------------------------------- attract mode

func draw_attract(p: Painter, w: int, h: int, t: float) -> void:
	var fw := float(w)
	var fh := float(h)
	var hz := fh * 0.42
	var night := 0xFF3A1250
	# Sky, setting sun and skyline.
	p.fill(0.0, 0.0, fw, hz * 0.55, 0xFF0A0420)
	p.fill(0.0, hz * 0.55, fw, hz * 0.45, night)
	# Stars, twinkling, and a rose glow along the horizon.
	for k in 14:
		p.disc(MathUtil.hash01(k, 21) * fw, MathUtil.hash01(k, 22) * hz * 0.55, 0.16, Pal.WHITE, 0.25 + 0.6 * (0.5 + 0.5 * sin(t * 2.0 + k * 2.3)))
	p.fill(0.0, hz - 1.6, fw, 1.6, Pal.PINK, 0.16)
	p.disc(fw / 2.0, hz - 0.2, 3.6, Pal.ORANGE, 0.22)
	p.disc(fw / 2.0, hz - 0.2, 3.4, Pal.ORANGE)
	p.disc(fw / 2.0, hz - 1.2, 2.6, Pal.PINK, 0.8)
	p.fill(fw / 2.0 - 4.0, hz - 1.6, 8.0, 0.35, night)
	p.fill(fw / 2.0 - 4.0, hz - 0.8, 8.0, 0.45, night)
	for k in 10:
		var bh := 0.8 + MathUtil.hash01(k, 7) * 2.2
		p.fill(k * 2.7, hz - bh, 2.2, bh, Pal.PLUM)
	p.fill(0.0, hz, fw, fh - hz, 0xFF14082A)

	# A 14 s loop: the grid under the start lights, lights out, then the race.
	var loop := fmod(t, 14.0)
	var go := loop - 2.4
	var run := maxf(go, 0.0)
	var scroll := run * 7.0
	var bend := sin(run * 0.5) * 5.0 if go > 0.0 else 0.0
	var rows := int((fh - hz) * 2.0)
	for row in rows:
		var y := hz + row * 0.5
		var tt := (row + 1.0) / rows
		var half := 0.4 + tt * fw * 0.62
		var cx := fw / 2.0 + bend * (1.0 - tt) * (1.0 - tt)
		p.fill(cx - half, y, half * 2.0, 0.5, 0xFF24203A)
		var band := int(scroll + 3.0 / tt)
		var rc := Pal.CYAN if band % 2 == 0 else Pal.PINK
		p.fill(cx - half - 0.5, y, 0.5, 0.5, rc)
		p.fill(cx + half, y, 0.5, 0.5, rc)
		if band % 3 == 0:
			p.fill(cx - 0.15, y, 0.3, 0.5, Pal.WHITE, 0.6)

	# Lights streaming out of the vanishing point once the race is on.
	if go > 0.0:
		for k in 10:
			var ang := 0.2 + k * 0.29
			var d := fmod(t * 9.0 + k * 2.7, 12.0)
			if d > 1.5:
				p.disc(fw / 2.0 + cos(ang) * d * 2.2, hz + sin(ang) * d * 0.9, 0.12 + d * 0.012, 0xFFCFE8FF, 0.35 * MathUtil.clamp01(go / 0.8))

	# Rivals, attract_gap car lengths ahead of the player; the slower ones drop back as they're passed.
	var ahead := 0
	var drawn := 0
	for pass_no in ATTRACT_RIVALS:
		# Far to near.
		var pick := -1
		var pick_gap := 0.0
		for k in ATTRACT_RIVALS:
			if drawn & (1 << k) != 0:
				continue
			var g := _attract_gap(k, run)
			if pick < 0 or g > pick_gap:
				pick = k
				pick_gap = g
		drawn = drawn | (1 << pick)
		if pick_gap > 0.0:
			ahead += 1
		if pick_gap < -0.6:
			continue
		var tt := 0.86 / (1.0 + maxf(pick_gap, -0.4) * 0.55)
		var y := hz + tt * (fh - hz)
		var half := 0.4 + tt * fw * 0.62
		var cx := fw / 2.0 + bend * (1.0 - tt) * (1.0 - tt) + (-0.45 if pick % 2 == 0 else 0.45) * half
		var cw := 0.5 + tt * 3.4
		var ch := cw * 0.55
		p.fill(cx - cw / 2.0, y - ch, cw, ch, RIVAL_COLORS[pick])
		p.fill(cx - cw / 2.0, y - ch * 0.35, cw * 0.25, ch * 0.2, Pal.RED)
		p.fill(cx + cw / 4.0, y - ch * 0.35, cw * 0.25, ch * 0.2, Pal.RED)

	# The start gantry over the grid: lights coming on one by one, then green.
	if go < 1.2:
		var tt := 0.3
		var y := hz + tt * (fh - hz)
		var half := 0.4 + tt * fw * 0.62
		p.fill(fw / 2.0 - half - 0.6, y - 5.0, 0.5, 5.0, Pal.GRAY)
		p.fill(fw / 2.0 + half + 0.1, y - 5.0, 0.5, 5.0, Pal.GRAY)
		p.fill(fw / 2.0 - half - 0.6, y - 5.6, half * 2.0 + 1.2, 1.6, 0xFF08060C)
		var lit := 5 if go >= 0.0 else mini(int(loop / 0.45), 5)
		var on := Pal.GREEN if go >= 0.0 else Pal.RED
		for n in 5:
			p.disc(fw / 2.0 + (n - 2) * 1.5, y - 4.8, 0.5, on if n < lit else Pal.DARKGRAY)
		# Chequered line across the road.
		for n in 12:
			p.fill(fw / 2.0 - half + n * half / 6.0, y, half / 6.0, 0.4, Pal.WHITE if n % 2 == 0 else Pal.BLACK)

	# The player's car, with its underglow and tail lights glowing.
	p.fill(fw / 2.0 - 3.4, fh - 0.9, 6.8, 0.8, Pal.RED, 0.28)
	p.disc(fw / 2.0 - 1.5, fh - 1.9, 1.2, Pal.RED, 0.25)
	p.disc(fw / 2.0 + 1.5, fh - 1.9, 1.2, Pal.RED, 0.25)
	p.fill(fw / 2.0 - 2.2, fh - 2.6, 4.4, 2.2, Pal.RED)
	p.fill(fw / 2.0 - 1.3, fh - 3.3, 2.6, 0.9, Pal.shade(Pal.SKY, 0.6))
	p.fill(fw / 2.0 - 2.0, fh - 1.6, 1.0, 0.5, Pal.YELLOW)
	p.fill(fw / 2.0 + 1.0, fh - 1.6, 1.0, 0.5, Pal.YELLOW)
	if go > 0.0 and int(t * 12.0) % 2 == 0:
		p.fill(fw / 2.0 - 0.6, fh - 0.5, 1.2, 0.5, Pal.CYAN)

	# Race HUD: position (the seventh rival trails behind, out of sight) and lap.
	var pos := 1 + ahead
	p.text(ATTRACT_POS[pos - 1], 0.6, 0.5, Pal.GOLD if pos == 1 else Pal.WHITE, true)
	var lap := clampi(int(run / 3.6), 0, LAPS - 1)
	p.text_centered(ATTRACT_LAP[lap], fw - 4.2, 0.5, Pal.CYAN, true)
	if loop < 2.4:
		var a := MathUtil.clamp01(minf(loop / 0.3, (2.4 - loop) / 0.4))
		p.text_centered("TURBO RACER", fw / 2.0, hz * 0.55, Pal.YELLOW, true, 0.45 * a, 0.55)
		p.text_centered("TURBO RACER", fw / 2.0, hz * 0.55, Pal.CREAM, true, a, 0.5)
	if go >= 0.0 and go < 0.9 and int(t * 6.0) % 2 == 0:
		p.text_centered("GO!", fw / 2.0, fh * 0.55, Pal.LIME, true)
	if loop > 12.6 and int(t * 3.0) % 2 == 0:
		p.text_centered("FINISH!", fw / 2.0, fh * 0.55, Pal.YELLOW, true)


## How many car lengths attract-mode rival [param k] runs ahead of the player, [param run] seconds after the start.
static func _attract_gap(k: int, run: float) -> float:
	return ATTRACT_START[k] + run * (ATTRACT_PACE[k] - 1.0)
