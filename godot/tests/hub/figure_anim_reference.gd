class_name FigureAnimReference
extends RefCounted
## Godot-only test helper: hub/FigureAnim.kt ported line by line, calling the helpers Kotlin calls
## (AnimSpring, Gait, AnimMath). scripts/hub/figure_anim.gd writes that maths out in place for speed;
## tests/hub/figure_anim_inline_test.gd checks the two agree.

## Reduce-motion setting: no bounce, no overshoot, calmer follow-through (set from the settings).
static var reduce_motion := false

## How far a seated figure's hips drop (figure units), and how far their legs stick out (radians, forward).
const SEAT_DROP := 5.0
const SEAT_LEG := 1.45

## The seat spring: frequency (rad/s) and damping ratio, which leaves a small settle on sitting down.
const SEAT_OMEGA := 12.0
const SEAT_ZETA := 0.62

# ---- moving about

## The longest step of time simulated in one update (a stalled frame isn't worth animating in full).
const MAX_DT := 0.1
## The figure's own velocity is smoothed at this rate (per second) before it is differentiated into an acceleration.
const VEL_SMOOTH := 22.0
## A move faster than this (world units a second) in one step is a shove or a teleport, not walking: it isn't animated.
const MAX_STEP_SPEED := 160.0
## The strongest acceleration (world units a second squared) that leans a body or throws a hat.
const ACCEL_CAP := 700.0
## The gait's weight reaches 1 at this speed, and follows the speed at this rate (per second)...
const GAIT_FULL_SPEED := 14.0
const GAIT_RATE := 16.0
## ...the walk-to-run blend follows the speed at this rate...
const RUN_RATE := 5.0
## ...and below this speed the stride stops advancing and the feet settle together.
const STOP_SPEED := 4.0
const PLANT_RATE := 12.0
## The stride phase is wrapped after this many radians (whole turns, so nothing changes).
const PHASE_WRAP := 200.0 * PI
## Facing is smoothed at this rate (per second) and never turns faster than TURN_MAX radians a second.
const YAW_RATE := 16.0
const TURN_MAX := 14.0
const YAW_RATE_SMOOTH := 20.0
## Arms swing this much for each radian of leg swing, more when running, and never past ARM_SWING_MAX.
const ARM_PER_AMP := 0.9
const ARM_RUN_BONUS := 0.5
const ARM_SWING_MAX := 1.05
## A run carries the free arms forward and in (radians).
const RUN_ARM_FORWARD := 0.45
const RUN_ARM_IN := 0.10
## Shoulders twist against the hips (radians), 60% more when running, and the torso rocks side to
## side over the planted foot.
const TWIST_WALK := 0.11
const TWIST_RUN_BONUS := 0.6
const GAIT_ROLL := 0.035
## Forward lean (radians) at a shuffle, a walk and a run; then the body also leans into an
## acceleration (LEAN_ACCEL radians per world unit a second squared, so speeding up tips it forward
## and a hard stop rocks it back), capped at LEAN_MAX. The lean is a spring, so it settles.
const LEAN_SHUFFLE := 0.015
const LEAN_WALK := 0.05
const LEAN_RUN := 0.20
const LEAN_ACCEL := 0.0008
const LEAN_MAX := 0.4
const LEAN_OMEGA := 11.0
const LEAN_ZETA := 0.75
## The head stays this much more level than the spine (the neck takes up part of a lean).
const HEAD_LEVEL := 0.6
## Banking into a turn: radians of roll per radian a second of turning, at speeds up to
## BANK_SPEED, capped at BANK_MAX.
const TURN_BANK := 0.010
const BANK_SPEED := 60.0
const BANK_MAX := 0.16
const BANK_RATE := 8.0
## With reduce motion on, the body's dip with each step keeps this share, the run's hop none.
const REDUCED_DIP := 0.35
## The hard limits of an arm (radians): straight up and a little behind the back, and how far it can splay.
const ARM_PITCH_MIN := -3.05
const ARM_PITCH_MAX := 1.25
const ARM_ROLL_MAX := 0.85
## What a still picture assumes for the ground speed of a walking pose.
const STILL_WALK_SPEED := 40.0

# ---- looking

## The head turns at most this far (radians) from the body's facing. A target up to LOOK_FULL
## round from the facing is looked at as far as that allows; beyond it the gaze eases off, and
## past LOOK_CUTOFF (well behind the figure) it is ignored.
const HEAD_YAW_MAX := 1.0
const LOOK_FULL := 1.75
const LOOK_CUTOFF := 2.5
## How far the head tips up and down (radians) to follow a target's height.
const HEAD_UP := 0.35
const HEAD_DOWN := 0.45
## The shoulders turn this share of the head's turn, so a look starts in the body and not only the neck.
const GAZE_SHARE := 0.28
## The gaze fades in and out at this rate (per second); the head's spring is HEAD_OMEGA and
## HEAD_ZETA (almost no overshoot).
const GAZE_RATE := 7.0
const HEAD_OMEGA := 12.0
const HEAD_ZETA := 0.85
## Walking or turning, the head leads the body into the turn: this share of the turn still to do,
## up to LEAD_MAX radians.
const LEAD_GAIN := 0.55
const LEAD_MAX := 0.55

# ---- idle life

