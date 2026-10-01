class_name TiltSteer
extends RefCounted
## engine/TiltSteer.kt: reads the phone's tilt for a game that can be steered by tilting (see
## [method TiltMath.lean]). The Android plugin listens to the game rotation vector (a gyro-fused
## reading that needs no compass) where there is one, else the accelerometer, and hands over every
## reading since the last frame; the accelerometer is smoothed here exactly as build-13 smoothed it.
## Call [method start] when the round is on screen and the option is on, [method stop] when it isn't
## (a sensor left listening drains the battery), and [method poll] once a frame.

## Share of each accelerometer reading blended in (about 0.13 s of smoothing at 50 Hz).
const ACCEL_SMOOTH := 0.15

## Sensor reading kinds in the plugin's stream.
const KIND_ROTATION := 1
const KIND_ACCEL := 2

var _game: Object = null
var _fused := false
var _ax := 0.0
var _ay := 0.0
var _az := 0.0
var _primed := false


## Starts listening for [param game] (which has on_tilt(lean)); false if the phone has no suitable
## sensor (the game then keeps its touch steering).
func start(game: Object) -> bool:
	stop()
	var kind := AndroidBridge.tilt_start()
	if kind == 0:
		return false
	_fused = kind == KIND_ROTATION
	_primed = false
	_game = game
	return true


func stop() -> void:
	if _game != null:
		AndroidBridge.tilt_stop()
	_game = null


func listening() -> bool:
	return _game != null


## Hands the game every reading that arrived since the last call.
func poll() -> void:
	if _game == null:
		return
	var r := AndroidBridge.tilt_take()
	var i := 0
	while i + 3 < r.size():
		reading(int(r[i]), r[i + 1], r[i + 2], r[i + 3])
		i += 4


## One sensor reading: for the rotation vector, "up" in the device's own axes (the rotation
## matrix's third row); for the accelerometer, its raw values.
func reading(kind: int, x: float, y: float, z: float) -> void:
	if _game == null:
		return
	if kind == KIND_ROTATION:
		_game.on_tilt(TiltMath.lean(x, y, z))
		return
	var g := sqrt(x * x + y * y + z * z)
	if g < 1.0:
		return
	# Smooth the accelerometer: a knock or the engine's rumble is no steering.
	var k := ACCEL_SMOOTH if _primed else 1.0
	_primed = true
	_ax += (x / g - _ax) * k
	_ay += (y / g - _ay) * k
	_az += (z / g - _az) * k
	_game.on_tilt(TiltMath.lean(_ax, _ay, _az))
