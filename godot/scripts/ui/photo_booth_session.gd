class_name PhotoBoothSession
extends RefCounted
## ui/PhotoBoothScreen.kt's booth without the drawing (as [GameRound] is the game host's round):
## the phase (ready, shooting, done), one go on the frame clock (the 3-2-1 countdown's ticks, a pop
## and a tick of the phone at each shot), the strip composed when the go ends and printed, then
## saved off the main thread (the newest four kept), the "photos" stat, the photo wall, SHARE,
## RETAKE. [PhotoBoothScreen] draws it.
##
## Saving outlives the screen, as build-13's `services.persist { }` did: a strip still being written
## when the booth closes is still saved, counted and hung on the wall.

enum Phase { READY, SHOOTING, DONE }

## The share sheet's title (share/PhotoShare.kt's chooser).
const SHARE_TITLE := "SHARE YOUR PHOTO STRIP"
const NO_SAVE := "COULDN'T SAVE THE STRIP"
const NO_SHARE := "NO APP TO SHARE WITH"
## The lifetime counter each strip adds to (the profile's PHOTOS, the hall's wall).
const STAT := "photos"
## Frames a strip's pixels may take to arrive from the GPU before the save is given up on (they
## take two; without a GPU they never come).
const PIXELS_TIMEOUT_FRAMES := 120

var phase: int = Phase.READY
## Seconds since the go's first frame.
var elapsed := 0.0
## How many goes have started (Kotlin's `go`).
var go := 0
## Where the go has got to (PhotoBoothPlan.at(elapsed)), reused every frame.
var step: PhotoBoothPlan.Step = PhotoBoothPlan.at(0.0)
## The four poses' pictures (Texture2D), developed up front so a shot is instant when its flash
## comes; null until developed.
var shots: Array = [null, null, null, null]
## The printed strip, shown while it is saved; null until a go ends.
var strip: PaTexture = null
## Where the strip was saved; "" until it is (or when it couldn't be).
var saved := ""
## What went wrong ("" when nothing did).
var message := ""

var audio: AudioSynth
var haptics: Haptics
var repo: ArcadeRepository
## The app's files folder (user://, where build-13's strips are too).
var files_dir := "user://"
## How a strip is composed: (shots, arcade name, date) -> PaTexture (tests swap it).
var compose: Callable = PhotoStripArt.compose
## The time now in ms since 1970 (tests swap it).
var clock: Callable = ArcadeRepository.now_millis
## The arcade's name when there is no repository (tests).
var arcade_name := SaveState.DEFAULT_ARCADE_NAME

var _first_frame := false
var _last_count := 0
var _last_captured := 0

## Strips being saved, kept alive until they are (by any booth, open or closed).
static var _printing: Array = []


func _init(p_audio: AudioSynth = null, p_haptics: Haptics = null, p_repo: ArcadeRepository = null) -> void:
	audio = p_audio
	haptics = p_haptics
	repo = p_repo


func running() -> bool:
	return phase == Phase.SHOOTING


## SNAP (and RETAKE): starts a go. The countdown begins on the next frame.
func snap() -> void:
	go += 1
	phase = Phase.SHOOTING
	strip = null
	saved = ""
	message = ""
	elapsed = 0.0
	_first_frame = true
	_last_count = PhotoBoothPlan.COUNT + 1
	_last_captured = 0
	PhotoBoothPlan.at(0.0, step)


## RETAKE on the printed strip: another go.
func retake() -> void:
	snap()


## One frame of [param dt] seconds: a go runs on the frame clock from its first frame.
func frame(dt: float) -> void:
	if phase != Phase.SHOOTING:
		return
	if _first_frame:
		_first_frame = false
		elapsed = 0.0
	else:
		elapsed += dt
	var s := PhotoBoothPlan.at(elapsed, step)
	if s.count >= 1 and s.count < _last_count:
		_play(Sfx.COUNTDOWN, 0.6, 0.9 + 0.1 * (PhotoBoothPlan.COUNT - s.count))
	if s.captured > _last_captured:
		_play(Sfx.POP, 0.8, 1.5)
		if haptics != null:
			haptics.tick()
	_last_count = s.count if s.count > 0 else _last_count
	_last_captured = s.captured
	if s.done:
		_print()


