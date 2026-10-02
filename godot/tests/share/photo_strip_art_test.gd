extends PaTest
## Godot-only (build-13 had no test for PhotoStripArt): what the strip painter records, checked
## without a GPU against PhotoStripArt.kt's drawing: the card, the rim, the words sized to fit,
## the shots at 40% over their shadows in white frames, dark frames for missing shots.

var layout: StripLayout


func before_each() -> void:
	layout = StripLayout.new(PhotoStrip.FRAME)


func _shot(w: int, h: int) -> Texture2D:
	var img := Image.create_empty(w, h, false, Image.FORMAT_RGBA8)
	img.fill(Color(0.5, 0.2, 0.6, 1.0))
	return ImageTexture.create_from_image(img)


func _record(shots: Array, arcade_name: String = "POCKET ARCADE", date: String = "OCT 1 2026") -> TexPaint:
	var tp := TexPaint.new(layout.width, layout.height)
	PhotoStripArt.paint(tp, layout, shots, arcade_name, date)
	return tp


static func _of(tp: TexPaint, kind: int) -> Array:
	var out: Array = []
	for e: Array in tp.ops:
		if e[0] == kind:
			out.append(e)
	return out


static func _bounds(pts: PackedVector2Array) -> Rect2:
	var r := Rect2(pts[0], Vector2.ZERO)
	for p in pts:
		r = r.expand(p)
	return r


func test_the_card_and_rim_are_build_13s() -> void:
	var tp := _record([_shot(360, 360), _shot(360, 360), _shot(360, 360), _shot(360, 360)])
	var first: Array = tp.ops[0]
	assert_eq(TexPaint.K_POLY, first[0])
	assert_eq(Rect2(0, 0, layout.width, layout.height), _bounds(first[1]))
	var card: PaPaint = first[2]
	assert_not_null(card.shader, "the card is a gradient")
	if card.shader != null:
		assert_eq(PackedColorArray([Pal.c(Pal.INDIGO), Pal.c(Pal.NIGHT)]), card.shader.colors)
		assert_eq(Vector2(0, 0), card.shader.start)
		assert_eq(Vector2(0, layout.height), card.shader.end_point)
	var rims := _of(tp, TexPaint.K_POLYLINE)
	assert_eq(1, rims.size())
	if rims.size() == 1:
		var rim: Array = rims[0]
		var p: PaPaint = rim[3]
		assert_eq(Pal.PINK, p.color)
		assert_near(layout.margin * 0.16, p.stroke_width, 1e-6)
		assert_true(rim[2], "the rim is closed")
		var inset := layout.margin * 0.4
		var b := _bounds(rim[1])
		assert_near(inset, b.position.x, 1e-3)
		assert_near(inset, b.position.y, 1e-3)
		assert_near(layout.width - inset, b.end.x, 1e-3)
		assert_near(layout.height - inset, b.end.y, 1e-3)


func test_the_words_fit_their_bands_in_build_13s_colours() -> void:
	for arcade_name: String in ["POCKET ARCADE", "AN ARCADE WITH A VERY LONG NAME INDEED", "A"]:
		var tp := _record([null, null, null, null], arcade_name)
		var labels := _of(tp, TexPaint.K_LABEL)
		assert_eq(3, labels.size())
		if labels.size() != 3:
			continue
		var head := layout.header
		var name_l: Array = labels[0]
		var sub_l: Array = labels[1]
		var date_l: Array = labels[2]
		assert_eq(arcade_name, name_l[1])
		assert_eq("%s PHOTO BOOTH %s" % [ArcadeFont.STAR, ArcadeFont.STAR], sub_l[1])
		assert_eq("OCT 1 2026", date_l[1])
		assert_eq(Pal.YELLOW, (name_l[6] as PaPaint).color)
		assert_eq(Pal.SKY, (sub_l[6] as PaPaint).color)
		assert_eq(Pal.LAVENDER, (date_l[6] as PaPaint).color)
		assert_false(name_l[4])
		assert_true(sub_l[4], "the subtitle is in the tiny type")
		assert_false(date_l[4])
		for l: Array in labels:
			assert_eq(PhotoStripArt.TEXT_SHADOW, l[5], "every word has the type's shadow")
		# The name: as big as fits, never more than half the header tall, centred on it.
		var u: float = name_l[3]
		var w := ArcadeFont.width(arcade_name, u)
		assert_true(w <= head.width + 1e-3, "%s: %s wide in %d" % [arcade_name, w, head.width])
		assert_true(ArcadeFont.height(u) <= head.height * 0.5 + 1e-3)
		var at: Vector2 = name_l[2]
		assert_near(head.left + head.width / 2.0, at.x + w / 2.0, 1e-3)
		assert_true(at.y >= head.top and at.y + ArcadeFont.height(u) <= head.bottom)
		# The subtitle under it, inside the header too.
		var sub_at: Vector2 = sub_l[2]
		var su: float = sub_l[3]
		assert_near(head.height * 0.14 / ArcadeFont.TINY_CAP, su, 1e-6)
		assert_near(at.y + ArcadeFont.height(u) + head.height * 0.12, sub_at.y, 1e-3)
		assert_true(sub_at.y + ArcadeFont.height(su, true) <= head.bottom + 1e-3)
		# The date: centred in the footer, at most 0.3 of it tall.
		var foot := layout.footer
		var du: float = date_l[3]
		var date_at: Vector2 = date_l[2]
		assert_true(ArcadeFont.height(du) <= foot.height * 0.3 + 1e-3)
		assert_near(foot.top + (foot.height - ArcadeFont.height(du)) / 2.0, date_at.y, 1e-3)
		assert_near(foot.left + foot.width / 2.0, date_at.x + ArcadeFont.width("OCT 1 2026", du) / 2.0, 1e-3)


