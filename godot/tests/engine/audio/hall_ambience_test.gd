extends PaTest
## engine/audio/HallAmbienceTest.kt: where the hall's background sounds come from, with the mixer
## replaced by a recorder.

const NORTH := PI


class Started:
	var sfx: int
	var left: float
	var right: float
	var send: float
	var priority: int


## build-13's VoiceSink, recording what it is asked to play.
class Recorder:
	var started: Array[Started] = []
	var ambient := 0

	func start_voice(sfx: int, gain_l: float, gain_r: float, send: float, _pitch: float, priority: int) -> void:
		var s := Started.new()
		s.sfx = sfx
		s.left = gain_l
		s.right = gain_r
		s.send = send
		s.priority = priority
		started.append(s)

	func ambient_voices() -> int:
		return ambient

	func ambient_starts() -> Array[Started]:
		var out: Array[Started] = []
		for s in started:
			if s.priority == AudioPriority.AMBIENT:
				out.append(s)
		return out


func ambience() -> HallAmbience:
	var a := HallAmbience.new()
	a.target = 1.0
	return a


## Runs [param seconds] of ambience in 10 ms blocks as heard from ([param lx], [param lz]) facing
## north; returns the murmur's gain averaged over the second half (build-13 measured the mixed RMS).
func run(a: HallAmbience, sink: Recorder, seconds: float, lx: float = 300.0, lz: float = 500.0) -> float:
	var blocks := int(seconds * 48000 / 480)
	var sum := 0.0
	var count := 0
	for b in blocks:
		a.tick(0.01, sink, 1.0, lx, lz, NORTH)
		if b > blocks / 2:
			sum += a.murmur_gain
			count += 1
	return sum / maxi(count, 1)


func test_bleeps_come_from_cabinets_on_their_own_side() -> void:
	var a := ambience()
	# Two cabinets: one due east (right, facing north), one due west (left).
	a.set_sources(PackedFloat32Array([360.0, 240.0]), PackedFloat32Array([500.0, 500.0]), PackedInt32Array([Attract.GENERIC, Attract.GENERIC]), 2)
	var rec := Recorder.new()
	run(a, rec, 30.0)
	var bleeps := rec.ambient_starts()
	assert_true(bleeps.size() > 20, "expected plenty of bleeps, got %d" % bleeps.size())
	var right := 0
	var left := 0
	for b in bleeps:
		if b.right > b.left:
			right += 1
		else:
			left += 1
	assert_true(left > 3 and right > 3, "both cabinets should be heard (%d left, %d right)" % [left, right])
	for b in bleeps:
		assert_true(minf(b.left, b.right) < maxf(b.left, b.right) * 0.2, "each bleep is hard to one side")


func test_only_cabinets_within_earshot_bleep() -> void:
	var a := ambience()
	a.set_sources(PackedFloat32Array([300.0]), PackedFloat32Array([500.0 + Spatial.MAX_DISTANCE + 100.0]), PackedInt32Array([Attract.GENERIC]), 1)
	var rec := Recorder.new()
	run(a, rec, 20.0)
	for s in rec.started:
		assert_false(s.priority == AudioPriority.AMBIENT and s.sfx != Sfx.STEAM and s.sfx != Sfx.CLINK, "a far cabinet stays silent")
	# Walk up to it and it speaks.
	run(a, rec, 20.0, 300.0, 500.0 + Spatial.MAX_DISTANCE + 60.0)
	assert_false(rec.started.is_empty())


func test_each_machine_bleeps_in_its_own_voice() -> void:
	var a := ambience()
	var kinds := PackedInt32Array([Attract.kind_for("racer"), Attract.kind_for("claw")])
	a.set_sources(PackedFloat32Array([330.0, 270.0]), PackedFloat32Array([480.0, 480.0]), kinds, 2)
	var rec := Recorder.new()
	run(a, rec, 60.0)
	var racer := Attract.palette(kinds[0]).sfx
	var claw := Attract.palette(kinds[1]).sfx
	var heard_racer := false
	var heard_claw := false
	var stray := false
	for s in rec.ambient_starts():
		if racer.has(s.sfx):
			heard_racer = true
		if claw.has(s.sfx):
			heard_claw = true
		if not racer.has(s.sfx) and not claw.has(s.sfx):
			stray = true
	assert_true(heard_racer, "racer sounds heard")
	assert_true(heard_claw, "claw sounds heard")
	assert_false(stray, "nothing outside the two palettes")


