class_name HockeyArt
extends RefCounted
## games/airhockey/HockeyArt.kt: painted art for the 3D air hockey table. The look is a dark glossy
## playfield under neon: the player's half is cyan, the CPU's pink, and every marking is a bright
## thin line, so the bloom makes the lines glow while the wide surface stays dark and the puck, the
## one bright warm thing, reads instantly. (A pale surface would sit above the bloom threshold and
## glow white, so nothing large here is pale.)

## Transparent white: soft gradients fade to this so their middles don't turn grey.
const CLEAR_WHITE := 0x00FFFFFF
const LINE_HOT := 0xFFB8F4FF

static var _rail: PaTexture = null
static var _body: PaTexture = null
static var _slot: PaTexture = null
static var _post: PaTexture = null
static var _floor_tile: PaTexture = null
static var _floor_region: Region = null
static var _wall: PaTexture = null
static var _puck_side: PaTexture = null
static var _puck_top: PaTexture = null
static var _ring: PaTexture = null
static var _flare: PaTexture = null


## The playing surface: dark navy with a lattice of air holes, a glowing centre line and ring, and
## the goal creases (the top of the texture is the CPU's end, pink; the bottom the player's, cyan).
static func surface(w: int, h: int, goal_half: float) -> PaTexture:
	return TexPaint.paint_texture(w, h, 3, func(tp: TexPaint) -> void: _paint_surface(tp, w, h, goal_half))


static func _paint_surface(tp: TexPaint, w: int, h: int, goal_half: float) -> void:
	var wf := float(w)
	var hf := float(h)
	var cx := wf / 2.0
	var cy := hf / 2.0
	tp.vgrad(0.0, 0.0, wf, hf, [0xFF0A1A38, 0xFF0E2A52, 0xFF0A1A38])
	tp.radial(cx, cy, wf * 0.8, Pal.with_alpha(0xFF2A6AB0, 0.2), 0x002A6AB0)
	# Each half is washed with its team's colour.
	tp.rect(0.0, 0.0, wf, cy, Pal.with_alpha(Pal.PINK, 0.05))
	tp.rect(0.0, cy, wf, cy, Pal.with_alpha(Pal.CYAN, 0.05))
	# Air holes: kept dim, so they only wake where the puck's light falls.
	for y in range(6, h, 12):
		for x in range(6, w, 12):
			tp.circle(x, y, 0.95, 0xFF244A80)
	# Quarter lines, dashed and faint, in each team's colour.
	var x := 4.0
	while x < wf - 4.0:
		tp.rect(x, hf / 4.0 - 0.8, 7.0, 1.6, Pal.with_alpha(Pal.PINK, 0.4))
		tp.rect(x, hf * 3.0 / 4.0 - 0.8, 7.0, 1.6, Pal.with_alpha(Pal.CYAN, 0.4))
		x += 12.0
	# Centre line and ring.
	tp.rect(0.0, cy - 1.6, wf, 3.2, Pal.with_alpha(LINE_HOT, 0.85))
	tp.ring(cx, cy, 44.0, 3.0, Pal.with_alpha(LINE_HOT, 0.8))
	tp.circle(cx, cy, 6.0, Pal.WHITE)
	# Goal creases.
	tp.ring(cx, 0.0, goal_half + 14.0, 3.2, Pal.with_alpha(Pal.HOTPINK, 0.9))
	tp.ring(cx, hf, goal_half + 14.0, 3.2, Pal.with_alpha(Pal.CYAN, 0.9))
	# Sides fall away into shadow; a fine border line keeps the edge.
	tp.hgrad(0.0, 0.0, 12.0, hf, [0x80000000, 0])
	tp.hgrad(wf - 12.0, 0.0, 12.0, hf, [0, 0x80000000])
	tp.stroke_round(0.5, 0.5, wf - 1.0, hf - 1.0, 6.0, 1.5, Pal.with_alpha(Pal.SKY, 0.5))


## Rails: brushed dark blue steel, a touch lighter on top.
static func rail() -> PaTexture:
	if _rail == null:
		_rail = TexPaint.paint_texture(16, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 16.0, 16.0, [0xFF35558F, 0xFF15264C])
			tp.rect(0.0, 0.0, 16.0, 0.9, Pal.with_alpha(Pal.WHITE, 0.25)))
	return _rail


## The table's body: dark panels with vents.
static func body() -> PaTexture:
	if _body == null:
		_body = TexPaint.paint_texture(64, 32, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 64.0, 32.0, [0xFF14264C, 0xFF080F22])
			for x in range(0, 64, 16):
				tp.round(x + 6.0, 6.0, 4.0, 22.0, 2.0, 0xFF050A18)
			tp.rect(0.0, 0.0, 64.0, 1.5, Pal.shade(Pal.SKY, 0.6)))
	return _body


