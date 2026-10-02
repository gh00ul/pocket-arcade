class_name CoinPusherGame
extends BaseMiniGame
## games/coinpusher/CoinPusherGame.kt: drop coins onto a packed deck; the sliding shelf pushes the
## pile and whatever goes over the front edge scores. The tuning knobs are in [PusherTuning].

const PF_L := 30.0
const PF_R := 330.0
const DECK_TOP := 92.0
const FRONT_EDGE := 560.0
const GUTTER_TOP := 492.0
const COIN_R := 13.0

# 3D cabinet layout (world units = deck units).
const BACK_Z := DECK_TOP - 40.0
const BACK_H := 240.0
const GUTTER_W := 18.0
const GUTTER_DEPTH := 40.0
const TRAY_FRONT := 620.0
const TRAY_Y := -40.0
const SIDE_H := 40.0
const SHELF_H := 30.0
const GLASS_H := 50.0
const COIN_THICK := 3.0
const DROP_Z := BACK_Z + 6.0
const SLOT_Y := 70.0

const COIN := 0
const GEM := 1
const TICKETS := 2
const STAR := 3
const BIG := 4

# ---- Look (presentation only): these change how the machine looks, never how it plays.

## Brightness above which things bloom; a touch over the default so the gold pile and steel shelf stay calm.
const BLOOM_THRESHOLD := 0.66
const SHELF_GLOSS := 0.4
const DECK_GLOSS := 0.28
## Every coin is tinted one of these (multiplied into its gold), by a hash of the coin, so the pile isn't stamped out.
const COIN_TONES := [-1, 0xFFF0EAD8, 0xFFE4D8C0, 0xFFF8F0E0, 0xFFD8CCB4]
## One coin in this many twinkles now and then.
const GLINT_EVERY := 19
## Rings and lights left where a coin lands on the deck or spills into the tray: how many, how long (s).
const FX_SLOTS := 8
const LAND_LIFE := 0.4
const SPILL_LIFE := 0.7
## How quickly the machine's excitement (from bonuses and avalanches) cools off, per second.
const EXCITE_DECAY := 0.7
## Strength of the amber lights along the side ledges and the cyan light riding the shelf.
const LEDGE_LIGHT := 0.4
const SHELF_LIGHT := 0.35


class Drop:
	extends RefCounted
	var x := 0.0
	var t := 0.0
	var kind := COIN
	var active := false


class Faller:
	extends RefCounted
	var x := 0.0
	var y := 0.0
	var vy := 0.0
	var t := 0.0
	var kind := COIN
	var lost := false
	var active := false
	var angle := 0.0


## The deck's physics: CircleWorld's algorithm on packed arrays (see [FastCircleWorld]), with the
## shelf as its built-in pushing wall.
var world := FastCircleWorld.new()
var drops: Array[Drop] = []
var fallers: Array[Faller] = []
var coins_left := 0
var cooldown := 0.0
var pusher_front := PusherTuning.PUSHER_BACK
var pusher_v := 0.0
var pusher_phase := 0.0
var since_last_drop := 0.0
var won := 0
var lost := 0
var recent_spills := PackedFloat32Array()
var spill_count := 0
var tray_flash := 0.0
var low_coin_warned := false
# Looks only: where the last coins landed and spilled, how long ago, and how excited the machine is.
var land_x := PackedFloat32Array()
var land_z := PackedFloat32Array()
var land_age := PackedFloat32Array()
var land_next := 0
var spill_x := PackedFloat32Array()
var spill_kind := PackedInt32Array()
var spill_age := PackedFloat32Array()
var spill_next := 0
var excite := 0.0
var motion_k := 1.0
## Serial number of the next body: a stable stand-in for Kotlin's System.identityHashCode, which
## picks each coin's tint and glint (presentation only, never the game's rng).
var _body_serial := 0

## The machine in 3D, seen from where you'd stand. The deck simulation is top-down, so its x and y
## become the world's x and depth; items lie on the deck at height 0.
var stage := Stage3D.new(int(MiniGame.GAME_W), int(MiniGame.GAME_H))
## Kotlin's `pt` out-array: the last projected point (field x, y, depth) or plane hit (x, z).
var pt := PackedFloat32Array([0.0, 0.0, 0.0])
var front_screen_y := 0.0
var _deck_tex: PaTexture = null
var coins_panel := PusherArt.PaPanel.new(96, 22)
var won_panel := PusherArt.PaPanel.new(96, 22)
var _cabinet_model: Model = null

var warm := PointLight.new(180.0, 380.0, 320.0, 1.0, 0.9, 0.75, 720.0, 1.0)
var back_glow := PointLight.new(180.0, 150.0, 80.0, 1.0, 0.45, 0.6, 320.0, 0.7)
var tray_light := PointLight.new(180.0, TRAY_Y + 40.0, FRONT_EDGE + 40.0, 1.0, 0.85, 0.3, 300.0, 0.0)
## Amber lights over the gold side ledges, and a cyan one that rides along the shelf's front edge.
var ledge_left := PointLight.new(PF_L - 8.0, 46.0, 320.0, 1.0, 0.62, 0.2, 260.0, LEDGE_LIGHT)
var ledge_right := PointLight.new(PF_R + 8.0, 46.0, 320.0, 1.0, 0.62, 0.2, 260.0, LEDGE_LIGHT)
var shelf_light := PointLight.new(180.0, 44.0, 150.0, 0.3, 0.9, 1.0, 190.0, SHELF_LIGHT)


