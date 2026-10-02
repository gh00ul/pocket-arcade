class_name StackerGame
extends BaseMiniGame
## games/stacker/StackerGame.kt: the stacker. A slab slides back and forth over the tower; tap to
## drop it. The overhang is cut off and falls, a drop within a few units is perfect and keeps the
## slab whole (a run of perfects grows it back), a slab that misses altogether costs one of three
## lives. Every ten levels is a milestone. [StackerScene] draws it in 3D.


## One placed layer of the tower: centre and size on the ground plane.
class Slab:
	extends RefCounted
	var x: float
	var z: float
	var w: float
	var d: float

	func _init(p_x: float, p_z: float, p_w: float, p_d: float) -> void:
		x = p_x
		z = p_z
		w = p_w
		d = p_d


## A cut-off piece or a missed slab tumbling away; [member level] picks its colour.
class Chunk:
	extends RefCounted
	var x := 0.0
	var y := 0.0
	var z := 0.0
	var w := 0.0
	var d := 0.0
	var vx := 0.0
	var vy := 0.0
	var vz := 0.0
	var spin := 0.0
	var spin_v := 0.0
	var axis_x := true
	var level := 0
	var active := false


var _tower: Array[Slab] = []
var _chunks: Array[Chunk] = []
var _move_x := 0.0
var _move_z := 0.0
var _move_on_x := true
var _move_dir := 1.0
var _moving := false
var _lives := 0
var _combo := 0
var _height := 0
var _perfect_flash := 0.0
var _cam_y := 0.0
var _land_t := 0.0
var _miss_t := 0.0

## All the presentation state (glows, shockwaves); the simulation never reads it.
var _scene := StackerScene.new()

var _stage := Stage3D.new(int(GAME_W), int(GAME_H))
## Kotlin's `pt` scratch: the last point projected to the field (kept when a projection fails).
var _pt_x := 0.0
var _pt_y := 0.0


func _init() -> void:
	id = "stacker"
	title = "STACKER"
	marquee = "STACK"
	instructions = PackedStringArray([
		"TAP TO DROP THE SLAB",
		"OVERHANG GETS CUT OFF",
		"LINE IT UP PERFECTLY",
		"TO KEEP IT WHOLE",
		"3 MISSES AND YOU'RE OUT",
	])
	look = MiniGame.CabinetLook.new(Pal.VIOLET, Pal.CYAN, Pal.PURPLE, MiniGame.CabinetShape.TOWER)
	round_seconds = StackerTuning.ROUND_SECONDS
	for i in 12:
		_chunks.append(Chunk.new())
	_aim(0.0)


func reset() -> void:
	_tower.clear()
	_tower.append(Slab.new(0.0, 0.0, StackerTuning.BASE_SIZE, StackerTuning.BASE_SIZE))
	for c in _chunks:
		c.active = false
	_lives = StackerTuning.LIVES
	_combo = 0
	_height = 0
	_perfect_flash = 0.0
	_cam_y = 0.0
	_land_t = 0.0
	_miss_t = 0.0
	_scene.reset()
	_spawn_slab()


func tickets_for(p_score: int) -> int:
	return StackerTuning.BASE_TICKETS + p_score / StackerTuning.POINTS_PER_TICKET


func is_settled() -> bool:
	for c in _chunks:
		if c.active:
			return false
	return true


func on_time_up() -> void:
	_moving = false


func _top() -> Slab:
	return _tower[_tower.size() - 1]


func _spawn_slab() -> void:
	_move_on_x = _tower.size() % 2 == 1
	_move_dir = 1.0 if rng.next_boolean() else -1.0
	var top := _top()
	_move_x = top.x - StackerTuning.SWING * _move_dir if _move_on_x else top.x
	_move_z = top.z if _move_on_x else top.z - StackerTuning.SWING * _move_dir
	_moving = true


func _speed() -> float:
	return minf(StackerTuning.SPEED_START + _height * StackerTuning.SPEED_PER_LEVEL, StackerTuning.SPEED_MAX)


