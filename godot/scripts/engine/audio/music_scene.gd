class_name MusicScene
extends RefCounted
## engine/audio/Music.kt MusicScene: which theme should be playing, as a string key (build-13's
## sealed class): silence, the title, the hall, the results loop, or a machine's theme.

const SILENCE := ""
const TITLE := "title"
const HALL := "hall"
const RESULTS := "results"
const GAME_PREFIX := "game:"


## The theme of machine [param id] (an unknown one gets a generic upbeat theme).
static func game(id: String) -> String:
	return GAME_PREFIX + id


static func is_game(scene: String) -> bool:
	return scene.begins_with(GAME_PREFIX)


## The machine id of a game scene ("" for any other scene).
static func game_id(scene: String) -> String:
	return scene.substr(GAME_PREFIX.length()) if is_game(scene) else ""