## Breathing: seconds a breath takes, how far the chest swells (a fraction) and lifts the shoulders (figure units).
const BREATH_PERIOD := 3.8
const BREATH_SWELL := 0.018
const BREATH_LIFT := 0.22
## Standing still is never quite still: a slow sway (figure units, radians of roll) and a wandering head (radians).
const SWAY_PERIOD := 7.0
const SWAY_X := 0.25
const SWAY_ROLL := 0.012
const DRIFT_YAW := 0.03
## Seconds between fidgets while standing about (a fidget is a glance, a weight shift or a foot tap).
const FIDGET_MIN := 3.5
const FIDGET_MAX := 8.5
## A glance: how far the head turns away (radians) and for how long (seconds).
const GLANCE_YAW := 0.6
const GLANCE_TIME := 1.4
## A weight shift: sway (figure units), the opposite tilt of the shoulders and the free leg's step
## forward (radians), and its length.
const SHIFT_SWAY := 0.9
const SHIFT_ROLL := 0.05
const SHIFT_LEG := 0.10
const SHIFT_TIME := 1.9
## A foot tap: the toe's lift (figure units), the leg's step forward (radians), taps per second
## (radians) and its length.
const TAP_LIFT := 0.6
const TAP_PITCH := 0.09
const TAP_RATE := 14.0
const TAP_TIME := 1.5
## Seconds between blinks, how long one takes, and how often one is a quick double.
const BLINK_MIN := 2.2
const BLINK_MAX := 5.6
const BLINK_TIME := 0.14
const DOUBLE_BLINK := 0.15

# ---- follow-through

## The arms settle onto each pose with a little overshoot (frequency in rad/s, damping ratio).
const ARM_OMEGA := 18.0
const ARM_ZETA := 0.6
## Arms trail behind a body that speeds up or turns: a spring pushed by the acceleration (radians a
## second squared per world unit a second squared, forwards and sideways), capped at ARM_LAG_MAX.
## A held arm only trails this much of the way.
const ARM_LAG_OMEGA := 13.0
const ARM_LAG_ZETA := 0.42
const ARM_LAG_PITCH := 0.05
const ARM_LAG_ROLL := 0.03
const ARM_LAG_MAX := 0.4
const ARM_HELD_LAG := 0.3
## A hat sits on the head on springs: it tips back when the body speeds up and to the side when it
## turns (HAT_PITCH_GAIN, HAT_ROLL_GAIN as for the arms, never past HAT_MAX radians), and rides up
## and down a little with hops (HAT_LIFT_MAX figure units).
const HAT_OMEGA := 17.0
const HAT_ZETA := 0.3
const HAT_PITCH_GAIN := 0.045
const HAT_ROLL_GAIN := 0.03
const HAT_MAX := 0.16
const HAT_LIFT_OMEGA := 22.0
const HAT_LIFT_ZETA := 0.35
const HAT_LIFT_MAX := 0.9
## The strongest vertical acceleration (figure units a second squared) that shakes a hat or a ponytail.
const VERT_CAP := 600.0
## A ponytail swings on a looser spring, pushed by acceleration and by the head turning, never past TAIL_MAX radians.
const TAIL_OMEGA := 10.0
const TAIL_ZETA := 0.28
const TAIL_GAIN := 0.06
const TAIL_TURN_GAIN := 2.5
const TAIL_MAX := 0.6
## With reduce motion on, the follow-through keeps this share of its push (and the springs are
## critically damped, so there is no overshoot).
const REDUCED_FOLLOW := 0.35
## Sitting down and standing up tip the body forwards while the seat is moving: radians per unit of
## seat speed, up to SEAT_LEAN_MAX.
const SEAT_LEAN := 0.035
const SEAT_LEAN_MAX := 0.25
## A cheer is preceded by a short crouch: the body dips, the arms sweep back and the torso tips
## forwards for CHEER_WINDUP seconds before the arms fly up.
const CHEER_WINDUP := 0.11
const CHEER_DIP := 1.6
const CHEER_ARM_BACK := 0.55
const CHEER_LEAN := 0.14
## A wave: how far the hand swings from side to side (radians) and how fast (radians a second).
const WAVE_SWING := 0.32
const WAVE_RATE := 11.0
## A clap: the arms roll in by CLAP_MID plus or minus CLAP_SWING (radians), hands meeting at the
## most, at CLAP_RATE radians a second.
const CLAP_MID := 0.30
const CLAP_SWING := 0.26
const CLAP_RATE := 12.0

const FIDGET_NONE := 0
const FIDGET_GLANCE := 1
const FIDGET_SHIFT := 2
const FIDGET_TAP := 3

var seed_value: int
var scale: float

# ------------------------------------------------------------------ outputs

## Visual facing (radians): the owner's yaw, smoothed.
var yaw := 0.0
## Body height offset, in figure units (multiplied by the figure's scale when drawn).
var root_y := 0.0
## Arm angles, index 0 the left (-x) and 1 the right (+x): forward swing (negative pitch) and outward roll.
var arm_pitch := PackedFloat64Array([0.0, 0.0])
var arm_roll := PackedFloat64Array([0.0, 0.0])
## Leg swing (negative pitch is forwards) and how far each leg is lifted at the hip, index as [member arm_pitch].
var leg_pitch := PackedFloat64Array([0.0, 0.0])
var leg_lift := PackedFloat64Array([0.0, 0.0])
## How much of a café treat is in the hand: 0 none .. 1 fully held.
var item_amount := 0.0
## Sideways body sway (figure units, along the figure's own x axis).
var sway := 0.0
## The spine, about the hips: forward lean (positive is forwards), bank (roll) and twist (yaw).
var lean := 0.0
var lean_roll := 0.0
var twist := 0.0
## Breathing: the chest's swell (a fraction) and how far it lifts the shoulders and head (figure units).
var breath := 0.0
var breath_lift := 0.0
## The head, about the neck, relative to the spine: turn, nod (positive looks down) and tilt.
var head_yaw := 0.0
var head_pitch := 0.0
var head_roll := 0.0
## The hat's lag behind the head (pitch and roll about the head's centre) and its lift (figure units).
var hat_pitch := 0.0
var hat_roll := 0.0
var hat_lift := 0.0
## A ponytail's swing about its joint on the back of the head.
var tail_pitch := 0.0
var tail_roll := 0.0
## How far the eyelids are closed: 0 open .. 1 shut.
var blink := 0.0
## How many fidgets this figure has started (for tests and tuning).
var fidget_count: int:
	get:
		return _fidget_n