## Every drop is a single tap: there is no pointer to forget.
func cancel_input() -> void:
	pass


func on_touch(type: int, _id: int, _x: float, _y: float, _time_ms: int) -> void:
	if type != TouchType.DOWN or not _moving or time_up or ended_early:
		return
	_drop()


## Kotlin's `stage.toField(x, y, z, pt)`: projects into _pt_x / _pt_y, which keep their last
## value when the point is behind the camera.
func _to_field(x: float, y: float, z: float) -> void:
	var v: Variant = _stage.to_field(x, y, z)
	if v != null:
		var p: Vector3 = v
		_pt_x = p.x
		_pt_y = p.y


func _drop() -> void:
	_moving = false
	var t := _top()
	var delta := _move_x - t.x if _move_on_x else _move_z - t.z
	var size := t.w if _move_on_x else t.d
	var overlap := size - absf(delta)
	var level := _tower.size()
	var y := level * StackerTuning.SLAB_HEIGHT
	if overlap <= 0.0:
		_miss(level, y)
		return
	var perfect := absf(delta) <= StackerTuning.PERFECT_TOLERANCE
	var w := t.w
	var d := t.d
	var cx := t.x
	var cz := t.z
	if perfect:
		_combo += 1
		if _combo > StackerTuning.GROW_AFTER:
			w = minf(w + StackerTuning.GROW_AMOUNT, StackerTuning.BASE_SIZE)
			d = minf(d + StackerTuning.GROW_AMOUNT, StackerTuning.BASE_SIZE)
		_perfect_flash = 1.0
		var bonus := StackerTuning.PERFECT_POINTS + StackerTuning.COMBO_POINTS * mini(_combo - 1, StackerTuning.COMBO_CAP)
		add_score(StackerTuning.LEVEL_POINTS + bonus, GAME_W / 2.0, 200.0, Pal.CYAN, "PERFECT +%d" % (StackerTuning.LEVEL_POINTS + bonus))
		play(Sfx.SELECT, 0.9, 1.0 + (_combo % 8) * 0.08)
		fx.haptics.hit()
		_to_field(cx, y + StackerTuning.SLAB_HEIGHT, cz)
		particles.burst(_pt_x, _pt_y, 18 + _combo * 2, 60.0, 220.0, [Pal.WHITE, Pal.CYAN, StackerArt.glow_color(level)], 0.6, 4.0, 0.0, 2.0, Particles.SPARKLE)
		_scene.perfect(cx, y + StackerTuning.SLAB_HEIGHT, cz, w, d, level, _combo)
	else:
		_combo = 0
		# Keep the overlap; the overhang breaks off and falls.
		var cut := absf(delta)
		var side := 1.0 if delta > 0.0 else -1.0
		if _move_on_x:
			w = overlap
			cx = t.x + delta / 2.0
			_spawn_chunk(cx + side * (overlap / 2.0 + cut / 2.0), y, _move_z, cut, d, level, true, side)
			_scene.cut(cx + side * overlap / 2.0, y + StackerTuning.SLAB_HEIGHT, _move_z, level)
		else:
			d = overlap
			cz = t.z + delta / 2.0
			_spawn_chunk(_move_x, y, cz + side * (overlap / 2.0 + cut / 2.0), w, cut, level, false, side)
			_scene.cut(_move_x, y + StackerTuning.SLAB_HEIGHT, cz + side * overlap / 2.0, level)
		_scene.land(cx, y + StackerTuning.SLAB_HEIGHT, cz, w, d)
		add_score(StackerTuning.LEVEL_POINTS, GAME_W / 2.0, 220.0, Pal.WHITE)
		play(Sfx.THUD, 0.8, 0.9 + MathUtil.clamp01(overlap / StackerTuning.BASE_SIZE) * 0.4)
		fx.haptics.tick()
		shake.add(0.06)
	_tower.append(Slab.new(cx, cz, w, d))
	_height += 1
	_land_t = 0.2
	if _height % 10 == 0:
		popups.add("HEIGHT %d!" % _height, GAME_W / 2.0, 150.0, Pal.YELLOW, 4.0, 1.3)
		play(Sfx.WIN, 0.8)
		fx.haptics.win()
		_scene.milestone(_tower.size() * StackerTuning.SLAB_HEIGHT)
	if not time_up:
		_spawn_slab()


