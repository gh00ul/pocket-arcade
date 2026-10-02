class_name RigChecks
extends RefCounted
## hub/RigChecks.kt (test helper): every joint is finite and inside what a body can do.

const DT := 1.0 / 120.0

const JOINT_NAMES: Array[String] = [
	"yaw", "rootY", "sway", "lean", "leanRoll", "twist", "breath", "breathLift", "headYaw", "headPitch", "headRoll",
	"hatPitch", "hatRoll", "hatLift", "tailPitch", "tailRoll", "blink", "item",
	"armPitchL", "armRollL", "legPitchL", "legLiftL", "armPitchR", "armRollR", "legPitchR", "legLiftR",
]
const JOINTS := 26


## Every joint value the renderer reads, in the order of JOINT_NAMES, written into [param out].
static func joints(a: FigureAnim, out: PackedFloat64Array) -> void:
	if out.size() != JOINTS:
		out.resize(JOINTS)
	out[0] = a.yaw
	out[1] = a.root_y
	out[2] = a.sway
	out[3] = a.lean
	out[4] = a.lean_roll
	out[5] = a.twist
	out[6] = a.breath
	out[7] = a.breath_lift
	out[8] = a.head_yaw
	out[9] = a.head_pitch
	out[10] = a.head_roll
	out[11] = a.hat_pitch
	out[12] = a.hat_roll
	out[13] = a.hat_lift
	out[14] = a.tail_pitch
	out[15] = a.tail_roll
	out[16] = a.blink
	out[17] = a.item_amount
	var i := 18
	for s in 2:
		out[i] = a.arm_pitch[s]
		out[i + 1] = a.arm_roll[s]
		out[i + 2] = a.leg_pitch[s]
		out[i + 3] = a.leg_lift[s]
		i += 4


static func _in(v: float, lo: float, hi: float) -> bool:
	return v >= lo and v <= hi


## Records a failure on [param t] if any joint is NaN or outside the range a body could plausibly
## reach (Kotlin's assertSane). Returns whether everything was sane.
static func assert_sane(t: PaTest, a: FigureAnim, tag: String) -> bool:
	var ok := true
	var v := PackedFloat64Array()
	joints(a, v)
	for i in v.size():
		if is_nan(v[i]) or is_inf(v[i]):
			t.fail("%s: %s is not finite" % [tag, JOINT_NAMES[i]])
			ok = false
	for s in 2:
		# Arms: never past straight up or well behind the back; never crossing through the body sideways.
		if not _in(a.arm_pitch[s], -3.1, 1.3):
			t.fail("%s: arm pitch %f" % [tag, a.arm_pitch[s]])
			ok = false
		if not _in(a.arm_roll[s], -0.9, 0.9):
			t.fail("%s: arm roll %f" % [tag, a.arm_roll[s]])
			ok = false
		# Legs: a sitting kid's legs reach horizontal at most; walking legs stay inside a stride.
		if not _in(a.leg_pitch[s], -1.62, 1.0):
			t.fail("%s: leg pitch %f" % [tag, a.leg_pitch[s]])
			ok = false
		if not _in(a.leg_lift[s], -0.01, 2.6):
			t.fail("%s: leg lift %f" % [tag, a.leg_lift[s]])
			ok = false
	var checks := [
		[_in(a.root_y, -6.5, 5.0), "body height %f" % a.root_y],
		[absf(a.sway) < 2.0, "sway %f" % a.sway],
		[_in(a.lean, -0.5, 0.5), "lean %f" % a.lean],
		[absf(a.lean_roll) < 0.3, "bank %f" % a.lean_roll],
		[absf(a.twist) < 0.5, "twist %f" % a.twist],
		[absf(a.head_yaw) < 1.2, "head yaw %f" % a.head_yaw],
		[_in(a.head_pitch, -0.6, 0.7), "head pitch %f" % a.head_pitch],
		[absf(a.head_roll) < 0.4, "head roll %f" % a.head_roll],
		[absf(a.hat_pitch) < 0.3, "hat pitch %f" % a.hat_pitch],
		[absf(a.hat_roll) < 0.3, "hat roll %f" % a.hat_roll],
		[absf(a.hat_lift) < 1.5, "hat lift %f" % a.hat_lift],
		[absf(a.tail_pitch) < 0.9 and absf(a.tail_roll) < 0.9, "tail %f/%f" % [a.tail_pitch, a.tail_roll]],
		[_in(a.blink, 0.0, 1.0), "blink %f" % a.blink],
		[_in(a.item_amount, 0.0, 1.0), "item %f" % a.item_amount],
		[absf(a.breath) < 0.05, "breath %f" % a.breath],
	]
	for c: Array in checks:
		if not c[0]:
			t.fail("%s: %s" % [tag, c[1]])
			ok = false
	return ok
