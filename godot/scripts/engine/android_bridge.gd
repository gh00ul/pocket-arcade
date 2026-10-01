class_name AndroidBridge
extends RefCounted
## The Android plugin (android-plugin/, singleton "PocketArcade"): the platform calls build-13 made
## from MainActivity and its views. Vibration (VibrationEffect), the edge-swipe exclusion zones,
## Back sending the app to the background, the FileProvider share sheet, the launch intent's "play"
## extra, the raw touch stream (every batched sample with its time), the tilt sensor, and the
## soundtrack (build-13's synthesizer, see [MusicControl]). Where the plugin is missing (desktop,
## tests) every call does nothing and returns "nothing".

const SINGLETON := "PocketArcade"

## Touch actions in the plugin's stream.
const TOUCH_DOWN := 0
const TOUCH_MOVE := 1
const TOUCH_UP := 2
const TOUCH_CANCEL := 3
## Ints per touch sample: action, pointer id, x * 16, y * 16, time (ms since capture began).
const TOUCH_STRIDE := 5
const TOUCH_FIXED := 16.0

static var _plugin: Object = null
static var _checked := false
## A stand-in for the plugin (tests); takes precedence over the real one.
static var fake: Object = null


static func plugin() -> Object:
	if fake != null:
		return fake
	if not _checked:
		_checked = true
		if Engine.has_singleton(SINGLETON):
			_plugin = Engine.get_singleton(SINGLETON)
	return _plugin


static func available() -> bool:
	return plugin() != null


# ------------------------------------------------------------------ haptics

## The phone's vibrator as a [Haptics.Device], or null without one.
static func haptics_device() -> Haptics.Device:
	var p := plugin()
	if p == null:
		return null
	var info: PackedInt32Array = p.hapticsInfo()
	if info.size() < 5 or info[0] == 0:
		return null
	var d := PluginVibrator.new()
	d.amplitude_control = info[1] != 0
	d.primitives = info[2] != 0
	d.thud = info[3] != 0
	d.low_tick = info[4] != 0
	return d


class PluginVibrator:
	extends Haptics.Device

	func one_shot(ms: int, amplitude: int) -> void:
		AndroidBridge.plugin().vibrateOneShot(ms, amplitude)

	func waveform(timings: PackedInt32Array, amplitudes: PackedInt32Array) -> void:
		AndroidBridge.plugin().vibrateWaveform(timings, amplitudes)

	func composition(prims: PackedInt32Array, scales: PackedFloat32Array, delays: PackedInt32Array) -> void:
		AndroidBridge.plugin().vibrateComposition(prims, scales, delays)


# ------------------------------------------------------------------ the window and the app

## Keeps the system's Back swipe away from these rectangles (window pixels, x0 y0 x1 y1 each); an
## empty array clears them. Android honours about 200 dp of height per edge, from API 29.
static func set_gesture_exclusion(rects: PackedInt32Array) -> void:
	var p := plugin()
	if p != null:
		p.setGestureExclusion(rects)


## Sends the app to the background (Back at the title or in the hall, as Android's default did).
## Returns false where there is no plugin.
static func move_task_to_back() -> bool:
	var p := plugin()
	if p == null:
		return false
	p.moveTaskToBack()
	return true


## Opens the share sheet for the PNG at [param path] (inside user://photos); false if it couldn't.
static func share_png(path: String, title: String) -> bool:
	var p := plugin()
	if p == null:
		return false
	return p.sharePng(ProjectSettings.globalize_path(path), title)


## The machine id the app was launched (or brought back) to play, once; "" when there is none.
static func take_launch_game() -> String:
	var p := plugin()
	return "" if p == null else String(p.takeLaunchGame())


# ------------------------------------------------------------------ touch

## Starts or stops recording the raw touch stream (see [TouchInput]).
static func touch_capture(on: bool) -> void:
	var p := plugin()
	if p != null:
		p.touchCapture(on)


## While on, the first finger's touch stream is delivered as it arrives, not batched to the frame.
static func touch_unbuffered(on: bool) -> void:
	var p := plugin()
	if p != null:
		p.touchUnbuffered(on)


## Everything recorded since the last call: the plugin's clock first, then TOUCH_STRIDE ints a sample.
static func touch_take() -> PackedInt32Array:
	var p := plugin()
	return PackedInt32Array() if p == null else p.touchTake()


# ------------------------------------------------------------------ tilt

## Starts the tilt sensor: 1 for the game rotation vector, 2 for the accelerometer, 0 for none.
static func tilt_start() -> int:
	var p := plugin()
	return 0 if p == null else int(p.tiltStart())


static func tilt_stop() -> void:
	var p := plugin()
	if p != null:
		p.tiltStop()


## The readings since the last call, four floats each: kind, x, y, z (see [TiltSteer]).
static func tilt_take() -> PackedFloat32Array:
	var p := plugin()
	return PackedFloat32Array() if p == null else p.tiltTake()
