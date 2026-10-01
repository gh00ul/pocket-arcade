class_name HdrMath
extends RefCounted
## engine/gl/HdrMath.kt HdrMath: CPU mirrors of the HDR shaders' maths, so tests pin what the
## shaders do (each has a twin in shaders/hdr.gdshaderinc or the bloom shaders).


## The ACES filmic fit (Narkowicz), one channel, clamped to 0..1.
static func aces(c: float) -> float:
	return clampf((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0)


## The display value [param y] back to the linear light aces() maps to it; clamped near 1.
static func inverse_aces(y: float) -> float:
	var v := clampf(y, 0.0, HdrLook.ACES_INV_MAX)
	var a := 2.43 * v - 2.51
	var b := 0.59 * v - 0.03
	var c := 0.14 * v
	return (b + sqrt(maxf(b * b - 4.0 * a * c, 0.0))) / (2.0 * (2.51 - 2.43 * v))


## The scene encoding of a brightest-channel value (linear light): 0..1.
static func encode(m: float) -> float:
	return maxf(m, 0.0) / (1.0 + maxf(m, 0.0))


## Linear light back from an encoded value, capped at what ENC_MAX allows.
static func decode(x: float) -> float:
	return minf(x, HdrLook.ENC_MAX) / (1.0 - minf(x, HdrLook.ENC_MAX))


## Identity up to [param start], then an exponential ease into [param cap] with no kink.
static func soft_cap(x: float, cap: float, start: float) -> float:
	if x <= start:
		return x
	var r := maxf(cap - start, 1e-4)
	return start + r * (1.0 - exp(-(x - start) / r))


## How much of a pixel of brightness [param br] goes into the glow (soft knee, then capped).
static func bloom_energy(br: float, threshold: float, knee: float = HdrLook.BLOOM_KNEE, cap: float = HdrLook.BLOOM_INPUT_CAP, cap_start: float = HdrLook.BLOOM_INPUT_START) -> float:
	var s := clampf(br - threshold + knee, 0.0, 2.0 * knee)
	var soft := s * s / (4.0 * knee)
	return soft_cap(maxf(soft, br - threshold), cap, cap_start)


## The Karis weight of a first-downsample tap of brightness [param br].
static func karis_weight(br: float, strength: float = HdrLook.KARIS_STRENGTH) -> float:
	return 1.0 / (1.0 + br * strength)


## The bloom light a pixel of scene brightness [param scene] receives when [param bloom] is added.
static func bloom_added(bloom: float, scene: float) -> float:
	return soft_cap(bloom, HdrLook.BLOOM_ADD_CAP, HdrLook.BLOOM_ADD_START) / (1.0 + scene * HdrLook.BLOOM_SELF_SHADOW)


## The lit-paint ceiling.
static func lit_limit(m: float) -> float:
	return soft_cap(m, HdrLook.LIT_CEILING, HdrLook.LIT_START)
