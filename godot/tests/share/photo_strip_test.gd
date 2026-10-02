extends PaTest
## share/PhotoStripTest.kt: the photo strip's geometry, its file names, its date and its cropping:
## all plain numbers and text. (Kotlin's ZoneIds become fixed offsets east of UTC: Helsinki is
## UTC+3 and New York UTC-4 on the date tested.) Plus Godot-only checks marked as such.


func _inside(r: PxRect, w: int, h: int) -> bool:
	return r.left >= 0 and r.top >= 0 and r.right <= w and r.bottom <= h


## Instant.parse("yyyy-MM-ddTHH:mm:ss[.SSS]Z").toEpochMilli(), worked out from the calendar
## (days from 1970-01-01 by the proleptic Gregorian rules, as java.time counts them).
static func _instant(iso: String) -> int:
	var y := int(iso.substr(0, 4))
	var mo := int(iso.substr(5, 2))
	var d := int(iso.substr(8, 2))
	var secs := int(iso.substr(11, 2)) * 3600 + int(iso.substr(14, 2)) * 60 + int(iso.substr(17, 2))
	var ms := int(iso.substr(20, 3)) if iso.length() > 20 and iso[19] == "." else 0
	return (_days_from_civil(y, mo, d) * 86400 + secs) * 1000 + ms


## Days since 1970-01-01 of a calendar date (H. Hinnant's days_from_civil).
static func _days_from_civil(y: int, m: int, d: int) -> int:
	if m <= 2:
		y -= 1
	var era := (y if y >= 0 else y - 399) / 400
	var yoe := y - era * 400
	var mp := m - 3 if m > 2 else m + 9
	var doy := (153 * mp + 2) / 5 + d - 1
	var doe := yoe * 365 + yoe / 4 - yoe / 100 + doy
	return era * 146097 + doe - 719468


## kotlin.random.Random.nextLong(from, until): the same bits drawn the same way (KRandom has
## nextLong() only).
static func _next_long_range(rng: KRandom, from: int, until: int) -> int:
	var n := until - from
	if n > 0:
		var rnd: int
		if n & -n == n:
			var n_low := MathUtil.i32(n)
			var n_high := MathUtil.i32(n >> 32)
			if n_low != 0:
				rnd = rng.next_bits(31 - MathUtil.nlz32(n_low)) & 0xFFFFFFFF
			elif n_high == 1:
				rnd = rng.next_int() & 0xFFFFFFFF
			else:
				rnd = (rng.next_bits(31 - MathUtil.nlz32(n_high)) << 32) + (rng.next_int() & 0xFFFFFFFF)
		else:
			var v := 0
			while true:
				var bits := (rng.next_long() >> 1) & 0x7FFFFFFFFFFFFFFF
				v = bits % n
				if bits - v + (n - 1) >= 0:
					break
			rnd = v
		return from + rnd
	while true:
		var r := rng.next_long()
		if r >= from and r < until:
			return r
	return from


func test_the_strip_holds_four_square_shots_with_room_for_the_header_and_date() -> void:
	for frame: int in [PhotoStrip.FRAME, 100, 240, 361, 512]:
		var l := StripLayout.new(frame)
		var label := "frame %d" % frame
		assert_eq(PhotoStrip.SHOTS, l.frames.size(), label)
		assert_eq(frame + 2 * l.margin, l.width, label)
		# Everything is inside the strip and inside the margins.
		var all: Array[PxRect] = []
		all.append_array(l.frames)
		all.append(l.header)
		all.append(l.footer)
		for r in all:
			assert_true(_inside(r, l.width, l.height), "%s: %s is outside %d x %d" % [label, r, l.width, l.height])
			assert_true(r.left >= l.margin and r.right <= l.width - l.margin and r.top >= l.margin and r.bottom <= l.height - l.margin, "%s: %s is in the margin" % [label, r])
		for r in l.frames:
			assert_eq(frame, r.width, label + ": shots are square")
			assert_eq(frame, r.height, label + ": shots are square")
			assert_eq(l.margin, r.left, label + ": shots line up")
		# Header, the four shots and the footer run down the strip in order, a gap between the shots.
		assert_eq(l.margin, l.header.top, label + ": the header starts at the margin")
		assert_eq(l.header.bottom, l.frames[0].top, label + ": the first shot follows the header")
		for i in range(1, l.frames.size()):
			assert_eq(l.gap, l.frames[i].top - l.frames[i - 1].bottom, "%s: gap %d" % [label, i])
			assert_true(l.frames[i].top >= l.frames[i - 1].bottom, "%s: shots %d overlap" % [label, i])
		assert_eq(l.frames[l.frames.size() - 1].bottom, l.footer.top, label + ": the footer follows the last shot")
		assert_eq(l.footer.bottom + l.margin, l.height, label + ": the strip ends after the footer and a margin")
		assert_true(l.header.height >= frame / 4 and l.header.width == frame, label + ": room for the arcade's name")
		assert_true(l.footer.height >= frame / 6 and l.footer.width == frame, label + ": room for the date")


