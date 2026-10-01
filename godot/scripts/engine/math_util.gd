class_name MathUtil
extends RefCounted
## engine/MathUtil.kt, plus the integer helpers ported Kotlin needs: GDScript ints are 64-bit,
## Kotlin's Int is 32-bit and wraps, which hash functions and bit tricks rely on.

const TAU_F := TAU


static func clamp01(v: float) -> float:
	return clampf(v, 0.0, 1.0)


## Moves [param current] toward [param target] by at most [param max_delta].
static func approach(current: float, target: float, max_delta: float) -> float:
	if current < target:
		return minf(current + max_delta, target)
	if current > target:
		return maxf(current - max_delta, target)
	return current


## Frame-rate independent exponential smoothing toward [param target].
static func damp(current: float, target: float, rate: float, dt: float) -> float:
	return lerpf(current, target, 1.0 - exp(-rate * dt))


static func ease_out_cubic(t: float) -> float:
	var x := 1.0 - clamp01(t)
	return 1.0 - x * x * x


static func ease_out_back(t: float) -> float:
	var x := clamp01(t)
	var c1 := 1.70158
	var c3 := c1 + 1.0
	return 1.0 + c3 * pow(x - 1.0, 3.0) + c1 * pow(x - 1.0, 2.0)


static func len2(x: float, y: float) -> float:
	return sqrt(x * x + y * y)


static func dist(x1: float, y1: float, x2: float, y2: float) -> float:
	return len2(x2 - x1, y2 - y1)


## Kotlin's roundToInt(): rounds half up (floor(x + 0.5)), unlike Godot's roundi (half away from zero).
static func round_to_int(x: float) -> int:
	return int(floorf(x + 0.5))


## Kotlin Float.toInt(): truncates toward zero (same as int()), NaN to 0.
static func to_int(x: float) -> int:
	if is_nan(x):
		return 0
	return int(x)


# ---------------------------------------------------------------- 32-bit Int emulation

## Wraps [param x] to a signed 32-bit value, like Kotlin Int arithmetic.
static func i32(x: int) -> int:
	x &= 0xFFFFFFFF
	return x - 0x100000000 if x >= 0x80000000 else x


## Kotlin `x ushr n` on an Int.
static func ushr32(x: int, n: int) -> int:
	return (x & 0xFFFFFFFF) >> (n & 31)


## Kotlin `x shl n` on an Int.
static func shl32(x: int, n: int) -> int:
	return i32(x << (n & 31))


## Kotlin `x shr n` (arithmetic) on an Int.
static func shr32(x: int, n: int) -> int:
	return i32(x) >> (n & 31)


## Kotlin Int.inv().
static func inv32(x: int) -> int:
	return i32(~x)


## Integer.numberOfLeadingZeros for a 32-bit value.
static func nlz32(x: int) -> int:
	var v := x & 0xFFFFFFFF
	if v == 0:
		return 32
	var n := 0
	while (v & 0x80000000) == 0:
		v <<= 1
		n += 1
	return n


## Hash-based value noise in 0..1 for deterministic procedural detail (32-bit, as in Kotlin).
static func hash01(x: int, y: int, seed: int = 0) -> float:
	var h := i32(x * 374761393 + y * 668265263 + seed * 1274126177)
	h = i32((h ^ ushr32(h, 13)) * 1103515245)
	h = h ^ ushr32(h, 16)
	return float(h & 0xFFFF) / 65535.0
