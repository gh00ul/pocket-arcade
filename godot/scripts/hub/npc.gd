class_name Npc
extends RefCounted
## hub/Npc.kt: a kid enjoying the arcade: wanders a path to a free machine, plays it for a while
## (with the odd cheer), sits down for a bit, or queues at the café, buys a treat and takes it to a
## café seat, then moves on. In first person they don't let the player walk through them: a kid
## walking into the player steps aside (or waits), and one standing or playing where the player
## wants to be makes way.

enum State { IDLE, WALK, PLAY, SIT, QUEUE }

static var NO_PATH := PackedInt32Array()
## Kids give way to the player inside this distance (world units, centre to centre).
const GIVE_WAY := PaBody.RADIUS + HubWorld.KID_RADIUS + 4.0
## A kid waiting for the player to pass gives up on where they were going after this long.
const WAIT_LIMIT := 2.0
## Having given up, a kid walks on regardless of the player for this long (they are softly bumped
## aside): a kid pinned against a cabinet can't step aside, and every place they could go next
## starts the same way.
const IMPATIENT_TIME := 3.0
## A kid notices the player inside this distance and stops noticing beyond NOTICE_LEAVE (world units).
const NOTICE_DIST := 64.0
const NOTICE_LEAVE := 84.0
## A kid playing a machine looks at a point this far in front of them, at screen height.
const MACHINE_DIST := 14.0
const MACHINE_GAZE_Y := 44.0
## A kid only waves at a player within this angle (radians) of straight ahead.
const WAVE_CONE := 1.1

var look: CharacterLook
var x: float
var y: float
## The kid's own seed (Kotlin's Float `seed`); the animation and gestures use (seed × 1000) as an Int.
var seed_value: float

## Read only.
var yaw := 0.0
## Read only.
var pose: int = Pose.STAND
## The stride cycle: advanced by the ground the kid covers (see FigureAnim.phase).
var phase: float:
	get:
		return anim.phase
## How this kid moves: blended poses, gait, gaze and follow-through (see [FigureAnim]).
var anim: FigureAnim
## Read only.
var state: int = State.IDLE
## Index of the hangout this kid is heading to or using, or -1. Read only.
var hangout := -1
## The café queue spot this kid holds (walking there or standing in it), or -1. Read only.
var queue_spot := -1
## True while this kid stands at the till waiting for the barista.
var ordering: bool:
	get:
		return state == State.QUEUE and queue_spot == 0
## Carrying a café treat (a cup or a cone, by Figure.held_item). Read only.
var holding := false

var _rng: KRandom
## How long this kid has had the player close by, and how long it takes them to notice (seconds).
var _notice_t := 0.0
var _emotes: Emotes
var _reaction := 0.0

var _timer := 0.0
var _path := NO_PATH
var _path_pos := 0
var _speed := 0.0
var _stuck_t := 0.0
var _last_x := 0.0
var _last_y := 0.0
var _target_yaw := 0.0
## Where the walk ends after the last path tile (a seat, a play spot, a queue spot).
var _end_x := 0.0
var _end_y := 0.0
var _has_end := false
## How long this kid has been in the café queue; they give up after a while.
var _waited := 0.0
## How long this kid has been waiting for the player to get out of the way.
var _give_way_t := 0.0
## Seconds left of walking on regardless of the player (see IMPATIENT_TIME).
var _impatient_t := 0.0


func _init(p_look: CharacterLook, p_x: float, p_y: float, p_rng: KRandom, p_seed: float) -> void:
	look = p_look
	x = p_x
	y = p_y
	_rng = p_rng
	seed_value = p_seed
	# Kotlin's (seed * 1000f).toInt(), in Float arithmetic.
	var anim_seed := int(AnimMath.f32(AnimMath.f32(p_seed) * 1000.0))
	# The property initialisers' order (the random draws are build-13's): yaw, the animation, the
	# gestures, then the timer and the walking speed.
	yaw = _rng.range_f(0.0, 6.28)
	anim = FigureAnim.new(anim_seed, 1.0)
	_emotes = Emotes.new(anim_seed)
	_reaction = 0.25 + 0.45 * AnimMath.unit(anim_seed, 0, 9)
	anim.prime(x, y, yaw, pose)
	_timer = _rng.range_f(0.5, 3.0)
	_speed = _rng.range_f(30.0, 44.0)
	_last_x = x
	_last_y = y
	_target_yaw = yaw


