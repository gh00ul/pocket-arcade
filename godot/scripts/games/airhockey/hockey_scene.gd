class_name HockeyScene
extends RefCounted
## games/airhockey/HockeyScene.kt: the air hockey table as a 3D scene: a dark glossy playfield
## under neon, on an arena floor with an LED wall behind it and a lit scoreboard, with everything
## that answers the game (hits, goals, the serve, the puck's trail and light). It keeps all the
## visual state, so the simulation never reads it and rendering never touches the game's random
## generator.
##
## No texture is built until something is drawn, so headless tests can step a game freely.
## (HockeyGeo and HockeyLook, declared in the same Kotlin file, are hockey_geo.gd and hockey_look.gd.)

const TRAIL_N := 10
const BURSTS := 14
const K_RING := 0
const K_FLARE := 1
const K_GLOW := 2
const CYAN := 0xFF4FE8FF
const PINK := 0xFFFF4FA8
const SURFACE := 1.3


class Burst:
	extends RefCounted
	var kind := 0
	var x := 0.0
	var y := 0.0
	var z := 0.0
	var age := 1.0
	var life := 1.0
	var s0 := 0.0
	var s1 := 0.0
	var alpha := 0.0
	var tint := -1


# ---------------------------------------------------------------- visual state

var _bursts: Array[Burst] = []
var _burst_next := 0
var _trail_x := PackedFloat32Array()
var _trail_y := PackedFloat32Array()
var _trail_n := 0
var _trail_t := 0.0
var _me_glow := 0.0
var _cpu_glow := 0.0


func _init() -> void:
	for i in BURSTS:
		_bursts.append(Burst.new())
	_trail_x.resize(TRAIL_N)
	_trail_y.resize(TRAIL_N)


## Clears every glow and the trail, for a fresh round.
func reset() -> void:
	for b in _bursts:
		b.age = b.life
	_trail_n = 0
	_trail_t = 0.0
	_me_glow = 0.0
	_cpu_glow = 0.0


func _calm_k() -> float:
	return HockeyLook.CALM_K if ScreenShake.intensity <= 0.0 else 1.0


## Ages the glows (called from the game's fixed step).
func step(dt: float) -> void:
	for b in _bursts:
		if b.age < b.life:
			b.age += dt
	_me_glow = maxf(_me_glow - dt * 5.0, 0.0)
	_cpu_glow = maxf(_cpu_glow - dt * 5.0, 0.0)


## Adds the puck's position to its trail (a sample every 0.02 s).
func trail(x: float, y: float, dt: float) -> void:
	_trail_t -= dt
	if _trail_t > 0.0:
		return
	_trail_t = 0.02
	var tx := _trail_x
	var ty := _trail_y
	for i in range(TRAIL_N - 1, 0, -1):
		tx[i] = tx[i - 1]
		ty[i] = ty[i - 1]
	tx[0] = x
	ty[0] = y
	_trail_n = mini(_trail_n + 1, TRAIL_N)


func clear_trail() -> void:
	_trail_n = 0


func _burst(kind: int, x: float, y: float, z: float, s0: float, s1: float, life: float, alpha: float, tint: int) -> void:
	var b := _bursts[_burst_next]
	_burst_next = (_burst_next + 1) % BURSTS
	b.kind = kind
	b.x = x
	b.y = y
	b.z = z
	b.s0 = s0
	b.s1 = s1
	b.life = life
	b.age = 0.0
	b.alpha = alpha * _calm_k()
	b.tint = tint


# ---------------------------------------------------------------- events

## A mallet struck the puck at ([param x], [param y]) on the table with [param power] 0..1.
func mallet_hit(x: float, y: float, power: float, by_player: bool) -> void:
	var color := CYAN if by_player else PINK
	_burst(K_RING, x, SURFACE, y, 30.0, 44.0 + power * 100.0, 0.32, 0.5 + power * 0.4, color)
	_burst(K_FLARE, x, 10.0, y, 18.0, 34.0 + power * 60.0, 0.2, 0.9, Pal.mix(color, Pal.WHITE, 0.6))
	if by_player:
		_me_glow = 1.0
	else:
		_cpu_glow = 1.0