static func slot() -> PaTexture:
	if _slot == null:
		_slot = TexPaint.paint_texture(8, 8, 4, func(tp: TexPaint) -> void: tp.fill(0xFF020308))
	return _slot


static func post() -> PaTexture:
	if _post == null:
		_post = TexPaint.paint_texture(16, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 16.0, 16.0, [0xFF3A466A, 0xFF12182C])
			tp.rect(3.0, 0.0, 1.6, 16.0, Pal.with_alpha(Pal.WHITE, 0.2)))
	return _post


## The arena floor, tiled: near-black with a fine cyan grid.
static func floor_tile() -> PaTexture:
	if _floor_tile == null:
		_floor_tile = TexPaint.paint_texture(16, 16, 4, func(tp: TexPaint) -> void:
			tp.fill(0xFF0A0814)
			for i in 26:
				tp.circle(MathUtil.hash01(i, 21) * 16.0, MathUtil.hash01(i, 22) * 16.0, 0.25, 0xFF120E22)
			tp.rect(0.0, 0.0, 16.0, 0.45, Pal.with_alpha(Pal.CYAN, 0.2))
			tp.rect(0.0, 0.0, 0.45, 16.0, Pal.with_alpha(Pal.CYAN, 0.2)))
	return _floor_tile


static func floor_region() -> Region:
	if _floor_region == null:
		_floor_region = floor_tile().region(0, 0, -1, -1, true)
	return _floor_region


## The wall behind the far end: dark panels with a curtain of vertical LED strips shading from cyan
## to pink, brighter towards a glowing bar at the floor. It draws emissive, so the dark parts stay
## dark and the strips shine. 232 × 116 units span 1160 × 580 cm.
static func wall() -> PaTexture:
	if _wall == null:
		_wall = TexPaint.paint_texture(232, 116, 4, func(tp: TexPaint) -> void:
			tp.fill(0xFF06040E)
			for px in range(0, 232, 29):
				tp.rect(px + 1.0, 2.0, 27.0, 104.0, 0xFF0B0919)
			var sx := 2.0
			while sx < 230.0:
				var c := Pal.mix(Pal.CYAN, Pal.PINK, sx / 232.0)
				tp.vgrad(sx, 6.0, 1.4, 100.0, [Pal.with_alpha(c, 0.0), Pal.with_alpha(c, 0.85)])
				sx += 5.0
			tp.hgrad(0.0, 106.0, 232.0, 2.6, [Pal.CYAN, Pal.HOTPINK, Pal.CYAN])
			tp.hgrad(0.0, 108.6, 232.0, 7.4, [0xFF10182E, 0xFF181030, 0xFF10182E]))
	return _wall


## A mallet's skin, painted top (the knob) to bottom (the base's foot) to match the lathe's profile
## in [HockeyScene]: a bright knob, a dark neck, a lit shoulder, then the base in the team's colour
## with a dark foot.
static func mallet_skin(color: int) -> PaTexture:
	return TexPaint.paint_texture(16, 32, 4, func(tp: TexPaint) -> void:
		tp.vgrad(0.0, 0.0, 16.0, 6.8, [Pal.mix(color, Pal.WHITE, 0.5), color])
		tp.vgrad(0.0, 6.8, 16.0, 11.3, [Pal.shade(color, 0.4), Pal.shade(color, 0.25)])
		tp.rect(0.0, 18.1, 16.0, 1.8, Pal.mix(color, Pal.WHITE, 0.55))
		tp.vgrad(0.0, 19.9, 16.0, 10.0, [Pal.shade(color, 0.85), Pal.shade(color, 0.55)])
		tp.rect(0.0, 29.9, 16.0, 2.1, Pal.shade(color, 0.2)))


static func puck_side() -> PaTexture:
	if _puck_side == null:
		_puck_side = TexPaint.paint_texture(32, 5, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 5.0, [0xFFFF7A44, 0xFFB02A14])
			tp.rect(0.0, 0.3, 32.0, 0.9, Pal.with_alpha(Pal.WHITE, 0.35)))
	return _puck_side


## The puck's top: a hot orange face with a white ring and a bright centre, so it stays vivid.
static func puck_top() -> PaTexture:
	if _puck_top == null:
		_puck_top = TexPaint.paint_texture(16, 16, 8, func(tp: TexPaint) -> void:
			tp.radial(6.5, 6.0, 11.0, 0xFFFFB070, 0xFFE0421E)
			tp.ring(8.0, 8.0, 6.0, 0.9, Pal.with_alpha(Pal.WHITE, 0.85))
			tp.circle(8.0, 8.0, 2.1, Pal.with_alpha(Pal.WHITE, 0.9)))
	return _puck_top