## One simulation step: where the kid goes and what they do (which the animation never changes),
## then the animation follows it.
func update(dt: float, world: HubWorld) -> void:
	_step(dt, world)
	_aim_gaze(dt, world)
	_gesture(dt, world)
	anim.update(dt, x, y, yaw, pose, _target_yaw)


## Sets this kid cheering or clapping (a clap when seated) after [param delay] seconds, for someone's
## good news. Only the pose they show changes: where they go and what they do next doesn't.
func celebrate(delay: float) -> void:
	_emotes.celebrate(delay, state == State.SIT)


## Now and then, a kid who has noticed the player in front of them waves; and a celebration (see
## [method celebrate]) shows as a cheer. Swaps the pose shown, nothing else.
func _gesture(dt: float, world: HubWorld) -> void:
	var p := world.player
	# AnimMath.wrap, in place.
	var rel := fmod(atan2(p.x - x, p.y - y) - anim.yaw, TAU)
	if rel > PI:
		rel -= TAU
	if rel < -PI:
		rel += TAU
	var close := _notice_t > _reaction and absf(rel) < WAVE_CONE
	var standing := not holding and (state == State.IDLE or (state == State.QUEUE and not ordering))
	var shown := _emotes.update(dt, close, standing, state == State.SIT, state == State.WALK)
	if shown != Pose.NONE:
		pose = shown


## Decides what this kid is looking at: the machine they are playing, the barista at the till, the
## till from further back in the queue, and anyone who comes near, once they have noticed (after a
## moment's reaction time, different for each kid). Only the head follows.
func _aim_gaze(dt: float, world: HubWorld) -> void:
	var p := world.player
	var near := NOTICE_LEAVE if _notice_t > 0.0 else NOTICE_DIST
	var ndx := p.x - x
	var ndy := p.y - y
	_notice_t = _notice_t + dt if sqrt(ndx * ndx + ndy * ndy) < near else 0.0
	if state == State.PLAY:
		if hangout >= 0:
			var h := world.map.hangouts[hangout]
			anim.look(h.x + sin(h.yaw) * MACHINE_DIST, h.z + cos(h.yaw) * MACHINE_DIST, MACHINE_GAZE_Y)
	elif state == State.QUEUE:
		if ordering:
			var b := world.cafe.barista
			anim.look(b.x, b.z, Figure.HEAD_Y * 1.05)
		else:
			anim.look(CafeLayout.TILL_X, CafeLayout.LANE_Z, Figure.HEAD_Y)
	# Someone nearby gets noticed, unless this kid is absorbed in a game or giving an order.
	if _notice_t > _reaction and state != State.PLAY and not ordering:
		anim.look(p.x, p.y, Figure.HEAD_Y)


func _step(dt: float, world: HubWorld) -> void:
	if world.first_person and _give_way(dt, world):
		yaw = _turn_towards(yaw, _target_yaw, dt * 8.0)
		return
	match state:
		State.IDLE:
			pose = Pose.HOLD if holding else Pose.STAND
			_timer -= dt
			if _timer <= 0.0:
				# Finished a treat without finding a seat.
				if holding and _rng.next_float() < 0.5:
					holding = false
				_choose_target(world)
		State.PLAY, State.SIT:
			_timer -= dt
			# A little celebration now and then.
			if state == State.SIT:
				pose = Pose.SIP if holding else Pose.SIT
			else:
				pose = Pose.CHEER if fmod(_timer, 5.0) < 0.8 else Pose.PLAY
			if _timer <= 0.0:
				state = State.IDLE
				hangout = -1
				holding = false
				_timer = _rng.range_f(1.0, 3.0)
		State.QUEUE:
			_queue(dt, world)
		State.WALK:
			_walk(dt, world)
	yaw = _turn_towards(yaw, _target_yaw, dt * 8.0)


func _turn_towards(a: float, b: float, max_step: float) -> float:
	var d := fmod(b - a, TAU)
	if d > PI:
		d -= TAU
	if d < -PI:
		d += TAU
	return a + clampf(d, -max_step, max_step)


