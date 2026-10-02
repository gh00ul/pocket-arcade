extends PaTest
## games/pinball/PinballDisplayTest.kt: the backbox's dot-matrix display: its font, how text fits,
## and the dots it bakes. No graphics needed.

const DotMatrix := PinballDisplay.DotMatrix
const PinballDmd := PinballDisplay.PinballDmd

const LETTERS_AND_SYMBOLS := "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!.,-+:?*<> "


func _lit(m: DotMatrix) -> int:
	var n := 0
	for v in m.lit:
		if v > 0.0:
			n += 1
	return n


func test_the_font_covers_every_character_the_display_can_show() -> void:
	for i in LETTERS_AND_SYMBOLS.length():
		var c := LETTERS_AND_SYMBOLS[i]
		assert_true(DotMatrix.has_glyph(c), "no glyph for '%s'" % c)
	# Lower case is set in capitals.
	assert_true(DotMatrix.has_glyph("q"))
	var game := PinballGame.new()
	var texts := game.bot_dmd_strings()
	texts.append_array(PackedStringArray(["TILT", "NO BONUS", "MULTIBALL", "BALL SAVE", "12,345,678"]))
	for t in texts:
		for i in t.length():
			assert_true(DotMatrix.has_glyph(t[i]), "'%s' in \"%s\" has no glyph" % [t[i], t])


func test_glyphs_are_trimmed_to_their_ink() -> void:
	var m := DotMatrix.new()
	assert_eq(5, m.text_width("A"))
	assert_eq(3, m.text_width("I"))
	assert_eq(1, m.text_width("!"))
	assert_eq(1, m.text_width("."))
	assert_eq(3, m.text_width(" "))
	# One dot between letters, and everything doubles at scale 2.
	assert_eq(5 + 1 + 5, m.text_width("AB"))
	assert_eq(2 * (5 + 1 + 5), m.text_width("AB", 2))
	assert_eq(0, m.text_width(""))


func test_a_letter_lights_exactly_its_dots() -> void:
	var m := DotMatrix.new()
	m.text("A", 10, 10, 1, 1.0)
	assert_eq(18, _lit(m))
	m.clear()
	m.text("A", 10, 10, 2, 1.0)
	assert_eq(18 * 4, _lit(m))
	m.clear()
	# Text hanging off the grid is clipped, never an error.
	m.text("MULTIBALL", DotMatrix.COLS - 6, DotMatrix.ROWS - 3, 3, 1.0)
	m.text("X", -20, -20, 4, 1.0)
	assert_true(_lit(m) > 0)


func test_every_message_fits_the_display_at_some_size() -> void:
	var m := DotMatrix.new()
	var game := PinballGame.new()
	for t in game.bot_dmd_strings():
		var scale := m.fit_scale(t, DotMatrix.COLS - 8, 3)
		assert_true(m.text_width(t, scale) <= DotMatrix.COLS - 8, "\"%s\" is %d dots wide at the smallest size" % [t, m.text_width(t, 1)])
		assert_true(scale >= 1)
	# The big ones really do get the big size.
	assert_eq(3, m.fit_scale("SHOOT!", DotMatrix.COLS - 8, 3))


func test_baking_makes_round_bright_dots_on_a_dark_grid() -> void:
	var m := DotMatrix.new()
	m.dot(5, 5, 1.0)
	m.bake()
	var px := m.texture.get_argb()
	var stride := DotMatrix.COLS * DotMatrix.CELL
	var luma := func(gx: int, gy: int) -> int:
		@warning_ignore("integer_division")
		var c := px[(gy * DotMatrix.CELL + DotMatrix.CELL / 2) * stride + gx * DotMatrix.CELL + DotMatrix.CELL / 2]
		return ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255)
	assert_true(luma.call(5, 5) > luma.call(6, 5) * 4, "a lit dot should outshine an unlit one")
	var opaque := true
	for c in px:
		if MathUtil.ushr32(c, 24) != 255:
			opaque = false
			break
	assert_true(opaque, "every pixel is opaque")
	# The gap between dots is darker than the dot itself.
	var corner := px[(5 * DotMatrix.CELL) * stride + 5 * DotMatrix.CELL]
	assert_true(((corner >> 16) & 255) + ((corner >> 8) & 255) < luma.call(5, 5))
	assert_true(m.texture.version > 0, "baking marks the texture changed")


func test_the_display_paints_every_screen_and_only_repaints_when_something_changes() -> void:
	var d := PinballDmd.new()
	var m := d.matrix
	# Playing: the score, ball and multiplier.
	d.update(1.0, "12,340", "BALL 2", "3X", "", 0.0, 1.6, 0, false, false, false)
	assert_true(_lit(m) > 60)
	var v := m.texture.version
	d.update(1.0, "12,340", "BALL 2", "3X", "", 0.0, 1.6, 0, false, false, false)
	assert_eq(v, m.texture.version, "nothing changed, so it is not repainted")
	d.update(1.0, "12,350", "BALL 2", "3X", "", 0.0, 1.6, 0, false, false, false)
	assert_ne(v, m.texture.version, "a new score repaints")
	# A message, a big moment, a tilt, ball save and multiball all put something on the grid.
	var game := PinballGame.new()
	for t in game.bot_dmd_strings():
		for hot in 3:
			d.update(2.0 + hot, "0", "BALL 1", "1X", t, 0.5, 1.6, hot, false, false, false)
			assert_true(_lit(m) > 0, "\"%s\" (hot %d) drew nothing" % [t, hot])
	d.update(9.0, "0", "BALL 1", "1X", "", 0.0, 1.6, 0, false, false, true)
	assert_true(_lit(m) > 0)
	d.update(10.0, "0", "BALL 1", "1X", "", 0.0, 1.6, 0, true, false, false)
	assert_true(_lit(m) > 0)
	d.update(11.0, "0", "BALL 1", "1X", "", 0.0, 1.6, 0, false, true, false)
	assert_true(_lit(m) > 0)
	var nan_found := false
	for x in m.lit:
		if is_nan(x):
			nan_found = true
	assert_false(nan_found)


## Godot-only: the change-tracked bake leaves exactly the picture a full re-bake of every dot
## would (build-13 rewrote all 4096 dots on every bake).
func test_godot_tracked_bake_matches_a_full_bake() -> void:
	var d := PinballDmd.new()
	var m := d.matrix
	var states := [
		[1.0, "12,340", "BALL 2", "3X", "", 0.0, 0],
		[1.5, "12,340", "BALL 2", "3X", "JACKPOT!", 0.2, 2],
		[1.7, "12,340", "BALL 2", "3X", "JACKPOT!", 0.5, 2],
		[3.0, "999", "BALL 3", "1X", "", 0.0, 0],
	]
	for s: Array in states:
		d.update(s[0], s[1], s[2], s[3], s[4], s[5], 1.6, s[6], false, false, false)
		var full := DotMatrix.new()
		for i in DotMatrix.COLS * DotMatrix.ROWS:
			@warning_ignore("integer_division")
			full.dot(i % DotMatrix.COLS, i / DotMatrix.COLS, m.lit[i])
		full.bake()
		assert_eq(full.texture.get_argb(), m.texture.get_argb(), "state %s" % str(s))
