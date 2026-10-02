class_name HubWorld
extends RefCounted
## hub/HubWorld.kt: the walkable arcade hall: map, player, wandering kids, camera and the prompt the
## player is standing at. Lives as long as the app so returning from a game puts you back where you
## were.
##
## [member stage] is the hall agent's HallStage (scripts/hub/hall_stage.gd: the built scene and its
## renderer), made by path so the world also runs where that file isn't (tests, other branches); it
## is null there. The world calls stage.map_changed() when the floor plan is rebuilt and
## stage.look_changed(look) when the player's look changes, as build-13 did.

## First-person look speed: degrees turned per dp dragged sideways...
const LOOK_DEG_PER_DP := 0.3
## ...and up and down this much of it (looking up and down wants a steadier hand).
const LOOK_PITCH_SCALE := 0.7
## The look drag is smoothed over this long (seconds): enough to iron out a finger's pixel jitter,
## far too short to feel. Every pixel dragged still turns the view in full.
const LOOK_SMOOTH := 0.012
## A look-side touch that moves less than this (dp) is a tap, not a turn.
const TAP_SLOP_DP := 10.0
## A touch held longer than this (seconds) isn't a tap.
const TAP_TIME := 0.35
## While walking with no look finger down, the view levels off after this long (seconds)...
const LEVEL_DELAY := 0.6
## ...easing back to the resting pitch at this rate (per second, at walking pace).
const LEVEL_RATE := 1.4
## After a walk overhead, the kid turns to face the machine at this rate (per second).
const FACE_RATE := 10.0
## Stepping into a play spot turns the view to its machine at this rate (per second).
const ASSIST_RATE := 7.0
## The assist only kicks in if you're facing within this of the machine (radians) or stopping.
const ASSIST_CONE := 1.3
## Kids are this round (world units) for bumping into in first person: their big heads, so one
## beside you never fills the view.
const KID_RADIUS := 9.0
## How much of an overlap with a kid is undone each step: a soft bump, not a wall.
const KID_PUSH := 0.35
## Tap-to-walk stops this far in front of a machine's (or counter's) front.
const STAND_DEPTH := PaBody.RADIUS + PaBody.FRONT_GAP + 1.0
## First-person tap-to-walk ignores floor taps further away than this (the horizon is a long way off).
const TAP_REACH := 700.0
## A wall bump is the player, still pushing the stick, falling from WALL_FROM of the walking speed
## or more to under WALL_TO within two steps (the last step into a wall is shortened, so one step
## alone can miss it). Only a near head-on stop does that: sliding along a wall keeps most of the
## speed, and braking (even a full reversal) is far too gradual.
const WALL_FROM := 0.5
const WALL_TO := 0.2
## The prize clerk turns their head to anyone within this distance of them (world units).
const CLERK_NOTICE := 110.0
## Kids within this distance of the prize counter (world units) join in when someone buys a prize,
## the nearest first.
const PRIZE_CHEER_RADIUS := 220.0
const PRIZE_CHEER_DELAY := 0.004

const DEG := PI / 180.0
const STAGE_PATH := "res://scripts/hub/hall_stage.gd"

var games: Array[MiniGame]
var _audio: Object
var _haptics: Haptics

## Read only (rebuilt by [method set_decor]).
var map: HubMap
## What the first-person body walks among: the map's solids with the play spots' fronts kept clear.
## Read only.
var body_solids: Array[Box] = []
## The hall's built 3D scene and its renderer (HallStage), kept as long as the world is; null where
## the hall's stage isn't there (see the class notes).
var stage: Object = null
var player := Player.new()
var npcs: Array[Npc] = []
## The café queue and the barista.
var cafe := CafeLife.new()
## The prize clerk's animation: standing behind the counter, turning to whoever comes up.
var clerk := FigureAnim.new(5, 1.12)
var camera := HubCamera.new()
var joystick := Joystick.new()
## Read only.
var time := 0.0

## Read only.
var screen_w := 0.0
var screen_h := 0.0

## Read only.
var active_spot: Spot = null
## Seconds since the current prompt appeared (drives its pop-in). Read only.
var prompt_t := 0.0

## Screen-space rectangle of the prompt bubble, for tap hit-testing.
var bubble_left := 0.0
var bubble_top := 0.0
var bubble_right := 0.0
var bubble_bottom := 0.0
## Read only.
var bubble_pressed := -1

## Becomes true once the player has walked, which retires the "drag to walk" hint. Read only.
var has_walked := false
## Becomes true once the player has looked around in first person, which retires its hint. Read only.
var has_looked := false

