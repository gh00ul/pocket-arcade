class_name KRandom
extends RefCounted
## kotlin.random.Random as the Kotlin app used it: `Random(seed)` is Kotlin's XorWowRandom, and
## every method below draws exactly the bits Kotlin's does, so a seeded round, a bot simulation
## or a procedural texture makes the same choices as build-13 (up to float vs double maths).

var _x := 0
var _y := 0
var _z := 0
var _w := 0
var _v := 0
var _addend := 0


## Kotlin `Random(seed: Long)`: XorWowRandom(seed.toInt(), seed.shr(32).toInt()).
func _init(seed: int = 0) -> void:
	var seed1 := MathUtil.i32(seed)
	var seed2 := MathUtil.i32(seed >> 32)
	_x = seed1
	_y = seed2
	_z = 0
	_w = 0
	_v = MathUtil.inv32(seed1)
	_addend = MathUtil.i32((seed1 << 10) ^ MathUtil.ushr32(seed2, 4))
	for i in 64:
		next_int()


## A generator seeded from the clock (Kotlin `Random(System.nanoTime())`).
static func unseeded() -> KRandom:
	return KRandom.new(Time.get_ticks_usec() * 1000 + randi())


## nextInt(): 32 random bits as a signed Int.
func next_int() -> int:
	var t := _x
	t = t ^ MathUtil.ushr32(t, 2)
	_x = _y
	_y = _z
	_z = _w
	var v0 := _v
	_w = v0
	t = MathUtil.i32((t ^ MathUtil.i32(t << 1)) ^ v0 ^ MathUtil.i32(v0 << 4))
	_v = t
	_addend = MathUtil.i32(_addend + 362437)
	return MathUtil.i32(t + _addend)


## nextBits(bitCount): the upper bits of nextInt().
func next_bits(bit_count: int) -> int:
	var r := next_int()
	if bit_count <= 0:
		return 0
	# takeUpperBits: this.ushr(32 - bitCount) and (-bitCount).shr(31)
	return MathUtil.i32(MathUtil.ushr32(r, 32 - bit_count) & MathUtil.shr32(-bit_count, 31))


## nextInt(until) / nextInt(from, until).
func next_int_range(from: int, until: int) -> int:
	assert(until > from, "Random range is empty")
	var n := MathUtil.i32(until - from)
	if n > 0 or n == -0x80000000:
		var rnd: int
		if n > 0 and (n & -n) == n:
			var bit_count := 31 - MathUtil.nlz32(n)
			rnd = next_bits(bit_count)
		else:
			var v: int
			while true:
				var bits := MathUtil.ushr32(next_int(), 1)
				v = bits % n
				if MathUtil.i32(bits - v + (n - 1)) >= 0:
					break
			rnd = v
		return MathUtil.i32(from + rnd)
	while true:
		var r := next_int()
		if r >= from and r < until:
			return r
	return from


func next_int_until(until: int) -> int:
	return next_int_range(0, until)


## nextLong(): two Ints.
func next_long() -> int:
	return (next_int() << 32) + next_int()


func next_boolean() -> bool:
	return next_bits(1) != 0


## nextFloat(): 24 random bits in [0, 1).
func next_float() -> float:
	return float(next_bits(24)) / 16777216.0


## nextDouble(): 53 random bits in [0, 1).
func next_double() -> float:
	var hi := next_bits(26)
	var lo := next_bits(27)
	return float((hi << 27) + lo) / 9007199254740992.0


func next_double_range(from: float, until: float) -> float:
	var r := from + next_double() * (until - from)
	return until - 1e-12 if r >= until else r


## engine/MathUtil.kt `Random.range(min, max)` (nextFloat based).
func range_f(min_v: float, max_v: float) -> float:
	return min_v + next_float() * (max_v - min_v)


## engine/MathUtil.kt `Random.chance(p)`.
func chance(p: float) -> bool:
	return next_float() < p


## `list.random(rng)`.
func pick(list: Array) -> Variant:
	return list[next_int_until(list.size())]


## `list.shuffled(rng)` / `shuffle(rng)` in place: Kotlin's Fisher-Yates from the top.
func shuffle(list: Array) -> void:
	var i := list.size() - 1
	while i >= 1:
		var j := next_int_until(i + 1)
		var tmp: Variant = list[i]
		list[i] = list[j]
		list[j] = tmp
		i -= 1