func _miss(level: int, y: float) -> void:
	_combo = 0
	_lives -= 1
	_miss_t = 0.5
	var t := _top()
	_spawn_chunk(_move_x, y, _move_z, t.w, t.d, level, _move_on_x, _move_dir)
	_scene.miss(t.x, y, t.z)
	play(Sfx.DROP)
	fx.haptics.heavy()
	shake.add(0.3)
	if _lives <= 0:
		popups.add("TOWER TOPPLED", GAME_W / 2.0, 240.0, Pal.RED, 4.0, 1.5)
		play(Sfx.GUTTER)
		ended_early = true
	else:
		popups.add("MISS! %d LEFT" % _lives, GAME_W / 2.0, 240.0, Pal.ORANGE, 3.0, 1.1)
		_spawn_slab()


func _spawn_chunk(x: float, y: float, z: float, w: float, d: float, level: int, axis_x: bool, dir: float) -> void:
	var c: Chunk = _chunks[0]
	for candidate in _chunks:
		if not candidate.active:
			c = candidate
			break
	c.active = true
	c.x = x
	c.y = y
	c.z = z
	c.w = w
	c.d = d
	c.vx = dir * 60.0 if axis_x else rng.range_f(-10.0, 10.0)
	c.vz = dir * 60.0 if not axis_x else rng.range_f(-10.0, 10.0)
	c.vy = 40.0
	c.spin = 0.0
	c.spin_v = dir * rng.range_f(2.0, 4.0)
	c.axis_x = axis_x
	c.level = level


func step(dt: float) -> void:
	_perfect_flash = maxf(_perfect_flash - dt * 2.0, 0.0)
	_land_t = maxf(_land_t - dt, 0.0)
	_miss_t = maxf(_miss_t - dt, 0.0)
	_scene.step(dt, _combo)
	if _moving:
		var s := _speed() * dt * _move_dir
		var top := _top()
		if _move_on_x:
			_move_x += s
			if _move_x > top.x + StackerTuning.SWING:
				_move_x = top.x + StackerTuning.SWING
				_move_dir = -1.0
			if _move_x < top.x - StackerTuning.SWING:
				_move_x = top.x - StackerTuning.SWING
				_move_dir = 1.0
		else:
			_move_z += s
			if _move_z > top.z + StackerTuning.SWING:
				_move_z = top.z + StackerTuning.SWING
				_move_dir = -1.0
			if _move_z < top.z - StackerTuning.SWING:
				_move_z = top.z - StackerTuning.SWING
				_move_dir = 1.0
	for c in _chunks:
		if not c.active:
			continue
		c.vy -= 900.0 * dt
		c.x += c.vx * dt
		c.y += c.vy * dt
		c.z += c.vz * dt
		c.spin += c.spin_v * dt
		if c.y < _cam_y - 400.0:
			c.active = false
	_cam_y = MathUtil.damp(_cam_y, _tower.size() * StackerTuning.SLAB_HEIGHT, 4.0, dt)


# ---------------------------------------------------------------- 3D presentation

## The camera rises with the top of the tower, looking down on it from a fixed angle.
func _aim(top_y: float) -> void:
	_stage.look(330.0, top_y + 330.0, 420.0, 0.0, top_y - 20.0, 0.0, 44.0, 0.5)