## Set while the tutorial's coach marks are up: they say what the walking and looking hints say,
## better, so those hints keep quiet. Set by the app, never by the hall.
var hints_suppressed := false

## Screen pixels per dp, for the look speed, the tap slop and the stick's size.
var density := 1.0

var _hud_base := 0.0
## Extra rows of HUD buttons under the first one, in pixels ([member hud_bottom] includes them).
var hud_extra := 0.0

## Pixels down from the top of the screen that the HUD's buttons cover (the prompt stays below).
var hud_bottom: float:
	get:
		return _hud_base + hud_extra
	set(v):
		_hud_base = v

## The player's options (see [method apply_settings]): look speed and direction, which hand walks,
## and the run latch. The camera and the joystick carry the rest. Read only.
var settings := GameSettings.new()

## The finger dragging the first-person view (the right half of the screen), or -1. Read only.
var look_pointer := -1
var _look_start_x := 0.0
var _look_start_y := 0.0
var _look_last_x := 0.0
var _look_last_y := 0.0
## Whether the look finger has moved past the tap slop (only then does the view turn). Read only.
var look_dragging := false
var _move := PackedFloat32Array([0.0, 0.0])
var _tmp := PackedFloat32Array([0.0, 0.0])
## Look drag not yet applied to the view (radians), smoothed in over LOOK_SMOOTH.
var _pending_yaw := 0.0
var _pending_pitch := 0.0
## Seconds since the view was last dragged.
var _since_look := 99.0
var _look_down_t := 0.0
var _stick_down_t := 0.0

## Tap-to-walk's route (see [method tap_to_walk]); WalkRoute.active while on the way.
var route := WalkRoute.new()
var _pick_cam := PaCamera3D.new()
## Overhead: the spot whose machine the kid is turning to face after walking there, or null.
var _face_target: Spot = null

## The spot whose machine the view is turning to face, or null. Read only.
var assist_spot: Spot = null
var _assist_t := 0.0
## The spot the view last turned to face (so it only does it once a visit).
var _assisted_spot: Spot = null

## For the bumps: the walking speed one and two steps ago, whether the player was stopped, and
## whether a kid overlapped them.
var _speed1 := 0.0
var _speed2 := 0.0
var _was_stopped := true
var _kid_touching := false

## Footsteps played so far, and the last one's pitch. Read only.
var steps := 0
var last_step_pitch := 1.0

## Whether first person is on (the camera may still be easing there).
var first_person: bool:
	get:
		return camera.first_person

## How many things the save owned when last told (-1 before then) and whether a purchase is waiting
## to be cheered.
var _owned_seen := -1
var _cheer_pending := false
## Seconds the clerk has left of clapping.
var _clerk_cheer_t := 0.0

var _owned_decor: Array[int] = []
var _rng := KRandom.new(42)

## The hall as heard by the player: ears, cabinets' bleeps, kids' footsteps, the café and the crowd.
var _soundscape: HallSoundscape

# Path finding's scratch (Kotlin allocated these per search).
var _bfs_prev := PackedInt32Array()
var _bfs_queue := PackedInt32Array()
## The spots' areas (left, top, right, bottom each), so the step's "which spot am I in" reads one
## packed array instead of 44 boxes.
var _spot_rects := PackedFloat64Array()


func _init(p_games: Array[MiniGame], p_audio: Object = null, p_haptics: Haptics = null) -> void:
	games = p_games
	_audio = p_audio
	_haptics = p_haptics
	map = HubLayout.build(games, _owned_decor)
	body_solids = PaBody.solids_for(map)
	_pack_spots()
	stage = _make_stage()
	_soundscape = HallSoundscape.new(self, _audio)
	player.x = map.spawn_x
	player.y = map.spawn_y
	camera.snap_to(player.x, player.y)
	# About as many kids per square metre as before the hall grew; each one is a lot of vertices,
	# and only the ones in view are drawn.
	var tile := float(HubLayout.TILE)
	for i in 16:
		var look := Looks.random_kid(i + 3)
		var tx: int
		var ty: int
		var tries := 0
		while true:
			tx = _rng.next_int_range(1, map.cols - 1)
			ty = _rng.next_int_range(8, map.rows - 4)
			tries += 1
			if map.tile_walkable(tx, ty) or tries >= 50:
				break
		# Kotlin's `i * 1.7f`.
		npcs.append(Npc.new(look, tx * tile + 8.0, ty * tile + 12.0, KRandom.new(100 + i), AnimMath.f32(i * AnimMath.f32(1.7))))
	_soundscape.publish()