## True for the one update in which a foot came down (for footstep sounds).
var stepped := false

# ------------------------------------------------------------------ state

## The cross-fading pose weights.
var blender := PoseBlender.new()
## The animation's own clock (seconds), offset per figure so a crowd never moves in step.
var clock := 0.0
## The stride cycle (radians), advanced by the ground covered so a planted foot stays put: a foot
## lands at every multiple of π. It only advances while the figure moves.
var phase := 0.0
## Ground speed (world units a second), smoothed.
var speed := 0.0

var _started := false
## How far the legs are eased on from [member phase] to stand under the body when the figure stops (radians).
var _plant := 0.0
## How much of a walk (0..1) and of a run the speed makes.
var _gait := 0.0
var _run := 0.0
var _px := 0.0
var _pz := 0.0
var _vxs := 0.0
var _vzs := 0.0
var _yaw_rate := 0.0
var _bank := 0.0
var _lean_spring := AnimSpring.new()
## 1 while seated (eases with a small settle), 0 standing.
var _seat := AnimSpring.new()
var _cheer_age := 0.0
var _wave_age := 0.0
var _clap_age := 0.0

# Pose accumulators (weights times what each pose asks for), reset in solve.
var _acc_arm_p := PackedFloat64Array([0.0, 0.0])
var _acc_arm_r := PackedFloat64Array([0.0, 0.0])
var _acc_swing := PackedFloat64Array([0.0, 0.0])
var _acc_root := 0.0
var _acc_item := 0.0
var _acc_head_yaw := 0.0
var _acc_head_pitch := 0.0
var _acc_head_roll := 0.0
var _acc_lean := 0.0

# Gaze: a target set with [method look] for the next update, how strongly it is followed, and the head's springs.
var _look_on := false
var _look_x := 0.0
var _look_z := 0.0
var _look_y := 0.0
var _gaze := 0.0
var _head_yaw_spring := AnimSpring.new()
var _head_pitch_spring := AnimSpring.new()
var _head_roll_spring := AnimSpring.new()

# Follow-through: springs on the arms (settling onto a pose, and trailing behind acceleration), the hat and a ponytail.
var _arm_pose_p: Array[AnimSpring] = [AnimSpring.new(), AnimSpring.new()]
var _arm_pose_r: Array[AnimSpring] = [AnimSpring.new(), AnimSpring.new()]
var _arm_lag_p: Array[AnimSpring] = [AnimSpring.new(), AnimSpring.new()]
var _arm_lag_r: Array[AnimSpring] = [AnimSpring.new(), AnimSpring.new()]
var _hat_pitch_spring := AnimSpring.new()
var _hat_roll_spring := AnimSpring.new()
var _hat_lift_spring := AnimSpring.new()
var _tail_pitch_spring := AnimSpring.new()
var _tail_roll_spring := AnimSpring.new()
var _prev_root_y := 0.0
var _prev_root_vy := 0.0

## Seconds left of a cheer wind-up (negative when not winding up) and how much of it shows (0..1).
var _wind_left := -1.0
var _wind_env := 0.0

# Fidgets and blinks (seeded from [member seed_value], so the same figure fidgets the same way every run).
var _fidget_kind := FIDGET_NONE
var _fidget_t := 0.0
var _fidget_dur := 0.0
var _fidget_dir := 1.0
var _fidget_n := 0
var _fidget_wait := 0.0
var _blink_t := -1.0
var _blink_wait := 0.0
var _blink_n := 1

# What the fidget is doing to each joint this step (already weighted by how idle the figure is).
var _fid_yaw := 0.0
var _fid_pitch := 0.0
var _fid_roll := 0.0
var _fid_sway := 0.0
var _fid_bank := 0.0
var _fid_leg := PackedFloat64Array([0.0, 0.0])
var _fid_lift := PackedFloat64Array([0.0, 0.0])


func _init(p_seed: int = 0, p_scale: float = 1.0) -> void:
	seed_value = p_seed
	scale = p_scale
	clock = (seed_value & 63) * 1.7
	_fidget_wait = FIDGET_MIN * (0.3 + 0.7 * AnimMath.unit(seed_value, 0, 5))
	_blink_wait = BLINK_MIN + (BLINK_MAX - BLINK_MIN) * AnimMath.unit(seed_value, 0, 1)


## Asks the figure to look at the world point ([param x], [param y] up, [param z]) for the coming
## update: the head turns toward it (within what a neck can do), the shoulders follow a little,
## and the gaze eases off again once this stops being called. A point behind the figure is
## ignored. [param y] defaults to the figure's head height.
func look(x: float, z: float, y: float = NAN) -> void:
	_look_on = true
	_look_x = x
	_look_z = z
	_look_y = Figure.HEAD_Y * scale if is_nan(y) else y


