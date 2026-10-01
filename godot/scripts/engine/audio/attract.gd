class_name Attract
extends RefCounted
## engine/audio/HallAmbience.kt Attract: what each kind of machine sounds like when it bleeps to
## itself in attract mode, so the hall is a mix of clacks, coin rattles and pews.

## A machine's few attract sounds, the pitch range they're replayed at and how loud they are.
class Palette:
	var sfx: PackedInt32Array
	var pitch_lo: float
	var pitch_hi: float
	var level: float

	func _init(p_sfx: Array, lo: float, hi: float, p_level: float) -> void:
		sfx = PackedInt32Array(p_sfx)
		pitch_lo = lo
		pitch_hi = hi
		level = p_level


## The kind for a machine with no palette of its own.
const GENERIC := 0

static var _palettes: Array = []


static func _all() -> Array:
	if _palettes.is_empty():
		_palettes = [
			Palette.new([Sfx.BLIP, Sfx.COIN, Sfx.POP, Sfx.SELECT, Sfx.CLINK], 0.5, 1.6, 1.0),
			# Claw: music-box plinks and a motor whirr.
			Palette.new([Sfx.CLINK, Sfx.CLINK, Sfx.POP, Sfx.CLAW_MOTOR], 0.7, 1.5, 0.9),
			# Whack-a-mole: bonks and boings.
			Palette.new([Sfx.BONK, Sfx.POP, Sfx.BONK], 0.8, 1.5, 0.9),
			# Skee-ball: a ball rolling and a bell.
			Palette.new([Sfx.RIM, Sfx.THUD, Sfx.ROLL], 0.8, 1.3, 0.8),
			# Hoops: bounces and swishes.
			Palette.new([Sfx.BOUNCE, Sfx.SWISH, Sfx.BOUNCE], 0.9, 1.4, 0.85),
			# Coin pusher: coins and a rattle.
			Palette.new([Sfx.COIN, Sfx.CLINK, Sfx.SPILL], 0.7, 1.3, 1.0),
			# Air hockey: puck clacks.
			Palette.new([Sfx.BOUNCE, Sfx.POP, Sfx.RIM], 1.4, 2.2, 0.7),
			# Racer: an engine rev and the odd horn.
			Palette.new([Sfx.ENGINE, Sfx.ENGINE, Sfx.HORN], 1.2, 2.4, 0.7),
			# Stacker: bare synth blips.
			Palette.new([Sfx.BLIP, Sfx.SELECT, Sfx.BLIP], 0.6, 1.2, 0.9),
			# Shootout: pews and ricochets.
			Palette.new([Sfx.ENEMY_FIRE, Sfx.RICOCHET, Sfx.DRY_FIRE], 0.9, 1.4, 0.7),
			# Pinball: bumpers and the spinner.
			Palette.new([Sfx.BUMPER, Sfx.SPINNER, Sfx.SLINGSHOT], 0.9, 1.4, 0.8),
			# Fishing: splashes and the reel.
			Palette.new([Sfx.SPLASH, Sfx.REEL, Sfx.BITE], 0.9, 1.4, 0.7),
		]
	return _palettes


## The palette index for the machine with game id [param game_id].
static func kind_for(game_id: String) -> int:
	match game_id:
		"claw":
			return 1
		"whack":
			return 2
		"skeeball":
			return 3
		"hoops":
			return 4
		"pusher":
			return 5
		"airhockey":
			return 6
		"racer":
			return 7
		"stacker":
			return 8
		"shooter":
			return 9
		"pinball":
			return 10
		"fishing":
			return 11
	return GENERIC


static func palette(kind: int) -> Palette:
	var all := _all()
	return all[kind if kind >= 0 and kind < all.size() else GENERIC]