## The go is over: the strip is composed under the arcade's name and today's date, shown, and
## saved.
func _print() -> void:
	var now: int = clock.call()
	var name := repo.state().arcade_name if repo != null else arcade_name
	strip = compose.call(shots, name, PhotoStrip.date_label(now))
	phase = Phase.DONE
	_play(Sfx.PRINT, 0.6, 1.0)
	var job := PrintJob.new()
	job.session = weakref(self)
	job.go = go
	job.strip = strip
	job.now = now
	job.files_dir = files_dir
	job.repo = repo
	job.start()


## SHARE: the share sheet for the saved strip.
func share() -> void:
	if saved != "" and not AndroidBridge.share_png(saved, SHARE_TITLE):
		message = NO_SHARE


## The newest strip's PNG has been written ([param path]; "" if it couldn't be), for go [param g].
func _on_saved(g: int, path: String) -> void:
	# A strip from an earlier go (RETAKE pressed while it was still being written) doesn't
	# speak for the one now on show.
	if g != go:
		return
	saved = path
	if path == "":
		message = NO_SAVE


func _play(sfx: int, volume: float, pitch: float) -> void:
	if audio != null:
		audio.play(sfx, volume, pitch)


## Strips still being saved (tests).
static func printing() -> int:
	return _printing.size()


## Saves one printed strip once its pixels arrive: the PNG is written on a worker thread, then the
## stat is counted, the wall refreshed and the booth (if it is still open) told.
class PrintJob:
	extends RefCounted
	var session: WeakRef
	var go := 0
	var strip: PaTexture
	var now := 0
	var files_dir := ""
	var repo: ArcadeRepository
	var _frames := 0
	var _task := -1
	var _path := ""
	var _tree: SceneTree

	func start() -> void:
		_tree = Engine.get_main_loop() as SceneTree
		PhotoBoothSession._printing.append(self)
		if _tree != null:
			_tree.process_frame.connect(_tick)
		_tick()

	func _tick() -> void:
		_frames += 1
		if _task < 0:
			if strip != null and strip.image != null:
				_task = WorkerThreadPool.add_task(_write.bind(strip.image), false, "Save a photo strip")
			elif strip == null or _frames > PhotoBoothSession.PIXELS_TIMEOUT_FRAMES:
				_finish("")
			return
		if WorkerThreadPool.is_task_completed(_task):
			WorkerThreadPool.wait_for_task_completion(_task)
			_finish(_path)

	## On the worker: the PNG.
	func _write(img: Image) -> void:
		_path = PhotoStore.save(files_dir, img, now)

	func _finish(path: String) -> void:
		if _tree != null and _tree.process_frame.is_connected(_tick):
			_tree.process_frame.disconnect(_tick)
		PhotoBoothSession._printing.erase(self)
		var s: PhotoBoothSession = session.get_ref() if session != null else null
		if s != null:
			s._on_saved(go, path)
		if repo != null:
			repo.add_stat(PhotoBoothSession.STAT)
		# Hang it on the photo wall by the booth.
		if path != "":
			PhotoBoothSession.refresh_wall(files_dir)


## Repaints the hall's photo wall with the strips now saved (hub/PhotoWall.kt's refresh).
static func refresh_wall(p_files_dir: String) -> void:
	var wall: Variant = _wall_class()
	if wall != null:
		wall.refresh(p_files_dir)


static var _wall: Variant = null
static var _wall_looked := false


## The hall's PhotoWall (the hall's port), found by its class name so the booth runs before it is in.
static func _wall_class() -> Variant:
	if not _wall_looked:
		_wall_looked = true
		for c: Dictionary in ProjectSettings.get_global_class_list():
			if c.get("class", "") == "PhotoWall":
				_wall = load(c["path"])
	return _wall
