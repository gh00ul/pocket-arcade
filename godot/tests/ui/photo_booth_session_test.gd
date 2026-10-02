extends PaTest
## Godot-only (build-13 tested only PhotoBoothPlan): the booth's go as ui/PhotoBoothScreen.kt runs
## it: the countdown's ticks and the shots' pops and haptic ticks on the frame clock, the strip
## printed when the go ends, saved under its time (the newest four kept), the "photos" stat and the
## share sheet, and a save that outlives the screen.

const DT := 1.0 / 60.0
const NOW := 1_790_000_000_000

var fixture: RepoFixture
var audio: FakeAudio
var haptics: FakeHaptics
var files_dir := ""
var composed: Array = []


class FakeAudio:
	extends AudioSynth
	var plays: Array = []

	func play(sfx: int, volume: float = 1.0, pitch: float = 1.0) -> void:
		plays.append([sfx, volume, pitch])

	func of(sfx: int) -> Array:
		var out: Array = []
		for p: Array in plays:
			if p[0] == sfx:
				out.append(p)
		return out


class FakeHaptics:
	extends Haptics
	var ticks := 0

	func tick() -> void:
		ticks += 1


class FakePlugin:
	extends RefCounted
	var calls: Array = []
	var answer := true

	func sharePng(path: String, title: String) -> bool:
		calls.append([path, title])
		return answer


func before_each() -> void:
	fixture = RepoFixture.new()
	files_dir = fixture.dir.path_join("files")
	audio = FakeAudio.new()
	host.add_child(audio)
	haptics = FakeHaptics.new()
	composed = []


func after_each() -> void:
	AndroidBridge.fake = null
	fixture.cleanup()


## A booth whose strips come out at once as a small picture (the GPU's part is the strip art's).
func session(develops: bool = true) -> PhotoBoothSession:
	var s := PhotoBoothSession.new(audio, haptics, fixture.repo())
	s.files_dir = files_dir
	s.clock = func() -> int: return NOW
	s.compose = func(shots: Array, arcade_name: String, date: String) -> PaTexture:
		composed.append([shots, arcade_name, date])
		var t := PaTexture.new(8, 20)
		if develops:
			var img := Image.create_empty(8, 20, false, Image.FORMAT_RGBA8)
			img.fill(Color(0.3, 0.2, 0.6, 1.0))
			t.image = img
		return t
	return s


## Runs [param seconds] of 60 Hz frames.
func run(s: PhotoBoothSession, seconds: float) -> void:
	for i in roundi(seconds / DT):
		s.frame(DT)


## Lets the strips being saved finish (they wait for frames and a worker thread).
func settle() -> void:
	for i in 5000:
		if PhotoBoothSession.printing() == 0:
			return
		await frames(1)
	fail("the strips were never saved")


func test_snap_counts_down_takes_four_shots_and_prints() -> void:
	var s := session()
	assert_eq(PhotoBoothSession.Phase.READY, s.phase)
	s.frame(DT)
	assert_eq(0, audio.plays.size(), "nothing happens before SNAP")
	s.snap()
	assert_true(s.running())
	run(s, PhotoBoothPlan.duration - 0.05)
	assert_eq(PhotoBoothSession.Phase.SHOOTING, s.phase)
	# 3, 2, 1: rising ticks.
	var ticks := audio.of(Sfx.COUNTDOWN)
	assert_eq(3, ticks.size())
	if ticks.size() == 3:
		for i in 3:
			assert_near(0.6, ticks[i][1], 1e-6)
			assert_near(0.9 + 0.1 * i, ticks[i][2], 1e-6)
	# A pop and a tick of the phone at each shot.
	var pops := audio.of(Sfx.POP)
	assert_eq(4, pops.size())
	for p: Array in pops:
		assert_near(0.8, p[1], 1e-6)
		assert_near(1.5, p[2], 1e-6)
	assert_eq(4, haptics.ticks)
	assert_eq(0, audio.of(Sfx.PRINT).size())
	run(s, 0.1)
	assert_eq(PhotoBoothSession.Phase.DONE, s.phase)
	assert_eq(1, audio.of(Sfx.PRINT).size())
	assert_near(0.6, audio.of(Sfx.PRINT)[0][1], 1e-6)
	assert_not_null(s.strip)
	# Composed once, from the four shots, under the arcade's name and the day's date.
	assert_eq(1, composed.size())
	assert_eq(SaveState.DEFAULT_ARCADE_NAME, composed[0][1])
	assert_eq(PhotoStrip.date_label(NOW), composed[0][2])
	assert_eq(4, (composed[0][0] as Array).size())
	# Frames after the go change nothing.
	run(s, 1.0)
	assert_eq(1, audio.of(Sfx.PRINT).size())
	await settle()