func _pack_spots() -> void:
	_spot_rects.resize(map.spots.size() * 4)
	var j := 0
	for s: Spot in map.spots:
		_spot_rects[j] = s.area.left
		_spot_rects[j + 1] = s.area.top
		_spot_rects[j + 2] = s.area.right
		_spot_rects[j + 3] = s.area.bottom
		j += 4


func _make_stage() -> Object:
	if ResourceLoader.exists(STAGE_PATH):
		var script: Script = load(STAGE_PATH)
		if script != null and script.can_instantiate():
			return script.new(self)
	return null


## Whether a first-person touch at screen x [param x] (of a [param width]-wide screen) drags the
## view rather than the stick: the right half, or the left half for a left-handed player.
static func is_look_side(x: float, width: float, left_handed: bool) -> bool:
	return (x >= width / 2.0) != left_handed


## Footstep pitch for walking at [param speed_frac] of the walking speed: brisker steps, a touch higher.
static func step_pitch(speed_frac: float) -> float:
	return 0.8 + 0.25 * clampf(speed_frac, 0.0, 1.4)


## The owned decorations ([enum Catalog.DecorStyle] values): the floor plan is rebuilt with them.
func set_decor(owned: Array[int]) -> void:
	var sorted := owned.duplicate()
	sorted.sort()
	if sorted == _owned_decor:
		return
	_owned_decor = sorted
	map = HubLayout.build(games, sorted)
	body_solids = PaBody.solids_for(map)
	_pack_spots()
	# The scene takes the new decorations now, not in the next frame drawn.
	if stage != null:
		stage.map_changed()
	_soundscape.publish()
	route.clear()
	# If a new decoration landed on the player, nudge them to the nearest free spot.
	if Collision.blocked(map.solids, player.x, player.y):
		for r in range(1, 7):
			var found := false
			for dx in range(-r, r + 1):
				for dy in range(-r, r + 1):
					if not Collision.blocked(map.solids, player.x + dx * 8.0, player.y + dy * 8.0):
						player.x += dx * 8.0
						player.y += dy * 8.0
						found = true
						break
				if found:
					break
			if found:
				break


func set_player_look(look: CharacterLook) -> void:
	if look == null or look.equals(player.look):
		return
	player.set_look(look)
	if stage != null:
		stage.look_changed(look)


## Takes the player's options: the controls, the view and the comfort settings.
func apply_settings(s: GameSettings) -> void:
	var v := s.sanitized()
	settings = v
	camera.fov_scale = v.fov_deg / HubCamera.FP_FOV_DEG
	camera.bob_scale = 0.0 if v.reduce_motion else 1.0
	camera.kick_scale = 0.0 if v.reduce_motion else 1.0
	# Kids and staff keep moving but drop the bounce, the overshoot and the crouch before a cheer.
	FigureAnim.reduce_motion = v.reduce_motion


func set_viewport(width_px: float, height_px: float) -> void:
	if width_px <= 0.0 or height_px <= 0.0:
		return
	screen_w = width_px
	screen_h = height_px
	joystick.radius = Joystick.radius_for(density, width_px, height_px)


func update(dt: float) -> void:
	time += dt
	var fp := camera.first_person
	if fp:
		_walk_first_person(dt)
	else:
		_walk_overhead(dt)
	if player.moving:
		has_walked = true
	if player.stepped:
		_footstep(fp)
	_feel_walls()
	for i in npcs.size():
		npcs[i].update(dt, self)
	cafe.update(dt, self)
	_look_around()
	if _clerk_cheer_t > 0.0:
		_clerk_cheer_t -= dt
	clerk.update(dt, map.clerk_x, map.clerk_y, sin(time * 0.4) * 0.4, Pose.CLAP if _clerk_cheer_t > 0.0 else Pose.STAND)
	_soundscape.update(dt)
	var gait := player.speed_frac if fp else (1.0 if player.moving else 0.0)
	var run := (player.speed_frac - 1.0) / (Player.RUN_SCALE - 1.0) if fp else 0.0
	camera.update(player.x, player.y, player.vx, player.vy, player.moving, player.phase, dt, gait, run)

	var spot: Spot = null
	var px := player.x
	var py := player.y
	var rects := _spot_rects
	var j := 0
	for i in rects.size() / 4:
		# Box.contains, from the packed copy of the spots' areas.
		if px >= rects[j] and px < rects[j + 2] and py >= rects[j + 1] and py < rects[j + 3]:
			spot = map.spots[i]
			break
		j += 4
	if spot != active_spot:
		active_spot = spot
		prompt_t = 0.0
		bubble_pressed = -1
		if spot != null:
			if _audio != null:
				_audio.play(Sfx.BLIP, 0.35, 1.4)
			if _haptics != null:
				_haptics.soft()
		if spot == null:
			_assisted_spot = null
	else:
		prompt_t += dt
	if fp:
		_assist(dt)