## One simulation step of [param dt] seconds. ([param x], [param z]) is where the figure stands,
## [param p_yaw] where it is facing (the visual facing follows it smoothly) and [param pose] what
## it is doing. [param yaw_goal] defaults to [param p_yaw]. Its speed is worked out from how far it
## moved, unless the owner knows better and passes the velocity ([param vx], [param vz]); a move
## too big to be walking (a shove, a teleport) is ignored.
func update(dt: float, x: float, z: float, p_yaw: float, pose: int, yaw_goal: float = NAN, vx: float = NAN, vz: float = NAN) -> void:
	if not (dt > 0.0):
		return
	if is_nan(yaw_goal):
		yaw_goal = p_yaw
	var h := MAX_DT if dt > MAX_DT else dt
	if not _started:
		_started = true
		yaw = p_yaw
		_px = x
		_pz = z
		blender.snap(pose)
		_seat.reset(_seat_target(pose))
	clock += h

	# --- how the figure moved this step
	var rvx: float
	var rvz: float
	var dist: float
	if is_nan(vx) or is_nan(vz):
		var dx := x - _px
		var dz := z - _pz
		dist = sqrt(dx * dx + dz * dz)
		if dist > MAX_STEP_SPEED * h:
			# Shoved or moved by something else: carry on as we were.
			rvx = _vxs
			rvz = _vzs
			dist = 0.0
		else:
			rvx = dx / h
			rvz = dz / h
	else:
		rvx = vx
		rvz = vz
		dist = sqrt(vx * vx + vz * vz) * h
	_px = x
	_pz = z
	var kv := AnimMath.k(VEL_SMOOTH, h)
	var ax := clampf((rvx - _vxs) * kv / h, -ACCEL_CAP, ACCEL_CAP)
	var az := clampf((rvz - _vzs) * kv / h, -ACCEL_CAP, ACCEL_CAP)
	_vxs += (rvx - _vxs) * kv
	_vzs += (rvz - _vzs) * kv
	speed = sqrt(_vxs * _vxs + _vzs * _vzs)

	# --- facing, smoothed; how fast it is turning
	var turn := clampf(AnimMath.wrap(p_yaw - yaw) * AnimMath.k(YAW_RATE, h), -TURN_MAX * h, TURN_MAX * h)
	yaw += turn
	_yaw_rate += (turn / h - _yaw_rate) * AnimMath.k(YAW_RATE_SMOOTH, h)
	var sy := sin(yaw)
	var cy := cos(yaw)
	# The acceleration along the way the figure faces (forwards is positive).
	var af := ax * sy + az * cy
	# And sideways (along the figure's own +x).
	var al := ax * cy - az * sy

	# --- pose
	var shown := _wind_up(pose, h)
	var was := blender.target
	blender.set_pose(shown)
	if blender.target != was:
		if shown == Pose.CHEER:
			_cheer_age = 0.0
		elif shown == Pose.WAVE:
			_wave_age = 0.0
		elif shown == Pose.CLAP:
			_clap_age = 0.0
	_cheer_age += h
	_wave_age += h
	_clap_age += h
	blender.update(h)
	# The seat follows the (already eased) sitting weight, so sitting starts gently, and its spring
	# adds the settle at the bottom.
	var w := blender.weights
	_seat.step(w[Pose.SIT] + w[Pose.SIP], h, SEAT_OMEGA, 1.0 if reduce_motion else SEAT_ZETA)
	_accumulate_pose()
	# Arms settle onto their poses with a little overshoot.
	var arm_zeta := 1.0 if reduce_motion else ARM_ZETA
	for s in 2:
		_arm_pose_p[s].step(_acc_arm_p[s], h, ARM_OMEGA, arm_zeta)
		_arm_pose_r[s].step(_acc_arm_r[s], h, ARM_OMEGA, arm_zeta)

	# --- gait: the stride advances with the ground covered, and settles when the figure stops
	_gait += (AnimMath.smooth(speed / GAIT_FULL_SPEED) - _gait) * AnimMath.k(GAIT_RATE, h)
	_run += (Gait.run_blend(speed) - _run) * AnimMath.k(RUN_RATE, h)
	stepped = false
	if speed > STOP_SPEED:
		if dist > 0.0:
			var before := floorf(phase / PI)
			phase += Gait.phase_for(dist, Gait.amplitude(speed))
			stepped = floorf(phase / PI) != before
			if phase > PHASE_WRAP:
				phase -= PHASE_WRAP
		_plant += (0.0 - _plant) * AnimMath.k(PLANT_RATE, h)
	else:
		_plant += (Gait.rest_phase(phase) - phase - _plant) * AnimMath.k(PLANT_RATE, h)

	# --- lean into speeding up, out of stopping, and banking into turns
	var walk_lean := LEAN_SHUFFLE + (LEAN_WALK - LEAN_SHUFFLE) * AnimMath.smooth(speed / Gait.WALK_TOP)
	var lean_now := walk_lean + (LEAN_RUN - walk_lean) * _run
	var lean_target := clampf(lean_now * _gait + LEAN_ACCEL * af, -LEAN_MAX, LEAN_MAX)
	_lean_spring.step(lean_target, h, LEAN_OMEGA, 1.0 if reduce_motion else LEAN_ZETA)
	var bank_target := clampf(-_yaw_rate * TURN_BANK * minf(speed / BANK_SPEED, 1.0), -BANK_MAX, BANK_MAX)
	_bank += (bank_target - _bank) * AnimMath.k(BANK_RATE, h)

	_step_fidgets(h)
	_step_gaze(x, z, yaw_goal, h)
	_step_blink(h)
	_step_follow_through(af, al, h)
	_solve()