func test_the_strip_is_saved_under_its_time_and_counted() -> void:
	var s := session()
	s.snap()
	run(s, PhotoBoothPlan.duration + 0.1)
	assert_eq("", s.saved)
	await settle()
	var path := PhotoStore.dir(files_dir).path_join(PhotoStrip.file_name(NOW))
	assert_eq(path, s.saved)
	assert_eq("", s.message)
	assert_true(FileAccess.file_exists(path))
	assert_eq(1, s.repo.state().stat(PhotoBoothSession.STAT))


func test_the_go_runs_on_the_frame_clock_from_its_first_frame() -> void:
	var s := session()
	s.snap()
	s.frame(5.0)
	assert_near(0.0, s.elapsed, 0.0, "the first frame is the go's start, however late it comes")
	assert_eq(3, s.step.count)
	s.frame(PhotoBoothPlan.TICK)
	assert_eq(2, s.step.count)
	# A long frame over several shots pops once (the frame sees one change).
	s.frame(PhotoBoothPlan.shot_time(2) - PhotoBoothPlan.TICK + 0.01)
	assert_eq(3, s.step.captured)
	assert_eq(1, audio.of(Sfx.POP).size())
	assert_eq(1, haptics.ticks)


func test_a_strip_that_never_develops_is_reported_and_still_counted() -> void:
	var s := session(false)
	s.snap()
	run(s, PhotoBoothPlan.duration + 0.1)
	for i in PhotoBoothSession.PIXELS_TIMEOUT_FRAMES + 5:
		await frames(1)
	assert_eq(0, PhotoBoothSession.printing())
	assert_eq("", s.saved)
	assert_eq(PhotoBoothSession.NO_SAVE, s.message)
	assert_eq(1, s.repo.state().stat(PhotoBoothSession.STAT))
	assert_false(DirAccess.dir_exists_absolute(PhotoStore.dir(files_dir)) and DirAccess.get_files_at(PhotoStore.dir(files_dir)).size() > 0)


func test_retake_starts_afresh_and_an_older_save_does_not_speak_for_it() -> void:
	var s := session()
	s.snap()
	run(s, PhotoBoothPlan.duration + 0.1)
	assert_eq(PhotoBoothSession.Phase.DONE, s.phase)
	# RETAKE before the first strip is written.
	s.retake()
	assert_eq(2, s.go)
	assert_eq(PhotoBoothSession.Phase.SHOOTING, s.phase)
	assert_null(s.strip)
	assert_eq(0, s.step.captured)
	await settle()
	assert_eq("", s.saved, "the first go's strip is saved, but it isn't this go's")
	assert_eq(1, s.repo.state().stat(PhotoBoothSession.STAT))
	run(s, PhotoBoothPlan.duration + 0.1)
	await settle()
	assert_ne("", s.saved)
	assert_eq(2, s.repo.state().stat(PhotoBoothSession.STAT))


func test_a_strip_is_still_saved_after_the_booth_closes() -> void:
	var s := session()
	var repo := s.repo
	s.snap()
	run(s, PhotoBoothPlan.duration + 0.1)
	s = null
	await settle()
	assert_eq(0, PhotoBoothSession.printing())
	assert_eq(1, repo.state().stat(PhotoBoothSession.STAT))
	assert_true(FileAccess.file_exists(PhotoStore.dir(files_dir).path_join(PhotoStrip.file_name(NOW))))


func test_share_hands_the_saved_strip_to_the_share_sheet() -> void:
	var s := session()
	# Nothing saved yet: SHARE does nothing (its button is off).
	var plugin := FakePlugin.new()
	AndroidBridge.fake = plugin
	s.share()
	assert_eq(0, plugin.calls.size())
	s.snap()
	run(s, PhotoBoothPlan.duration + 0.1)
	await settle()
	s.share()
	assert_eq(1, plugin.calls.size())
	if plugin.calls.size() == 1:
		assert_eq(ProjectSettings.globalize_path(s.saved), plugin.calls[0][0])
		assert_eq("SHARE YOUR PHOTO STRIP", plugin.calls[0][1])
	assert_eq("", s.message)
	# No app to share with.
	plugin.answer = false
	s.share()
	assert_eq(PhotoBoothSession.NO_SHARE, s.message)
	# Without the plugin (a desktop run) the same.
	AndroidBridge.fake = null
	s.message = ""
	s.share()
	if not AndroidBridge.available():
		assert_eq(PhotoBoothSession.NO_SHARE, s.message)


func test_only_the_newest_four_strips_are_kept() -> void:
	var t := NOW
	for k in 6:
		var s := session()
		var when := t + k * 1000
		s.clock = func() -> int: return when
		s.snap()
		run(s, PhotoBoothPlan.duration + 0.1)
		await settle()
	var names := PackedStringArray()
	for p in PhotoStore.list(PhotoStore.dir(files_dir)):
		names.append(p.get_file())
	assert_eq(PackedStringArray([PhotoStrip.file_name(t + 5000), PhotoStrip.file_name(t + 4000), PhotoStrip.file_name(t + 3000), PhotoStrip.file_name(t + 2000)]), names)