## The puck struck a rail at ([param x], [param y]) at [param strength] 0..1.
func wall_hit(x: float, y: float, strength: float) -> void:
	_burst(K_FLARE, x, HockeyGeo.RAIL_H, y, 10.0, 22.0 + strength * 26.0, 0.18, 0.55 + strength * 0.4, 0xFFB8ECFF)


## A goal at the far end ([param by_player]) or the near one: a shockwave across the table and a
## flare in the slot.
func goal(by_player: bool) -> void:
	var z := HockeyGeo.RT if by_player else HockeyGeo.RB
	var color := CYAN if by_player else PINK
	_burst(K_RING, HockeyGeo.CX, SURFACE, z, 60.0, 700.0, 0.95, 0.75, color)
	_burst(K_FLARE, HockeyGeo.CX, 16.0, z, 40.0, 300.0, 0.5, 1.0, Pal.mix(color, Pal.WHITE, 0.5))
	_burst(K_GLOW, HockeyGeo.CX, 14.0, z, 140.0, 460.0, 0.8, 0.6, color)


# ---------------------------------------------------------------- lights

var _lamp := PointLight.new(HockeyGeo.CX, HockeyLook.LAMP_Y, HockeyGeo.CY, 1.0, 0.96, 0.9, HockeyLook.LAMP_RADIUS, HockeyLook.LAMP_INTENSITY)
var _near_light := PointLight.new(HockeyGeo.CX, HockeyLook.END_Y, HockeyGeo.RB + HockeyLook.END_OFFSET, 0.25, 0.9, 1.0, HockeyLook.END_RADIUS, HockeyLook.END_INTENSITY)
var _far_light := PointLight.new(HockeyGeo.CX, HockeyLook.END_Y, HockeyGeo.RT - HockeyLook.END_OFFSET, 1.0, 0.3, 0.66, HockeyLook.END_RADIUS, HockeyLook.END_INTENSITY)
var _puck_light := PointLight.new(0.0, HockeyLook.PUCK_LIGHT_Y, 0.0, 1.0, 0.42, 0.25, HockeyLook.PUCK_LIGHT_RADIUS, 0.0)
var _goal_light := PointLight.new(HockeyGeo.CX, 40.0, HockeyGeo.RT, 0.4, 1.0, 1.0, HockeyLook.GOAL_LIGHT_RADIUS, 0.0)


## Sets the lighting and the renderer's look for this frame.
func light(r: Renderer3D, puck_x: float, puck_y: float, puck_live: bool, goal_flash: float, goal_by_player: bool) -> void:
	var l := r.lighting
	l.amb_r = HockeyLook.AMB_R
	l.amb_g = HockeyLook.AMB_G
	l.amb_b = HockeyLook.AMB_B
	l.set_direction(0.0, 1.0, 0.5)
	l.dir_r = HockeyLook.DIR_R
	l.dir_g = HockeyLook.DIR_G
	l.dir_b = HockeyLook.DIR_B
	_puck_light.x = puck_x
	_puck_light.z = puck_y
	_puck_light.intensity = HockeyLook.PUCK_LIGHT_LIVE if puck_live else HockeyLook.PUCK_LIGHT_SERVE
	l.points.clear()
	l.points.append(_lamp)
	l.points.append(_near_light)
	l.points.append(_far_light)
	l.points.append(_puck_light)
	if goal_flash > 0.0:
		_goal_light.z = HockeyGeo.RT if goal_by_player else HockeyGeo.RB
		if goal_by_player:
			_goal_light.r = 0.4
			_goal_light.g = 1.0
			_goal_light.b = 1.0
		else:
			_goal_light.r = 1.0
			_goal_light.g = 0.3
			_goal_light.b = 0.5
		_goal_light.intensity = MathUtil.clamp01(goal_flash) * HockeyLook.GOAL_LIGHT_PEAK * _calm_k()
		l.points.append(_goal_light)
	r.vignette = HockeyLook.VIGNETTE
	r.bloom = HockeyLook.BLOOM


# ---------------------------------------------------------------- models

var _room: Model = null
var _table: Model = null
var _puck_model: Model = null
var _my_mallet: Model = null
var _cpu_mallet: Model = null
var _xf := Xform.new()


