class_name Lighting
extends RefCounted
## engine/r3d/Lighting.kt: ambient + one directional light + point lights, evaluated with a
## wrapped Lambert term so walls and floors both pick up nearby glow. 1.0 = texture colour.
## The GPU does this per pixel (shaders/scene.gdshader); [method shade] is the same sum on the CPU.

var amb_r := 0.4
var amb_g := 0.4
var amb_b := 0.45
var dir_x := 0.0
var dir_y := 1.0
var dir_z := 0.0
var dir_r := 0.0
var dir_g := 0.0
var dir_b := 0.0
var points: Array[PointLight] = []


func set_direction(x: float, y: float, z: float) -> void:
	var l := maxf(sqrt(x * x + y * y + z * z), 1e-5)
	dir_x = x / l
	dir_y = y / l
	dir_z = z / l


## The light reaching a point with normal (nx, ny, nz), as an RGB multiplier (capped at 2.2).
func shade(x: float, y: float, z: float, nx: float, ny: float, nz: float) -> Vector3:
	var r := amb_r
	var g := amb_g
	var b := amb_b
	var d := nx * dir_x + ny * dir_y + nz * dir_z
	if d > 0.0:
		r += dir_r * d
		g += dir_g * d
		b += dir_b * d
	for p in points:
		var dx := p.x - x
		var dy := p.y - y
		var dz := p.z - z
		var d2 := dx * dx + dy * dy + dz * dz
		var rad := p.radius
		if d2 >= rad * rad:
			continue
		var dist := maxf(sqrt(d2), 1e-3)
		var fall := 1.0 - dist / rad
		var lam := maxf((dx * nx + dy * ny + dz * nz) / dist * 0.6 + 0.4, 0.0)
		var k := fall * fall * lam * p.intensity
		r += p.r * k
		g += p.g * k
		b += p.b * k
	return Vector3(minf(r, 2.2), minf(g, 2.2), minf(b, 2.2))