## Holds a cheer back for a beat: asked for a [param pose] of CHEER, the figure keeps the pose it
## was in for CHEER_WINDUP seconds while it crouches ([member _wind_env] carries the crouch), then
## cheers. Returns the pose to blend to now.
func _wind_up(pose: int, h: float) -> int:
	if reduce_motion or pose != Pose.CHEER or blender.target == Pose.CHEER:
		_wind_left = -1.0
		return pose
	if _wind_left < 0.0:
		_wind_left = CHEER_WINDUP
	_wind_left -= h
	return blender.target if _wind_left > 0.0 else pose


## Springs that lag behind the body: a hat and a ponytail that trail behind speeding up and
## turning and hops, arms that trail behind it. [param af] and [param al] are the accelerations
## along and across the way the figure faces.
func _step_follow_through(af: float, al: float, h: float) -> void:
	var calm := REDUCED_FOLLOW if reduce_motion else 1.0
	# How hard the body is being thrown up and down, from its own height over the last steps.
	var v := (root_y - _prev_root_y) / h
	var ay := clampf((v - _prev_root_vy) / h, -VERT_CAP, VERT_CAP)
	_prev_root_y = root_y
	_prev_root_vy = v
	# The wind-up crouch eases in and out.
	var wind_target := AnimMath.bell(1.0 - _wind_left / CHEER_WINDUP) if _wind_left > 0.0 else 0.0
	_wind_env += (wind_target - _wind_env) * AnimMath.k(30.0, h)

	# A hat lags: forward acceleration tips its top back (negative pitch), sideways acceleration to the other side.
	var hat_z := 1.0 if reduce_motion else HAT_ZETA
	_hat_pitch_spring.drive(0.0, -HAT_PITCH_GAIN * af * calm, h, HAT_OMEGA, hat_z)
	_hat_roll_spring.drive(0.0, HAT_ROLL_GAIN * al * calm, h, HAT_OMEGA, hat_z)
	_hat_lift_spring.drive(0.0, -ay * calm, h, HAT_LIFT_OMEGA, 1.0 if reduce_motion else HAT_LIFT_ZETA)
	# A ponytail hangs from the back of the head: speeding up swings its end back, sideways
	# acceleration or the head turning swings it the other way.
	var tail_z := 1.0 if reduce_motion else TAIL_ZETA
	_tail_pitch_spring.drive(0.0, TAIL_GAIN * af * calm, h, TAIL_OMEGA, tail_z)
	_tail_roll_spring.drive(0.0, (-TAIL_GAIN * al - TAIL_TURN_GAIN * _head_yaw_spring.v) * calm, h, TAIL_OMEGA, tail_z)
	# Arms trail behind: forward acceleration swings them back, sideways acceleration across.
	var arm_z := 1.0 if reduce_motion else ARM_LAG_ZETA
	for s in 2:
		# The two arms' springs differ a touch, so they never flap in perfect step.
		var omega := ARM_LAG_OMEGA * (1.0 + 0.04 * (2 * s - 1))
		_arm_lag_p[s].drive(0.0, ARM_LAG_PITCH * af * calm, h, omega, arm_z)
		_arm_lag_r[s].drive(0.0, -ARM_LAG_ROLL * al * calm, h, omega, arm_z)


## Idle fidgets: while the figure stands easy, every few seconds it glances away, shifts its weight
## or taps a foot. Timed and chosen from the seed, and weighted by how idle it is, so setting off or
## sitting down fades one out instead of cutting it.
func _step_fidgets(h: float) -> void:
	var w := blender.weights
	var idle: float = w[Pose.STAND] + w[Pose.HOLD]
	var still := (1.0 - _gait) * (1.0 - clampf(_seat.x, 0.0, 1.0))
	_fid_yaw = 0.0
	_fid_pitch = 0.0
	_fid_roll = 0.0
	_fid_sway = 0.0
	_fid_bank = 0.0
	_fid_leg[0] = 0.0
	_fid_leg[1] = 0.0
	_fid_lift[0] = 0.0
	_fid_lift[1] = 0.0
	if _fidget_kind == FIDGET_NONE:
		if idle > 0.9 and still > 0.95:
			_fidget_wait -= h
		if _fidget_wait <= 0.0:
			var n := _fidget_n
			_fidget_n += 1
			var r := AnimMath.unit(seed_value, n, 3)
			_fidget_kind = FIDGET_GLANCE if r < 0.45 else (FIDGET_SHIFT if r < 0.75 else FIDGET_TAP)
			if _fidget_kind == FIDGET_GLANCE:
				_fidget_dur = GLANCE_TIME
			elif _fidget_kind == FIDGET_SHIFT:
				_fidget_dur = SHIFT_TIME
			else:
				_fidget_dur = TAP_TIME
			_fidget_dir = -1.0 if AnimMath.unit(seed_value, n, 4) < 0.5 else 1.0
			_fidget_t = 0.0
	else:
		_fidget_t += h
		if _fidget_t >= _fidget_dur:
			_fidget_kind = FIDGET_NONE
			_fidget_wait = FIDGET_MIN + (FIDGET_MAX - FIDGET_MIN) * AnimMath.unit(seed_value, _fidget_n, 5)
	var calm := idle * still
	if _fidget_kind != FIDGET_NONE:
		# Up quickly, held, and back down: a raised window over the fidget's length.
		var e := AnimMath.smooth(_fidget_t / 0.25) * AnimMath.smooth((_fidget_dur - _fidget_t) / 0.35) * calm
		var d := _fidget_dir
		if _fidget_kind == FIDGET_GLANCE:
			_fid_yaw = d * GLANCE_YAW * e
			_fid_pitch = -0.06 * e
			_fid_roll = d * 0.05 * e
		elif _fidget_kind == FIDGET_SHIFT:
			_fid_sway = d * SHIFT_SWAY * e
			_fid_bank = d * SHIFT_ROLL * e
			# The free leg (opposite the weight-bearing side) steps out a little.
			_fid_leg[0 if d > 0.0 else 1] = -SHIFT_LEG * e
		else:
			var tap := sin(_fidget_t * TAP_RATE)
			var leg := 1 if d > 0.0 else 0
			_fid_leg[leg] = -TAP_PITCH * e
			_fid_lift[leg] = TAP_LIFT * maxf(0.0, tap) * e
			_fid_pitch = 0.03 * tap * e
	# Standing still is never quite still: a slow sway and a wandering head.
	var sw := sin(clock * TAU / SWAY_PERIOD)
	_fid_sway += SWAY_X * sw * calm
	_fid_bank += SWAY_ROLL * sw * calm
	_fid_yaw += DRIFT_YAW * sin(clock * 0.7 + seed_value) * calm