func _choose_target(world: HubWorld) -> void:
	var map := world.map
	# Now and then, fancy a treat from the café.
	if not holding and not map.cafe_queue.is_empty() and _rng.next_float() < 0.2:
		var k := world.cafe.join(self, map.cafe_queue.size())
		if k >= 0:
			queue_spot = k
			_waited = 0.0
			var q := map.cafe_queue[k]
			if _start_walk(world, q.tile_x, q.tile_y, -1, q.x, q.z):
				return
			world.cafe.leave(self)
			queue_spot = -1
			return
	var goal_x: int
	var goal_y: int
	var target := -1
	if _rng.next_float() < 0.75 and not map.hangouts.is_empty():
		var pick := _rng.next_int_until(map.hangouts.size())
		if world.hangout_free(pick, self):
			target = pick
	if target >= 0:
		var h := map.hangouts[target]
		_start_walk(world, h.tile_x, h.tile_y, target, h.x, h.z)
		return
	var tries := 0
	while true:
		goal_x = _rng.next_int_range(1, map.cols - 1)
		goal_y = _rng.next_int_range(8, map.rows - 4)
		tries += 1
		if map.tile_walkable(goal_x, goal_y) or tries >= 30:
			break
	if not map.tile_walkable(goal_x, goal_y):
		_timer = 1.0
		return
	_start_walk(world, goal_x, goal_y, -1, 0.0, 0.0, false)


## Paths to tile ([param goal_x], [param goal_y]) and then, with [param end], steps on to
## ([param ex], [param ey]). Returns false (and waits a moment) when there's no way there.
func _start_walk(world: HubWorld, goal_x: int, goal_y: int, target: int, ex: float, ey: float, end: bool = true) -> bool:
	var tile := float(HubLayout.TILE)
	var sx := int(x / tile)
	var sy := int((y - 4.0) / tile)
	var p: Variant = world.find_path(sx, sy, goal_x, goal_y)
	if p == null or ((p as PackedInt32Array).is_empty() and not end):
		_timer = _rng.range_f(0.5, 1.5)
		return false
	_path = p
	_path_pos = 0
	hangout = target
	_has_end = end
	_end_x = ex
	_end_y = ey
	state = State.WALK
	_stuck_t = 0.0
	return true


func _walk(dt: float, world: HubWorld) -> void:
	pose = Pose.CARRY if holding else Pose.WALK
	var goal_x: float
	var goal_y: float
	var on_path := _path_pos < _path.size()
	var tile := float(HubLayout.TILE)
	if on_path:
		var node := _path[_path_pos]
		var cols := world.map.cols
		var tx := (node % cols) * tile + tile / 2.0
		var ty := (node / cols) * tile + tile / 2.0 + 4.0
		var final_node := _path_pos == _path.size() - 1
		goal_x = _end_x if final_node and _has_end else tx
		goal_y = _end_y if final_node and _has_end else ty
	elif _has_end and MathUtil.dist(x, y, _end_x, _end_y) >= 1.5:
		goal_x = _end_x
		goal_y = _end_y
	else:
		_arrive(world)
		return
	var dx := goal_x - x
	var dy := goal_y - y
	var d := MathUtil.dist(x, y, goal_x, goal_y)
	if d < 1.5:
		if on_path:
			_path_pos += 1
		else:
			_arrive(world)
	else:
		var step := minf(_speed * dt, d)
		x += dx / d * step
		y += dy / d * step
		_target_yaw = atan2(dx, dy)
	_stuck_t = _stuck_t + dt if MathUtil.dist(x, y, _last_x, _last_y) < 0.01 else 0.0
	_last_x = x
	_last_y = y
	if _stuck_t > 1.5:
		state = State.IDLE
		hangout = -1
		if queue_spot >= 0:
			world.cafe.leave(self)
			queue_spot = -1
		_timer = 0.5


## The direction (radians, as yaw) this walk is heading right now: at the next path tile or, last,
## the end point.
func _heading_of(world: HubWorld) -> float:
	var gx: float
	var gy: float
	var tile := float(HubLayout.TILE)
	if _path_pos < _path.size():
		var node := _path[_path_pos]
		var cols := world.map.cols
		var at_end := _has_end and _path_pos == _path.size() - 1
		gx = _end_x if at_end else (node % cols) * tile + tile / 2.0
		gy = _end_y if at_end else (node / cols) * tile + tile / 2.0 + 4.0
	elif _has_end:
		gx = _end_x
		gy = _end_y
	else:
		return _target_yaw
	return _target_yaw if gx == x and gy == y else atan2(gx - x, gy - y)


