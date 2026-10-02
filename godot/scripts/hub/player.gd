class_name Player
extends RefCounted
## hub/Player.kt: the player's kid: analog movement with wall sliding, smooth turning and a walk
## cycle. Overhead the kid moves at once; in first person ([method walk_first_person]) they get up
## to speed and stop over a fraction of a second, and the stride quickens with the pace.

const SPEED := 78.0
## First person: seconds from standing to full walking speed...
const ACCEL_TIME := 0.18
## ...and from full walking speed to a stop (also how hard a reversal brakes).
const STOP_TIME := 0.1
## First person: walking backwards and sideways is a little slower than forwards.
const BACK_SCALE := 0.72
const STRAFE_SCALE := 0.85
## First person: pushing the stick to its rim breaks into a run this much faster.
const RUN_SCALE := 1.35
## Below this speed (world units a second) first person counts as standing still.
const STILL_SPEED := 3.0

var x := 0.0
var y := 0.0
## Velocity (world x and z per second). Read only.
var vx := 0.0
var vy := 0.0
## Facing, radians: 0 looks toward the entrance (+z), π toward the back wall.
var yaw := PI
## Read only.
var pose: int = Pose.STAND
## The stride cycle, from [member anim]: a foot lands at every multiple of π (the first-person head
## bob keeps time with it). Read only.
var phase := 0.0
## Read only.
var moving := false
## True for the one update in which a foot touched down (for footstep sounds). Read only.
var stepped := false

## Read only: set with [method set_look].
var look: CharacterLook = null

## How the kid moves: blended poses, gait and follow-through (see [FigureAnim]); stepped by
## [method update] and [method walk_first_person].
var anim := FigureAnim.new(1)

var _out := PackedFloat32Array([0.0, 0.0])
var _slid := PackedFloat32Array([0.0, 0.0])

## Speed as a fraction of SPEED (up to RUN_SCALE running).
var speed_frac: float:
	get:
		return sqrt(vx * vx + vy * vy) / SPEED


func _init() -> void:
	anim.prime(0.0, 0.0, PI, Pose.STAND)


func set_look(new_look: CharacterLook) -> void:
	look = new_look


## Walks by the analog input ([param input_x], [param input_y] in world x and z, length ≤ 1). The
## kid turns toward where they walk, or, given a [param face_yaw] (first person), faces that way instead.
func update(dt: float, input_x: float, input_y: float, solids: Array[Box], face_yaw: float = NAN) -> void:
	stepped = false
	var goal := yaw
	var mag := minf(sqrt(input_x * input_x + input_y * input_y), 1.0)
	if mag > 0.01:
		vx = input_x * SPEED
		vy = input_y * SPEED
		var moved := Collision.move(solids, x, y, vx * dt, vy * dt, _out)
		vx = (_out[0] - x) / dt
		vy = (_out[1] - y) / dt
		x = _out[0]
		y = _out[1]
		moving = moved
		goal = atan2(input_x, input_y)
		if not is_nan(face_yaw):
			yaw = face_yaw
		else:
			# Turn smoothly towards where the stick points.
			var target := atan2(input_x, input_y)
			var d := fmod(target - yaw, TAU)
			if d > PI:
				d -= TAU
			if d < -PI:
				d += TAU
			yaw += clampf(d, -dt * 12.0, dt * 12.0)
	else:
		vx = 0.0
		vy = 0.0
		moving = false
		if not is_nan(face_yaw):
			yaw = face_yaw
	pose = Pose.WALK if moving else Pose.STAND
	anim.update(dt, x, y, yaw, pose, goal, vx, vy)
	phase = anim.phase
	stepped = anim.stepped


## First-person walking: eases the velocity toward ([param wish_x], [param wish_y]) × SPEED (world
## x and z, length up to RUN_SCALE), reaching it in about ACCEL_TIME and stopping in about
## STOP_TIME, and moves the round [PaBody] through [param solids], stepping round corners. The kid
## faces [param face_yaw]. The walk cycle runs off the actual speed, so steps quicken with the pace.
func walk_first_person(dt: float, wish_x: float, wish_y: float, solids: Array[Box], face_yaw: float) -> void:
	stepped = false
	var tx := wish_x * SPEED
	var ty := wish_y * SPEED
	var wishing := wish_x * wish_x + wish_y * wish_y > 1e-4
	# Speeding up is gentle; stopping, or turning back against the way you're going, is quick.
	var braking := not wishing or vx * tx + vy * ty < 0.0
	var rate := SPEED / (STOP_TIME if braking else ACCEL_TIME)
	var dvx := tx - vx
	var dvy := ty - vy
	var dl := sqrt(dvx * dvx + dvy * dvy)
	var max_dv := rate * dt
	if dl <= max_dv:
		vx = tx
		vy = ty
	else:
		vx += dvx / dl * max_dv
		vy += dvy / dl * max_dv
	var x0 := x
	var y0 := y
	var mx := vx * dt
	var my := vy * dt
	PaBody.move(solids, x, y, mx, my, _out)
	var want := mx * mx + my * my
	if want > 1e-8:
		# All but stopped by a face met nearly head-on (sliding along a wall met at an angle is
		# fine as it is): if its end is close, step round it.
		var gx := _out[0] - x0
		var gy := _out[1] - y0
		if gx * gx + gy * gy < 0.09 * want and PaBody.slide_round(solids, _out[0], _out[1], mx, my, _slid):
			_out[0] = _slid[0]
			_out[1] = _slid[1]
	x = _out[0]
	y = _out[1]
	if dt > 0.0:
		# What actually happened is the new velocity (a wall takes the speed into it away), but
		# getting pushed clear of something isn't walking.
		var wanted := sqrt(vx * vx + vy * vy)
		vx = (x - x0) / dt
		vy = (y - y0) / dt
		var got := sqrt(vx * vx + vy * vy)
		if got > wanted and got > 0.0:
			vx *= wanted / got
			vy *= wanted / got
	yaw = face_yaw
	var frac := speed_frac
	moving = frac * SPEED > STILL_SPEED
	pose = Pose.WALK if moving else Pose.STAND
	anim.update(dt, x, y, yaw, pose, yaw, vx, vy)
	phase = anim.phase
	stepped = anim.stepped


## Nudges the kid (after something else moved them, like a kid bumping into them).
func place(nx: float, ny: float) -> void:
	x = nx
	y = ny


## Stops dead (a view switch, a machine).
func halt() -> void:
	vx = 0.0
	vy = 0.0
	moving = false
	pose = Pose.STAND