## Tells the hall how many things the player owns now. A purchase (the count going up while standing
## at the prize counter) sets the crowd cheering as soon as the shop closes, see [method hall_resumed].
func note_owned(count: int) -> void:
	if _owned_seen >= 0 and count > _owned_seen and active_spot != null and active_spot.type == SpotType.PRIZES:
		_cheer_pending = true
	_owned_seen = count


## The hall has the player's attention again (a shop or a game closed): a purchase made meanwhile gets its cheer.
func hall_resumed() -> void:
	if not _cheer_pending:
		return
	_cheer_pending = false
	celebrate_purchase()


## Someone bought a prize: the clerk claps, and every kid near the prize counter cheers or claps, the
## nearest first and each with a beat of their own. Only what they show changes.
func celebrate_purchase() -> void:
	_clerk_cheer_t = Emotes.CELEBRATE_TIME + 0.4
	for n: Npc in npcs:
		var d := MathUtil.dist(n.x, n.y, map.clerk_x, map.clerk_y)
		if d < PRIZE_CHEER_RADIUS:
			n.celebrate(d * PRIZE_CHEER_DELAY)


## Points heads at what they would be looking at: the player at the machine they stand before, the
## prize clerk at whoever comes up to the counter. (The kids and the barista decide for themselves,
## in their own updates.)
func _look_around() -> void:
	var spot := active_spot
	if spot != null and not player.moving:
		player.anim.look(spot.focus_x, spot.focus_z, spot.focus_y)
	if MathUtil.dist(map.clerk_x, map.clerk_y, player.x, player.y) < CLERK_NOTICE:
		clerk.look(player.x, player.y, Figure.HEAD_Y)


## Walking into a wall or a cabinet gives a bump: brought to a sudden stop while still pushing on
## (see WALL_FROM).
func _feel_walls() -> void:
	var speed := player.speed_frac
	var stopped := speed < WALL_TO
	var pushed := joystick.out_x * joystick.out_x + joystick.out_y * joystick.out_y > 0.25
	if pushed and stopped and not _was_stopped and maxf(_speed1, _speed2) >= WALL_FROM and _haptics != null:
		_haptics.bump()
	_was_stopped = stopped
	_speed2 = _speed1
	_speed1 = speed


## A footstep: overhead as ever; in first person quieter, and brisker steps sound a touch higher.
func _footstep(fp: bool) -> void:
	var pitch: float
	var volume: float
	if fp:
		var f := player.speed_frac
		pitch = step_pitch(f) + _rng.range_f(-0.05, 0.05)
		volume = 0.22 + 0.1 * minf(f, 1.4)
	else:
		pitch = _rng.range_f(0.8, 1.2)
		volume = 0.5
	steps += 1
	last_step_pitch = pitch
	if _audio != null:
		_audio.play(Sfx.STEP, volume, pitch)


## Overhead's step: the stick walks the kid, or the tap-to-walk route does (the stick takes over at
## once); arriving at a machine turns the kid to face it.
func _walk_overhead(dt: float) -> void:
	var jx := joystick.out_x
	var jy := joystick.out_y
	if jx != 0.0 or jy != 0.0:
		route.clear()
		_face_target = null
	elif route.active:
		var was_for := route.spot
		if route.steer(player.x, player.y, dt, _move):
			jx = _move[0]
			jy = _move[1]
		elif was_for != null and was_for.area.contains(player.x, player.y):
			_face_target = was_for
	player.update(dt, jx, jy, map.solids)
	var face := _face_target
	if face == null:
		return
	if player.moving:
		_face_target = null
		return
	var d := HubCamera.wrap(atan2(face.focus_x - player.x, face.focus_z - player.y) - player.yaw)
	player.yaw += d * (1.0 - exp(-dt * FACE_RATE))
	if absf(d) < 0.02:
		_face_target = null


