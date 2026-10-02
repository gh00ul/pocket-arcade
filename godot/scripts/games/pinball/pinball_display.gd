class_name PinballDisplay
extends RefCounted
## games/pinball/PinballDisplay.kt: the backbox's dot-matrix display. The Kotlin file holds two
## classes; here they are [PinballDisplay.DotMatrix] (the 128 x 32 grid, its font and its baked
## texture) and [PinballDisplay.PinballDmd] (what the display shows, composed from the game's state).


## A 128 x 32 dot-matrix display, like the amber ones on real machines: a grid of dot brightnesses
## ([member lit], 0..1), a 5 x 7 pixel font, and a [method bake] that turns the grid into a texture
## of round glowing dots with dark gaps between them (mapped onto the backbox as an emissive quad,
## so it blooms like a lamp). Pure numbers and one plain [PaTexture]: safe in tests.
class DotMatrix:
	extends RefCounted

	const COLS := 128
	const ROWS := 32
	## Texture pixels per dot along each axis: enough for a round dot with a visible gap.
	const CELL := 6
	## Brightness levels in the dot colour ramp.
	const LEVELS := 48
	## Glyph height in dots; the font is 5 wide, trimmed to each glyph's ink.
	const GLYPH_H := 7
	## Width of a space, and the gap between letters, in dots at scale 1.
	const SPACE_W := 3
	const GAP := 1

	## The ramp a dot's brightness runs through, off to hot: a dim brown (an unlit dot is still
	## visible as part of the grid), deep orange, amber, then a pale yellow core. Kept saturated
	## below the very top so only a fully lit dot can reach the bloom.
	const RAMP_STOPS := [
		[0.00, 30.0, 12.0, 4.0],
		[0.12, 92.0, 34.0, 6.0],
		[0.45, 214.0, 96.0, 14.0],
		[0.80, 255.0, 150.0, 30.0],
		[1.00, 255.0, 208.0, 110.0],
	]

	## The font: 5 x 7 glyphs as rows of `#` and `.`, one entry per character.
	const FONT := {
		"A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
		"B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
		"C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
		"D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
		"E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
		"F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
		"G": [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"],
		"H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
		"I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
		"J": ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
		"K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
		"L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
		"M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
		"N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
		"O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
		"P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
		"Q": [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
		"R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
		"S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
		"T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
		"U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
		"V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
		"W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
		"X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
		"Y": ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
		"Z": ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
		"0": [".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."],
		"1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
		"2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
		"3": ["####.", "....#", "....#", ".###.", "....#", "....#", "####."],
		"4": ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
		"5": ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
		"6": [".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."],
		"7": ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
		"8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
		"9": [".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."],
		"!": ["#", "#", "#", "#", "#", ".", "#"],
		".": [".", ".", ".", ".", ".", ".", "#"],
		",": [".", ".", ".", ".", ".", "#", "#"],
		"-": [".....", ".....", ".....", "#####", ".....", ".....", "....."],
		"+": [".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."],
		":": [".", "#", ".", ".", ".", "#", "."],
		"?": [".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."],
		"*": [".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", "....."],
		">": ["#..", ".#.", "..#", ".#.", "#..", "...", "..."],
		"<": ["..#", ".#.", "#..", ".#.", "..#", "...", "..."],
	}

	## Every level's 6 x 6 dot pixels, packed ARGB: [level * CELL * CELL + row * CELL + col].
	static var _dot_pixels := PackedInt32Array()
	## The same pixels side by side, one CELL x CELL block per level, for blitting.
	static var _atlas: Image = null
	## Per character: [first inked column, the ink's width (0 for a space), one bit mask per row].
	static var _glyphs := {}

	## Dot brightnesses, row by row, 0 (off) .. 1 (fully lit). Kotlin's FloatArray (32-bit).
	var lit := PackedFloat32Array()
	## The baked picture: COLS x ROWS dots at CELL pixels a dot.
	var texture: PaTexture

	# Change tracking, so a bake rewrites only the dots whose brightness may have moved (the picture
	# is the same as re-baking every dot): the level each dot was last baked at, the dots lit now
	# (so a clear touches only them) and the dots written or cleared since the last bake.
	var _baked := PackedInt32Array()
	var _lit_mark := PackedByteArray()
	var _lit_list := PackedInt32Array()
	var _lit_n := 0
	var _chg_mark := PackedByteArray()
	var _chg_list := PackedInt32Array()
	var _chg_n := 0
	var _baked_once := false
	var _image: Image

	static func _static_init() -> void:
		_dot_pixels = _build_dot_pixels()
		var aw := LEVELS * CELL
		var bytes := PackedByteArray()
		bytes.resize(aw * CELL * 4)
		for l in LEVELS:
			for row in CELL:
				for col in CELL:
					var c := _dot_pixels[l * CELL * CELL + row * CELL + col]
					var o := (row * aw + l * CELL + col) * 4
					bytes[o] = (c >> 16) & 0xFF
					bytes[o + 1] = (c >> 8) & 0xFF
					bytes[o + 2] = c & 0xFF
					bytes[o + 3] = (c >> 24) & 0xFF
		_atlas = Image.create_from_data(aw, CELL, false, Image.FORMAT_RGBA8, bytes)
		for key: String in FONT:
			var art: Array = FONT[key]
			var lo := 1 << 30
			var hi := -1
			for row: String in art:
				for c in row.length():
					if row[c] == "#":
						lo = mini(lo, c)
						hi = maxi(hi, c)
			var rows := PackedInt32Array()
			rows.resize(GLYPH_H)
			for r in GLYPH_H:
				var m := 0
				var line: String = art[r]
				for c in line.length():
					if line[c] == "#":
						m |= 1 << c
				rows[r] = m
			# '.' rows can be blank in a glyph that is all ink on one row; keep the trimmed span.
			_glyphs[key] = [0 if hi < 0 else lo, 0 if hi < 0 else hi - lo + 1, rows]

	static func _build_dot_pixels() -> PackedInt32Array:
		var out := PackedInt32Array()
		out.resize(LEVELS * CELL * CELL)
		# Round dot coverage: the dot fills most of the cell, with a soft edge.
		var cover := PackedFloat32Array()
		cover.resize(CELL * CELL)
		var c := CELL / 2.0
		for y in CELL:
			for x in CELL:
				var d := sqrt((x + 0.5 - c) * (x + 0.5 - c) + (y + 0.5 - c) * (y + 0.5 - c))
				cover[y * CELL + x] = clampf(CELL * 0.43 - d + 0.5, 0.0, 1.0)
		for l in LEVELS:
			var t := l / (LEVELS - 1.0)
			var i := 1
			while i < RAMP_STOPS.size() - 1 and float(RAMP_STOPS[i][0]) < t:
				i += 1
			var a: Array = RAMP_STOPS[i - 1]
			var b: Array = RAMP_STOPS[i]
			var k := clampf((t - float(a[0])) / (float(b[0]) - float(a[0])), 0.0, 1.0)
			var r := float(a[1]) + (float(b[1]) - float(a[1])) * k
			var g := float(a[2]) + (float(b[2]) - float(a[2])) * k
			var bl := float(a[3]) + (float(b[3]) - float(a[3])) * k
			for p in CELL * CELL:
				var m := cover[p]
				# The gap between dots is near black, not transparent (the quad is opaque).
				var gap := 0.10
				var mm := gap + (1.0 - gap) * m
				out[l * CELL * CELL + p] = (255 << 24) | (int(r * mm) << 16) | (int(g * mm) << 8) | int(bl * mm)
		return out

	## Whether the font has a glyph for [param c] (letters are looked up in capitals).
	static func has_glyph(c: String) -> bool:
		return c == " " or _glyphs.has(c.to_upper())

	func _init() -> void:
		lit.resize(COLS * ROWS)
		_baked.resize(COLS * ROWS)
		_baked.fill(-1)
		_lit_mark.resize(COLS * ROWS)
		_lit_list.resize(COLS * ROWS)
		_chg_mark.resize(COLS * ROWS)
		_chg_list.resize(COLS * ROWS)
		_image = Image.create_empty(COLS * CELL, ROWS * CELL, false, Image.FORMAT_RGBA8)
		texture = PaTexture.new(COLS, ROWS, _image, CELL)

	func clear() -> void:
		# Only lit dots are ever non-zero (every write goes through dot), so this is lit.fill(0).
		var l := lit
		var lm := _lit_mark
		var ll := _lit_list
		for k in _lit_n:
			var i := ll[k]
			l[i] = 0.0
			lm[i] = 0
			if _chg_mark[i] == 0:
				_chg_mark[i] = 1
				_chg_list[_chg_n] = i
				_chg_n += 1
		_lit_n = 0

	## Lights the dot at ([param x], [param y]) to at least [param v] (off the grid is ignored).
	func dot(x: int, y: int, v: float) -> void:
		if x < 0 or y < 0 or x >= COLS or y >= ROWS:
			return
		var i := y * COLS + x
		if v > lit[i]:
			lit[i] = v
			if _lit_mark[i] == 0:
				_lit_mark[i] = 1
				_lit_list[_lit_n] = i
				_lit_n += 1
			if _chg_mark[i] == 0:
				_chg_mark[i] = 1
				_chg_list[_chg_n] = i
				_chg_n += 1

	## Fills a block of dots.
	func rect(x: int, y: int, w: int, h: int, v: float) -> void:
		for yy in range(y, y + h):
			for xx in range(x, x + w):
				dot(xx, yy, v)

	## Width of [param s] in dots at [param scale] (letters are 1 dot apart at scale 1).
	func text_width(s: String, scale: int = 1) -> int:
		var w := 0
		var n := s.length()
		for i in n:
			w += _glyph_width(s[i]) * scale
			if i < n - 1:
				w += GAP * scale
		return w

	func _glyph_width(c: String) -> int:
		if c == " ":
			return SPACE_W
		var g: Variant = _glyphs.get(c.to_upper())
		return SPACE_W if g == null else int(g[1])

	## Draws [param s] with its top-left at ([param x], [param y]); returns the x just past it. Every
	## dot is [param scale] dots square.
	func text(s: String, x: int, y: int, scale: int, v: float) -> int:
		var pen := x
		for i in s.length():
			var c := s[i]
			var g: Variant = null if c == " " else _glyphs.get(c.to_upper())
			if g != null:
				var first: int = g[0]
				var width: int = g[1]
				var rows: PackedInt32Array = g[2]
				for r in GLYPH_H:
					var m := rows[r]
					if m == 0:
						continue
					for col in width:
						if (m & (1 << (first + col))) == 0:
							continue
						rect(pen + col * scale, y + r * scale, scale, scale, v)
			pen += _glyph_width(c) * scale + GAP * scale
		return pen - GAP * scale

	func text_centered(s: String, cx: int, y: int, scale: int, v: float) -> void:
		@warning_ignore("integer_division")
		text(s, cx - text_width(s, scale) / 2, y, scale, v)

	## The largest scale (up to [param max_scale]) at which [param s] fits in [param room] dots.
	func fit_scale(s: String, room: int, max_scale: int) -> int:
		var k := max_scale
		while k > 1 and text_width(s, k) > room:
			k -= 1
		return k

	## Turns the dot grid into the texture's pixels and marks it changed, ready for the GPU.
	func bake() -> void:
		var top := LEVELS - 1
		var l := lit
		var baked := _baked
		var img := _image
		var atlas := _atlas
		var cm := _chg_mark
		var cl := _chg_list
		# Every dot written or cleared since the last bake; the others haven't changed.
		for k in _chg_n:
			var i := cl[k]
			cm[i] = 0
			var level := int(clampf(l[i], 0.0, 1.0) * top + 0.5)
			if baked[i] == level:
				continue
			baked[i] = level
			@warning_ignore("integer_division")
			img.blit_rect(atlas, Rect2i(level * CELL, 0, CELL, CELL), Vector2i((i % COLS) * CELL, (i / COLS) * CELL))
		_chg_n = 0
		# The first bake also paints the dark grid everywhere else.
		if not _baked_once:
			_baked_once = true
			for i in COLS * ROWS:
				if baked[i] < 0:
					baked[i] = 0
					@warning_ignore("integer_division")
					img.blit_rect(atlas, Rect2i(0, 0, CELL, CELL), Vector2i((i % COLS) * CELL, (i / COLS) * CELL))
		texture.touch()


## What the backbox display shows, composed from the game's state each time it repaints.
## Read-only: it looks at numbers the game hands it and never touches the simulation or the game's
## random numbers, and every animation is a function of the round clock.
class PinballDmd:
	extends RefCounted

	const TICKS_PER_SECOND := 16

	var matrix := DotMatrix.new()
	## The 1/16 s tick the display was last painted for; it repaints when the tick moves.
	var _last_tick := -1
	## What the last paint showed, so an unchanged display costs nothing.
	var _last_key := -0x7FFFFFFFFFFFFFFF - 1

	## Repaints if anything visible changed (the score, message, lamps or the animation tick).
	## [param message] is "" when none is up (Kotlin's null); [param message_age] is seconds since
	## it appeared, [param message_life] how long it stays. [param hot] marks a big moment:
	## 1 multiball, 2 jackpot, 0 nothing special.
	func update(time: float, score_text: String, ball_text: String, mult_text: String,
			message: String, message_age: float, message_life: float, hot: int,
			multiball: bool, saving: bool, tilted: bool) -> void:
		var tick := int(time * TICKS_PER_SECOND)
		var key := score_text.hash()
		key = key * 31 + ball_text.hash()
		key = key * 31 + mult_text.hash()
		key = key * 31 + (message.hash() if not message.is_empty() else 0)
		key = key * 31 + (1 if multiball else 0) + (2 if saving else 0) + (4 if tilted else 0) + hot * 8
		if tick == _last_tick and key == _last_key:
			return
		_last_tick = tick
		_last_key = key
		var m := matrix
		m.clear()
		var phase := tick / float(TICKS_PER_SECOND)
		if tilted:
			var on := tick % 6 < 4
			if on:
				@warning_ignore("integer_division")
				m.text_centered("TILT", DotMatrix.COLS / 2, 6, 3, 1.0)
			@warning_ignore("integer_division")
			m.text_centered("NO BONUS", DotMatrix.COLS / 2, 25, 1, 0.7)
		elif not message.is_empty():
			_paint_message(m, message, message_age, message_life, hot, tick)
		else:
			_paint_play(m, score_text, ball_text, mult_text, multiball, saving, phase, tick)
		m.bake()

	func _paint_play(m: DotMatrix, score_text: String, ball_text: String, mult_text: String,
			multiball: bool, saving: bool, phase: float, tick: int) -> void:
		@warning_ignore("integer_division")
		var cx := DotMatrix.COLS / 2
		# The score: big, with a faint echo below it (the dot-matrix "shadow").
		var scale := m.fit_scale(score_text, DotMatrix.COLS - 10, 2)
		var y := 5 if scale == 2 else 8
		m.text_centered(score_text, cx, y, scale, 1.0)
		# Bottom row: ball on the left, multiplier on the right, lamps between.
		m.text(ball_text, 4, 24, 1, 0.75)
		var mw := m.text_width(mult_text, 1)
		m.text(mult_text, DotMatrix.COLS - 4 - mw, 24, 1, 0.55 if mult_text == "1X" else 1.0)
		if multiball:
			var on := tick % 8 < 5
			m.text_centered("MULTIBALL", cx, 24, 1, 1.0 if on else 0.35)
		elif saving:
			var on := tick % 8 < 5
			m.text_centered("BALL SAVE", cx, 24, 1, 0.9 if on else 0.3)
		else:
			# A little runner of dots along the bottom edge, so the display is never dead.
			var run := int(phase * 22.0) % (DotMatrix.COLS + 24)
			for k in 12:
				m.dot(run - k, 31, 0.5 * (1.0 - k / 12.0))
		# Top corners: small stars that twinkle.
		var tw := 0.4 + 0.6 * absf(((tick % 10) - 5) / 5.0)
		m.dot(2, 2, tw)
		m.dot(1, 3, tw)
		m.dot(3, 3, tw)
		m.dot(2, 4, tw)
		m.dot(DotMatrix.COLS - 3, 2, 1.2 - tw)
		m.dot(DotMatrix.COLS - 4, 3, 1.2 - tw)
		m.dot(DotMatrix.COLS - 2, 3, 1.2 - tw)
		m.dot(DotMatrix.COLS - 3, 4, 1.2 - tw)

	func _paint_message(m: DotMatrix, text: String, age: float, life: float, hot: int, tick: int) -> void:
		@warning_ignore("integer_division")
		var cx := DotMatrix.COLS / 2
		var scale := m.fit_scale(text, DotMatrix.COLS - 8, 3)
		# Fades out over the last fifth of its life; flashes on and off as it arrives.
		var fade_out := clampf((life - age) / (life * 0.2), 0.0, 1.0)
		var arriving := age < 0.42
		var flash := 1.0
		if arriving:
			flash = 1.0 if int(age * 16.0) % 2 == 0 else 0.25
		var v := flash * (0.35 + 0.65 * fade_out)
		var h := DotMatrix.GLYPH_H * scale
		@warning_ignore("integer_division")
		var y := maxi((DotMatrix.ROWS - h) / 2, 1)
		if hot > 0:
			# A border of running dots round the edge, and beams sweeping out either side.
			var perimeter := 2 * (DotMatrix.COLS + DotMatrix.ROWS) - 4
			var step := 3 if hot == 2 else 4
			for i in perimeter:
				if (i + tick * 2) % step != 0:
					continue
				m.dot(_perimeter_x(i), _perimeter_y(i), 0.9 * fade_out)
		m.text_centered(text, cx, y, scale, v)
		if scale == 1 and text.length() < 12:
			# Short text at the smallest size gets an echo underline so it doesn't look lost.
			var w := m.text_width(text, 1)
			@warning_ignore("integer_division")
			for x in range(cx - w / 2, cx + (w + 1) / 2):
				m.dot(x, y + h + 3, 0.4 * v)

	## The i-th dot walking clockwise round the display's edge, from the top left.
	static func _perimeter_x(i: int) -> int:
		var w := DotMatrix.COLS
		var h := DotMatrix.ROWS
		if i < w:
			return i
		if i < w + h - 1:
			return w - 1
		if i < 2 * w + h - 2:
			return w - 1 - (i - (w + h - 2))
		return 0

	static func _perimeter_y(i: int) -> int:
		var w := DotMatrix.COLS
		var h := DotMatrix.ROWS
		if i < w:
			return 0
		if i < w + h - 1:
			return i - w + 1
		if i < 2 * w + h - 2:
			return h - 1
		return h - 1 - (i - (2 * w + h - 3))
