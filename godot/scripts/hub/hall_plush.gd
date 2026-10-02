class_name HallPlush
extends RefCounted
## Godot-only: the claw game's plush models (games/claw/Plush3D.kt, ported by the games-a port as
## `scripts/games/claw/plush_3d.gd`, `Plush3D.model(plush)`), reached by path so the hall builds and
## its tests run while that port isn't in yet. Until it is, [method model] gives null and the plushes
## (claw piles, the prize counter's cases, the prize wall, the giant bear) are left out.

const PATH := "res://scripts/games/claw/plush_3d.gd"

static var _script: Script = null
static var _looked := false


## Whether the plush models are available.
static func available() -> bool:
	return _plush_script() != null


## Plush3D.model(plush), or null while the claw game's port isn't in.
static func model(p: Catalog.Plush) -> Model:
	var s := _plush_script()
	if s == null:
		return null
	return s.call("model", p) as Model


static func _plush_script() -> Script:
	if not _looked:
		_looked = true
		if ResourceLoader.exists(PATH):
			_script = load(PATH)
	return _script