func _init() -> void:
	id = "pusher"
	title = "COIN PUSHER"
	marquee = "PUSHER"
	instructions = PackedStringArray([
		"TAP TO DROP A COIN",
		"THE SHELF PUSHES THE PILE",
		"COINS OVER THE FRONT EDGE",
		"SCORE! SIDE GUTTERS DON'T.",
		"GRAB GEMS, STARS & TICKETS",
	])
	look = MiniGame.CabinetLook.new(Pal.ORANGE, Pal.GOLD, Pal.YELLOW, MiniGame.CabinetShape.PUSHER)
	round_seconds = PusherTuning.ROUND_SECONDS
	world.iterations = 5
	world.resting_speed = 1000.0
	# Kotlin's `world.constraint = { b -> pushBody(b) }`: the shelf front and speed are handed to
	# the world before every step (_sync_shelf), and it pushes bodies exactly as pushBody does.
	world.push_wall = true
	for i in 16:
		drops.append(Drop.new())
	for i in 40:
		fallers.append(Faller.new())
	recent_spills.resize(12)
	land_x.resize(FX_SLOTS)
	land_z.resize(FX_SLOTS)
	land_age.resize(FX_SLOTS)
	land_age.fill(LAND_LIFE)
	spill_x.resize(FX_SLOTS)
	spill_kind.resize(FX_SLOTS)
	spill_age.resize(FX_SLOTS)
	spill_age.fill(SPILL_LIFE)
	stage.look(180.0, 520.0, 900.0, 180.0, 0.0, 330.0, 50.0)
	stage.r.bloom_threshold = BLOOM_THRESHOLD
	_to_field(MiniGame.GAME_W / 2.0, 0.0, FRONT_EDGE)
	front_screen_y = pt[1]


static func _radius_for(kind: int) -> float:
	match kind:
		BIG:
			return 20.0
		TICKETS:
			return 15.0
		GEM:
			return 12.0
	return COIN_R


## Kotlin's pushBody, kept for reference: [FastCircleWorld] applies exactly this every solver
## iteration from the values [method _sync_shelf] hands it.
func _push_body(b: CircleWorld.Body) -> void:
	var front := pusher_front + b.r
	if b.y < front:
		b.y = front
		if b.vy < pusher_v:
			b.vy = pusher_v


func _sync_shelf() -> void:
	world.push_front = pusher_front
	world.push_v = pusher_v


func reset() -> void:
	world.bodies.clear()
	world.segments.clear()
	world.segments.append(CircleWorld.Segment.new(PF_L, DECK_TOP - 40.0, PF_L, GUTTER_TOP, 0.2))
	world.segments.append(CircleWorld.Segment.new(PF_R, DECK_TOP - 40.0, PF_R, GUTTER_TOP, 0.2))
	for d in drops:
		d.active = false
	for f in fallers:
		f.active = false
	coins_left = PusherTuning.COINS_PER_ROUND
	cooldown = 0.0
	pusher_phase = 0.0
	pusher_front = PusherTuning.PUSHER_BACK
	pusher_v = 0.0
	since_last_drop = 0.0
	won = 0
	lost = 0
	spill_count = 0
	tray_flash = 0.0
	low_coin_warned = false
	land_age.fill(LAND_LIFE)
	spill_age.fill(SPILL_LIFE)
	excite = 0.0
	# A real pusher deck is packed edge to edge: a jittered hex pack from the front lip back to just
	# ahead of the shelf, so every push travels through the pile.
	var spacing_x := COIN_R * 2.0 + 0.6
	var spacing_y := spacing_x * 0.866
	var y := FRONT_EDGE - COIN_R * PusherTuning.FRONT_ROW_OVERHANG
	var row := 0
	var back_limit := PusherTuning.PUSHER_FORWARD + COIN_R + 4.0
	while y > back_limit:
		var x := PF_L + COIN_R + 1.0 + (0.0 if row % 2 == 0 else spacing_x / 2.0)
		while x < PF_R - COIN_R - 1.0:
			if rng.next_float() > PusherTuning.PACK_GAP_CHANCE:
				var jx := x + rng.range_f(-1.5, 1.5)
				var jy := y + rng.range_f(-1.5, 1.5)
				_add_body(COIN, jx, jy)
			x += spacing_x
		y -= spacing_y
		row += 1
	# Bonus items hide among the coins.
	for i in PusherTuning.START_ITEMS:
		var b := world.bodies[rng.next_int_until(world.bodies.size())]
		b.kind = 1 + i % 4
		b.r = _radius_for(b.kind)
		b.mass = 2.5 if b.kind == BIG else 1.0
	_sync_shelf()
	for i in 90:
		world.step(GameLoop.FIXED_DT)
	# Anything the settle nudged off the deck is removed without scoring.
	var kept := 0
	for i in world.bodies.size():
		var b := world.bodies[i]
		if b.y > FRONT_EDGE or (b.y > GUTTER_TOP and (b.x < PF_L or b.x > PF_R)):
			continue
		world.bodies[kept] = b
		kept += 1
	world.bodies.resize(kept)
	for b in world.bodies:
		b.vx = 0.0
		b.vy = 0.0


func _add_body(kind: int, x: float, y: float) -> CircleWorld.Body:
	var b := CircleWorld.Body.new(x, y, _radius_for(kind))
	b.kind = kind
	b.damping = PusherTuning.SLIDE_DAMPING
	b.friction = 0.5
	b.restitution = 0.05
	b.mass = 2.5 if kind == BIG else 1.0
	b.angle = rng.range_f(0.0, TAU)
	b.tag = _stable_hash(_body_serial)
	_body_serial += 1
	world.bodies.append(b)
	return b