func test_the_standard_strip_is_tall_and_narrow() -> void:
	var l := StripLayout.new(PhotoStrip.FRAME)
	assert_true(l.height > 3 * l.width and l.height < 6 * l.width, "%d x %d" % [l.width, l.height])
	# Small enough to hold in memory and share.
	assert_true(l.width * l.height * 4 < 8 * 1024 * 1024)


func test_file_names_are_the_utc_time_so_they_sort_in_the_order_made() -> void:
	assert_eq("strip-19700101-000000-000.png", PhotoStrip.file_name(0))
	# 2026-09-28 21:30:45.123 UTC
	var millis := _instant("2026-09-28T21:30:45.123Z")
	assert_eq("strip-20260928-213045-123.png", PhotoStrip.file_name(millis))

	var rng := KRandom.new(7)
	var seen := {}
	var times: Array[int] = []
	for i in 200:
		var t := _next_long_range(rng, 0, 4_102_444_800_000)
		if not seen.has(t):
			seen[t] = true
			times.append(t)
	times.sort()
	var names := PackedStringArray()
	for t in times:
		names.append(PhotoStrip.file_name(t))
	var sorted := names.duplicate()
	sorted.sort()
	assert_eq(sorted, names, "names sort in the order the strips were made")
	var unique := {}
	for n in names:
		unique[n] = true
	assert_eq(names.size(), unique.size(), "every strip gets its own name")
	# A second apart, and a millisecond apart, across midnight and new year.
	for pair: Array in [["2026-12-31T23:59:59.999Z", "2027-01-01T00:00:00.000Z"], ["2026-09-28T09:59:59.999Z", "2026-09-28T10:00:00.000Z"]]:
		assert_true(PhotoStrip.file_name(_instant(pair[0])) < PhotoStrip.file_name(_instant(pair[1])))


func test_only_our_own_names_count_as_strips() -> void:
	assert_true(PhotoStrip.is_strip_name(PhotoStrip.file_name(123456789)))
	assert_true(PhotoStrip.is_strip_name("strip-20260928-213045-123.png"))
	for other: String in ["", "strip.png", "strip-1.png", "strip-20260928-213045-123.png.tmp", "notes.txt", "xstrip-20260928-213045-123.png", "strip-20260928-213045-123.jpg", "../strip-20260928-213045-123.png"]:
		assert_false(PhotoStrip.is_strip_name(other), "'%s'" % other)


func test_the_date_is_printed_in_the_local_zone_in_words() -> void:
	assert_eq("JAN 1 1970", PhotoStrip.date_label(0, 0))
	var late := _instant("2026-09-28T23:30:00Z")
	assert_eq("SEP 28 2026", PhotoStrip.date_label(late, 0))
	# The same instant is already tomorrow two hours east, and still today in New York.
	assert_eq("SEP 29 2026", PhotoStrip.date_label(late, 3 * 60))
	assert_eq("SEP 28 2026", PhotoStrip.date_label(late, -4 * 60))
	assert_eq("DEC 5 2031", PhotoStrip.date_label(_instant("2031-12-05T12:00:00Z"), 0))
	# Every month has a three-letter name.
	var re := RegEx.create_from_string("^[A-Z]{3} 15 2026$")
	for m in range(1, 13):
		var s := PhotoStrip.date_label(_instant("2026-%02d-15T12:00:00Z" % m), 0)
		assert_true(re.search(s) != null, s)


func test_a_long_name_shrinks_to_fit_and_a_short_one_keeps_its_size() -> void:
	# 100 pixels wide at one unit, 360 to put it in.
	assert_near(3.6, PhotoStrip.fit_unit(100.0, 360.0, 6.0), 1e-4)
	assert_near(6.0, PhotoStrip.fit_unit(40.0, 360.0, 6.0), 0.0)
	assert_near(6.0, PhotoStrip.fit_unit(0.0, 360.0, 6.0), 0.0)
	assert_near(6.0, PhotoStrip.fit_unit(100.0, 0.0, 6.0), 0.0)
	# Whatever it comes to, the text ends up no wider than the room.
	for natural: float in [10.0, 55.0, 99.0, 140.0, 400.0]:
		var unit := PhotoStrip.fit_unit(natural, 360.0, 6.0)
		assert_true(unit * natural <= 360.0 + 1e-3)
		assert_true(unit <= 6.0)