func test_shots_sit_at_40_percent_over_their_shadows_in_white_frames() -> void:
	var tp := _record([_shot(360, 360), _shot(640, 480), _shot(300, 500), _shot(360, 360)])
	var images := _of(tp, TexPaint.K_IMAGE)
	assert_eq(4, images.size())
	var border := layout.margin * 0.16
	var polys := _of(tp, TexPaint.K_POLY)
	for i in images.size():
		var e: Array = images[i]
		var r := layout.frames[i]
		var dst := Rect2(r.left, r.top, r.width, r.height)
		assert_eq(dst, e[2], "shot %d fills its frame" % i)
		assert_eq(PhotoStripArt.SHOT_PAINT, (e[3] as PaPaint).color, "shot %d is drawn with the shadow's alpha" % i)
		var part: AtlasTexture = e[1]
		var src := part.atlas
		var crop := PhotoStrip.crop_to_aspect(src.get_width(), src.get_height(), r.width, r.height)
		assert_eq(Rect2(crop.left, crop.top, crop.width, crop.height), part.region, "shot %d is cropped, not squashed" % i)
		# Its shadow (drawn first) and four white bands one border wide round it.
		var shadow_found := false
		var white := Rect2()
		var bands := 0
		for p: Array in polys:
			var pb := _bounds(p[1])
			var color: int = (p[2] as PaPaint).color
			if color == PhotoStripArt.SHADOW and pb.is_equal_approx(Rect2(dst.position + Vector2(border, border * 1.4), dst.size)):
				shadow_found = true
			if color == Pal.WHITE and dst.grow(border + 0.01).encloses(pb) and not dst.grow(-0.01).intersects(pb):
				bands += 1
				white = pb if white.has_area() == false else white.merge(pb)
		assert_true(shadow_found, "shot %d has its shadow" % i)
		assert_eq(4, bands, "shot %d has a white frame" % i)
		assert_true(white.is_equal_approx(dst.grow(border)), "shot %d's frame runs a border round it: %s" % [i, white])


func test_a_missing_shot_is_a_dark_frame() -> void:
	var tp := _record([_shot(360, 360), null, Image.new(), "not a picture"])
	assert_eq(1, _of(tp, TexPaint.K_IMAGE).size())
	var dark := 0
	for p: Array in _of(tp, TexPaint.K_POLY):
		if (p[2] as PaPaint).color == Pal.BLACK:
			var pb := _bounds(p[1])
			var found := false
			for i in [1, 2, 3]:
				var r := layout.frames[i]
				if pb.is_equal_approx(Rect2(r.left, r.top, r.width, r.height)):
					found = true
			assert_true(found, "a dark frame where a shot is missing: %s" % pb)
			dark += 1
	assert_eq(3, dark)
	# Fewer shots than frames: the rest are dark too.
	var short := _record([_shot(360, 360)])
	assert_eq(1, _of(short, TexPaint.K_IMAGE).size())


func test_compose_returns_a_strip_sized_texture() -> void:
	var t := PhotoStripArt.compose([null, null, null, null], "POCKET ARCADE", "OCT 1 2026")
	assert_eq(layout.width, t.width)
	assert_eq(layout.height, t.height)
	assert_true(t.smooth)
	await frames(3)