## Where the head points: at the [method look] target if there is one, otherwise leading the body
## into whatever turn it is making, plus the fidgets. Springs give the head a soft arrival.
func _step_gaze(x: float, z: float, yaw_goal: float, h: float) -> void:
	var target_yaw := 0.0
	var target_pitch := 0.0
	var strength := 0.0
	if _look_on:
		var dx := _look_x - x
		var dz := _look_z - z
		var d := sqrt(dx * dx + dz * dz)
		if d > 1.0:
			var rel := AnimMath.wrap(atan2(dx, dz) - yaw)
			var away := absf(rel)
			if away < LOOK_CUTOFF:
				target_yaw = clampf(rel, -HEAD_YAW_MAX, HEAD_YAW_MAX)
				target_pitch = clampf(atan2(Figure.HEAD_Y * scale - _look_y, d), -HEAD_UP, HEAD_DOWN)
				# A target well round the side is let go of gradually rather than at a cut-off.
				strength = 1.0 - AnimMath.smooth((away - LOOK_FULL) / (LOOK_CUTOFF - LOOK_FULL))
	_look_on = false
	_gaze += (strength - _gaze) * AnimMath.k(GAZE_RATE, h)
	var lead := clampf(AnimMath.wrap(yaw_goal - yaw) * LEAD_GAIN, -LEAD_MAX, LEAD_MAX)
	var yaw_t := _gaze * target_yaw + (1.0 - _gaze) * lead + _acc_head_yaw + _fid_yaw
	var pitch_t := _gaze * target_pitch + _acc_head_pitch + _fid_pitch
	var roll_t := _acc_head_roll + _fid_roll
	var zeta := 1.0 if reduce_motion else HEAD_ZETA
	_head_yaw_spring.step(clampf(yaw_t, -HEAD_YAW_MAX, HEAD_YAW_MAX), h, HEAD_OMEGA, zeta)
	_head_pitch_spring.step(pitch_t, h, HEAD_OMEGA, zeta)
	_head_roll_spring.step(roll_t, h, HEAD_OMEGA, zeta)


## Blinking on a seeded timer: a quick close and slower open, now and then twice in a row.
func _step_blink(h: float) -> void:
	if _blink_t >= 0.0:
		_blink_t += h
		var p := _blink_t / BLINK_TIME
		if p >= 1.0:
			_blink_t = -1.0
			var n := _blink_n
			_blink_n += 1
			if AnimMath.unit(seed_value, n, 2) < DOUBLE_BLINK:
				_blink_wait = 0.16
			else:
				_blink_wait = BLINK_MIN + (BLINK_MAX - BLINK_MIN) * AnimMath.unit(seed_value, n, 1)
			blink = 0.0
		else:
			blink = AnimMath.smooth(p / 0.4) if p < 0.4 else 1.0 - AnimMath.smooth((p - 0.4) / 0.6)
	else:
		blink = 0.0
		_blink_wait -= h
		if _blink_wait <= 0.0:
			_blink_t = 0.0


## Sets the pose at once, for a still picture (the prize counter, the photo booth): [param time]
## drives the idle motion, [param walk_phase] the stride (0 has the legs under the body), and a
## walking pose gets a walking gait.
func set_static(pose: int, time: float, walk_phase: float, p_yaw: float) -> void:
	_started = true
	yaw = p_yaw
	clock = time
	blender.snap(pose)
	var walking := pose == Pose.WALK or pose == Pose.CARRY
	speed = STILL_WALK_SPEED if walking else 0.0
	_gait = 1.0 if walking else 0.0
	_run = 0.0
	phase = walk_phase + PI / 2.0
	_plant = 0.0
	_yaw_rate = 0.0
	_bank = 0.0
	_lean_spring.reset(LEAN_WALK if walking else 0.0)
	_seat.reset(_seat_target(pose))
	_cheer_age = time
	_wave_age = time
	_clap_age = time
	stepped = false
	# No history: no gaze, no fidget, eyes open, and every spring at rest on its pose.
	_look_on = false
	_gaze = 0.0
	_fidget_kind = FIDGET_NONE
	_fid_yaw = 0.0
	_fid_pitch = 0.0
	_fid_roll = 0.0
	_fid_sway = 0.0
	_fid_bank = 0.0
	_fid_leg[0] = 0.0
	_fid_leg[1] = 0.0
	_fid_lift[0] = 0.0
	_fid_lift[1] = 0.0
	_blink_t = -1.0
	blink = 0.0
	_accumulate_pose()
	_wind_left = -1.0
	_wind_env = 0.0
	for s in 2:
		_arm_pose_p[s].reset(_acc_arm_p[s])
		_arm_pose_r[s].reset(_acc_arm_r[s])
		_arm_lag_p[s].reset()
		_arm_lag_r[s].reset()
	_hat_pitch_spring.reset()
	_hat_roll_spring.reset()
	_hat_lift_spring.reset()
	_tail_pitch_spring.reset()
	_tail_roll_spring.reset()
	_prev_root_y = 0.0
	_prev_root_vy = 0.0
	_head_yaw_spring.reset(_acc_head_yaw)
	_head_pitch_spring.reset(_acc_head_pitch)
	_head_roll_spring.reset(_acc_head_roll)
	_solve()