## A well-spread non-negative 32-bit hash of [param n] (Knuth's multiplicative hash, shifted like
## Kotlin's `System.identityHashCode(b) ushr 4`).
static func _stable_hash(n: int) -> int:
	return MathUtil.ushr32(MathUtil.i32(n * -1640531535 + 0x7F4A7C15), 4)


func tickets_for(p_score: int) -> int:
	return PusherTuning.BASE_TICKETS + p_score / PusherTuning.POINTS_PER_TICKET


func is_settled() -> bool:
	for d in drops:
		if d.active:
			return false
	for f in fallers:
		if f.active:
			return false
	return true


## Every coin is a single tap: there is no pointer to forget.
func cancel_input() -> void:
	pass


## Kotlin's `stage.toField(x, y, z, pt)`: projects into [member pt] (left as it was when the point
## is behind the eye) and says whether it projected.
func _to_field(x: float, y: float, z: float) -> bool:
	var v: Variant = stage.to_field(x, y, z)
	if v == null:
		return false
	var p: Vector3 = v
	pt[0] = p.x
	pt[1] = p.y
	pt[2] = p.z
	return true


## Kotlin's `stage.touchToPlane(x, y, planeY, pt)`: the hit's (x, z) into pt[0], pt[1].
func _touch_to_plane(x: float, y: float, plane_y: float) -> bool:
	var v: Variant = stage.touch_to_plane(x, y, plane_y)
	if v == null:
		return false
	var p: Vector2 = v
	pt[0] = p.x
	pt[1] = p.y
	return true


func on_touch(type: int, _pointer_id: int, x: float, y: float, _time_ms: int) -> void:
	if type != TouchType.DOWN or time_up or ended_early:
		return
	if y > front_screen_y + 10.0:
		return
	if coins_left <= 0:
		play(Sfx.ERROR, 0.4)
		popups.add("NO COINS", MiniGame.GAME_W / 2.0, 300.0, Pal.GRAY, 3.0)
		return
	if cooldown > 0.0:
		return
	cooldown = PusherTuning.DROP_COOLDOWN
	coins_left -= 1
	since_last_drop = 0.0
	# Drop above the spot on the deck under the finger.
	var deck_x := pt[0] if _touch_to_plane(x, y, 0.0) else x
	var dx := clampf(deck_x, PF_L + COIN_R + 4.0, PF_R - COIN_R - 4.0)
	_launch_drop(dx, COIN)
	if rng.chance(PusherTuning.ITEM_DROP_CHANCE):
		var kind := 1 + rng.next_int_until(4)
		_launch_drop(clampf(dx + rng.range_f(-40.0, 40.0), PF_L + 24.0, PF_R - 24.0), kind)
	play(Sfx.COIN, 0.5, rng.range_f(0.9, 1.1))
	fx.haptics.tick()
	if coins_left == 5 and not low_coin_warned:
		low_coin_warned = true
		popups.add("5 COINS LEFT", MiniGame.GAME_W / 2.0, 300.0, Pal.ORANGE, 3.0)


func _launch_drop(x: float, kind: int) -> void:
	for d in drops:
		if not d.active:
			d.active = true
			d.x = x
			d.t = 0.0
			d.kind = kind
			return


func step(dt: float) -> void:
	cooldown -= dt
	since_last_drop += dt
	tray_flash = maxf(tray_flash - dt * 2.0, 0.0)
	for i in recent_spills.size():
		recent_spills[i] -= dt
	for i in FX_SLOTS:
		if land_age[i] < LAND_LIFE:
			land_age[i] += dt
		if spill_age[i] < SPILL_LIFE:
			spill_age[i] += dt
	excite = maxf(excite - EXCITE_DECAY * dt, 0.0)

	# Pusher shelf: smooth back-and-forth.
	pusher_phase += dt / PusherTuning.PUSHER_PERIOD * TAU
	var mid := (PusherTuning.PUSHER_BACK + PusherTuning.PUSHER_FORWARD) / 2.0
	var amp := (PusherTuning.PUSHER_FORWARD - PusherTuning.PUSHER_BACK) / 2.0
	var new_front := mid - cos(pusher_phase) * amp
	pusher_v = (new_front - pusher_front) / dt
	pusher_front = new_front

	for d in drops:
		if not d.active:
			continue
		d.t += dt
		if d.t >= 0.32:
			d.active = false
			var land_y := pusher_front + _radius_for(d.kind) + rng.range_f(4.0, 26.0)
			var b := _add_body(d.kind, d.x, land_y)
			land_x[land_next] = d.x
			land_z[land_next] = land_y
			land_age[land_next] = 0.0
			land_next = (land_next + 1) % FX_SLOTS
			b.vy = 120.0
			b.vx = rng.range_f(-30.0, 30.0)
			play(Sfx.CLINK, 0.5, rng.range_f(0.8, 1.2))
			_to_field(d.x, 0.0, land_y)
			particles.burst(pt[0], pt[1], 5, 20.0, 80.0, [Pal.YELLOW, Pal.WHITE], 0.3, 3.0)

	_sync_shelf()
	world.step(dt)

	# Coins past the front edge fall into the tray; past the sides into the gutters.
	var bodies := world.bodies
	var n := bodies.size()
	var kept := 0
	for i in n:
		var b := bodies[i]
		var over_front := b.y > FRONT_EDGE
		var over_side := b.y > GUTTER_TOP and (b.x < PF_L or b.x > PF_R)
		if not over_front and not over_side:
			bodies[kept] = b
			kept += 1
			continue
		if over_front:
			_collect(b.kind, b.x)
		else:
			lost += 1
			_to_field(b.x, 0.0, GUTTER_TOP + 20.0)
			popups.add("LOST", clampf(pt[0], 40.0, 320.0), pt[1], Pal.GRAY, 2.0)
			play(Sfx.GUTTER, 0.35, 1.6)
		var f: Faller = null
		for c in fallers:
			if not c.active:
				f = c
				break
		if f == null:
			continue
		f.active = true
		f.x = b.x
		f.y = b.y
		f.vy = maxf(b.vy, 60.0)
		f.t = 0.0
		f.kind = b.kind
		f.lost = not over_front
		f.angle = b.angle
	if kept < n:
		bodies.resize(kept)

	for f in fallers:
		if not f.active:
			continue
		f.t += dt
		f.vy += 900.0 * dt
		f.y += f.vy * dt
		if f.t > 0.5:
			f.active = false

	if not time_up and coins_left == 0 and not _any_drop() and since_last_drop > PusherTuning.SETTLE_AFTER_LAST_COIN:
		ended_early = true


