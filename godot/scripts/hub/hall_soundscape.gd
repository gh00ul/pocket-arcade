class_name HallSoundscape
extends RefCounted
## hub/HallSoundscape.kt: the hall as heard by the player. Each frame it puts the listener's ears
## where the player stands, facing where the camera looks; it tells the ambience where the cabinets
## and the café are, so their bleeps, steam and clinks come from there; it plays other kids'
## footsteps and the odd cheer from where they are; and it works out how busy it is around the
## player, which swells the crowd murmur (louder by the café and among many kids). Voice counts are
## capped so a full hall stays a bed.
##
## The sink is Kotlin's HallSoundSink: any object with set_listener(x, z, yaw_rad),
## set_hall_sources(xs, zs, kinds, count), set_cafe(x, z), set_crowd(level),
## play_at(sfx, x, z, volume, pitch) and set_music_intensity(level). [AudioSynth] is the real one;
## tests record.

## A kid's footstep is this loud (the player's own are 0.22-0.5): a soft pattering.
const STEP_VOLUME := 0.14
## Kids further than this (world units) aren't given footsteps at all: they'd be too quiet to hear.
const STEP_RANGE := 200.0
## At most this many kid footsteps start in one frame (a crowd doesn't all land at once).
const STEPS_PER_FRAME := 3
## A far-off kid cheering: a faint "yay" (the game's own is 0.7-1), heard within this range, at
## most this often (seconds).
const CHEER_VOLUME := 0.09
const CHEER_RANGE := 220.0
const CHEER_COOLDOWN := 4.0
## How often the crowd level is re-worked (seconds): it only drifts, so ten times a second is plenty.
const CROWD_INTERVAL := 0.1
## Kids within this of the player count towards the crowd, more the closer they are.
const CROWD_RADIUS := 260.0
## This many kids' worth of crowd around you is as busy as the kids alone can make it.
const CROWD_FULL := 4.0
## The café counter's murmur reaches this far, and adds this much at the counter.
const CAFE_RADIUS := 240.0
const CAFE_WEIGHT := 0.5
## The music's intensity in the hall: the crowd around you (the café, a room full of kids) and
## whether you are walking. Standing alone it stays with the bed (under the hall theme's first layer
## at 0.3); walking brings in the arpeggio and the beat; walking through a busy spot brings in the
## lead as well (0.65).
const INTENSITY_CROWD := 0.65
const INTENSITY_WALKING := 0.35
## The crowd level of an empty hall, and how much of the rest the kids can add.
const CROWD_FLOOR := 0.15
const CROWD_KIDS := 0.4
## The café's counter: its middle, where the steam wand and the cups are heard from.
const CAFE_X := (CafeLayout.LANE_X0 + CafeLayout.LANE_X1) / 2.0
const CAFE_Z := CafeLayout.LANE_Z

var _world: HubWorld
var _sink: Object
var _last_step := PackedInt32Array()
var _was_cheering := PackedByteArray()
var _crowd_timer := 0.0
var _crowd_now := CROWD_FLOOR
var _cheer_cooldown := 0.0
var _rng := KRandom.new(2024)


func _init(world: HubWorld, sink: Object) -> void:
	_world = world
	_sink = sink


## How busy it is at a spot with [param kid_weight] (kids around, each weighted by nearness) and
## [param cafe_distance] from the café counter: 0 to 1, rising with either.
static func crowd_level(kid_weight: float, cafe_distance: float) -> float:
	var kids := MathUtil.clamp01(kid_weight / CROWD_FULL)
	var cafe := MathUtil.clamp01(1.0 - cafe_distance / CAFE_RADIUS)
	return MathUtil.clamp01(CROWD_FLOOR + CROWD_KIDS * kids + CAFE_WEIGHT * cafe)


## The hall music's intensity for a player who is [param moving] (or not) at crowd level [param crowd].
static func hall_intensity(moving: bool, crowd: float) -> float:
	return MathUtil.clamp01(INTENSITY_CROWD * crowd + (INTENSITY_WALKING if moving else 0.0))