## Starts the figure off already standing at ([param x], [param z]) facing [param p_yaw] in
## [param pose], so that the first frame drawn before its first update is not a figure facing the
## wrong way.
func prime(x: float, z: float, p_yaw: float, pose: int) -> void:
	set_static(pose, clock, 0.0, p_yaw)
	_px = x
	_pz = z
	blender.snap(pose)


func _seat_target(pose: int) -> float:
	return 1.0 if pose == Pose.SIT or pose == Pose.SIP else 0.0


# ------------------------------------------------------------------ the rig

## Sums every pose's share of the arms, the body and the head into the accumulators.
func _accumulate_pose() -> void:
	var w := blender.weights
	_acc_arm_p[0] = 0.0
	_acc_arm_p[1] = 0.0
	_acc_arm_r[0] = 0.0
	_acc_arm_r[1] = 0.0
	_acc_swing[0] = 0.0
	_acc_swing[1] = 0.0
	_acc_root = 0.0
	_acc_item = 0.0
	_acc_head_yaw = 0.0
	_acc_head_pitch = 0.0
	_acc_head_roll = 0.0
	_acc_lean = 0.0
	for i in Pose.COUNT:
		var wi: float = w[i]
		if wi > 1e-4:
			_add_pose(i, wi)


func _solve() -> void:
	# Legs walk unless seated; the body sits on whichever foot is lower.
	var rig := phase + _plant
	var c := cos(rig)
	var amp := Gait.amplitude(speed)
	var seat_x := _seat.x
	var seated := clampf(seat_x, 0.0, 1.0)
	var gait_k := _gait * (1.0 - seated)
	var lift_max := Gait.lift(amp, _run)
	var low := INF
	for s in 2:
		var side := -1.0 if s == 0 else 1.0
		var pitch := Gait.leg_pitch(side, rig, amp) * gait_k
		var lift := Gait.leg_lift(side, rig, lift_max) * gait_k
		leg_pitch[s] = pitch - SEAT_LEG * seat_x + _fid_leg[s]
		leg_lift[s] = lift + _fid_lift[s]
		low = minf(low, Gait.foot_height(pitch, lift))
	var dip := (Gait.CONTACT + (Gait.CONTACT_RUN - Gait.CONTACT) * _run) * (REDUCED_DIP if reduce_motion else 1.0)
	# A run also rises at the top of each stride, when the legs pass under the body.
	var hop := 0.0 if reduce_motion else Gait.RUN_HOP * _run * (1.0 - c * c) * gait_k

	# Arms swing against the legs (a hand holding something keeps still); a run carries them forward.
	var arm_amp := minf(ARM_SWING_MAX, amp * (ARM_PER_AMP + ARM_RUN_BONUS * _run))
	for s in 2:
		var side := -1.0 if s == 0 else 1.0
		var swing: float = _acc_swing[s]
		var free := swing * _gait
		# A held arm trails behind acceleration less than a free one.
		var lag := ARM_HELD_LAG + (1.0 - ARM_HELD_LAG) * clampf(swing, 0.0, 1.0)
		arm_pitch[s] = clampf(_arm_pose_p[s].x + CHEER_ARM_BACK * _wind_env + lag * clampf(_arm_lag_p[s].x, -ARM_LAG_MAX, ARM_LAG_MAX) \
			+ free * (-side * arm_amp * c - RUN_ARM_FORWARD * _run), ARM_PITCH_MIN, ARM_PITCH_MAX)
		arm_roll[s] = clampf(_arm_pose_r[s].x + lag * clampf(_arm_lag_r[s].x, -ARM_LAG_MAX, ARM_LAG_MAX) \
			+ free * (-side * RUN_ARM_IN * _run), -ARM_ROLL_MAX, ARM_ROLL_MAX)

	root_y = _acc_root - low * dip * gait_k + hop - SEAT_DROP * seat_x - CHEER_DIP * _wind_env
	item_amount = clampf(_acc_item, 0.0, 1.0)

	# Spine: shoulders twist against the hips, the torso rocks over the planted foot, leans and banks.
	twist = -TWIST_WALK * (1.0 + TWIST_RUN_BONUS * _run) * c * _gait + GAZE_SHARE * _head_yaw_spring.x
	lean = _lean_spring.x + _acc_lean + CHEER_LEAN * _wind_env + minf(SEAT_LEAN_MAX, SEAT_LEAN * absf(_seat.v))
	lean_roll = _bank + GAIT_ROLL * sin(rig) * _gait + _fid_bank
	sway = _fid_sway

	# Hat and ponytail lag behind the head.
	hat_pitch = clampf(_hat_pitch_spring.x, -HAT_MAX, HAT_MAX)
	hat_roll = clampf(_hat_roll_spring.x, -HAT_MAX, HAT_MAX)
	hat_lift = clampf(_hat_lift_spring.x, -HAT_LIFT_MAX, HAT_LIFT_MAX)
	tail_pitch = clampf(_tail_pitch_spring.x, -TAIL_MAX, TAIL_MAX)
	tail_roll = clampf(_tail_roll_spring.x, -TAIL_MAX, TAIL_MAX)

	# Head: the gaze, less what the spine has already turned, so it stays steady through a stride.
	head_yaw = _head_yaw_spring.x - twist
	head_pitch = _head_pitch_spring.x - HEAD_LEVEL * _lean_spring.x
	head_roll = _head_roll_spring.x

	# Breathing, a little less noticeable once the figure is moving.
	var breathing := sin(clock * TAU / BREATH_PERIOD) * (1.0 - 0.7 * _gait)
	breath = BREATH_SWELL * breathing
	breath_lift = BREATH_LIFT * breathing