func _arrive(world: HubWorld) -> void:
	if queue_spot >= 0:
		state = State.QUEUE
		_target_yaw = world.map.cafe_queue[queue_spot].yaw
		return
	if hangout >= 0 and world.hangout_free(hangout, self):
		var h := world.map.hangouts[hangout]
		state = State.PLAY if h.playing else State.SIT
		_target_yaw = h.yaw
		_timer = _rng.range_f(9.0, 16.0) if holding else _rng.range_f(5.0, 12.0)
	else:
		state = State.IDLE
		hangout = -1
		_timer = _rng.range_f(1.5, 4.0)


## Waiting in the café queue: shuffle up when there's room, give up if it takes too long.
func _queue(dt: float, world: HubWorld) -> void:
	pose = Pose.STAND
	_waited += dt
	var k := queue_spot
	if k > 0 and world.cafe.is_free(k - 1):
		world.cafe.move_up(self, k)
		queue_spot = k - 1
		var q := world.map.cafe_queue[k - 1]
		_path = NO_PATH
		_path_pos = 0
		hangout = -1
		_has_end = true
		_end_x = q.x
		_end_y = q.z
		state = State.WALK
		_stuck_t = 0.0
		return
	if _waited > 45.0:
		world.cafe.leave(self)
		queue_spot = -1
		state = State.IDLE
		_timer = _rng.range_f(0.5, 2.0)


## The barista hands over the treat: take it to a free café seat.
func served(world: HubWorld) -> void:
	world.cafe.leave(self)
	queue_spot = -1
	holding = true
	var map := world.map
	var n := map.hangouts.size()
	var start := _rng.next_int_until(maxi(n, 1))
	for i in n:
		var idx := (start + i) % n
		var h := map.hangouts[idx]
		if not h.cafe or not world.hangout_free(idx, self):
			continue
		if _start_walk(world, h.tile_x, h.tile_y, idx, h.x, h.z):
			return
	# Nowhere to sit: stand about with it for a bit.
	state = State.IDLE
	_timer = _rng.range_f(3.0, 6.0)


## First person: keeps out of the player's way. Returns true if this step went on stepping aside
## (instead of the usual walk). Nobody is ever moved into a solid.
func _give_way(dt: float, world: HubWorld) -> bool:
	var p := world.player
	var dx := x - p.x
	var dy := y - p.y
	var d2 := dx * dx + dy * dy
	if d2 >= GIVE_WAY * GIVE_WAY:
		_give_way_t = 0.0
		_impatient_t = 0.0
		return false
	if _impatient_t > 0.0:
		_impatient_t -= dt
	var d := maxf(sqrt(d2), 1e-3)
	var solids := world.map.solids
	var stepping := false
	if state == State.PLAY or state == State.IDLE:
		# Playing or loitering right where the player wants to be: make way.
		if d < GIVE_WAY - 2.0:
			state = State.IDLE
			hangout = -1
			_timer = 0.0
	elif state == State.WALK:
		# Which way they're really going: their facing only turns to it once they've taken a step,
		# and this branch keeps them from taking one.
		var heading := _heading_of(world)
		var hx := sin(heading)
		var hy := cos(heading)
		# The player is ahead: step aside, away from them, if there's room; else wait.
		if _impatient_t <= 0.0 and -(dx * hx + dy * hy) / d > 0.2:
			var sx := -hy
			var sy := hx
			if sx * dx + sy * dy < 0.0:
				sx = -sx
				sy = -sy
			var step := _speed * 0.8 * dt
			var nx := x + sx * step
			var ny := y + sy * step
			if not Collision.blocked(solids, nx, ny):
				x = nx
				y = ny
			pose = Pose.CARRY if holding else Pose.WALK
			_give_way_t += dt
			stepping = _give_way_t < WAIT_LIMIT
			if not stepping:
				# Waited long enough: go somewhere else.
				_give_way_t = 0.0
				_impatient_t = IMPATIENT_TIME
				state = State.IDLE
				hangout = -1
				if queue_spot >= 0:
					world.cafe.leave(self)
					queue_spot = -1
				_timer = 0.5
	# Sitting or queueing: the player goes round.
	# Bumped into: shuffle out of the way if there's room behind.
	var min_d := PaBody.RADIUS + HubWorld.KID_RADIUS
	if d < min_d and state != State.SIT and state != State.QUEUE:
		var push := (min_d - d) * 0.5
		var nx := x + dx / d * push
		var ny := y + dy / d * push
		if not Collision.blocked(solids, nx, ny):
			x = nx
			y = ny
	return stepping
