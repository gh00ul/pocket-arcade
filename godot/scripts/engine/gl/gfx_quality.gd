class_name GfxQuality
extends RefCounted
## engine/gl/GfxQuality.kt: graphics quality: a tier the player picks, a frame-rate cap, and the
## ladder of levers the renderer walks down when the GPU can't keep up.
##
## The levers: MSAA samples (4 / 2 / 0), bloom octaves (4 / 3 / 2), floor reflections (mirror
## images where a scene asks, cheap streaks, or off), the light budget packed per frame
## (64 / 32 / 16), the render scale floor, ceiling and boost, and the HDR picture (with its glare
## and film finish) against the LDR one. Rung 0 is the best picture.

enum Tier { AUTO, BATTERY, QUALITY }
enum Reflections { MIRROR, STREAKS, OFF }


## One step of the ladder: every lever at once.
class Rung:
	extends RefCounted
	var scale_floor: float
	var scale_ceiling: float
	## How far the scale may go above the ceiling when the GPU time is known to allow it.
	var scale_boost: float
	var msaa: int
	var bloom_octaves: int
	var reflections: int
	var lights: int
	var hdr: bool
	var glare: bool
	var film: bool

	func _init(p_floor: float, p_ceiling: float, p_boost: float, p_msaa: int, p_bloom: int, p_refl: int, p_lights: int, p_hdr: bool = false, p_glare: bool = false, p_film: bool = false) -> void:
		scale_floor = p_floor
		scale_ceiling = p_ceiling
		scale_boost = p_boost
		msaa = p_msaa
		bloom_octaves = p_bloom
		reflections = p_refl
		lights = p_lights
		hdr = p_hdr
		glare = p_glare
		film = p_film


static var LADDER: Array[Rung] = [
	Rung.new(0.5, 0.8, 1.0, 4, 4, Reflections.MIRROR, RenderPass.MAX_LIGHTS, true, true, true),
	Rung.new(0.5, 0.8, 0.8, 4, 3, Reflections.STREAKS, 32, true),
	Rung.new(0.5, 0.7, 0.7, 2, 3, Reflections.STREAKS, 32),
	Rung.new(0.45, 0.7, 0.7, 2, 2, Reflections.OFF, 16),
	Rung.new(0.4, 0.6, 0.6, 0, 2, Reflections.OFF, 16),
]

## The best rung [constant Tier.BATTERY] may use.
const BATTERY_TOP := 2
## The display must refresh at least this many times faster than the cap for the cap to bite.
const CAP_MIN_RATIO := 1.6
## A capped frame is due this much before a whole interval has passed, to absorb timing jitter.
const CAP_SLACK_USEC := 3000

static var tier: int = Tier.AUTO
## Frames per second to draw at most: 0 = auto (60), 30 or 60. Rendering only.
static var frame_cap := 0
## The display's refresh rate, and whether the OS calls this a low-RAM device.
static var display_hz := 60.0
static var low_ram_device := false
## Whether particles are drawn inside the 3D picture (else painted in 2D).
static var gl_particles := true
## The light budget of the rung in force, read by the recorder when it packs the lights.
static var light_budget := RenderPass.MAX_LIGHTS
## The rung in force (set by the pacer in Gfx).
static var rung := 0


## Whether the player asked for less motion (no film grain, no motion-coupled aberration).
static func motion_reduced() -> bool:
	return ScreenShake.intensity <= 0.0


## The cap in force: 30 or 60.
static func effective_cap() -> int:
	return 30 if frame_cap == 30 else 60


## Whether a cap does anything on a display refreshing at [param hz].
static func cap_applies(cap: int, hz: float) -> bool:
	return hz >= cap * CAP_MIN_RATIO


static func top_rung(t: int) -> int:
	return BATTERY_TOP if t == Tier.BATTERY else 0


static func bottom_rung(t: int) -> int:
	return 0 if t == Tier.QUALITY else LADDER.size() - 1


static func start_rung(t: int, device_rung: int) -> int:
	var r := device_rung
	if t == Tier.BATTERY:
		r = maxi(BATTERY_TOP, device_rung)
	elif t == Tier.QUALITY:
		r = 0
	return clampi(r, top_rung(t), bottom_rung(t))


const SOFTWARE := ["swiftshader", "llvmpipe", "softpipe", "software", "microsoft basic"]
static var _old_gpu: RegEx = null


## The rung a device should start on, from what the driver says about itself.
static func device_rung(gl_renderer: String, low_ram: bool) -> int:
	var r := gl_renderer.to_lower()
	for s in SOFTWARE:
		if r.contains(s):
			return LADDER.size() - 1
	if _old_gpu == null:
		_old_gpu = RegEx.create_from_string("mali-4\\d\\d|mali-t[0-7]\\d\\d|adreno \\(tm\\) ?[2-4]\\d\\d\\b|powervr sgx|powervr rogue ge8|vivante|tegra [234]\\b")
	if low_ram or _old_gpu.search(r) != null:
		return 2
	return 0
