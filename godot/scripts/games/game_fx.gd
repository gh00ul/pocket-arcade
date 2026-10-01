class_name GameFx
extends RefCounted
## games/MiniGame.kt GameFx: services a game uses while it runs (sound, haptics, collectibles), and
## its requests for the feel of a moment: [method hit_stop], [method slow_mo] and [method punch].
## They are plain fields the host reads once per step ([method flush]) and turns into a freeze, a
## slow-motion beat and a camera kick; a game just says when something big happens and never
## touches its own clock, so its simulation always steps by the same fixed time. All three do
## nothing while reduce motion is on, and the host caps and spaces them (see [TimeScale]), so
## calling them generously is safe.

## The game's sound (an [AudioSynth], or anything with play / play_at; tests pass a recorder).
var audio: Object
var haptics: Haptics
## Adds a collectible (e.g. a claw-machine plush id) to the player's collection.
var on_collectible: Callable

var _stop_seconds := 0.0
var _slow_speed := 1.0
var _slow_seconds := 0.0
var _punch_amount := 0.0


func _init(p_audio: Object = null, p_haptics: Haptics = null, p_on_collectible: Callable = Callable()) -> void:
	audio = p_audio
	haptics = p_haptics if p_haptics != null else Haptics.new(null)
	on_collectible = p_on_collectible


## Adds [param item_id] to the player's collection.
func collect(item_id: String) -> void:
	if on_collectible.is_valid():
		on_collectible.call(item_id)


## Freezes the game for about [param seconds] (0.04-0.1 feels right for a big hit; the host caps it at 0.12).
func hit_stop(seconds: float) -> void:
	if seconds > _stop_seconds:
		_stop_seconds = seconds


## Runs the game at [param speed] (0.3-0.5) for [param seconds] with eased ramps: a jackpot, a new high score.
func slow_mo(speed: float, seconds: float) -> void:
	if seconds <= 0.0:
		return
	_slow_speed = minf(_slow_speed, speed)
	if seconds > _slow_seconds:
		_slow_seconds = seconds


## Kicks the 3D camera in and lets it spring back: [param amount] 0..1 (0.3 for a solid hit, 1 for the biggest moment).
func punch(amount: float) -> void:
	if amount > _punch_amount:
		_punch_amount = amount


## Host only: hands the pending requests to [param time] (or drops them if it is null: outside the
## playing phase nothing may slow the game), clears them and returns the pending camera punch.
func flush(time: TimeScale) -> float:
	if time != null:
		if _stop_seconds > 0.0:
			time.hit_stop(_stop_seconds)
		if _slow_seconds > 0.0:
			time.slow_mo(_slow_speed, _slow_seconds)
	_stop_seconds = 0.0
	_slow_speed = 1.0
	_slow_seconds = 0.0
	var p := _punch_amount
	_punch_amount = 0.0
	return p