## First person's step: the smoothed look drag, then the walk — the stick (forward where you look, a
## little slower backwards and sideways, a run at the rim) or the tap-to-walk route — then a soft
## bump off any kid, and the view levelling off as you walk.
func _walk_first_person(dt: float) -> void:
	_apply_look(dt)
	var jx := joystick.out_x
	var jy := joystick.out_y
	if jx != 0.0 or jy != 0.0:
		route.clear()
	if route.active:
		var was_for := route.spot
		if route.steer(player.x, player.y, dt, _move):
			# Turn to face the way you're being walked, as you would.
			var m := sqrt(_move[0] * _move[0] + _move[1] * _move[1])
			if m > 0.3 and look_pointer < 0:
				var target := atan2(_move[0], _move[1])
				var k := 1.0 - exp(-dt * 5.0)
				camera.set_look(camera.yaw + HubCamera.wrap(target - camera.yaw) * k, camera.pitch)
		elif was_for != null and was_for.area.contains(player.x, player.y):
			# Arrived: face the machine.
			_start_assist(was_for)
	else:
		# Stick: up is forward, sideways strafes; backwards and sideways are a little slower and
		# pushing forward at the rim breaks into a run.
		var strafe := jx * Player.STRAFE_SCALE
		var fwd := -jy
		var mag := sqrt(jx * jx + jy * jy)
		if fwd > 0.0:
			var forwardness := fwd / mag if mag > 0.0 else 0.0
			fwd *= 1.0 + (Player.RUN_SCALE - 1.0) * joystick.run * forwardness
		else:
			fwd *= Player.BACK_SCALE
		HubCamera.move_relative(strafe, -fwd, camera.yaw, _move)
	player.walk_first_person(dt, _move[0], _move[1], body_solids, camera.yaw)
	_bump_kids()
	# Walking along with no finger on the view: let it drift back to level.
	if look_pointer < 0 and _since_look > LEVEL_DELAY and player.moving and assist_spot == null:
		var rest := HubCamera.REST_PITCH_DEG * DEG
		var pace := minf(player.speed_frac, 1.0)
		camera.set_look(camera.yaw, MathUtil.damp(camera.pitch, rest, LEVEL_RATE * pace, dt))


## Feeds the look drag into the view, smoothed over LOOK_SMOOTH.
func _apply_look(dt: float) -> void:
	_since_look += dt
	if _pending_yaw == 0.0 and _pending_pitch == 0.0:
		return
	var k := 1.0 - exp(-dt / LOOK_SMOOTH)
	var dy := _pending_yaw * k
	var dp := _pending_pitch * k
	if absf(_pending_yaw - dy) < 1e-5:
		dy = _pending_yaw
	if absf(_pending_pitch - dp) < 1e-5:
		dp = _pending_pitch
	_pending_yaw -= dy
	_pending_pitch -= dp
	camera.look(dy, dp)


## Applies any look drag still being smoothed in, at once.
func _flush_look() -> void:
	if _pending_yaw != 0.0 or _pending_pitch != 0.0:
		camera.look(_pending_yaw, _pending_pitch)
	_pending_yaw = 0.0
	_pending_pitch = 0.0


## Soft bumps: the player eases out of any kid they walk into, never into a wall.
func _bump_kids() -> void:
	var min_d := PaBody.RADIUS + KID_RADIUS
	var px := player.x
	var py := player.y
	var touching := false
	for n: Npc in npcs:
		var dx := px - n.x
		var dy := py - n.y
		var d2 := dx * dx + dy * dy
		if d2 >= min_d * min_d:
			continue
		touching = true
		var d := sqrt(d2)
		var push := (min_d - d) * KID_PUSH
		if d > 1e-3:
			px += dx / d * push
			py += dy / d * push
		else:
			py += push
	# A bump as you walk into a kid (once per meeting, not while they stay overlapped).
	if touching and not _kid_touching and player.moving and _haptics != null:
		_haptics.bump()
	_kid_touching = touching
	if px == player.x and py == player.y:
		return
	PaBody.push_out(body_solids, px, py, PaBody.RADIUS, _tmp)
	if PaBody.clear(body_solids, _tmp[0], _tmp[1]):
		player.place(_tmp[0], _tmp[1])


