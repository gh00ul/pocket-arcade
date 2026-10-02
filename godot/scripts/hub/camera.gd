class_name HubCamera
extends RefCounted
## hub/Camera.kt HubCamera: the hall's 3D camera. Overhead (the default) it looks north and down
## across the floor from behind the player, far enough back that a whole bank of machines fits
## across the screen whatever its aspect, trailing the player smoothly with a little look-ahead. In
## first person it is the player's eyes: at a kid's head (so turning on the spot turns the view in
## place), free yaw, clamped pitch, a head-bob in step with the feet that grows with the pace, and a
## slight widening of the view when running. Switching eases between the two poses, and either can
## dive into a machine for the enter/exit transition.

## First-person eye height: a little above the kids' heads, so marquees and screens read.
const EYE_HEIGHT := 54.0
## How far behind the feet the eye sits: at the back of the head, so turning on the spot turns the
## view in place. The body's clearance (PaBody.RADIUS) keeps cabinets at a comfortable distance instead.
const EYE_BACK := 2.0
## First-person vertical field of view on screens about as wide as they are tall, degrees.
const FP_FOV_DEG := 70.0
## On tall portrait screens the vertical view widens (up to this) to keep a sensible width.
const FP_MAX_FOV_DEG := 90.0
## However the player's setting scales it, first person's vertical view stays within these, degrees.
const MIN_FOV_DEG := 40.0
const MAX_FOV_DEG := 120.0
## The narrowest horizontal view first person aims for, degrees.
const FP_MIN_HFOV_DEG := 50.0
## How far first person can look up or down, degrees.
const PITCH_LIMIT_DEG := 40.0
## First person's resting pitch: a touch down, so the floor ahead and the screens show.
const REST_PITCH_DEG := -6.0
## Seconds to ease between overhead and first person.
const BLEND_TIME := 0.5
## Head-bob while walking: up and down, and side to side, in world units.
const BOB_HEIGHT := 1.2
const BOB_SWAY := 0.5
## How much wider (degrees) the first-person view gets at a full run.
const RUN_FOV_KICK_DEG := 4.0
## The arrival from the title: the eye starts this share further back from what it looks at and a
## little wider, and settles in as [member entrance] drains, so the hall's first frames carry on
## the title's push instead of popping.
const ENTRANCE_PULLBACK := 0.20
const ENTRANCE_FOV := 0.08
## Near clipping distances: first person stands right against cabinets.
const OVERHEAD_NEAR := 8.0
const FP_NEAR := 1.5

const DEG := PI / 180.0

var target_x := 304.0
var target_z := 600.0

var pitch_deg := 55.0
## Vertical field of view, degrees.
var fov_deg := 56.0
## How much of the floor, in world units, should span the screen at the target.
var cover_width := 370.0
var cover_height := 420.0
var min_x := 150.0
var max_x := HubLayout.WIDTH - 150.0
## Clamp for the target so the view stays over the hall (and the pavement out front).
var min_z := 190.0
var max_z := HubLayout.FRONT_WALL - 204.0

var _lead_x := 0.0
var _lead_z := 0.0

## 0 = following the player, 1 = right in front of the dive target.
var dive := 0.0
var dive_x := 0.0
var dive_y := 0.0
var dive_z := 0.0

## 1 as the title hands over to the hall, easing to 0 (see [constant ENTRANCE_PULLBACK]); 0 at all
## other times. Set by the app's handoff, never by the hall itself.
var entrance := 0.0

## The player sits a little below the middle of the screen, where there's less perspective squeeze.
var _below := 30.0

# ------------------------------------------------------------------ first person

## Whether first person is on (what the blend is heading for).
var first_person := false
## Progress from overhead (0) to first person (1), linear in time; see [member fp_amount].
var fp_blend := 0.0

## The eased blend between the two poses (0 overhead … 1 first person).
var fp_amount: float:
	get:
		var t := clampf(fp_blend, 0.0, 1.0)
		return t * t * (3.0 - 2.0 * t)

## First-person heading like Player.yaw: 0 faces the entrance (+z), π the back wall.
var yaw := PI
## First-person pitch, radians, up positive; clamped to ±PITCH_LIMIT_DEG.
var pitch := REST_PITCH_DEG * DEG