## hub/CabinetForm.kt ModelBuilder.beveledBox (chamfered edges, floor occlusion). [param bevel] and
## [param floor_ao] NAN mean the hall's defaults. Until the hall's port is merged this builds the
## plain box (see docs/parity/notes/games-b.md).
static func _beveled_box(b: ModelBuilder, x0: float, y0: float, z0: float, x1: float, y1: float, z1: float,
		f: BoxFaces, tint: int = -1, _bevel: float = NAN, _floor_ao: float = NAN) -> void:
	b.box(x0, y0, z0, x1, y1, z1, f, tint)


func _build_room() -> Model:
	var b := ModelBuilder.new()
	var fx := HockeyLook.FLOOR_HALF_X
	var fy := HockeyGeo.FLOOR_Y
	var wz := HockeyLook.WALL_Z
	var tile := 60.0
	var floor_region := HockeyArt.floor_region()
	var tw := float(floor_region.w)
	# The arena floor, tiled, and the LED wall behind the far end.
	b.quad(-fx, fy, wz, fx, fy, wz, fx, fy, 1000.0, -fx, fy, 1000.0, floor_region, 0.0, 1.0, 0.0,
		0.0, 0.0, 2.0 * fx / tile * tw, (1000.0 - wz) / tile * tw, Blend.OPAQUE, 0.0, true, -1, HockeyLook.FLOOR_GLOSS)
	var hw := HockeyLook.WALL_HALF_W
	b.quad(HockeyGeo.CX - hw, HockeyLook.WALL_TOP, wz, HockeyGeo.CX + hw, HockeyLook.WALL_TOP, wz, HockeyGeo.CX + hw, fy, wz, HockeyGeo.CX - hw, fy, wz,
		HockeyArt.wall().full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, HockeyLook.WALL_EMISSIVE)
	return b.build()


