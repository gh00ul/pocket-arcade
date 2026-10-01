class_name Xform
extends RefCounted
## engine/r3d/Model.kt Xform: a rigid placement for drawing a [Model]: uniform scale, then
## rotation (roll about z, then pitch about x, then yaw about y), then translation. Placements
## chain with [method set_product] for jointed parts.

# Row-major 3x3: x' = m0 x + m1 y + m2 z + tx.
var m0 := 1.0
var m1 := 0.0
var m2 := 0.0
var m3 := 0.0
var m4 := 1.0
var m5 := 0.0
var m6 := 0.0
var m7 := 0.0
var m8 := 1.0
var tx := 0.0
var ty := 0.0
var tz := 0.0


func set_xf(x: float = 0.0, y: float = 0.0, z: float = 0.0, yaw: float = 0.0, pitch: float = 0.0, roll: float = 0.0, scale: float = 1.0) -> Xform:
	var cy := cos(yaw)
	var sy := sin(yaw)
	var cp := cos(pitch)
	var sp := sin(pitch)
	var cr := cos(roll)
	var sr := sin(roll)
	# Ry · Rx · Rz, times the scale.
	m0 = (cy * cr + sy * sp * sr) * scale
	m1 = (-cy * sr + sy * sp * cr) * scale
	m2 = (sy * cp) * scale
	m3 = (cp * sr) * scale
	m4 = (cp * cr) * scale
	m5 = (-sp) * scale
	m6 = (-sy * cr + cy * sp * sr) * scale
	m7 = (sy * sr + cy * sp * cr) * scale
	m8 = (cy * cp) * scale
	tx = x
	ty = y
	tz = z
	return self


## Stretches the placed thing along its own axes (squash and stretch).
func stretch(sx: float, sy: float, sz: float) -> Xform:
	m0 *= sx
	m3 *= sx
	m6 *= sx
	m1 *= sy
	m4 *= sy
	m7 *= sy
	m2 *= sz
	m5 *= sz
	m8 *= sz
	return self


## Makes this [param a] ∘ [param b]: b's placement, then a's (b is a part mounted on a).
func set_product(a: Xform, b: Xform) -> Xform:
	var r0 := a.m0 * b.m0 + a.m1 * b.m3 + a.m2 * b.m6
	var r1 := a.m0 * b.m1 + a.m1 * b.m4 + a.m2 * b.m7
	var r2 := a.m0 * b.m2 + a.m1 * b.m5 + a.m2 * b.m8
	var r3 := a.m3 * b.m0 + a.m4 * b.m3 + a.m5 * b.m6
	var r4 := a.m3 * b.m1 + a.m4 * b.m4 + a.m5 * b.m7
	var r5 := a.m3 * b.m2 + a.m4 * b.m5 + a.m5 * b.m8
	var r6 := a.m6 * b.m0 + a.m7 * b.m3 + a.m8 * b.m6
	var r7 := a.m6 * b.m1 + a.m7 * b.m4 + a.m8 * b.m7
	var r8 := a.m6 * b.m2 + a.m7 * b.m5 + a.m8 * b.m8
	var ntx := a.x(b.tx, b.ty, b.tz)
	var nty := a.y(b.tx, b.ty, b.tz)
	var ntz := a.z(b.tx, b.ty, b.tz)
	m0 = r0
	m1 = r1
	m2 = r2
	m3 = r3
	m4 = r4
	m5 = r5
	m6 = r6
	m7 = r7
	m8 = r8
	tx = ntx
	ty = nty
	tz = ntz
	return self


func copy_from(o: Xform) -> Xform:
	m0 = o.m0
	m1 = o.m1
	m2 = o.m2
	m3 = o.m3
	m4 = o.m4
	m5 = o.m5
	m6 = o.m6
	m7 = o.m7
	m8 = o.m8
	tx = o.tx
	ty = o.ty
	tz = o.tz
	return self


func x(px: float, py: float, pz: float) -> float:
	return m0 * px + m1 * py + m2 * pz + tx


func y(px: float, py: float, pz: float) -> float:
	return m3 * px + m4 * py + m5 * pz + ty


func z(px: float, py: float, pz: float) -> float:
	return m6 * px + m7 * py + m8 * pz + tz


## Rotates a direction (normals); the scale doesn't matter once normalised.
func dir_x(px: float, py: float, pz: float) -> float:
	return m0 * px + m1 * py + m2 * pz


func dir_y(px: float, py: float, pz: float) -> float:
	return m3 * px + m4 * py + m5 * pz


func dir_z(px: float, py: float, pz: float) -> float:
	return m6 * px + m7 * py + m8 * pz


## Transforms a surface normal (unnormalised) by the cofactor matrix, kept outward when mirroring.
func normal_x(px: float, py: float, pz: float) -> float:
	return ((m4 * m8 - m5 * m7) * px + (m5 * m6 - m3 * m8) * py + (m3 * m7 - m4 * m6) * pz) * _det_sign()


func normal_y(px: float, py: float, pz: float) -> float:
	return ((m2 * m7 - m1 * m8) * px + (m0 * m8 - m2 * m6) * py + (m1 * m6 - m0 * m7) * pz) * _det_sign()


func normal_z(px: float, py: float, pz: float) -> float:
	return ((m1 * m5 - m2 * m4) * px + (m2 * m3 - m0 * m5) * py + (m0 * m4 - m1 * m3) * pz) * _det_sign()


## The largest factor the placement scales any direction by (for bounding spheres).
func max_scale() -> float:
	var a := m0 * m0 + m3 * m3 + m6 * m6
	var b := m1 * m1 + m4 * m4 + m7 * m7
	var c := m2 * m2 + m5 * m5 + m8 * m8
	return sqrt(maxf(a, maxf(b, c)))


func _det_sign() -> float:
	var det := m0 * (m4 * m8 - m5 * m7) - m1 * (m3 * m8 - m5 * m6) + m2 * (m3 * m7 - m4 * m6)
	return -1.0 if det < 0.0 else 1.0


## The placement as a Godot transform.
func to_transform() -> Transform3D:
	return Transform3D(Basis(Vector3(m0, m3, m6), Vector3(m1, m4, m7), Vector3(m2, m5, m8)), Vector3(tx, ty, tz))


## Appends the placement in MultiMesh buffer order (3×4 row-major) to [param out].
func append_to(out: PackedFloat32Array) -> void:
	out.append(m0)
	out.append(m1)
	out.append(m2)
	out.append(tx)
	out.append(m3)
	out.append(m4)
	out.append(m5)
	out.append(ty)
	out.append(m6)
	out.append(m7)
	out.append(m8)
	out.append(tz)