func test_the_cafe_hisses_and_clinks_from_its_counter_only_when_you_are_near_it() -> void:
	var a := ambience()
	a.set_cafe(100.0, 900.0)
	# One cabinet miles away, so the only sounds nearby are the cafe's.
	a.set_sources(PackedFloat32Array([5000.0]), PackedFloat32Array([5000.0]), PackedInt32Array([0]), 1)
	var near := Recorder.new()
	# Standing close by, with the cafe to the west (left, facing north).
	run(a, near, 60.0, 160.0, 900.0)
	var steam := false
	var clink := false
	var all_left := true
	for s in near.started:
		if s.sfx == Sfx.STEAM:
			steam = true
		if s.sfx == Sfx.CLINK:
			clink = true
		if (s.sfx == Sfx.STEAM or s.sfx == Sfx.CLINK) and not (s.left > s.right):
			all_left = false
	assert_true(steam and clink, "steam and cups near the cafe")
	assert_true(all_left, "from the left")
	var far := Recorder.new()
	var b := ambience()
	b.set_cafe(100.0, 900.0)
	b.set_sources(PackedFloat32Array([5000.0]), PackedFloat32Array([5000.0]), PackedInt32Array([0]), 1)
	run(b, far, 60.0, 500.0, 100.0)
	for s in far.started:
		assert_false(s.sfx == Sfx.STEAM or s.sfx == Sfx.CLINK, "silent from across the hall")


func test_a_busy_hall_murmurs_louder_than_an_empty_one() -> void:
	var quiet := ambience()
	quiet.crowd = 0.0
	var busy := ambience()
	busy.crowd = 1.0
	var q := run(quiet, Recorder.new(), 12.0)
	var b := run(busy, Recorder.new(), 12.0)
	assert_true(b > q * 1.15, "busy %f should beat quiet %f by a good margin" % [b, q])


func test_silent_when_the_target_or_the_volume_is_zero() -> void:
	var a := HallAmbience.new()
	var rec := Recorder.new()
	for i in 50:
		a.tick(0.01, rec, 1.0, 0.0, 0.0, 0.0)
	assert_eq(0.0, a.hum_gain)
	assert_eq(0.0, a.murmur_gain)
	assert_true(rec.started.is_empty())
	a.target = 1.0
	for i in 200:
		a.tick(0.01, rec, 0.0, 0.0, 0.0, 0.0)
	assert_eq(0.0, a.hum_gain)
	assert_eq(0.0, a.murmur_gain)
	assert_true(rec.started.is_empty())


func test_the_ambience_voices_are_capped() -> void:
	var a := ambience()
	a.set_cafe(300.0, 500.0)
	var xs := PackedFloat32Array()
	var zs := PackedFloat32Array()
	for i in 20:
		xs.append(300.0 + i)
		zs.append(500.0)
	var kinds := PackedInt32Array()
	kinds.resize(20)
	a.set_sources(xs, zs, kinds, 20)
	var rec := Recorder.new()
	rec.ambient = 5 # the mixer already has five going
	run(a, rec, 20.0)
	assert_true(rec.started.is_empty(), "no more while at the cap")


func test_before_the_hall_reports_bleeps_still_happen_from_somewhere() -> void:
	var a := ambience()
	var rec := Recorder.new()
	run(a, rec, 20.0)
	var bleeps := rec.ambient_starts()
	assert_true(bleeps.size() > 10)
	var lefty := false
	var righty := false
	for b in bleeps:
		if b.left > b.right:
			lefty = true
		if b.right > b.left:
			righty = true
	assert_true(lefty and righty, "both ears get some")


func test_easing_does_not_depend_on_the_frame_rate() -> void:
	# build-13 eased once per 10 ms block; a frame of any length eases by the same total.
	var a := ambience()
	var b := ambience()
	var rec := Recorder.new()
	for i in 60:
		a.tick(1.0 / 60.0, rec, 1.0, 0.0, 0.0, 0.0)
	for i in 100:
		b.tick(0.01, rec, 1.0, 0.0, 0.0, 0.0)
	assert_near(b.ambient, a.ambient, 1e-6)
	assert_near(b.crowd_now, a.crowd_now, 1e-6)