## The listener's yaw: overhead the camera looks at the back wall (pi), in first person along the
## player's gaze, and while the view eases between them so do the ears. [param first_person_amount]
## is the camera's eased 0..1 blend, [param gaze_yaw] its first-person yaw.
static func listener_yaw(first_person_amount: float, gaze_yaw: float) -> float:
	var back := PI
	return HubCamera.wrap(back + HubCamera.wrap(gaze_yaw - back) * first_person_amount)


## Tells the ambience where every cabinet and the café are. Call once the map exists, and again if
## it is rebuilt.
func publish() -> void:
	var s := _sink
	if s == null:
		return
	var games := _world.games
	var xs := PackedFloat32Array()
	var zs := PackedFloat32Array()
	var kinds := PackedInt32Array()
	for p: Prop in _world.map.props:
		if p.kind == PropKind.MACHINE and p.machine >= 0 and p.machine < games.size():
			xs.append(p.center_x)
			zs.append(p.center_z)
			kinds.append(Attract.kind_for(games[p.machine].id))
	s.set_hall_sources(xs, zs, kinds, xs.size())
	s.set_cafe(CAFE_X, CAFE_Z)
	# Until the first frame, the ears are at the doors (so the title's bleeps come from the front of the hall).
	s.set_listener(_world.player.x, _world.player.y, listener_yaw(_world.camera.fp_amount, _world.camera.yaw))


## One frame: the ears, the kids' footsteps and cheers, and every so often the crowd level.
func update(dt: float) -> void:
	var s := _sink
	if s == null:
		return
	var px := _world.player.x
	var pz := _world.player.y
	s.set_listener(px, pz, listener_yaw(_world.camera.fp_amount, _world.camera.yaw))

	var npcs := _world.npcs
	var count := npcs.size()
	if _last_step.size() != count:
		_last_step.resize(count)
		for i in count:
			_last_step[i] = int(npcs[i].phase / PI)
		_was_cheering.resize(count)
		_was_cheering.fill(0)
	_cheer_cooldown -= dt
	var steps := 0
	for i in count:
		var n := npcs[i]
		var idx := int(n.phase / PI)
		if idx != _last_step[i]:
			_last_step[i] = idx
			if n.state == Npc.State.WALK and steps < STEPS_PER_FRAME and _near(n.x, n.y, px, pz, STEP_RANGE):
				steps += 1
				s.play_at(Sfx.STEP, n.x, n.y, STEP_VOLUME, _rng.range_f(0.85, 1.2))
		var cheering := n.pose == Pose.CHEER
		if cheering and _was_cheering[i] == 0 and _cheer_cooldown <= 0.0 and _near(n.x, n.y, px, pz, CHEER_RANGE):
			_cheer_cooldown = CHEER_COOLDOWN
			s.play_at(Sfx.CHEER, n.x, n.y, CHEER_VOLUME, _rng.range_f(0.9, 1.15))
		_was_cheering[i] = 1 if cheering else 0

	_crowd_timer -= dt
	if _crowd_timer <= 0.0:
		_crowd_timer = CROWD_INTERVAL
		var weight := 0.0
		for i in count:
			var n := npcs[i]
			var d := sqrt((n.x - px) * (n.x - px) + (n.y - pz) * (n.y - pz))
			if d < CROWD_RADIUS:
				weight += 1.0 - d / CROWD_RADIUS
		_crowd_now = crowd_level(weight, sqrt((CAFE_X - px) * (CAFE_X - px) + (CAFE_Z - pz) * (CAFE_Z - pz)))
		s.set_crowd(_crowd_now)
	s.set_music_intensity(hall_intensity(_world.player.moving, _crowd_now))


func _near(x: float, z: float, px: float, pz: float, r: float) -> bool:
	var dx := x - px
	var dz := z - pz
	return dx * dx + dz * dz <= r * r