func _build_table() -> Model:
	var b := ModelBuilder.new()
	var rl := HockeyGeo.RL
	var rr := HockeyGeo.RR
	var rt := HockeyGeo.RT
	var rb := HockeyGeo.RB
	var cx := HockeyGeo.CX
	var gh := HockeyGeo.GOAL_HALF
	var rh := HockeyGeo.RAIL_H
	var rail := HockeyArt.rail().full()
	var body := HockeyArt.body().full()
	var post := HockeyArt.post().full()
	b.quad(rl, 0.0, rt, rr, 0.0, rt, rr, 0.0, rb, rl, 0.0, rb, HockeyArt.surface(int(rr - rl), int(rb - rt), gh).full(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, HockeyLook.SURFACE_GLOSS)
	var g := HockeyLook.RAIL_GLOSS
	# Side rails and the end rails either side of each goal slot, chamfered so the edges catch the lamp.
	_beveled_box(b, rl - 16.0, 0.0, rt - 16.0, rl, rh, rb + 16.0, BoxFaces.new(rail, null, rail, rail, null, 0.0, 0.0, 0.0, g), -1, 1.4, 0.0)
	_beveled_box(b, rr, 0.0, rt - 16.0, rr + 16.0, rh, rb + 16.0, BoxFaces.new(rail, rail, null, rail, null, 0.0, 0.0, 0.0, g), -1, 1.4, 0.0)
	for end in 2:
		var z0 := rt - 16.0 if end == 0 else rb
		var z1 := z0 + 16.0
		_beveled_box(b, rl, 0.0, z0, cx - gh, rh, z1, BoxFaces.new(rail, null, rail, rail, null, 0.0, 0.0, 0.0, g), -1, 1.2, 0.0)
		_beveled_box(b, cx + gh, 0.0, z0, rr, rh, z1, BoxFaces.new(rail, rail, null, rail, null, 0.0, 0.0, 0.0, g), -1, 1.2, 0.0)
		# The goal slot: a dark pocket below the rail, and a plate over its mouth.
		b.quad(cx - gh, -30.0, z0, cx + gh, -30.0, z0, cx + gh, -30.0, z1, cx - gh, -30.0, z1, HockeyArt.slot().full(), 0.0, 1.0, 0.0)
		b.box(cx - gh, rh - 4.0, z0, cx + gh, rh, z1, BoxFaces.new(rail, null, null, rail, null, 0.0, 0.0, 0.0, g))
	# Rounded corners: a curved rail filling each corner of the rink.
	var half_w := (rr - rl) / 2.0
	var half_h := (rb - rt) / 2.0
	var cr := HockeyGeo.CORNER_R
	for sx: float in [-1.0, 1.0]:
		for sz: float in [-1.0, 1.0]:
			var ccx := cx + sx * (half_w - cr)
			var ccz := HockeyGeo.CY + sz * (half_h - cr)
			var kx := cx + sx * half_w
			var kz := HockeyGeo.CY + sz * half_h
			var n := 6
			for i in n:
				var t0 := i / float(n) * (PI / 2.0)
				var t1 := (i + 1) / float(n) * (PI / 2.0)
				var ax := ccx + sx * cos(t0) * cr
				var az := ccz + sz * sin(t0) * cr
				var bx := ccx + sx * cos(t1) * cr
				var bz := ccz + sz * sin(t1) * cr
				b.quad(kx, rh, kz, kx, rh, kz, ax, rh, az, bx, rh, bz, rail, 0.0, 1.0, 0.0,
					0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false, -1, g)
				var tm := (t0 + t1) / 2.0
				b.quad(ax, rh, az, bx, rh, bz, bx, 0.0, bz, ax, 0.0, az, rail, -sx * cos(tm), 0.0, -sz * sin(tm),
					0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false, -1, g)
	# Table body under the rink.
	_beveled_box(b, rl - 16.0, HockeyGeo.FLOOR_Y, rt - 16.0, rr + 16.0, 0.0, rb + 16.0, BoxFaces.new(body, body, body), -1, 1.2)
	# The scoreboard on two posts behind the far goal, in a housing.
	for px: float in [cx - 80.0, cx + 80.0]:
		_beveled_box(b, px - 5.0, HockeyGeo.FLOOR_Y, rt - 42.0, px + 5.0, 150.0, rt - 32.0, BoxFaces.new(post, post, post, null, null, 0.0, 0.0, 0.0, 0.4), -1, 1.0)
	_beveled_box(b, cx - 104.0, 148.0, rt - 46.0, cx + 104.0, 222.0, rt - 32.0, BoxFaces.new(post, post, post, post, null, 0.0, 0.0, 0.0, 0.5), -1, 2.5, 0.0)
	return b.build()


func _build_puck() -> Model:
	var r := HockeyGeo.PUCK_R
	var profile := PackedFloat32Array([r - 0.4, 0.0, r, 0.7, r, 4.2, r - 0.7, 5.0])
	return ModelBuilder.new() \
		.lathe(0.0, 0.0, 0.0, profile, 14, HockeyArt.puck_side().full(), -1, 0.5) \
		.disc(0.0, 0.0, 5.02, r - 0.8, 14, HockeyArt.puck_top().full(), 1.0, 0.9, -1, 0.3) \
		.build()


func _build_mallet(color: int) -> Model:
	var r := HockeyGeo.MALLET_R
	# Foot, base with a chamfered shoulder, neck, knob: matches HockeyArt.mallet_skin's bands.
	var profile := PackedFloat32Array([
		r - 0.5, 0.0, r, 1.4, r, 5.2, 16.0, 8.2, 9.0, 9.4, 8.4, 17.0, 10.4, 19.6, 0.0, 21.6,
	])
	return ModelBuilder.new().lathe(0.0, 0.0, 0.0, profile, 16, HockeyArt.mallet_skin(color).full(), -1, HockeyLook.MALLET_GLOSS).build()


# ---------------------------------------------------------------- drawing

var _board := HockeyArt.Scoreboard.new(HockeyTuning.GOALS_TO_WIN)


## Repaints the scoreboard when a score changes.
func update_board(you: int, cpu: int) -> void:
	_board.paint(you, cpu)


## The arena: floor and LED wall, the glow of the table's underlight on the floor, and the wall
## flaring in the scorer's colour for a goal. Draws first.
func draw_room(r: Renderer3D, t: float, goal_flash: float, goal_by_player: bool) -> void:
	if _room == null:
		_room = _build_room()
	_room.draw(r)
	var glow := TexKit.glow().full()
	var fy := HockeyGeo.FLOOR_Y + 0.6
	var pulse := 1.0 + 0.12 * sin(t * 2.4)
	var g := MathUtil.clamp01(goal_flash) * _calm_k()
	var near := 0.0 if goal_by_player else g
	var far := g if goal_by_player else 0.0
	# Underglow: cyan under the player's end, pink under the CPU's, brightening for a goal.
	r.flat(HockeyGeo.CX, 470.0, fy, 520.0, 520.0, glow, 0.0, Blend.ADD, 1.0, (HockeyLook.UNDERGLOW_ALPHA + near * 0.5) * pulse, CYAN)
	r.flat(HockeyGeo.CX, 190.0, fy, 520.0, 520.0, glow, 0.0, Blend.ADD, 1.0, (HockeyLook.UNDERGLOW_ALPHA + far * 0.5) * pulse, PINK)
	if goal_flash > 0.0:
		var hw := HockeyLook.WALL_HALF_W
		var wz := HockeyLook.WALL_Z + 1.0
		r.quad(HockeyGeo.CX - hw, HockeyLook.WALL_TOP, wz, HockeyGeo.CX + hw, HockeyLook.WALL_TOP, wz, HockeyGeo.CX + hw, HockeyGeo.FLOOR_Y, wz, HockeyGeo.CX - hw, HockeyGeo.FLOOR_Y, wz,
			TexKit.white().full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, g * 0.3, true, CYAN if goal_by_player else PINK)


## The table, its lit rail inlays and neon skirts, and the glow at each goal mouth.
func draw_table(r: Renderer3D, t: float, goal_flash: float, goal_by_player: bool) -> void:
	if _table == null:
		_table = _build_table()
	_table.draw(r)
	var rl := HockeyGeo.RL
	var rr := HockeyGeo.RR
	var rt := HockeyGeo.RT
	var rb := HockeyGeo.RB
	var cx := HockeyGeo.CX
	var cy := HockeyGeo.CY
	var gh := HockeyGeo.GOAL_HALF
	var y := HockeyGeo.RAIL_H + 0.2
	var white := TexKit.white().full()
	var glow := TexKit.glow().full()
	var level := HockeyLook.LED_LEVEL * (0.9 + 0.1 * sin(t * 3.0))
	# Light inlays along the rails' inner edges: pink at the CPU's end, cyan at the player's.
	_strip(r, white, rl - 3.4, rt - 16.0, rl - 2.0, cy, y, PINK, level)
	_strip(r, white, rl - 3.4, cy, rl - 2.0, rb + 16.0, y, CYAN, level)
	_strip(r, white, rr + 2.0, rt - 16.0, rr + 3.4, cy, y, PINK, level)
	_strip(r, white, rr + 2.0, cy, rr + 3.4, rb + 16.0, y, CYAN, level)
	_strip(r, white, rl, rt - 3.4, cx - gh, rt - 2.0, y, PINK, level)
	_strip(r, white, cx + gh, rt - 3.4, rr, rt - 2.0, y, PINK, level)
	_strip(r, white, rl, rb + 2.0, cx - gh, rb + 3.4, y, CYAN, level)
	_strip(r, white, cx + gh, rb + 2.0, rr, rb + 3.4, y, CYAN, level)
	# Neon skirts under the side rails, split by team colour, with a soft halo.
	var pulse := HockeyLook.NEON_GLOW_ALPHA + 0.08 * sin(t * 3.0)
	for side in 2:
		var x := rl - 17.0 if side == 0 else rr + 17.0
		r.beam(x, -2.0, rt - 16.0, x, -2.0, cy, 3.0, white, Blend.OPAQUE, 1.3, 1.0, PINK)
		r.beam(x, -2.0, cy, x, -2.0, rb + 16.0, 3.0, white, Blend.OPAQUE, 1.3, 1.0, CYAN)
		r.beam(x, -2.0, rt - 16.0, x, -2.0, cy, 20.0, glow, Blend.ADD, 1.0, pulse, PINK)
		r.beam(x, -2.0, cy, x, -2.0, rb + 16.0, 20.0, glow, Blend.ADD, 1.0, pulse, CYAN)
	# Each goal mouth glows in its team's colour, fiercely for a goal.
	var g := MathUtil.clamp01(goal_flash) * _calm_k()
	var far_a := 0.28 + (g * 0.7 if goal_by_player else 0.0)
	var near_a := 0.28 + (0.0 if goal_by_player else g * 0.7)
	r.flat(cx, rt - 8.0, y + 0.4, gh * 2.6, 56.0, glow, 0.0, Blend.ADD, 1.0, far_a, PINK)
	r.flat(cx, rb + 8.0, y + 0.4, gh * 2.6, 56.0, glow, 0.0, Blend.ADD, 1.0, near_a, CYAN)


func _strip(r: Renderer3D, white: Region, x0: float, z0: float, x1: float, z1: float, y: float, tint: int, level: float) -> void:
	r.quad(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, white, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, level, 1.0, false, tint)


## The lit scoreboard face, glowing, and flashing in the scorer's colour on a goal.
func draw_scoreboard(r: Renderer3D, t: float, goal_flash: float, goal_by_player: bool) -> void:
	var z := HockeyGeo.RT - 31.5
	var hw := HockeyLook.BOARD_HW
	var y0 := HockeyLook.BOARD_Y0
	var y1 := HockeyLook.BOARD_Y1
	var cx := HockeyGeo.CX
	var white := TexKit.white().full()
	r.quad(cx - hw, y1, z, cx + hw, y1, z, cx + hw, y0, z, cx - hw, y0, z, _board.tex().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.0)
	# A lamp bar along the top of the housing and a soft halo behind the face.
	r.quad(cx - 100.0, 221.0, z + 0.2, cx + 100.0, 221.0, z + 0.2, cx + 100.0, 219.4, z + 0.2, cx - 100.0, 219.4, z + 0.2, white, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.1, 1.0, true, 0xFFB8ECFF)
	r.sprite(cx, (y0 + y1) / 2.0, z - 10.0, 300.0, 120.0, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, 0.14, 1.0, 0xFF7AB8FF)
	if goal_flash > 0.0 and int(t * 8.0) % 2 == 0:
		r.quad(cx - hw, y1, z + 0.4, cx + hw, y1, z + 0.4, cx + hw, y0, z + 0.4, cx - hw, y0, z + 0.4, white, 0.0, 0.0, 1.0,
			0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, MathUtil.clamp01(goal_flash) * 0.4 * _calm_k(), true, CYAN if goal_by_player else PINK)


## Shadow offset for something at ([param x], [param z]): away from the lamp above the middle of the table.
func _shadow_dx(x: float, h: float) -> float:
	return (x - HockeyGeo.CX) * h * 0.01 + 2.0


func _shadow_dz(z: float, h: float) -> float:
	return (z - HockeyGeo.CY) * h * 0.01 + 3.0


## The puck, the mallets and what goes with them: contact shadows, a glow ring under each mallet,
## the puck's warm glow and hot trail, and the serve marker that closes in on the puck before it is
## released. [param serve_t] runs the serve delay down to 0.
func draw_pieces(r: Renderer3D, t: float,
		puck_x: float, puck_y: float, puck_vx: float, puck_vy: float, puck_shown: bool, puck_live: bool,
		me_x: float, me_y: float, me_speed: float, cpu_x: float, cpu_y: float, cpu_speed: float,
		serve_t: float, serving: bool) -> void:
	if _puck_model == null:
		_puck_model = _build_puck()
	if _my_mallet == null:
		_my_mallet = _build_mallet(0xFF29C8E8)
	if _cpu_mallet == null:
		_cpu_mallet = _build_mallet(0xFFE8408C)
	var sh := TexKit.shadow().full()
	var glow := TexKit.glow().full()
	var ring := HockeyArt.ring().full()
	var pr := HockeyGeo.PUCK_R
	var mr := HockeyGeo.MALLET_R
	r.flat(puck_x + _shadow_dx(puck_x, 5.0), puck_y + _shadow_dz(puck_y, 5.0), 0.4, pr * 2.6, pr * 2.6, sh, 0.0, Blend.ALPHA, 0.0, 0.6)
	r.flat(me_x + _shadow_dx(me_x, 22.0), me_y + _shadow_dz(me_y, 22.0), 0.4, mr * 2.7, mr * 2.7, sh, 0.0, Blend.ALPHA, 0.0, 0.6)
	r.flat(cpu_x + _shadow_dx(cpu_x, 22.0), cpu_y + _shadow_dz(cpu_y, 22.0), 0.4, mr * 2.7, mr * 2.7, sh, 0.0, Blend.ALPHA, 0.0, 0.6)
	if puck_shown:
		_xf.set_xf(puck_x, 0.0, puck_y)
		_puck_model.draw(r, -1, 1.0, _xf)
	_xf.set_xf(cpu_x, 0.0, cpu_y)
	_cpu_mallet.draw(r, -1, 1.0, _xf)
	_xf.set_xf(me_x, 0.0, me_y)
	_my_mallet.draw(r, -1, 1.0, _xf)

	# A glow ring under each mallet, swelling with its speed and with a hit.
	var me_ring := mr * (2.5 + me_speed / 2500.0 + _me_glow * 0.6)
	var cpu_ring := mr * (2.5 + cpu_speed / 2500.0 + _cpu_glow * 0.6)
	r.flat(me_x, me_y, SURFACE, me_ring, me_ring, ring, 0.0, Blend.ADD, 1.0, 0.5 + _me_glow * 0.4, CYAN)
	r.flat(me_x, me_y, SURFACE, me_ring * 1.6, me_ring * 1.6, glow, 0.0, Blend.ADD, 1.0, 0.16 + _me_glow * 0.3, CYAN)
	r.flat(cpu_x, cpu_y, SURFACE, cpu_ring, cpu_ring, ring, 0.0, Blend.ADD, 1.0, 0.5 + _cpu_glow * 0.4, PINK)
	r.flat(cpu_x, cpu_y, SURFACE, cpu_ring * 1.6, cpu_ring * 1.6, glow, 0.0, Blend.ADD, 1.0, 0.16 + _cpu_glow * 0.3, PINK)

	var sp := sqrt(puck_vx * puck_vx + puck_vy * puck_vy)
	if puck_shown:
		# The puck's own glow, and a hot trail behind a fast one.
		var a := 0.22 + MathUtil.clamp01(sp / 900.0) * 0.3
		r.flat(puck_x, puck_y, SURFACE + 0.1, pr * 3.6, pr * 3.6, glow, 0.0, Blend.ADD, 1.0, a, 0xFFFF7A38)
	if puck_live and sp > 300.0:
		var a := MathUtil.clamp01((sp - 300.0) / 700.0) * HockeyLook.TRAIL_ALPHA
		var tx := _trail_x
		var ty := _trail_y
		for i in range(1, _trail_n):
			var k := 1.0 - i / float(_trail_n)
			var hot := Pal.mix_argb(0xFFFF6A30, 0xFFFFE0A8, k)
			r.flat(tx[i], ty[i], SURFACE + 0.2, pr * 3.2 * k + 6.0, pr * 3.2 * k + 6.0, glow, 0.0, Blend.ADD, 1.0, a * k, hot)
			if i % 2 == 0:
				r.flat(tx[i], ty[i], SURFACE + 0.3, pr * 1.3 * k + 3.0, pr * 1.3 * k + 3.0, glow, 0.0, Blend.ADD, 1.0, a * k, 0xFFFFF2D0)
	if not puck_live and serving and serve_t > 0.0:
		# The serve: a ring closes in on the puck, and the puck breathes.
		var blink := 0.5 + 0.5 * sin(t * 12.0)
		var close := MathUtil.clamp01(serve_t / 1.2)
		var s := lerpf(36.0, 110.0, close)
		r.flat(puck_x, puck_y, SURFACE + 0.1, s, s, ring, 0.0, Blend.ADD, 1.0, 0.5 + 0.3 * blink, 0xFFFFD0A0)
		r.flat(puck_x, puck_y, SURFACE + 0.2, 60.0, 60.0, glow, 0.0, Blend.ADD, 1.0, 0.4 * blink, Pal.WHITE)


## Shockwaves, glints and flares from hits and goals, on top of everything.
func draw_effects(r: Renderer3D) -> void:
	var ring := HockeyArt.ring().full()
	var flare := HockeyArt.flare().full()
	var glow := TexKit.glow().full()
	for b in _bursts:
		if b.age >= b.life:
			continue
		var p := b.age / b.life
		var e := 1.0 - (1.0 - p) * (1.0 - p)
		var size := lerpf(b.s0, b.s1, e)
		var a := b.alpha * (1.0 - p) * (1.0 - p * 0.3)
		if b.kind == K_RING:
			r.flat(b.x, b.z, b.y, size, size, ring, 0.0, Blend.ADD, 1.0, a, b.tint)
		elif b.kind == K_FLARE:
			r.sprite(b.x, b.y, b.z, size, size, flare, 0.0, Blend.ADD, 1.0, a, 1.0, b.tint)
		else:
			r.sprite(b.x, b.y, b.z, size, size, glow, 0.0, Blend.ADD, 1.0, a * 0.7, 1.0, b.tint)
