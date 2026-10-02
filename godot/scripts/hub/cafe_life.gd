class_name CafeLife
extends RefCounted
## hub/CafeLife.kt: the café's comings and goings: who holds each queue spot, and the barista behind
## the counter who idles, wipes the counter, takes the front kid's order, makes it at the right
## machine and hands it over. Pure logic (seeded, allocation-free per step).

const QUEUE_LEN := 4
const BARISTA_SPEED := 38.0
## The barista glances at the player standing within this distance of them (world units).
const BARISTA_NOTICE := 90.0

## The kid holding each queue spot (walking there or standing in it), front first; null when free.
var queue: Array[Npc] = [null, null, null, null]
var barista := Barista.new()
## How many treats have been handed over. Read only.
var served := 0


## Joins the end of the queue: returns the spot [param npc] should walk to, or -1 when the queue is
## full. A newcomer never cuts in ahead of someone already waiting.
func join(npc: Npc, spots: int) -> int:
	var length := mini(QUEUE_LEN, spots)
	var last := -1
	for k in length:
		if queue[k] == npc:
			return k
		if queue[k] != null:
			last = k
	var k := last + 1
	if k >= length:
		return -1
	queue[k] = npc
	return k


## Whether queue spot [param k] is free (Kotlin's `free`: Object.free is Godot's).
func is_free(k: int) -> bool:
	return k >= 0 and k < QUEUE_LEN and queue[k] == null


## Moves [param npc] up from spot [param k] to [param k] - 1.
func move_up(npc: Npc, k: int) -> void:
	if queue[k] == npc:
		queue[k] = null
	queue[k - 1] = npc


## Takes [param npc] out of the queue wherever it is.
func leave(npc: Npc) -> void:
	for k in QUEUE_LEN:
		if queue[k] == npc:
			queue[k] = null
	if barista.customer == npc:
		barista.customer = null


func update(dt: float, world: HubWorld) -> void:
	# Drop anyone who wandered off without leaving properly.
	for k in QUEUE_LEN:
		var n := queue[k]
		if n == null:
			continue
		if n.queue_spot != k:
			queue[k] = null
	barista.update(dt, self, world)


