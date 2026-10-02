class_name HallGames
extends RefCounted
## The machines the hall tests build the floor with: build-13's eleven, in registry order. A machine
## already ported (with its own cabinet design, if build-13 gave it one) comes from GameRegistry;
## one not ported yet is stood in for by a do-nothing machine with build-13's id, names, look and
## cabinet sizes (its own design's for the racer, shooter, pinball and fishing), so the floor plan
## the tests check is build-13's either way. Godot-only helper (Kotlin used GameRegistry directly).

const S := MiniGame.CabinetShape

## build-13's machines: id, title, marquee, body, trim, glow, shape, and the size of its own cabinet
## design ([] for none): width, depth, height, focus height, focus set back, screen w, screen h.
const BUILD_13 := [
	["claw", "CLAW MACHINE", "CLAW", Pal.PINK, Pal.YELLOW, Pal.HOTPINK, S.CLAW, []],
	["whack", "WHACK-A-MOLE", "WHACK", Pal.GREEN, Pal.BROWN, Pal.LIME, S.WHACK, []],
	["skeeball", "SKEE-BALL", "SKEE", Pal.BLUE, Pal.YELLOW, Pal.SKY, S.SKEEBALL, []],
	["hoops", "HOOP SHOT", "HOOPS", Pal.RED, Pal.ORANGE, Pal.ORANGE, S.HOOPS, []],
	["pusher", "COIN PUSHER", "PUSHER", Pal.ORANGE, Pal.GOLD, Pal.YELLOW, S.PUSHER, []],
	["airhockey", "AIR HOCKEY", "HOCKEY", Pal.SKY, Pal.WHITE, Pal.CYAN, S.AIR_HOCKEY, []],
	["racer", "TURBO RACER", "RACER", Pal.DARKRED, Pal.WHITE, Pal.RED, S.RACER, [34.0, 56.0, 70.0, 44.0, 56.0 - 12.0, 26, 18]],
	["stacker", "STACKER", "STACK", Pal.VIOLET, Pal.CYAN, Pal.PURPLE, S.TOWER, []],
	["shooter", "SHOOTOUT", "SHOOT", Pal.NAVY, Pal.ORANGE, Pal.ORANGE, S.GUN, [44.0, 40.0, 85.0, 53.0, 18.0, 24, 18]],
	["pinball", "STAR FLIPPER", "FLIP", Pal.PURPLE, Pal.CYAN, Pal.HOTPINK, S.PINBALL, [28.0, 58.0, 76.0, 54.0, 58.0 - 14.0, 40, 30]],
	["fishing", "GONE FISHING", "FISH", Pal.TEAL, Pal.YELLOW, Pal.SKY, S.FISHING, [62.0, 62.0, 62.0, 44.0, 62.0 - 10.0, 24, 18]],
]

static var _maps: Array = []
static var _games: Array[MiniGame] = []


## A do-nothing machine (Kotlin HubMapTest's Probe, and the stand-ins).
class StandIn:
	extends MiniGame
	var design: CabinetDesign = null

	func _init(p_id: String, p_title: String, p_marquee: String, p_look: MiniGame.CabinetLook, p_design: CabinetDesign = null) -> void:
		id = p_id
		title = p_title
		marquee = p_marquee
		look = p_look
		design = p_design
		round_seconds = 1.0

	func cabinet() -> Object:
		return design

	func finished() -> bool:
		return true


## A cabinet design that only has sizes.
class SizedDesign:
	extends CabinetDesign

	func _init(w: float, d: float, h: float, fh: float, fsb: float, screen: Variant = null) -> void:
		width = w
		depth = d
		height = h
		focus_height = fh
		focus_set_back = fsb
		screen_units = screen


## build-13's machines (see the class description).
static func create_all() -> Array[MiniGame]:
	var out: Array[MiniGame] = []
	for e: Array in BUILD_13:
		var sizes: Array = e[7]
		var real := GameRegistry.create(e[0])
		if real != null and (sizes.is_empty() or real.cabinet() != null):
			out.append(real)
		else:
			out.append(stand_in(e))
	return out


## The stand-in for one [constant BUILD_13] entry.
static func stand_in(e: Array) -> MiniGame:
	var sizes: Array = e[7]
	var design: CabinetDesign = null
	if not sizes.is_empty():
		design = SizedDesign.new(sizes[0], sizes[1], sizes[2], sizes[3], sizes[4], Vector2i(sizes[5], sizes[6]))
	return StandIn.new(e[0], e[1], e[2], MiniGame.CabinetLook.new(e[3], e[4], e[5], e[6]), design)


## Every decoration (Catalog.DecorStyle.entries).
static func every_decor() -> Array[int]:
	var out: Array[int] = []
	for v: int in Catalog.DecorStyle.values():
		out.append(v)
	return out


## The machines the shared floors ([method maps]) were built with (made once).
static func games() -> Array[MiniGame]:
	if _games.is_empty():
		_games = create_all()
	return _games


## The two floors the Kotlin tests check, built once from [method games]:
## [["no decor", map], ["all decor", map]].
static func maps() -> Array:
	if _maps.is_empty():
		var none: Array[int] = []
		_maps = [
			["no decor", HubLayout.build(games(), none)],
			["all decor", HubLayout.build(games(), every_decor())],
		]
	return _maps
