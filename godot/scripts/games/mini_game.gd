class_name MiniGame
extends RefCounted
## games/MiniGame.kt: the contract every arcade machine implements. The host owns the round flow
## (intro, countdown, timer, pause, results and ticket printing); a game only simulates, draws and
## scores. To add a machine: extend [BaseMiniGame] and add it to [GameRegistry]; the hall gives it
## a cabinet automatically.
##
## Kotlin's `val` properties are plain fields here, set in the subclass's _init; `finished` is the
## method [method finished].

## Every mini-game plays inside a fixed portrait field of GAME_W x GAME_H units.
const GAME_W := 360.0
const GAME_H := 640.0

## The kind of cabinet the hall builds for a machine (games/MiniGame.kt CabinetShape).
enum CabinetShape {
	## Classic upright video cabinet.
	UPRIGHT,
	## Wide glass-fronted merchandiser.
	WIDE,
	## Long alley you stand at the end of.
	LANE,
	## Low playing table with a scoreboard at the far end.
	TABLE,
	CLAW,
	WHACK,
	SKEEBALL,
	HOOPS,
	PUSHER,
	AIR_HOCKEY,
	RACER,
	## Tall upright with a vertical screen and a big button (stacker).
	TOWER,
	## Light-gun cabinet: a big screen with two guns on the control shelf (shooter).
	GUN,
	## Pinball table: a slanted glass playfield on legs with a lit backbox (pinball).
	PINBALL,
	## A big round fishing tub with rods around the rim and a sign on a post (fishing).
	FISHING,
}


## How a machine looks in the hall: body and trim colours (ARGB), the neon glow it casts, and its shape.
class CabinetLook:
	extends RefCounted
	var body: int
	var trim: int
	var glow: int
	var shape: int

	func _init(p_body: int, p_trim: int, p_glow: int, p_shape: int = CabinetShape.UPRIGHT) -> void:
		body = p_body
		trim = p_trim
		glow = p_glow
		shape = p_shape

	func equals(o: CabinetLook) -> bool:
		return o != null and body == o.body and trim == o.trim and glow == o.glow and shape == o.shape


## Stable id used for save data (high scores). Never change it after release.
var id := ""
## Full name shown on the intro card and results.
var title := ""
## Up to 6 characters, printed on the hall cabinet's marquee.
var marquee := ""
## Short how-to-play lines for the intro card.
var instructions := PackedStringArray()
var look: CabinetLook = null
## Length of a round in seconds.
var round_seconds := 60.0
var score := 0
## Extra tickets won directly during play (e.g. ticket bundles in the coin pusher).
var bonus_tickets := 0


## The machine's own hall cabinet (a CabinetDesign: size, camera dive point, model, lights, live
## screen and moving parts), or null for the built-in cabinet of [member CabinetLook.shape].
func cabinet() -> Object:
	return null


## Draws the looping attract-mode animation on the cabinet screen in the hall. [param p] is set up
## so one unit is one art pixel; the screen is [param w] x [param h] art pixels.
func draw_attract(_p: Painter, _w: int, _h: int, _time: float) -> void:
	pass


## Resets all state for a fresh round.
func start(_fx: GameFx) -> void:
	pass


## Advances the simulation by a fixed [param dt]. [param time_left] reaches 0 when the clock expires.
func update(_dt: float, _time_left: float) -> void:
	pass


## Draws the game in field units (0..GAME_W, 0..GAME_H); the host applies scaling.
func draw(_scope: DrawScope) -> void:
	pass


## Touch input in field units ([param type] is a [TouchType]). Coordinates may fall outside the field.
func on_touch(_type: int, _id: int, _x: float, _y: float, _time_ms: int) -> void:
	pass


## The host calls this whenever it stops forwarding touches (pause, the app going to the background,
## the round ending): forget every tracked pointer and let go of every held control; keep the
## simulation state exactly as it is.
func cancel_input() -> void:
	pass


## True once the round is over (clock expired or the game ended itself) and nothing is still moving.
func finished() -> bool:
	return false


## Tickets printed for a final [param p_score].
func tickets_for(_p_score: int) -> int:
	return 0