func _any_drop() -> bool:
	for d in drops:
		if d.active:
			return true
	return false


func _collect(kind: int, deck_x: float) -> void:
	won += 1
	tray_flash = 1.0
	spill_x[spill_next] = deck_x
	spill_kind[spill_next] = kind
	spill_age[spill_next] = 0.0
	spill_next = (spill_next + 1) % FX_SLOTS
	var k := 0.15
	if kind == STAR:
		k = 1.0
	elif kind == TICKETS or kind == GEM:
		k = 0.7
	elif kind == BIG:
		k = 0.5
	excite = maxf(excite, k)
	recent_spills[spill_count % recent_spills.size()] = PusherTuning.AVALANCHE_WINDOW
	spill_count += 1
	# Effects happen where the lip is on screen.
	_to_field(deck_x, 0.0, FRONT_EDGE)
	var x := pt[0]
	var y := pt[1] + 20.0
	if kind == GEM:
		add_score(PusherTuning.GEM_POINTS, x, y - 30.0, Pal.CYAN)
		play(Sfx.PRIZE, 0.8)
		fx.haptics.win()
		particles.burst(x, y, 24, 60.0, 240.0, [Pal.CYAN, Pal.PINK, Pal.WHITE], 0.7, 4.0, 0.0, 2.0, Particles.SPARKLE)
	elif kind == TICKETS:
		bonus_tickets += PusherTuning.TICKET_BUNDLE
		popups.add("+%d TICKETS" % PusherTuning.TICKET_BUNDLE, clampf(x, 80.0, 280.0), y - 40.0, Pal.ORANGE, 3.0, 1.3)
		play(Sfx.TICKET, 1.0)
		play(Sfx.WIN, 0.6, 1.2)
		fx.haptics.win()
		particles.burst(x, y, 20, 60.0, 220.0, [Pal.ORANGE, Pal.YELLOW], 0.7, 5.0, 0.0, 2.0, Particles.CONFETTI)
	elif kind == STAR:
		popups.add("COIN SHOWER!", MiniGame.GAME_W / 2.0, 300.0, Pal.YELLOW, 4.0, 1.3)
		add_score(PusherTuning.COIN_POINTS, x, y - 30.0, Pal.YELLOW)
		play(Sfx.LUCKY)
		fx.haptics.jackpot()
		shake.add(0.4)
		for c in PusherTuning.SHOWER_COINS:
			_launch_drop(PF_L + 30.0 + c * (PF_R - PF_L - 60.0) / (PusherTuning.SHOWER_COINS - 1), COIN)
		since_last_drop = 0.0
	elif kind == BIG:
		add_score(PusherTuning.BIG_COIN_POINTS, x, y - 30.0, Pal.GOLD)
		play(Sfx.COIN, 1.0, 0.7)
		fx.haptics.hit()
		shake.add(0.2)
	else:
		add_score(PusherTuning.COIN_POINTS, x, y - 30.0, Pal.YELLOW)
		play(Sfx.CLINK, 0.8, rng.range_f(0.9, 1.3))
		fx.haptics.tick()
	particles.burst(x, y, 6, 40.0, 140.0, [Pal.GOLD, Pal.YELLOW], 0.4, 3.0, 400.0)
	var recent := 0
	for s in recent_spills:
		if s > 0.0:
			recent += 1
	if recent >= PusherTuning.AVALANCHE_COUNT:
		excite = 1.0
		recent_spills.fill(0.0)
		add_score(PusherTuning.AVALANCHE_BONUS, MiniGame.GAME_W / 2.0, 250.0, Pal.PINK, "AVALANCHE +%d" % PusherTuning.AVALANCHE_BONUS)
		play(Sfx.SPILL)
		play(Sfx.CHEER, 0.7)
		fx.haptics.jackpot()
		shake.add(0.5)
		particles.confetti(0.0, 0.0, MiniGame.GAME_W, 60)
	elif recent >= 3:
		play(Sfx.SPILL, 0.6)
		shake.add(0.15)


# ---------------------------------------------------------------- 3D presentation

func _deck_texture() -> PaTexture:
	if _deck_tex == null:
		_deck_tex = PusherArt.deck(int(PF_R - PF_L), int(FRONT_EDGE - DECK_TOP))
	return _deck_tex


