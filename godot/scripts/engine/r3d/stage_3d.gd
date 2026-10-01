class_name Stage3D
extends RefCounted
## engine/r3d/Stage3D.kt: a mini-game's 3D view. The game aims the camera with [method look]; each
## frame [method begin] starts a picture covering the whole field and [method present] hands it
## to the GPU. It also maps between field units (touches, particles, popups) and the 3D world.
## Projection and touch helpers are plain maths, usable in headless tests.

## At a full punch the lens tightens by this fraction of the field of view...
const PUNCH_FOV := 0.05
## ...and the eye moves this fraction of the way to the target: together about 8% bigger.
const PUNCH_DOLLY := 0.03

var field_w: int
var field_h: int
## Follows the frame-rate cap.
var r: Renderer3D
## The camera in field units; begin() copies it to the renderer.
var cam := PaCamera3D.new()

var _eye_x := 0.0
var _eye_y := 0.0
var _eye_z := 1.0
var _tgt_x := 0.0
var _tgt_y := 0.0
var _tgt_z := 0.0
var _fov := 1.0
var _center_y := 0.5
var _punch_spring := PunchSpring.new()
var _punched_cam := false
var _last_begin_usec := 0
## Where the frame clock comes from (microseconds); tests swap it.
var usec_clock: Callable = func() -> int: return Time.get_ticks_usec()


func _init(p_field_w: int, p_field_h: int) -> void:
	field_w = p_field_w
	field_h = p_field_h
	r = Renderer3D.new(field_w, field_h)
	r.frame_capped = true


## Kicks the camera: a quick push-in that springs back; off with reduce motion.
func punch(amount: float) -> void:
	_punch_spring.kick(amount)


## How far the punch is out right now.
func punch_value() -> float:
	return _punch_spring.value


func look(eye_x: float, eye_y: float, eye_z: float, tx: float, ty: float, tz: float, fov_deg: float, center_y_frac: float = 0.5) -> void:
	_eye_x = eye_x
	_eye_y = eye_y
	_eye_z = eye_z
	_tgt_x = tx
	_tgt_y = ty
	_tgt_z = tz
	_fov = deg_to_rad(fov_deg)
	_center_y = center_y_frac
	cam.look_at(eye_x, eye_y, eye_z, tx, ty, tz, _fov, field_w, field_h, center_y_frac)


## Projects a world point to field units (x, y, depth); null if behind the eye.
func to_field(x: float, y: float, z: float) -> Variant:
	return cam.project(x, y, z)


## How many field units one world unit spans at that point's depth.
func scale_at(x: float, y: float, z: float) -> float:
	return cam.focal / maxf(cam.view_z(x, y, z), cam.near)


## Casts a touch at field (fx, fy) onto the plane y = [param plane_y]: the hit's (x, z), or null.
func touch_to_plane(fx: float, fy: float, plane_y: float) -> Variant:
	return cam.ray_to_plane_y(fx, fy, plane_y)


## Starts a frame with the camera set (and any punch applied). Draw into the returned renderer.
func begin() -> Renderer3D:
	r.start_frame()
	r.resize(field_w, field_h)
	r.camera.near = cam.near
	var requested := GameViewport.take_punch()
	if requested > 0.0:
		_punch_spring.kick(requested)
	# The punch runs on the wall clock, so it plays out the same whether the game is frozen,
	# slowed, paused or drawing at 30 or 120 fps.
	var now: int = usec_clock.call()
	var dt := 0.0 if _last_begin_usec == 0 else clampf((now - _last_begin_usec) / 1e6, 0.0, 0.05)
	_last_begin_usec = now
	_punch_spring.update(dt)
	var p := _punch_spring.value
	if p != 0.0:
		# Push in: a tighter lens and the eye a little nearer the target. The game's own camera
		# follows, so touches and projected popups still land where the picture shows things.
		var k := p * PUNCH_DOLLY
		var ex := _eye_x + (_tgt_x - _eye_x) * k
		var ey := _eye_y + (_tgt_y - _eye_y) * k
		var ez := _eye_z + (_tgt_z - _eye_z) * k
		var f := _fov * (1.0 - p * PUNCH_FOV)
		r.camera.look_at(ex, ey, ez, _tgt_x, _tgt_y, _tgt_z, f, field_w, field_h, _center_y)
		cam.look_at(ex, ey, ez, _tgt_x, _tgt_y, _tgt_z, f, field_w, field_h, _center_y)
		_punched_cam = true
	else:
		r.camera.look_at(_eye_x, _eye_y, _eye_z, _tgt_x, _tgt_y, _tgt_z, _fov, field_w, field_h, _center_y)
		if _punched_cam:
			cam.look_at(_eye_x, _eye_y, _eye_z, _tgt_x, _tgt_y, _tgt_z, _fov, field_w, field_h, _center_y)
			_punched_cam = false
	return r


## Hands the frame to the GPU, placed over the field wherever the host has put it.
func present() -> void:
	var v := GameViewport
	var x := v.x + v.shake_x * v.scale
	var y := v.y + v.shake_y * v.scale
	var rp := r.finish_frame(x, y, field_w * v.scale, field_h * v.scale, v.clip_x0, v.clip_y0, v.clip_x1, v.clip_y1, true)
	var source := v.particles
	if source != null and GfxQuality.gl_particles:
		# A skipped frame keeps the last picture on screen, with its particles in it.
		if not rp.skipped:
			rp.particles = source
			rp.particle_field = Vector2(field_w, field_h)
		v.particles_in_gl = true
	Gfx.submit(GameViewport.SLOT, rp)