## Adds pose [param p]'s share [param wi] of every joint.
func _add_pose(p: int, wi: float) -> void:
	var t := clock
	if p == Pose.STAND:
		_acc_arm_p[0] += wi * sin(t * 1.3 - 1.0) * 0.05
		_acc_arm_r[0] += wi * -0.1
		_acc_arm_p[1] += wi * sin(t * 1.3 + 1.0) * 0.05
		_acc_arm_r[1] += wi * 0.1
		_acc_swing[0] += wi
		_acc_swing[1] += wi
	elif p == Pose.WALK:
		_acc_arm_r[0] += wi * -0.12
		_acc_arm_r[1] += wi * 0.12
		_acc_swing[0] += wi
		_acc_swing[1] += wi
	elif p == Pose.PLAY:
		_acc_arm_p[0] += wi * (-1.05 + sin(t * 11.0 - 1.0) * 0.12)
		_acc_arm_r[0] += wi * -0.05
		_acc_arm_p[1] += wi * (-1.05 + sin(t * 11.0 + 1.0) * 0.12)
		_acc_arm_r[1] += wi * 0.05
		# Leaning in to the game, eyes on the screen and glancing about it.
		_acc_head_pitch += wi * 0.10
		_acc_head_yaw += wi * 0.10 * sin(t * 0.9)
		_acc_lean += wi * 0.05
	elif p == Pose.CHEER:
		_acc_arm_p[0] += wi * (-2.6 + sin(_cheer_age * 9.0 - 1.0) * 0.25)
		_acc_arm_r[0] += wi * -0.2
		_acc_arm_p[1] += wi * (-2.6 + sin(_cheer_age * 9.0 + 1.0) * 0.25)
		_acc_arm_r[1] += wi * 0.2
		_acc_root += wi * absf(sin(_cheer_age * 9.0)) * 2.5
		_acc_head_pitch += wi * -0.16
		_acc_lean += wi * -0.04
	elif p == Pose.SIT:
		_acc_arm_p[0] += wi * -0.6
		_acc_arm_r[0] += wi * -0.1
		_acc_arm_p[1] += wi * -0.6
		_acc_arm_r[1] += wi * 0.1
	elif p == Pose.WIPE:
		# Circles with a cloth on the counter top.
		_acc_arm_p[1] += wi * (-1.15 + sin(t * 7.0) * 0.1)
		_acc_arm_r[1] += wi * (0.05 + cos(t * 7.0) * 0.28)
		_acc_arm_p[0] += wi * -0.75
		_acc_arm_r[0] += wi * -0.12
		# Head down, watching the cloth.
		_acc_head_pitch += wi * 0.28
		_acc_lean += wi * 0.10
	elif p == Pose.CARRY:
		_acc_arm_p[1] += wi * (-1.25 + sin(t * 2.0) * 0.04)
		_acc_arm_r[1] += wi * 0.02
		_acc_arm_r[0] += wi * -0.12
		_acc_swing[0] += wi
		_acc_item += wi
	elif p == Pose.HOLD:
		_acc_arm_p[1] += wi * (-1.25 + sin(t * 2.0) * 0.04)
		_acc_arm_r[1] += wi * 0.02
		_acc_arm_p[0] += wi * sin(t * 1.3) * 0.05
		_acc_arm_r[0] += wi * -0.1
		_acc_swing[0] += wi
		_acc_item += wi
	elif p == Pose.SIP:
		# Every few seconds the cup comes up for a sip.
		var cyc := fmod(t, 4.5)
		var lift := sin(cyc / 1.4 * PI) if cyc < 1.4 else 0.0
		_acc_arm_p[1] += wi * (-0.95 - lift * 1.35)
		_acc_arm_r[1] += wi * (0.05 + lift * 0.2)
		_acc_arm_p[0] += wi * -0.6
		_acc_arm_r[0] += wi * -0.1
		_acc_head_pitch += wi * -0.12 * lift
		_acc_item += wi
	elif p == Pose.WAVE:
		# The right arm goes up and the hand swings from side to side; the other hangs as when standing.
		_acc_arm_p[1] += wi * (-2.75 + 0.05 * sin(_wave_age * 3.0))
		_acc_arm_r[1] += wi * (0.15 + WAVE_SWING * sin(_wave_age * WAVE_RATE))
		_acc_arm_p[0] += wi * sin(t * 1.3 - 1.0) * 0.05
		_acc_arm_r[0] += wi * -0.1
		_acc_swing[0] += wi
		_acc_head_pitch += wi * -0.05
		_acc_head_roll += wi * 0.07
	elif p == Pose.CLAP:
		# Both hands out in front, coming together and apart (inward roll is negative on the right,
		# positive on the left).
		var shut := CLAP_MID + CLAP_SWING * sin(_clap_age * CLAP_RATE)
		_acc_arm_p[0] += wi * -1.35
		_acc_arm_r[0] += wi * shut
		_acc_arm_p[1] += wi * -1.35
		_acc_arm_r[1] += wi * -shut
		_acc_head_pitch += wi * -0.1
		if not reduce_motion:
			_acc_root += wi * 0.5 * absf(sin(_clap_age * CLAP_RATE * 0.5))