## Stepping into a play spot turns the view to its machine — if you walked in roughly facing it (or
## stopped there) and aren't steering the view yourself.
func _assist(dt: float) -> void:
	var spot := active_spot
	if assist_spot == null and spot != null and spot != _assisted_spot and not route.active and look_pointer < 0 and _since_look > 0.3:
		var to_machine := atan2(spot.focus_x - player.x, spot.focus_z - player.y)
		var off := absf(HubCamera.wrap(to_machine - camera.yaw))
		if off < ASSIST_CONE or player.speed_frac < 0.15:
			_start_assist(spot)
	var a := assist_spot
	if a == null:
		return
	if look_dragging or route.active or active_spot != a:
		assist_spot = null
		return
	_assist_t += dt
	var dx := a.focus_x - player.x
	var dz := a.focus_z - player.y
	var flat := sqrt(dx * dx + dz * dz)
	if flat < 1e-3:
		assist_spot = null
		return
	var ty := atan2(dx, dz)
	var tp := clampf(atan2(a.focus_y - HubCamera.EYE_HEIGHT, flat), -20.0 * DEG, 10.0 * DEG)
	var k := 1.0 - exp(-dt * ASSIST_RATE)
	var d_yaw := HubCamera.wrap(ty - camera.yaw)
	var d_pitch := tp - camera.pitch
	camera.set_look(camera.yaw + d_yaw * k, camera.pitch + d_pitch * k)
	if (absf(d_yaw) < 0.3 * DEG and absf(d_pitch) < 0.3 * DEG) or _assist_t > 1.5:
		assist_spot = null


func _start_assist(spot: Spot) -> void:
	assist_spot = spot
	_assisted_spot = spot
	_assist_t = 0.0


## The prompt spot of machine [param index]'s cabinet nearest the player, or null if it has none.
func nearest_machine_spot(index: int) -> Spot:
	var best: Spot = null
	var best_d := INF
	for s: Spot in map.spots:
		if s.type != SpotType.MACHINE or s.machine != index:
			continue
		var d := absf(s.area.center_x - player.x) + absf(s.area.center_y - player.y)
		if d < best_d:
			best_d = d
			best = s
	return best


## Points the camera's dive at a machine's screen (used for the enter/exit transition). While fully
## inside, the first-person view turns to face that machine, so coming back out lands looking at it.
func set_dive(spot: Spot, amount: float) -> void:
	if spot == null or amount <= 0.0:
		camera.dive = 0.0
		return
	camera.dive_x = spot.focus_x
	camera.dive_y = spot.focus_y
	camera.dive_z = spot.focus_z
	camera.dive = amount
	if amount >= 0.999:
		face_spot(spot)


## Turns the player (and the first-person view) toward a spot's machine or counter.
func face_spot(spot: Spot) -> void:
	camera.face(player.x, player.y, spot.focus_x, spot.focus_y, spot.focus_z)
	player.yaw = camera.yaw


## Switches between the overhead camera and first person; [param animate] eases the camera between
## the two, otherwise it cuts. Entering first person looks the way the kid is facing.
func set_first_person(on: bool, animate: bool) -> void:
	if on == camera.first_person:
		if not animate:
			camera.set_first_person(on, false)
		return
	if on:
		camera.set_look(player.yaw, HubCamera.REST_PITCH_DEG * DEG)
	# A finger mid-look or mid-walk belongs to the old controls.
	cancel_input()
	player.halt()
	camera.set_first_person(on, animate)


## Whether no other kid (and not the player) is using hangout [param index].
func hangout_free(index: int, asker: Npc) -> bool:
	for n: Npc in npcs:
		if n != asker and n.hangout == index:
			return false
	var h := map.hangouts[index]
	return not (absf(player.x - h.x) < 20.0 and absf(player.y - h.z) < 24.0)


## Breadth-first search over walkable tiles; returns tile indices from start (exclusive) to goal as a
## PackedInt32Array, or null when there's no way.
func find_path(sx: int, sy: int, gx: int, gy: int) -> Variant:
	var cols := map.cols
	var rows := map.rows
	if not map.tile_walkable(gx, gy):
		return null
	var start := clampi(sy, 0, rows - 1) * cols + clampi(sx, 0, cols - 1)
	var goal := gy * cols + gx
	if start == goal:
		return PackedInt32Array()
	var size := cols * rows
	if _bfs_prev.size() != size:
		_bfs_prev.resize(size)
		_bfs_queue.resize(size)
	var prev := _bfs_prev
	var queue := _bfs_queue
	var walkable := map.walkable
	prev.fill(-2)
	var head := 0
	var tail := 0
	queue[tail] = start
	tail += 1
	prev[start] = -1
	while head < tail:
		var cur := queue[head]
		head += 1
		if cur == goal:
			break
		var cx := cur % cols
		var cy := cur / cols
		for d in 4:
			var nx := cx + (1 if d == 0 else (-1 if d == 1 else 0))
			var ny := cy + (1 if d == 2 else (-1 if d == 3 else 0))
			if nx < 0 or nx >= cols or ny < 0 or ny >= rows:
				continue
			var ni := ny * cols + nx
			if walkable[ni] == 0 or prev[ni] != -2:
				continue
			prev[ni] = cur
			queue[tail] = ni
			tail += 1
	if prev[goal] == -2:
		return null
	var length := 0
	var at := goal
	while at != start and at >= 0:
		length += 1
		at = prev[at]
	var out := PackedInt32Array()
	out.resize(length)
	at = goal
	var i := length - 1
	while at != start and at >= 0:
		out[i] = at
		i -= 1
		at = prev[at]
	return out