## Where the player stands (feet), for the eye.
var _player_x := 304.0
var _player_z := 600.0
var _walk_phase := 0.0
## How much of the head-bob is on: follows the walking pace (1 at walking speed, a little more
## running) and eases to exactly 0 at rest.
var bob_weight := 0.0
## Degrees the first-person view is widened by right now (running).
var fov_kick := 0.0

## The player's options: a multiplier on the first-person field of view (1 = as designed; the
## setting's degrees over FP_FOV_DEG), and on the head-bob and the run's field-of-view kick (both 0
## with reduced motion).
var fov_scale := 1.0
var bob_scale := 1.0
var kick_scale := 1.0

## How far behind the feet the eye is.
var eye_back: float:
	get:
		return EYE_BACK

## The first-person eye as of the last [method update], world units.
var eye_x: float:
	get:
		return _player_x - sin(yaw) * eye_back + _sway() * -cos(yaw)
var eye_y: float:
	get:
		return EYE_HEIGHT + _bob_lift()
var eye_z: float:
	get:
		return _player_z - cos(yaw) * eye_back + _sway() * sin(yaw)


## First person's vertical field of view (radians) for a screen [param aspect] (width / height):
## FP_FOV_DEG, widened on narrow screens so the view is at least FP_MIN_HFOV_DEG across, but never
## past FP_MAX_FOV_DEG.
static func fp_fov_y(aspect: float) -> float:
	var base := FP_FOV_DEG * DEG
	var a := maxf(aspect, 0.1)
	var needed := 2.0 * atan(tan(FP_MIN_HFOV_DEG * DEG / 2.0) / a)
	return minf(maxf(base, needed), FP_MAX_FOV_DEG * DEG)


## Turns a joystick deflection ([param jx] right, [param jy] down the screen) into a world-space
## walk direction relative to a first-person [param p_yaw]: up is forward, sideways strafes. Writes
## (x, z) into [param out]; the length is the stick's.
static func move_relative(jx: float, jy: float, p_yaw: float, out: PackedFloat32Array) -> void:
	var fx := sin(p_yaw)
	var fz := cos(p_yaw)
	# Right of the view: forward × up.
	var rx := -fz
	var rz := fx
	out[0] = rx * jx - fx * jy
	out[1] = rz * jx - fz * jy


## Wraps an angle into (-π, π].
static func wrap(a: float) -> float:
	var r := fmod(a, TAU)
	if r > PI:
		r -= TAU
	if r <= -PI:
		r += TAU
	return r


func _bob_lift() -> float:
	if bob_weight <= 0.0:
		return 0.0
	return BOB_HEIGHT * bob_scale * bob_weight * (absf(sin(_walk_phase)) - 0.35)


func _sway() -> float:
	if bob_weight <= 0.0:
		return 0.0
	return BOB_SWAY * bob_scale * bob_weight * cos(_walk_phase)


func snap_to(px: float, pz: float) -> void:
	target_x = clampf(px, min_x, max_x)
	target_z = clampf(pz - _below, min_z, max_z)
	_player_x = px
	_player_z = pz


func follow(px: float, pz: float, vx: float, vz: float, dt: float) -> void:
	_lead_x = MathUtil.damp(_lead_x, vx * 0.35, 3.0, dt)
	_lead_z = MathUtil.damp(_lead_z, vz * 0.45, 3.0, dt)
	target_x = MathUtil.damp(target_x, clampf(px + _lead_x, min_x, max_x), 4.0, dt)
	target_z = MathUtil.damp(target_z, clampf(pz + _lead_z - _below, min_z, max_z), 4.0, dt)


## One simulation step: trails the player overhead, tracks the eye, runs the head-bob off the walk
## cycle ([param phase], [param moving], at [param gait] times its walking size, by default 1
## walking and 0 standing) and eases the overhead/first-person blend and the running view
## ([param run], 0..1).
func update(px: float, pz: float, vx: float, vz: float, moving: bool, phase: float, dt: float, gait: float = NAN, run: float = 0.0) -> void:
	if is_nan(gait):
		gait = 1.0 if moving else 0.0
	follow(px, pz, vx, vz, dt)
	_player_x = px
	_player_z = pz
	_walk_phase = phase
	bob_weight = MathUtil.approach(bob_weight, clampf(gait, 0.0, 1.5) if moving else 0.0, dt * 5.0)
	fov_kick = MathUtil.approach(fov_kick, RUN_FOV_KICK_DEG * kick_scale * clampf(run, 0.0, 1.0), dt * RUN_FOV_KICK_DEG * 3.0)
	fp_blend = MathUtil.approach(fp_blend, 1.0 if first_person else 0.0, dt / BLEND_TIME)


