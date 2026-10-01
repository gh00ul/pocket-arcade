class_name SfxMix
extends RefCounted
## engine/audio/MixEngine.kt SfxMix: how much of each sound effect goes to the room reverb, and
## which sounds duck the music.

## Small, close, mechanical sounds: hardly any reverb, so they stay crisp.
const SEND_DRY := 0.06
## Menu and UI sounds, and the ticks and beeps of a round.
const SEND_UI := 0.1
## Most sounds.
const SEND_NORMAL := 0.16
## Bright, ringing sounds (coins, glass, chimes) bloom into a room.
const SEND_RING := 0.26
## Fanfares and big moments get the most room.
const SEND_BIG := 0.34

static var _send := PackedFloat32Array()


## How deeply (0 = not at all) [param sfx] ducks the music while it plays.
static func duck_depth(sfx: int) -> float:
	if sfx == Sfx.JACKPOT:
		return 0.55
	if sfx == Sfx.HIGHSCORE:
		return 0.6
	if sfx == Sfx.WIN or sfx == Sfx.FINISH or sfx == Sfx.LUCKY:
		return 0.35
	if sfx == Sfx.EXPLOSION:
		return 0.4
	if sfx == Sfx.BOMB or sfx == Sfx.CRASH or sfx == Sfx.PRIZE:
		return 0.28
	if sfx == Sfx.CHEER:
		return 0.15
	return 0.0


## ...and for how many seconds.
static func duck_hold(sfx: int) -> float:
	if sfx == Sfx.HIGHSCORE:
		return 2.0
	if sfx == Sfx.JACKPOT:
		return 1.3
	if sfx == Sfx.FINISH:
		return 1.2
	if sfx == Sfx.WIN or sfx == Sfx.LUCKY or sfx == Sfx.CHEER:
		return 0.9
	if sfx == Sfx.EXPLOSION:
		return 0.7
	return 0.5


## Reverb send when a sound is played without saying otherwise (0 = dry, 1 = as loud as the sound).
static func send_for(sfx: int) -> float:
	if _send.is_empty():
		var t := PackedFloat32Array()
		t.resize(Sfx.COUNT)
		t.fill(SEND_NORMAL)
		for s: int in [Sfx.STEP, Sfx.ENGINE, Sfx.ROLL, Sfx.CLAW_MOTOR, Sfx.REEL, Sfx.PRINT, Sfx.SKID, Sfx.SPINNER,
				Sfx.SHELL, Sfx.DRY_FIRE, Sfx.TICKET, Sfx.FLIPPER, Sfx.SLINGSHOT]:
			t[s] = SEND_DRY
		for s: int in [Sfx.BLIP, Sfx.SELECT, Sfx.ERROR, Sfx.COUNTDOWN, Sfx.WHOOSH, Sfx.BUZZER, Sfx.TILT, Sfx.PLUNGER]:
			t[s] = SEND_UI
		for s: int in [Sfx.COIN, Sfx.TOKEN, Sfx.CLINK, Sfx.SPILL, Sfx.RIM, Sfx.PRIZE, Sfx.LAP, Sfx.BITE, Sfx.CATCH,
				Sfx.BUMPER, Sfx.RICOCHET, Sfx.STEAM]:
			t[s] = SEND_RING
		for s: int in [Sfx.WIN, Sfx.JACKPOT, Sfx.HIGHSCORE, Sfx.LUCKY, Sfx.FINISH, Sfx.GO, Sfx.CHEER, Sfx.EXPLOSION,
				Sfx.BOMB, Sfx.CRASH, Sfx.GUNSHOT, Sfx.ALARM]:
			t[s] = SEND_BIG
		_send = t
	return _send[sfx]