# ---------------------------------------------------------------- tap to walk

## Walks to what's under screen pixel ([param sx], [param sy]) — a machine (anywhere on it) or a
## counter walks to its play spot and faces it; the floor walks to that point. The tap is projected
## through whichever camera is showing, the kid's eyes or the overhead view. Returns whether a route
## was set.
func tap_to_walk(sx: float, sy: float) -> bool:
	if camera.dive > 0.0 or screen_w <= 0.0:
		return false
	# Halfway between the two views nothing on screen is where it seems to be.
	var fp := camera.first_person
	if (camera.fp_amount < 0.99) if fp else (camera.fp_amount > 0.01):
		return false
	camera.apply(_pick_cam, int(screen_w), int(screen_h))
	var c := _pick_cam
	var u := (sx - c.cx) / c.focal
	var v := -(sy - c.cy) / c.focal
	var dx := c.fx + c.rx * u + c.ux * v
	var dy := c.fy + c.ry * u + c.uy * v
	var dz := c.fz + c.rz * u + c.uz * v
	var best_t := INF
	var hit: Prop = null
	for p: Prop in map.props:
		if not p.solid:
			continue
		var t := _ray_box(c.ex, c.ey, c.ez, dx, dy, dz, p.x0, p.z0, p.x1, p.height, p.z1)
		if t > 0.0 and t < best_t:
			best_t = t
			hit = p
	var floor_t := -c.ey / dy if dy < -1e-4 else INF
	if hit != null and best_t < floor_t:
		var spot := spot_of(hit)
		if spot != null:
			return walk_to(spot)
		# Something without a spot (a pillar, a table): walk up to where you tapped it.
		return walk_to_point(c.ex + dx * best_t, c.ez + dz * best_t)
	# From the eyes the floor stretches to the horizon: only what's near counts. The overhead view
	# only ever shows a slice of it, all of it within reach.
	if fp and floor_t > TAP_REACH:
		return false
	return walk_to_point(c.ex + dx * floor_t, c.ez + dz * floor_t)


## Walks (either camera) to [param spot]'s standing point and faces its machine there.
func walk_to(spot: Spot) -> bool:
	var x := spot.area.center_x
	var y := minf(spot.area.top + STAND_DEPTH, spot.area.bottom - 2.0)
	if spot.area.contains(player.x, player.y) and absf(player.x - x) < 4.0 and absf(player.y - y) < 4.0:
		route.clear()
		_start_assist(spot)
		if not camera.first_person:
			_face_target = spot
		return true
	return _plan(x, y, spot)


## Walks (either camera) to the nearest place the body fits by floor point ([param x], [param z]).
func walk_to_point(x: float, z: float) -> bool:
	var r := PaBody.RADIUS
	var cx := clampf(x, HubLayout.WALL + r, HubLayout.WIDTH - HubLayout.WALL - r)
	var cz := clampf(z, HubLayout.BACK_WALL + r, HubLayout.FRONT_WALL - r)
	PaBody.push_out(body_solids, cx, cz, r, _tmp)
	return _plan(_tmp[0], _tmp[1], null)


func _plan(x: float, y: float, spot: Spot) -> bool:
	assist_spot = null
	_face_target = null
	var ok := route.plan(self, body_solids, player.x, player.y, x, y, spot)
	if ok:
		has_walked = true
	return ok


## The play spot belonging to a prop: a machine's own, the token kiosk's, the prize counter's, or
## the one generated for an interactive prop (Spot.prop).
func spot_of(p: Prop) -> Spot:
	for s: Spot in map.spots:
		if s.prop == p:
			return s
		var mine := false
		if p.kind == PropKind.MACHINE:
			mine = s.type == SpotType.MACHINE and s.machine == p.machine \
				and absf(s.area.center_x - p.center_x) < 1.0 and absf(s.area.top - p.z1) < 1.0
		elif p.kind == PropKind.TOKENS or p.kind == PropKind.CHANGE:
			mine = s.type == SpotType.TOKENS
		elif p.kind == PropKind.COUNTER or p.kind == PropKind.PRIZE_WALL:
			mine = s.type == SpotType.PRIZES
		if mine:
			return s
	return null


