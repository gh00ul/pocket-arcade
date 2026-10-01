class_name GameRegistry
extends RefCounted
## games/GameRegistry.kt: the one place machines are registered, in build-13's order. The hall
## gives each entry a bank of cabinets; add a new [MiniGame] here and it appears in the arcade with
## its own cabinets, prompts and high-score table.
##
## Each game is loaded by path, so the registry (and everything that uses it) runs while the
## machines are still being ported: a machine whose script is missing is left out.

const GAMES := [
	["claw", "res://scripts/games/claw/claw_machine_game.gd"],
	["whack", "res://scripts/games/whackamole/whack_a_mole_game.gd"],
	["skeeball", "res://scripts/games/skeeball/skee_ball_game.gd"],
	["hoops", "res://scripts/games/hoops/hoops_game.gd"],
	["pusher", "res://scripts/games/coinpusher/coin_pusher_game.gd"],
	["airhockey", "res://scripts/games/airhockey/air_hockey_game.gd"],
	["racer", "res://scripts/games/racer/racer_game.gd"],
	["stacker", "res://scripts/games/stacker/stacker_game.gd"],
	["shooter", "res://scripts/games/shooter/shooter_game.gd"],
	["pinball", "res://scripts/games/pinball/pinball_game.gd"],
	["fishing", "res://scripts/games/fishing/fishing_game.gd"],
]


## A fresh instance of every machine that exists, in registry order.
static func create_all() -> Array[MiniGame]:
	var out: Array[MiniGame] = []
	for entry: Array in GAMES:
		var g := create(entry[0])
		if g != null:
			out.append(g)
	return out


## A fresh instance of machine [param game_id], or null if it isn't there.
static func create(game_id: String) -> MiniGame:
	for entry: Array in GAMES:
		if entry[0] == game_id and ResourceLoader.exists(entry[1]):
			var script: Script = load(entry[1])
			if script != null and script.can_instantiate():
				return script.new() as MiniGame
	return null


static func ids() -> PackedStringArray:
	var out := PackedStringArray()
	for entry: Array in GAMES:
		out.append(entry[0])
	return out