func _cabinet() -> Model:
	if _cabinet_model != null:
		return _cabinet_model
	var b := ModelBuilder.new()
	var cab := PusherArt.cabinet().full()
	var dark := PusherArt.dark().full()
	var gold := PusherArt.gold().full()
	b.quad(PF_L, 0.0, DECK_TOP, PF_R, 0.0, DECK_TOP, PF_R, 0.0, FRONT_EDGE, PF_L, 0.0, FRONT_EDGE, _deck_texture().full(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, DECK_GLOSS)
	# Under the shelf, behind the deck.
	b.quad(PF_L, 0.0, BACK_Z, PF_R, 0.0, BACK_Z, PF_R, 0.0, DECK_TOP, PF_L, 0.0, DECK_TOP, dark, 0.0, 1.0, 0.0)
	b.quad(0.0, BACK_H, BACK_Z, MiniGame.GAME_W, BACK_H, BACK_Z, MiniGame.GAME_W, 0.0, BACK_Z, 0.0, 0.0, BACK_Z, PusherArt.back_wall().full(), 0.0, 0.0, 1.0)
	# Side gutters beside the front of the deck, and their floors.
	for s in 2:
		var x0 := PF_L - GUTTER_W if s == 0 else PF_R
		var x1 := x0 + GUTTER_W
		b.quad(x0, -GUTTER_DEPTH, GUTTER_TOP, x1, -GUTTER_DEPTH, GUTTER_TOP, x1, -GUTTER_DEPTH, TRAY_FRONT, x0, -GUTTER_DEPTH, TRAY_FRONT, dark, 0.0, 1.0, 0.0)
		b.quad(x0, 0.0, GUTTER_TOP, x1, 0.0, GUTTER_TOP, x1, -GUTTER_DEPTH, GUTTER_TOP, x0, -GUTTER_DEPTH, GUTTER_TOP, dark, 0.0, 0.0, 1.0)
	# Cabinet walls outside the gutters, full length.
	b.box(0.0, -140.0, BACK_Z, PF_L - GUTTER_W, SIDE_H, TRAY_FRONT, BoxFaces.new(cab, null, cab, cab))
	b.box(PF_R + GUTTER_W, -140.0, BACK_Z, MiniGame.GAME_W, SIDE_H, TRAY_FRONT, BoxFaces.new(cab, cab, null, cab))
	# Solid side ledges along the back of the deck (where the sim's side walls are).
	b.box(PF_L - GUTTER_W, 0.0, BACK_Z, PF_L, 10.0, GUTTER_TOP, BoxFaces.new(gold, null, gold, gold))
	b.box(PF_R, 0.0, BACK_Z, PF_R + GUTTER_W, 10.0, GUTTER_TOP, BoxFaces.new(gold, gold, null, gold))
	# Front lip, the drop to the tray, and the tray.
	b.box(PF_L - GUTTER_W, -4.0, FRONT_EDGE, PF_R + GUTTER_W, 3.0, FRONT_EDGE + 6.0, BoxFaces.new(gold, null, null, gold))
	b.quad(PF_L - GUTTER_W, -4.0, FRONT_EDGE + 6.0, PF_R + GUTTER_W, -4.0, FRONT_EDGE + 6.0, PF_R + GUTTER_W, TRAY_Y, FRONT_EDGE + 6.0, PF_L - GUTTER_W, TRAY_Y, FRONT_EDGE + 6.0,
		PusherArt.lip_face().full(), 0.0, 0.0, 1.0)
	b.quad(PF_L - GUTTER_W, TRAY_Y, FRONT_EDGE + 6.0, PF_R + GUTTER_W, TRAY_Y, FRONT_EDGE + 6.0, PF_R + GUTTER_W, TRAY_Y, TRAY_FRONT, PF_L - GUTTER_W, TRAY_Y, TRAY_FRONT,
		PusherArt.tray().full(), 0.0, 1.0, 0.0)
	b.box(PF_L - GUTTER_W, TRAY_Y - 60.0, TRAY_FRONT, PF_R + GUTTER_W, TRAY_Y + 14.0, TRAY_FRONT + 8.0, BoxFaces.new(cab, null, null, gold))
	_cabinet_model = b.build()
	return _cabinet_model


func render(scope: DrawScope) -> void:
	var r := stage.begin()
	motion_k = clampf(ScreenShake.intensity, 0.0, 1.0)
	var l := r.lighting
	l.amb_r = 0.5
	l.amb_g = 0.46
	l.amb_b = 0.58
	l.set_direction(0.2, 1.0, 0.6)
	l.dir_r = 0.32
	l.dir_g = 0.3
	l.dir_b = 0.27
	l.points.clear()
	l.points.append(warm)
	back_glow.intensity = 0.7 + 0.6 * excite
	l.points.append(back_glow)
	l.points.append(ledge_left)
	l.points.append(ledge_right)
	shelf_light.z = pusher_front - 10.0
	l.points.append(shelf_light)
	tray_light.intensity = tray_flash * 1.6
	if tray_flash > 0.0:
		l.points.append(tray_light)
	r.gradient(0xFF0C0610, Pal.shade(Pal.ORANGE, 0.2))

	_cabinet().draw(r)
	_draw_shelf(r)
	_draw_tray_pile(r)
	_draw_panels(r)
	var bodies := world.bodies
	for i in bodies.size():
		var b := bodies[i]
		var h := b.tag
		_draw_item(r, b.kind, b.x, b.y, 0.0, b.angle, 1.0, COIN_TONES[h % COIN_TONES.size()])
		# One coin in a while catches the light.
		if h % GLINT_EVERY == 0 and b.kind == COIN:
			var tw := sin(time * 2.6 + (h % 97) * 0.7)
			if tw > 0.8:
				SceneFx.flare(r, b.x - 4.0, COIN_THICK + 5.0, b.y - 2.0, 20.0, Pal.WHITE, (tw - 0.8) * 4.0 * 0.6)
	for f in fallers:
		if not f.active:
			continue
		# Tipping over the lip and dropping into the tray (or a gutter).
		var d := maxf(f.y - (GUTTER_TOP if f.lost else FRONT_EDGE), 0.0)
		var z := f.y if f.lost else FRONT_EDGE + minf(d * 0.6, 24.0)
		var y := -minf(d * 1.4, GUTTER_DEPTH - 4.0 if f.lost else -TRAY_Y - 4.0)
		var tip := minf(d / 20.0, 1.3)
		_draw_tumbling(r, f.kind, f.x, z, y, tip, f.angle)
	for d in drops:
		if not d.active:
			continue
		var t := MathUtil.clamp01(d.t / 0.32)
		var z := lerpf(DROP_Z, pusher_front + 20.0, t)
		var y := SLOT_Y * (1.0 - t * t) + 4.0
		_draw_tumbling(r, d.kind, d.x, z, y, time * 9.0, time * 12.0)
	_draw_bulbs(r)
	_draw_impacts(r)
	# Glass side panels over the back of the deck.
	var glass := PusherArt.glass().full()
	for x in [PF_L - GUTTER_W, PF_R + GUTTER_W]:
		r.quad(x, 10.0 + GLASS_H, BACK_Z, x, 10.0 + GLASS_H, GUTTER_TOP, x, 10.0, GUTTER_TOP, x, 10.0, BACK_Z, glass, 1.0, 0.0, 0.0,
			0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, 1.0, false)
	stage.present()

	var cl := maxi(coins_left, 0)
	if cl > 0 and not time_up:
		var a := 0.4 + 0.4 * sin(time * 6.0)
		ArcadeFont.draw_centered(scope, "TAP TO DROP " + ArcadeFont.DOWN, MiniGame.GAME_W / 2.0, 150.0, 2.0, Color.WHITE, a)


## Winnings pile up in the tray.
func _draw_tray_pile(r: Renderer3D) -> void:
	var n := mini(won, 60)
	var coin := PusherArt.coin().full()
	for i in n:
		var x := PF_L + 10.0 + MathUtil.hash01(i, 41) * (PF_R - PF_L - 20.0)
		var z := FRONT_EDGE + 16.0 + MathUtil.hash01(i, 42) * (TRAY_FRONT - FRONT_EDGE - 26.0)
		var layer := i / 20
		r.flat(x, z, TRAY_Y + 1.0 + layer * 3.0, COIN_R * 2.0, COIN_R * 2.0, coin, MathUtil.hash01(i, 43) * TAU)


func _draw_shelf(r: Renderer3D) -> void:
	var front := pusher_front
	r.quad(PF_L, SHELF_H, front, PF_R, SHELF_H, front, PF_R, 0.0, front, PF_L, 0.0, front, PusherArt.shelf_front().full(), 0.0, 0.0, 1.0)
	r.quad(PF_L, SHELF_H, BACK_Z, PF_R, SHELF_H, BACK_Z, PF_R, SHELF_H, front, PF_L, SHELF_H, front, PusherArt.shelf_top().full(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, true, -1, 1.0, SHELF_GLOSS)
	var glow := TexKit.glow().full()
	var dot := TexKit.dot().full()
	for i in 6:
		var on := (int(time * 4.0) + i) % 2 == 0
		var x := PF_L + 25.0 + i * (PF_R - PF_L - 50.0) / 5.0
		r.sprite(x, SHELF_H + 3.0, front - 8.0, 7.0, 7.0, dot, 0.0, Blend.OPAQUE, 1.2, 1.0, 1.0, Pal.CYAN if on else Pal.TEAL)
		if on:
			r.sprite(x, SHELF_H + 3.0, front - 7.0, 24.0, 24.0, glow, 0.0, Blend.ADD, 1.0, 0.5, 1.0, Pal.CYAN)


func _draw_panels(r: Renderer3D) -> void:
	var cl := maxi(coins_left, 0)
	coins_panel.paint("COINS %d" % cl, Pal.ORANGE if cl <= 5 else Pal.YELLOW)
	r.quad(120.0, 118.0, BACK_Z + 1.0, 240.0, 118.0, BACK_Z + 1.0, 240.0, 90.0, BACK_Z + 1.0, 120.0, 90.0, BACK_Z + 1.0, coins_panel.tex.full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.0)
	# The coin slot the drops come out of.
	r.quad(150.0, SLOT_Y + 8.0, BACK_Z + 1.0, 210.0, SLOT_Y + 8.0, BACK_Z + 1.0, 210.0, SLOT_Y - 2.0, BACK_Z + 1.0, 150.0, SLOT_Y - 2.0, BACK_Z + 1.0, PusherArt.dark().full(), 0.0, 0.0, 1.0)
	won_panel.paint("WON %d" % won, Pal.YELLOW)
	r.quad(130.0, TRAY_Y - 8.0, TRAY_FRONT + 8.5, 230.0, TRAY_Y - 8.0, TRAY_FRONT + 8.5, 230.0, TRAY_Y - 32.0, TRAY_FRONT + 8.5, 130.0, TRAY_Y - 32.0, TRAY_FRONT + 8.5, won_panel.tex.full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.0)


func _draw_bulbs(r: Renderer3D) -> void:
	var glow := TexKit.glow().full()
	var dot := TexKit.dot().full()
	for s in 2:
		var x := (PF_L - GUTTER_W) / 2.0 if s == 0 else (PF_R + GUTTER_W + MiniGame.GAME_W) / 2.0
		for i in 12:
			var z := BACK_Z + 20.0 + i * (TRAY_FRONT - BACK_Z - 40.0) / 11.0
			var on := (int(time * (8.0 + 8.0 * excite)) - i) % 4 == 0
			r.sprite(x, SIDE_H + 4.0, z, 7.0, 7.0, dot, 0.0, Blend.OPAQUE, 1.2, 1.0, 1.0, Pal.YELLOW if on else Pal.shade(Pal.GOLD, 0.4))
			if on:
				r.sprite(x, SIDE_H + 5.0, z, 26.0, 26.0, glow, 0.0, Blend.ADD, 1.0, 0.55, 1.0, Pal.GOLD)


## An item lying flat on the deck at (x, z).
func _draw_item(r: Renderer3D, kind: int, x: float, z: float, y: float, angle: float, scale: float, tone: int = -1) -> void:
	var rad := _radius_for(kind) * scale
	var pulse := 0.5 + 0.5 * sin(time * 4.5 + x * 0.07)
	if kind == COIN or kind == BIG:
		# A darker disc under the face reads as the coin's edge.
		r.flat(x, z + 1.2, y + 0.4, rad * 2.0, rad * 2.0, PusherArt.coin_edge().full(), angle)
		r.flat(x, z, y + COIN_THICK, rad * 2.0, rad * 2.0, (PusherArt.big_coin() if kind == BIG else PusherArt.coin()).full(), angle,
			Blend.OPAQUE, 0.0, 1.0, tone)
		if kind == BIG:
			SceneFx.glow(r, x, 8.0, z, rad * 3.4, Pal.GOLD, 0.1 + 0.08 * pulse)
	elif kind == TICKETS:
		r.flat(x, z, y + 3.0, rad * 2.2, rad * 1.6, PusherArt.tickets().full(), angle)
		SceneFx.glow(r, x, 8.0, z, rad * 3.4, Pal.ORANGE, 0.1 + 0.08 * pulse)
	elif kind == GEM:
		r.billboard(x, y, z, rad * 2.0, rad * 2.0, PusherArt.gem().full(), false, 0.4)
		# A gem breathes cyan light and glints.
		SceneFx.glow(r, x, y + rad, z, rad * 4.0, Pal.CYAN, 0.14 + 0.12 * pulse)
		SceneFx.flare(r, x + rad * 0.2, y + rad * 1.6, z, rad * 2.2, Pal.WHITE, 0.25 + 0.5 * pulse, time * 0.6 * motion_k)
	else:
		r.billboard(x, y, z, rad * 2.2, rad * 2.2, PusherArt.star().full(), false, 0.4)
		# The shower star throws slow, turning rays.
		SceneFx.flare(r, x, y + rad, z, rad * 4.4, Pal.YELLOW, 0.3 + 0.2 * pulse, time * 0.9 * motion_k)
		SceneFx.glow(r, x, y + rad, z, rad * 3.4, Pal.GOLD, 0.12)


## Rings where coins land on the deck, and where anything spills into the tray, with a lift of light for bonuses.
func _draw_impacts(r: Renderer3D) -> void:
	for i in FX_SLOTS:
		var lt := land_age[i] / LAND_LIFE
		if lt < 1.0:
			SceneFx.shockwave(r, land_x[i], 1.2, land_z[i], 16.0 + 46.0 * MathUtil.ease_out_cubic(lt), Pal.CREAM, (1.0 - lt) * 0.5)
		var st := spill_age[i] / SPILL_LIFE
		if st < 1.0:
			var kind := spill_kind[i]
			var color := Pal.GOLD
			if kind == GEM:
				color = Pal.CYAN
			elif kind == TICKETS:
				color = Pal.ORANGE
			elif kind == STAR:
				color = Pal.YELLOW
			var e := MathUtil.ease_out_cubic(st)
			var a := 1.0 - st
			SceneFx.shockwave(r, spill_x[i], TRAY_Y + 1.2, FRONT_EDGE + 26.0, 30.0 + 120.0 * e, color, a * 0.7)
			SceneFx.pool(r, spill_x[i], TRAY_Y + 1.0, FRONT_EDGE + 26.0, 130.0, 90.0, color, a * 0.35)
			if kind != COIN:
				SceneFx.shaft_beam(r, spill_x[i], TRAY_Y + 2.0, FRONT_EDGE + 20.0, spill_x[i], TRAY_Y + 150.0 * (0.6 + 0.4 * e), FRONT_EDGE + 20.0, 34.0, color, a * 0.5)
				if st < 0.5:
					SceneFx.flare(r, spill_x[i], TRAY_Y + 30.0, FRONT_EDGE + 30.0, 90.0 * (0.6 + 0.4 * e), color, (1.0 - st * 2.0) * 0.8, st * 2.0)
	# The title glows brighter when the machine is excited, and the coin counter throbs when the coins run low.
	if excite > 0.02:
		SceneFx.glow(r, MiniGame.GAME_W / 2.0, 213.0, BACK_Z + 1.0, 240.0, Pal.YELLOW, 0.16 * excite)
	if coins_left >= 1 and coins_left <= 5 and not time_up:
		SceneFx.glow(r, MiniGame.GAME_W / 2.0, 104.0, BACK_Z + 1.5, 170.0, Pal.ORANGE, 0.14 + 0.1 * sin(time * 6.0) * motion_k)


## An item in the air: coins show their face turning edge-on as they tumble.
func _draw_tumbling(r: Renderer3D, kind: int, x: float, z: float, y: float, tumble: float, spin: float) -> void:
	var rad := _radius_for(kind)
	if kind == COIN or kind == BIG:
		var h := rad * 2.0 * (0.25 + 0.75 * absf(cos(tumble)))
		r.sprite(x, y + rad, z, rad * 2.0, h, (PusherArt.big_coin() if kind == BIG else PusherArt.coin()).full(), spin * 0.1)
	elif kind == TICKETS:
		r.sprite(x, y + rad, z, rad * 2.2, rad * 1.6, PusherArt.tickets().full(), spin * 0.2)
	elif kind == GEM:
		r.sprite(x, y + rad, z, rad * 2.0, rad * 2.0, PusherArt.gem().full())
	else:
		r.sprite(x, y + rad, z, rad * 2.2, rad * 2.2, PusherArt.star().full(), spin * 0.3)


# ---------------------------------------------------------------- simulation-test hooks

func bot_coins_left() -> int:
	return coins_left


# ---------------------------------------------------------------- attract mode

## The machine in miniature: a glowing gold marquee over the pusher shelf with its hazard front and
## chasing lights, a packed deck of coins, and the shelf sliding back and forth, shoving the front
## rows over the lip. Each spill lands in the tray with a flash and a score; every few pushes a gem
## and a star come over with it. Coins drop from the slot meanwhile. Looks only: the deck is a fixed
## packing, sheared by the shelf's stroke.
func draw_attract(p: Painter, w: int, h: int, p_time: float) -> void:
	var wf := float(w)
	var hf := float(h)
	p.fill(0.0, 0.0, wf, hf, Pal.NAVY)
	for row in h:
		p.fill(0.0, float(row), wf, 1.05, Pal.mix(Pal.shade(Pal.NAVY, 0.9), Pal.shade(Pal.INDIGO, 0.7), row / (hf - 1.0)))
	var push := 0.5 - 0.5 * cos(p_time * 2.0)
	# Back wall: orange stripes under a gold marquee bar with chasing bulbs.
	p.fill(0.0, 0.0, wf, 3.2, Pal.shade(Pal.ORANGE, 0.8))
	for i in range(0, w, 2):
		p.fill(float(i), 0.0, 0.8, 3.2, Pal.shade(Pal.DARKRED, 0.7), 0.5)
	p.fill(0.0, 0.0, wf, 0.4, Pal.GOLD)
	for i in 12:
		var on := (int(p_time * 6.0) + i) % 3 == 0
		p.px(i * (wf / 12.0) + 0.9, 2.85, Pal.YELLOW if on else Pal.shade(Pal.ORANGE, 0.5), 1.0)
	p.text_centered("PUSHER", wf / 2.0, 0.5, Pal.YELLOW, true, 0.9, 0.5)
	# The shelf: steel with a hazard front edge, sliding forward.
	var shelf := 3.2 + push * 3.2
	p.fill(0.0, 3.2, wf, shelf - 3.2, Pal.shade(Pal.GRAY, 0.75))
	p.fill(0.0, shelf - 0.2, wf, 0.2, Pal.LIGHTGRAY, 0.6)
	for i in range(0, w, 2):
		p.fill(float(i), shelf, 1.0, 0.9, Pal.YELLOW)
	for i in range(1, w, 2):
		p.fill(float(i), shelf, 1.0, 0.9, Pal.BLACK)
	p.fill(0.0, shelf + 0.9, wf, 0.25, Pal.CYAN, 0.4 + 0.3 * push)
	# The deck: a packed pile of coins the shelf shoves along, row by row.
	var lip := hf - 2.4
	var rows := 8
	for row in rows:
		var y := shelf + 2.1 + row * 1.55 + push * 0.6
		if y > lip - 0.5:
			break
		var off := 0.0 if row % 2 == 0 else 0.9
		var x := 0.9 + off
		var k := 0
		while x < wf - 0.5:
			var shine := 0.5 + 0.5 * sin(p_time * 2.4 + row * 0.9 + k * 1.3)
			var c := Pal.YELLOW if (row + k) % 7 == 3 else Pal.GOLD
			p.disc(x, y + 0.15, 0.92, Pal.shade(Pal.ORANGE, 0.65))
			p.disc(x, y, 0.92, c)
			# Only the coins that catch the light get a glint, to keep the screen cheap to paint.
			if shine > 0.75:
				p.disc(x - 0.3, y - 0.3, 0.3, Color.WHITE, 0.3 + 0.5 * shine * shine)
			x += 1.85
			k += 1
	# Coins dropping through the slot at the top and rolling out over the lip.
	var drop := fmod(p_time * 1.1, 1.0)
	p.disc(wf * 0.5, shelf - 0.6 + drop * 1.6, 0.7, Pal.YELLOW, 1.0 - drop)
	# The front lip, and the tray with the spills.
	p.fill(0.0, lip, wf, 0.4, Pal.GOLD)
	p.fill(0.0, lip + 0.4, wf, hf - lip, Pal.PLUM)
	for i in range(0, w, 2):
		p.fill(float(i), lip + 0.4, 0.3, hf - lip, Pal.shade(Pal.GOLD, 0.5), 0.8)
	# A coin tips off the lip roughly every push, in a lane of its own.
	var spill_t := fmod(p_time / 3.14, 1.0)
	var lane := int(p_time / 3.14) % 5
	var sx := 3.0 + lane * (wf - 6.0) / 4.0
	if spill_t >= 0.55 and spill_t <= 0.95:
		var k := (spill_t - 0.55) / 0.4
		var gem := int(p_time / 3.14) % 4 == 3
		var col := Pal.CYAN if gem else Pal.GOLD
		p.disc(sx, lip - 0.4 + k * 2.6, 0.8 if gem else 0.9, col, 1.0 - k * 0.4)
		if k > 0.75:
			var f := (k - 0.75) / 0.25
			p.disc(sx, lip + 1.6, 1.2 + f * 2.4, col, 0.5 * (1.0 - f))
			p.text_centered("+50" if gem else "+10", clampf(sx, 4.5, wf - 4.5), lip - 4.5 - f * 1.5, col, true, 1.0 - f * 0.5, 0.5)
	p.fill(0.0, lip - 0.02, wf, 0.15, Pal.YELLOW, 0.35)
