class_name RepoFixture
extends RefCounted
## data/RepositoryTestBase.kt: repository tests run against a real save file in a scratch folder,
## the same code path as the game (encoding, atomic writes, backup and corruption recovery).

const ROOT := "user://test_tmp"

var dir: String
var path: String


func _init() -> void:
	dir = "%s/%d_%d" % [ROOT, Time.get_ticks_usec(), randi() % 100000]
	DirAccess.make_dir_recursive_absolute(dir)
	path = dir + "/save.json"


## A store over [member path] (a fresh one is "the next app launch").
func new_store() -> PrefsStore:
	return PrefsStore.new(path)


## Writes the raw saved values a test wants to start from, by the on-disk key names.
func seed_store(store: PrefsStore, tokens: Variant = null, tickets: Variant = null) -> void:
	store.edit(func(p: Dictionary) -> void:
		if tokens != null:
			p["tokens"] = tokens
		if tickets != null:
			p["tickets"] = tickets)


func repo(tokens: Variant = null, tickets: Variant = null) -> ArcadeRepository:
	var s := new_store()
	if tokens != null or tickets != null:
		seed_store(s, tokens, tickets)
	return ArcadeRepository.new(s)


func write_raw(bytes: PackedByteArray) -> void:
	var f := FileAccess.open(path, FileAccess.WRITE)
	f.store_buffer(bytes)
	f.close()


## Deletes the scratch folder.
func cleanup() -> void:
	_remove(dir)


static func _remove(d: String) -> void:
	var da := DirAccess.open(d)
	if da == null:
		return
	da.include_hidden = true
	for f in da.get_files():
		DirAccess.remove_absolute(d.path_join(f))
	for sub in da.get_directories():
		_remove(d.path_join(sub))
	DirAccess.remove_absolute(d)