func render(scope: DrawScope) -> void:
	_aim(_cam_y)
	var r := _stage.begin()
	var h := StackerTuning.SLAB_HEIGHT
	var n := _tower.size()
	var top_y := n * h
	var climb := MathUtil.clamp01(_cam_y / 1400.0)
	_scene.light(r, _cam_y, top_y, n - 1, _perfect_flash, climb)
	r.gradient(0xFF07030F, 0xFF150A2A)
	_scene.draw_world(r, _cam_y, time, climb, top_y, n - 1)

	# The last 26 courses; each one's neon outline dims the further it is below the top.
	var first := maxi(n - 26, 0)
	var last_idx := n - 1
	for i in range(first, n):
		var s := _tower[i]
		var bottom := -60.0 if i == 0 else i * h
		var glow_k := 1.0 - (last_idx - i) / StackerLook.GLOW_FADE_LEVELS
		_scene.slab(r, s.x, bottom, (i + 1) * h - bottom, s.z, s.w, s.d, i, glow_k, 0.0, true, _perfect_flash if i == last_idx else 0.0)
	if _moving:
		# The sliding slab burns a little brighter than any placed one, breathing.
		var top := _top()
		_scene.slab(r, _move_x, top_y, h, _move_z, top.w, top.d, n, 1.1 + 0.15 * sin(time * 10.0), 0.0, true, 0.0)
	for c in _chunks:
		if not c.active:
			continue
		_scene.slab(r, c.x, c.y, h, c.z, c.w, c.d, c.level, 0.7, c.spin, c.axis_x, 0.0)
	_scene.draw_effects(r)
	_stage.present()

	ArcadeFont.draw_centered(scope, str(_height), GAME_W / 2.0, 24.0, 7.0, Pal.WHITE)
	for i in StackerTuning.LIVES:
		var on := i < _lives
		ArcadeFont.draw_centered(scope, ArcadeFont.HEART, GAME_W / 2.0 - 30.0 + i * 30.0, 86.0, 3.0, Pal.RED if on else Pal.DARKGRAY)
	if _combo >= 2:
		ArcadeFont.draw_centered(scope, "PERFECT x%d" % _combo, GAME_W / 2.0, 118.0, 2.0, Pal.CYAN, 0.7 + 0.3 * sin(time * 10.0))
	if _moving and _height == 0 and not time_up:
		ArcadeFont.draw_centered(scope, "TAP TO DROP", GAME_W / 2.0, 600.0, 2.0, Pal.WHITE, 0.5 + 0.5 * sin(time * 6.0))


# ---------------------------------------------------------------- simulation-test hooks

## How far the sliding slab is from lining up with the top of the tower (0 = perfect).
func bot_delta() -> float:
	var t := _top()
	return _move_x - t.x if _move_on_x else _move_z - t.z


func bot_moving() -> bool:
	return _moving


func bot_height() -> int:
	return _height


# ---------------------------------------------------------------- attract mode

static var _f32_buf := PackedFloat32Array([0.0])


## [param x] rounded to a 32-bit float (the skyline's buildings step along in Kotlin Float sums).
static func _f32(x: float) -> float:
	_f32_buf[0] = x
	return _f32_buf[0]