func test_crops_keep_the_shape_of_the_frame_and_the_middle_of_the_picture() -> void:
	# Already the right shape: everything.
	var c := PhotoStrip.crop_to_aspect(360, 360, 300, 300)
	assert_eq("(0, 0)-(360, 360)", str(c))
	# Too wide: trimmed evenly at the sides.
	c = PhotoStrip.crop_to_aspect(200, 100, 50, 50)
	assert_eq("(50, 0)-(150, 100)", str(c))
	# Too tall: trimmed evenly top and bottom.
	c = PhotoStrip.crop_to_aspect(100, 200, 50, 50)
	assert_eq("(0, 50)-(100, 150)", str(c))
	# Into a non-square frame.
	c = PhotoStrip.crop_to_aspect(400, 300, 200, 100)
	assert_eq("(0, 50)-(400, 250)", str(c))
	# Nonsense sizes don't crash.
	assert_eq(0, PhotoStrip.crop_to_aspect(0, 0, 10, 10).width)
	assert_eq(10, PhotoStrip.crop_to_aspect(10, 10, 0, 0).width)
	# Whatever the shapes, the crop is inside the picture and has the frame's shape (to a pixel).
	var rng := KRandom.new(3)
	for k in 300:
		var sw := rng.next_int_range(20, 900)
		var sh := rng.next_int_range(20, 900)
		var dw := rng.next_int_range(20, 900)
		var dh := rng.next_int_range(20, 900)
		var r := PhotoStrip.crop_to_aspect(sw, sh, dw, dh)
		assert_true(r.left >= 0 and r.top >= 0 and r.right <= sw and r.bottom <= sh and r.width > 0 and r.height > 0, "%d x %d -> %s" % [sw, sh, r])
		# The same shape as the frame, to the pixel the trimmed side is rounded by.
		var off := absi(r.width * dh - r.height * dw)
		assert_true(off <= maxi(dw, dh), "%d x %d into %d x %d: %s is off by %d" % [sw, sh, dw, dh, r, off])


# ---------------------------------------------------------------- Godot-only

## Godot-only: the layout's whole-pixel sizes are build-13's, which computed them with 32-bit
## Floats (numbers from build-13's arithmetic, checked with numpy float32).
func test_the_layout_matches_build_13s_float_sizes() -> void:
	# frame: margin, gap, header, footer
	var expect := {
		360: [28, 16, 115, 82], 100: [8, 4, 32, 23], 240: [19, 10, 76, 55], 361: [28, 16, 115, 83],
		512: [40, 23, 163, 117], 25: [2, 1, 8, 5], 10: [2, 1, 4, 4], 1: [2, 1, 4, 4],
	}
	for frame: int in expect:
		var e: Array = expect[frame]
		var l := StripLayout.new(frame)
		assert_eq(e, [l.margin, l.gap, l.header_height, l.footer_height], "frame %d" % frame)
	var std := StripLayout.new(PhotoStrip.FRAME)
	assert_eq(416, std.width)
	assert_eq(1741, std.height)


## Godot-only: dates before 1970 split the way Instant.ofEpochMilli does, and a name keeps its
## leading zeros.
func test_times_before_1970_and_small_values_are_named_like_build_13() -> void:
	assert_eq("strip-19691231-235959-999.png", PhotoStrip.file_name(-1))
	assert_eq("strip-19700101-000001-002.png", PhotoStrip.file_name(1002))
	assert_eq("DEC 31 1969", PhotoStrip.date_label(-1, 0))
	# A strip name with a trailing newline is not one of ours (PCRE's $ alone would allow it).
	assert_false(PhotoStrip.is_strip_name("strip-20260928-213045-123.png\n"))


## Godot-only: with no zone given the label is on the phone's calendar (its offset east of UTC).
func test_the_system_zone_is_used_by_default() -> void:
	assert_eq(int(Time.get_time_zone_from_system()["bias"]), PhotoStrip.system_zone_minutes())
	for millis: int in [0, _instant("2026-09-28T23:30:00Z"), _instant("2031-12-05T00:10:00Z")]:
		assert_eq(PhotoStrip.date_label(millis, PhotoStrip.system_zone_minutes()), PhotoStrip.date_label(millis))
	# The helper agrees with the calendar's own anchors.
	assert_eq(0, _instant("1970-01-01T00:00:00Z"))
	assert_eq(1_790_000_000_000, _instant("2026-09-21T14:13:20Z"))
	assert_eq(1_790_000_000_123, _instant("2026-09-21T14:13:20.123Z"))