## Distance along a ray to an upright box standing on the floor, or -1 if it misses.
func _ray_box(ox: float, oy: float, oz: float, dx: float, dy: float, dz: float,
		x0: float, z0: float, x1: float, h: float, z1: float) -> float:
	var t_min := 0.0
	var t_max := INF
	for axis in 3:
		var o := ox if axis == 0 else (oy if axis == 1 else oz)
		var d := dx if axis == 0 else (dy if axis == 1 else dz)
		var lo := x0 if axis == 0 else (0.0 if axis == 1 else z0)
		var hi := x1 if axis == 0 else (h if axis == 1 else z1)
		if absf(d) < 1e-6:
			if o < lo or o > hi:
				return -1.0
		else:
			var a := (lo - o) / d
			var b := (hi - o) / d
			if a > b:
				var t := a
				a = b
				b = t
			if a > t_min:
				t_min = a
			if b < t_max:
				t_max = b
			if t_min > t_max:
				return -1.0
	return t_min


# ---------------------------------------------------------------- input (screen pixels)

func _in_bubble(x: float, y: float) -> bool:
	if active_spot == null or bubble_right <= bubble_left:
		return false
	var pad := 12.0
	return x >= bubble_left - pad and x <= bubble_right + pad and y >= bubble_top - pad and y <= bubble_bottom + pad


## A finger went down. The prompt bubble takes it first. Overhead, anywhere else starts the floating
## joystick; in first person the left half does, and the right half drags the view (the other way
## round for a left-handed player).
func pointer_down(id: int, x: float, y: float) -> void:
	if _in_bubble(x, y) and bubble_pressed < 0:
		bubble_pressed = id
		return
	if camera.first_person and is_look_side(x, screen_w, settings.left_handed):
		if look_pointer < 0:
			look_pointer = id
			_look_start_x = x
			_look_start_y = y
			_look_last_x = x
			_look_last_y = y
			look_dragging = false
			_look_down_t = time
		return
	if not joystick.active:
		joystick.radius = Joystick.radius_for(density, screen_w, screen_h)
		# Only running (first person) has a rim to lock; overhead the stick is as it always was.
		joystick.run_latch = settings.run_latch and camera.first_person
		_stick_down_t = time
	joystick.down(id, x, y)


func pointer_move(id: int, x: float, y: float) -> void:
	if id == look_pointer:
		var slop := TAP_SLOP_DP * density
		if not look_dragging:
			var dx := x - _look_start_x
			var dy := y - _look_start_y
			if dx * dx + dy * dy > slop * slop:
				look_dragging = true
		if look_dragging:
			# Drag right to turn right, up to look up; the same angle per dp on any screen. Steering
			# the view yourself stops a walk to a machine and the turn to face one.
			var k := LOOK_DEG_PER_DP * DEG / maxf(density, 0.1) * settings.look_scale()
			_pending_yaw += -(x - _look_last_x) * k
			_pending_pitch += -(y - _look_last_y) * k * LOOK_PITCH_SCALE * (-1.0 if settings.invert_y else 1.0)
			_look_last_x = x
			_look_last_y = y
			has_looked = true
			_since_look = 0.0
			route.clear()
			assist_spot = null
		return
	joystick.move(id, x, y)


## Returns the spot whose prompt was tapped, if this release completes a tap on it. A tap anywhere
## else (either half, either camera) walks you to what you tapped ([method tap_to_walk]).
func pointer_up(id: int, x: float, y: float) -> Spot:
	var tap := false
	if id == look_pointer:
		tap = not look_dragging and time - _look_down_t < TAP_TIME
		look_pointer = -1
		look_dragging = false
	if id == joystick.pointer_id and joystick.active:
		tap = joystick.travel < TAP_SLOP_DP * density and time - _stick_down_t < TAP_TIME
	joystick.up(id)
	if id == bubble_pressed:
		bubble_pressed = -1
		if _in_bubble(x, y):
			return active_spot
		return null
	if tap:
		tap_to_walk(x, y)
	return null


## Lets go of every finger: the hall lost focus (a pause, a machine, a dialog).
func cancel_input() -> void:
	joystick.release()
	bubble_pressed = -1
	look_pointer = -1
	look_dragging = false
	_flush_look()
	route.clear()
	assist_spot = null
	_face_target = null