## The one member of staff: walks the lane behind the counter.
class Barista:
	extends RefCounted

	enum State { IDLE, WALK, WIPE, TAKE, MAKE, SERVE }

	## Read only.
	var x := CafeLayout.TILL_X
	var z := CafeLayout.LANE_Z
	## Read only.
	var yaw := 0.0
	## Read only.
	var pose: int = Pose.STAND
	## How the barista moves (see [FigureAnim]).
	var anim := FigureAnim.new(7, 1.1)
	var phase: float:
		get:
			return anim.phase
	## Read only.
	var state: int = State.IDLE
	## The kid being served, once they reach the till.
	var customer: Npc = null
	## Which treat the barista is carrying (see Figure.held_item) or 0. Read only.
	var item := 0

	var _rng := KRandom.new(7)
	var _timer := 2.0
	var _goal_x := CafeLayout.TILL_X
	var _goal_yaw := 0.0
	## What happens on arriving at _goal_x.
	var _next: int = State.IDLE

	func _walk_to(tx: float, facing: float, then: int) -> void:
		_goal_x = clampf(tx, CafeLayout.LANE_X0, CafeLayout.LANE_X1)
		_goal_yaw = facing
		_next = then
		state = State.WALK

	func update(dt: float, cafe: CafeLife, world: HubWorld) -> void:
		var c := customer
		# A kid waiting at the till gets served before anything else.
		if c == null:
			var front := cafe.queue[0]
			if front != null and front.ordering and state != State.MAKE and state != State.SERVE:
				customer = front
				item = 0
				_walk_to(CafeLayout.TILL_X, 0.0, State.TAKE)
		elif c.queue_spot != 0:
			# They gave up waiting.
			customer = null
			item = 0
			if state != State.WALK:
				state = State.IDLE
			_timer = 1.0
		match state:
			State.WALK:
				var d := _goal_x - x
				if absf(d) < 0.8:
					x = _goal_x
					state = _next
					yaw = _turn(yaw, _goal_yaw, 1.0)
					match _next:
						State.TAKE:
							_timer = 1.4
						State.MAKE:
							_timer = _rng.next_float() * 1.2 + 2.0
						State.SERVE:
							_timer = 1.1
						State.WIPE:
							_timer = _rng.next_float() * 2.0 + 2.5
						_:
							_timer = _rng.next_float() * 2.0 + 1.5
				else:
					var step := minf(absf(d), BARISTA_SPEED * dt)
					x += step if d > 0.0 else -step
					_goal_yaw_while_walking(PI / 2.0 if d > 0.0 else -PI / 2.0, dt)
			State.TAKE:
				_timer -= dt
				if _timer <= 0.0:
					var who := customer
					item = Figure.held_item(who.look) if who != null else 1
					# Cups come from the slushie tanks or the espresso machine, cones from the soft-serve.
					if item == Figure.ITEM_CONE:
						_walk_to(CafeLayout.SOFTSERVE_X, PI, State.MAKE)
					elif _rng.next_float() < 0.65:
						_walk_to(CafeLayout.SLUSH_X, 0.0, State.MAKE)
					else:
						_walk_to(CafeLayout.ESPRESSO_X, PI, State.MAKE)
			State.MAKE:
				_timer -= dt
				if _timer <= 0.0:
					if customer == null:
						item = 0
						state = State.IDLE
						_timer = 1.0
					else:
						_walk_to(CafeLayout.TILL_X, 0.0, State.SERVE)
			State.SERVE:
				_timer -= dt
				if _timer <= 0.0:
					if customer != null:
						customer.served(world)
						cafe.served += 1
					customer = null
					item = 0
					state = State.IDLE
					_timer = _rng.next_float() * 1.5 + 0.8
			State.WIPE:
				_timer -= dt
				if _timer <= 0.0:
					state = State.IDLE
					_timer = _rng.next_float() * 2.0 + 1.0
			State.IDLE:
				_timer -= dt
				if _timer <= 0.0:
					# Wipe down a stretch of counter, or check on the machines.
					var r := _rng.next_float()
					if r < 0.55:
						_walk_to(CafeLayout.LANE_X0 + _rng.next_float() * (CafeLayout.LANE_X1 - CafeLayout.LANE_X0), 0.0, State.WIPE)
					elif r < 0.8:
						_walk_to(CafeLayout.TILL_X, 0.0, State.IDLE)
					else:
						_walk_to(CafeLayout.ESPRESSO_X, PI, State.IDLE)
		if state != State.WALK:
			yaw = _turn(yaw, _goal_yaw, dt * 6.0)
		match state:
			State.WALK:
				pose = Pose.CARRY if item != 0 and _next == State.SERVE else Pose.WALK
			State.WIPE:
				pose = Pose.WIPE
			State.MAKE:
				pose = Pose.PLAY
			State.SERVE:
				pose = Pose.HOLD
			_:
				pose = Pose.STAND
		var serving := customer
		if serving != null and (state == State.TAKE or state == State.SERVE):
			anim.look(serving.x, serving.y, Figure.HEAD_Y)
		elif state == State.IDLE or state == State.WIPE:
			var p := world.player
			if MathUtil.dist(x, z, p.x, p.y) < BARISTA_NOTICE:
				anim.look(p.x, p.y, Figure.HEAD_Y)
		var heading := _goal_yaw
		if state == State.WALK:
			heading = PI / 2.0 if _goal_x > x else -PI / 2.0
		anim.update(dt, x, z, yaw, pose, heading)

	func _goal_yaw_while_walking(target: float, dt: float) -> void:
		yaw = _turn(yaw, target, dt * 8.0)

	func _turn(a: float, b: float, max_step: float) -> float:
		var d := fmod(b - a, TAU)
		if d > PI:
			d -= TAU
		if d < -PI:
			d += TAU
		return a + clampf(d, -max_step, max_step)
