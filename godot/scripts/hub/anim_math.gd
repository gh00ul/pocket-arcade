class_name AnimMath
extends RefCounted
## hub/AnimMath.kt AnimMath: small pure helpers for character animation: angles, easing and seeded
## "randomness". Everything here is allocation-free so it can run every simulation step. (The
## damped spring of the same Kotlin file is [AnimSpring]: the engine's Juice [Spring] holds that
## global name.)

const TAU_F := TAU
const _PI_F := PI


## [param a] wrapped into (-π, π].
static func wrap(a: float) -> float:
	var d := fmod(a, TAU)
	if d > PI:
		d -= TAU
	if d < -PI:
		d += TAU
	return d


## 0 at or below 0, 1 at or above 1, and smooth (zero slope at both ends) between.
static func smooth(t: float) -> float:
	var x := clampf(t, 0.0, 1.0)
	return x * x * (3.0 - 2.0 * x)


## A window that is 0 at [param t] = 0 and at [param t] = 1 and rises smoothly to 1 in between
## (for one-off gestures).
static func bell(t: float) -> float:
	if t <= 0.0 or t >= 1.0:
		return 0.0
	var s := sin(t * PI)
	return s * s


## Frame-rate independent share of the way to a target covered in [param dt] at [param rate] per second.
static func k(rate: float, dt: float) -> float:
	return 1.0 - exp(-rate * dt)


static var _f32 := PackedFloat32Array([0.0])


## [param x] rounded to a 32-bit float, as Kotlin's Float arithmetic leaves it (for the few values
## that are truncated to whole numbers afterwards, such as a kid's animation seed).
static func f32(x: float) -> float:
	_f32[0] = x
	return _f32[0]


## A repeatable number in [0, 1) from a figure's [param seed], a running [param n] and a
## [param salt] for what it's for (Kotlin's 32-bit Int arithmetic, so the same numbers as build-13).
static func unit(seed_value: int, n: int, salt: int) -> float:
	var x := MathUtil.i32(MathUtil.i32(seed_value * 31) + n)
	var h := MathUtil.hash01(x, salt, MathUtil.i32(seed_value ^ 0x5bd1e995))
	return minf(h, 0.9999)