## A thin ring, soft either side, for shockwaves and the serve marker.
static func ring() -> PaTexture:
	if _ring == null:
		_ring = TexPaint.paint_texture(32, 32, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			var p := tp.paint.reset()
			# Android ignores a shaded paint's RGB (only its alpha, 255 after reset, counts); the
			# port's painter modulates the gradient by the whole colour, so it is white here.
			p.color = Pal.WHITE
			p.shader = PaBrush.radial([CLEAR_WHITE, CLEAR_WHITE, Pal.WHITE, CLEAR_WHITE], Vector2(16.0, 16.0), 15.5, [0.0, 0.72, 0.88, 1.0])
			tp.c_draw_circle(16.0, 16.0, 15.5, p)
			p.shader = null)
	return _ring


## A four-point glint with a hot core.
static func flare() -> PaTexture:
	if _flare == null:
		_flare = TexPaint.paint_texture(32, 32, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.radial(16.0, 16.0, 14.0, Pal.with_alpha(Pal.WHITE, 0.8), CLEAR_WHITE)
			tp.oval(16.0, 16.0, 15.5, 0.8, Pal.with_alpha(Pal.WHITE, 0.9))
			tp.oval(16.0, 16.0, 0.8, 15.5, Pal.with_alpha(Pal.WHITE, 0.9))
			tp.circle(16.0, 16.0, 2.2, Pal.WHITE))
	return _flare


## LED scoreboard, repainted only when the score changes: a caption and big glowing digits for each
## side, and seven pips under each showing how far to the win they are.
class Scoreboard:
	extends RefCounted
	const W := 96
	const H := 34
	const SCALE := 4

	var _goals_to_win: int
	var _painter: TexPaint = null
	var _tex: PaTexture = null
	var _last := -1

	func _init(goals_to_win: int) -> void:
		_goals_to_win = goals_to_win

	func tex() -> PaTexture:
		if _tex == null:
			_tex = PaTexture.new(W, H, null, SCALE)
		return _tex

	func paint(you: int, cpu: int) -> void:
		var key := you * 100 + cpu
		if key == _last:
			return
		_last = key
		if _painter == null:
			_painter = TexPaint.new(W * SCALE, H * SCALE)
			_painter.use_units(SCALE)
		var tp := _painter
		# Each repaint starts a fresh recording (build-13 painted over the old picture: its first
		# gradient is opaque and covers everything, so the result is the same).
		tp.clear(0)
		tp.vgrad(0.0, 0.0, W, H, [0xFF10142A, 0xFF05060C])
		for y in range(0, H, 2):
			tp.rect(0.0, y, W, 0.55, Pal.with_alpha(Pal.NIGHT, 0.6))
		tp.stroke_round(0.6, 0.6, W - 1.2, H - 1.2, 3.0, 1.2, Pal.shade(Pal.SKY, 0.8))
		tp.label("YOU", 24.0, 2.6, 5.0, Pal.CYAN, true, true)
		tp.label("CPU", 72.0, 2.6, 5.0, Pal.HOTPINK, true, true)
		tp.label("TO %d" % _goals_to_win, 48.0, 12.0, 4.2, Pal.LAVENDER, true, true)
		var you_s := str(you)
		var cpu_s := str(cpu)
		tp.glow(1.5, Pal.with_alpha(Pal.YELLOW, 0.7), func(g: TexPaint) -> void:
			g.label(you_s, 24.0, 9.5, 13.0, Pal.WHITE)
			g.label(cpu_s, 72.0, 9.5, 13.0, Pal.WHITE))
		tp.label(you_s, 24.0, 9.5, 13.0, Pal.YELLOW)
		tp.label(cpu_s, 72.0, 9.5, 13.0, Pal.YELLOW)
		tp.round(45.0, 18.0, 6.0, 1.6, 0.8, Pal.with_alpha(Pal.WHITE, 0.5))
		# The race to the win, as pips.
		var gap := 4.7
		var start := -(_goals_to_win - 1) * gap / 2.0
		for i in _goals_to_win:
			var on := i < you
			tp.round(24.0 + start + i * gap - 1.7, 26.0, 3.4, 3.4, 1.0, Pal.CYAN if on else Pal.shade(Pal.CYAN, 0.22))
			var cpu_on := i < cpu
			tp.round(72.0 + start + i * gap - 1.7, 26.0, 3.4, 3.4, 1.0, Pal.HOTPINK if cpu_on else Pal.shade(Pal.HOTPINK, 0.22))
		tp.update(tex())