## Switches view; [param animate] eases there over BLEND_TIME, otherwise it cuts.
func set_first_person(on: bool, animate: bool) -> void:
	if on != first_person:
		first_person = on
	if not animate:
		fp_blend = 1.0 if on else 0.0


## Sets the first-person heading and pitch (pitch clamped).
func set_look(p_yaw: float, p_pitch: float) -> void:
	yaw = HubCamera.wrap(p_yaw)
	pitch = clampf(p_pitch, -PITCH_LIMIT_DEG * DEG, PITCH_LIMIT_DEG * DEG)


## Turns the first-person view by [param d_yaw] (positive turns left) and tilts it by [param d_pitch] (up).
func look(d_yaw: float, d_pitch: float) -> void:
	set_look(yaw + d_yaw, pitch + d_pitch)


## Points the first-person view from the eye above ([param from_x], [param from_z]) at a world
## point, with the pitch kept gentle ([-25°, 10°]) so the machine fills the view rather than the floor.
func face(from_x: float, from_z: float, x: float, y: float, z: float) -> void:
	var dx := x - from_x
	var dz := z - from_z
	var flat := sqrt(dx * dx + dz * dz)
	if flat < 1e-3:
		return
	var p := clampf(atan2(y - EYE_HEIGHT, flat), -25.0 * DEG, 10.0 * DEG)
	set_look(atan2(dx, dz), p)


func apply(cam: PaCamera3D, width: int, height: int) -> void:
	var p := deg_to_rad(pitch_deg)
	var fov_y := deg_to_rad(fov_deg)
	var aspect := float(width) / maxi(height, 1)
	var half_v := tan(fov_y / 2.0)
	var half_h := half_v * aspect
	# Back off until the wanted slice of floor fits both ways.
	var distance := maxf(cover_width / (2.0 * half_h), cover_height / (2.0 * half_v))
	var ex := target_x
	var ey := 10.0 + sin(p) * distance
	var ez := target_z + cos(p) * distance
	var gx := target_x
	var gy := 10.0
	var gz := target_z
	var fov := fov_y
	var s := fp_amount
	if s > 0.0:
		# The eye, and a point straight ahead of it, so the gaze swings evenly while the eye swoops
		# down (or back up).
		var fx := eye_x
		var fy := eye_y
		var fz := eye_z
		var cp := cos(pitch)
		var reach := 120.0
		var lx := fx + sin(yaw) * cp * reach
		var ly := fy + sin(pitch) * reach
		var lz := fz + cos(yaw) * cp * reach
		ex = lerpf(ex, fx, s)
		ey = lerpf(ey, fy, s)
		ez = lerpf(ez, fz, s)
		gx = lerpf(gx, lx, s)
		gy = lerpf(gy, ly, s)
		gz = lerpf(gz, lz, s)
		fov = lerpf(fov, clampf(fp_fov_y(aspect) * fov_scale, MIN_FOV_DEG * DEG, MAX_FOV_DEG * DEG) + fov_kick * DEG, s)
	if entrance > 0.0:
		# Back off along the line of sight, and open the lens a touch: both settle as it drains.
		var k := clampf(entrance, 0.0, 1.0)
		var pull := 1.0 + ENTRANCE_PULLBACK * k
		ex = gx + (ex - gx) * pull
		ey = gy + (ey - gy) * pull
		ez = gz + (ez - gz) * pull
		fov *= 1.0 + ENTRANCE_FOV * k
	if dive > 0.0:
		var t := clampf(dive, 0.0, 1.0)
		var d := t * t * (3.0 - 2.0 * t)
		ex = lerpf(ex, dive_x, d)
		ey = lerpf(ey, dive_y + 2.0, d)
		ez = lerpf(ez, dive_z + 22.0, d)
		gx = lerpf(gx, dive_x, d)
		gy = lerpf(gy, dive_y, d)
		gz = lerpf(gz, dive_z, d)
		fov = lerpf(fov, deg_to_rad(50.0), d)
	cam.near = lerpf(OVERHEAD_NEAR, FP_NEAR, s)
	cam.look_at(ex, ey, ez, gx, gy, gz, fov, width, height)
