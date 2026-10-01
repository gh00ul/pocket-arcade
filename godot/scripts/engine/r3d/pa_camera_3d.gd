class_name PaCamera3D
extends RefCounted
## engine/r3d/Camera3D.kt: a pinhole camera. World axes: x right, y up, z toward the viewer
## ("south"); the same handedness as Godot's, so the renderer uses world coordinates as they are.
## View space: vx right, vy up, vz depth in front of the eye. All projection maths here is plain
## arithmetic in image pixels (a game's field units), usable in headless tests.

var ex := 0.0
var ey := 0.0
var ez := 0.0
var rx := 1.0
var ry := 0.0
var rz := 0.0
var ux := 0.0
var uy := 1.0
var uz := 0.0
var fx := 0.0
var fy := 0.0
var fz := -1.0
var focal := 1.0
var cx := 0.0
var cy := 0.0
## Size of the camera image in pixels, as last given to [method look_at].
var image_w := 1
var image_h := 1
## Near clipping distance: closer points don't project and the GPU clips there.
var near := 8.0


## Aims the camera from the eye at a target with a vertical field of view in radians.
## [param center_y_frac] shifts the lens: the target lands that far down the image.
func look_at(eye_x: float, eye_y: float, eye_z: float, tx: float, ty: float, tz: float, fov_y: float,
		width: int, height: int, center_y_frac: float = 0.5) -> void:
	ex = eye_x
	ey = eye_y
	ez = eye_z
	var dx := tx - eye_x
	var dy := ty - eye_y
	var dz := tz - eye_z
	var dl := maxf(sqrt(dx * dx + dy * dy + dz * dz), 1e-5)
	dx /= dl
	dy /= dl
	dz /= dl
	fx = dx
	fy = dy
	fz = dz
	# right = forward × worldUp(0, 1, 0)
	var ax := -fz
	var az := fx
	var al := maxf(sqrt(ax * ax + az * az), 1e-5)
	ax /= al
	az /= al
	rx = ax
	ry = 0.0
	rz = az
	# up = right × forward
	ux = ry * fz - rz * fy
	uy = rz * fx - rx * fz
	uz = rx * fy - ry * fx
	focal = (height / 2.0) / tan(fov_y / 2.0)
	cx = width / 2.0
	cy = height * center_y_frac
	image_w = maxi(width, 1)
	image_h = maxi(height, 1)


func view_x(x: float, y: float, z: float) -> float:
	return (x - ex) * rx + (y - ey) * ry + (z - ez) * rz


func view_y(x: float, y: float, z: float) -> float:
	return (x - ex) * ux + (y - ey) * uy + (z - ez) * uz


func view_z(x: float, y: float, z: float) -> float:
	return (x - ex) * fx + (y - ey) * fy + (z - ez) * fz


## Projects a world point to image pixels (x, y, depth); null if it is behind the near plane.
## Kotlin's `project(x, y, z, out)` returning false is a null here.
func project(x: float, y: float, z: float) -> Variant:
	var vz := view_z(x, y, z)
	if vz < near:
		return null
	return Vector3(cx + view_x(x, y, z) / vz * focal, cy - view_y(x, y, z) / vz * focal, vz)


## Casts a ray through image pixel (sx, sy) and meets the plane y = [param plane_y]: the hit's
## (x, z), or null if the ray misses.
func ray_to_plane_y(sx: float, sy: float, plane_y: float) -> Variant:
	var px := (sx - cx) / focal
	var py := -(sy - cy) / focal
	var dx := fx + rx * px + ux * py
	var dy := fy + ry * px + uy * py
	var dz := fz + rz * px + uz * py
	if dy > -1e-5 and dy < 1e-5:
		return null
	var t := (plane_y - ey) / dy
	if t <= 0.0:
		return null
	return Vector2(ex + dx * t, ez + dz * t)


## The camera as a Godot transform (basis x = right, y = up, z = backwards).
func to_transform() -> Transform3D:
	return Transform3D(Basis(Vector3(rx, ry, rz), Vector3(ux, uy, uz), Vector3(-fx, -fy, -fz)), Vector3(ex, ey, ez))


## Godot frustum parameters reproducing this pinhole: [size, offset, near] for
## Camera3D.set_frustum with KEEP_HEIGHT (the near plane's height, its centre shift).
func frustum(near_plane: float) -> Array:
	var size := near_plane * image_h / focal
	var off := Vector2(near_plane * (cx - image_w / 2.0) / focal, near_plane * (cy - image_h / 2.0) / focal)
	return [size, off, near_plane]