## A tower going up at night: slabs slide in over a city skyline and drop one by one (some trimmed,
## the overhang falling away; every third one perfect, with a burst of sparks), then the tower fades
## and starts again, under a pulsing "STACK". [param w] × [param h] is the cabinet's small
## portrait screen.
func draw_attract(p: Painter, w: int, h: int, tm: float) -> void:
	var wf := float(w)
	var hf := float(h)
	var cx := wf / 2.0
	var cycle := 9.6
	var n := int(tm / cycle)
	var tt := fmod(tm, cycle)
	# Night sky, deepening towards a glow at the skyline, with a few stars.
	var bands := 8
	for i in bands:
		var k := i / (bands - 1.0)
		p.fill(0.0, i * hf / bands, wf, hf / bands + 0.3, Pal.mix(0xFF05030D, 0xFF2A1450, k * k))
	for i in 9:
		var twinkle := 0.5 + 0.5 * sin(tm * 2.0 + i * 1.9)
		p.disc(MathUtil.hash01(i, 3) * wf, MathUtil.hash01(i, 4) * hf * 0.5, 0.22, Pal.WHITE, 0.2 + 0.6 * twinkle)
	# The city, with lit windows.
	var x := 0.0
	var bi := 0
	while x < wf:
		var bw := _f32(_f32(1.6) + _f32(_f32(MathUtil.hash01(bi, 5)) * _f32(1.6)))
		var bh := 1.2 + MathUtil.hash01(bi, 6) * 2.6
		p.fill(x, hf - bh, bw, bh, 0xFF0B0718)
		if MathUtil.hash01(bi, 7) > 0.35:
			p.disc(x + bw * 0.5, hf - bh + 0.6, 0.18, 0xFFFFC060, 0.5 + 0.5 * sin(tm * 1.3 + bi))
		x = _f32(x + _f32(bw + _f32(0.15)))
		bi += 1
	# The rooftop the tower stands on.
	var base_y := hf - 3.2
	p.fill(1.2, base_y, wf - 2.4, 0.5, 0xFF3A2A66)
	p.fill(1.2, base_y, wf - 2.4, 0.15, Pal.CYAN, 0.7)

	# The tower: seven courses landing one after another, then fading out.
	var layers := 7
	var lh := 1.9
	var prev_w := 8.4
	var prev_x := cx
	var fade := MathUtil.clamp01(1.0 - (tt - 8.6) / 1.0) if tt > 8.6 else 1.0
	for k in layers:
		var land := 0.6 + k * 1.1
		var perfect := k % 3 == 2
		var over := 0.0 if perfect else 0.5 + MathUtil.hash01(n * 9 + k, 8) * 0.9
		var side := 1.0 if MathUtil.hash01(n * 9 + k, 9) > 0.5 else -1.0
		var final_w := maxf(prev_w - over, 3.2)
		var final_x := prev_x if perfect else clampf(prev_x + side * over / 2.0, final_w / 2.0 + 1.0, wf - final_w / 2.0 - 1.0)
		var slot := base_y - (k + 1) * lh
		if tt < land - 0.95:
			prev_w = final_w
			prev_x = final_x
			continue
		var glow := StackerArt.glow_color(k)
		var body_c := Pal.shade(glow, 0.62)
		var u := MathUtil.clamp01((tt - (land - 0.12)) / 0.12)
		var slide := cx + sin(tm * 5.0 + k * 1.7) * (wf / 2.0 - prev_w / 2.0 - 1.0)
		var bx := lerpf(slide, final_x, u * u) if tt < land else final_x
		# The course's glow, its body and its bright top edge.
		p.fill(bx - final_w / 2.0 - 0.5, slot - 0.4, final_w + 1.0, lh + 0.8, glow, 0.16 * fade)
		p.fill(bx - final_w / 2.0, slot, final_w, lh - 0.1, body_c, fade)
		p.fill(bx - final_w / 2.0, slot, final_w, 0.4, glow, fade)
		if tt >= land:
			var since := tt - land
			if not perfect and since < 0.9:
				# The overhang breaks off and falls.
				var cxo := final_x + side * (final_w / 2.0 + over / 2.0)
				p.fill(cxo - over / 2.0, slot + since * since * 14.0, over, lh - 0.1, body_c, (1.0 - since / 0.9) * fade)
			if perfect and since < 0.5:
				# A perfect drop: the outline flashes and sparks fly.
				p.frame(bx - final_w / 2.0 - 0.3, slot - 0.3, final_w + 0.6, lh + 0.5, Pal.WHITE, (1.0 - since / 0.5) * fade)
				for s in 6:
					var a := s / 5.0 * 3.1416
					p.disc(bx + cos(a) * since * 12.0, slot - sin(a) * since * 6.0, 0.3, Pal.WHITE, 1.0 - since / 0.5)
		prev_w = final_w
		prev_x = final_x
	# Title, pulsing.
	var pulse := 0.7 + 0.3 * sin(tm * 4.0)
	p.text_centered("STACK", cx, 1.2, Pal.CYAN, true, 0.4 * pulse, 0.76)
	p.text_centered("STACK", cx, 1.2, Pal.CREAM, true, pulse, 0.7)
