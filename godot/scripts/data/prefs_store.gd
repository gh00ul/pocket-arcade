class_name PrefsStore
extends RefCounted
## The Godot stand-in for a DataStore<Preferences>: a flat map of build-13's preference keys to
## typed values, kept in one versioned JSON file and edited atomically.
##
## On disk every value carries its type, so nothing comes back as the wrong kind (Godot's JSON
## parser turns every number into a float: bug 2 of the 2.0.0 port). Ints are written as decimal
## strings, so even a saturated Long counter survives exactly:
##   {"format": "pocket-arcade-prefs", "version": 1, "prefs": {"tokens": {"i": "17"}, "muted": {"b": true},
##    "hat": {"s": "hat_cap"}, "owned": {"ss": ["outfit_red"]}}}
##
## A write goes to `<path>.tmp`, is flushed, then the current file is copied to `<path>.bak` and
## the temp file renamed over it. A file that can't be read falls back to the backup, then to an
## empty map (DataStore's ReplaceFileCorruptionHandler): the player starts afresh, the app runs.

const FORMAT := "pocket-arcade-prefs"
const VERSION := 1

var path: String
var _prefs: Dictionary = {}
var _loaded := false
## Where the last load came from: "file", "backup", "empty" (none on disk) or "corrupt".
var loaded_from := ""
## Test hook: every write fails as if the disk refused it.
var fail_writes := false


func _init(file_path: String) -> void:
	path = file_path


func exists_on_disk() -> bool:
	return FileAccess.file_exists(path) or FileAccess.file_exists(path + ".bak")


## The current preferences (a copy: edit them through [method edit]).
func read() -> Dictionary:
	_ensure_loaded()
	return _prefs.duplicate(true)


## Applies [param transform] (called with a mutable copy of the map) as one atomic edit.
## Returns false if the write failed; then nothing changed, in memory or on disk.
func edit(transform: Callable) -> bool:
	_ensure_loaded()
	var p := _prefs.duplicate(true)
	transform.call(p)
	if not _write(p):
		return false
	_prefs = p
	return true


## Drops what is in memory, so the next read comes from disk (a relaunch, in tests).
func forget() -> void:
	_loaded = false
	_prefs = {}


func _ensure_loaded() -> void:
	if _loaded:
		return
	_loaded = true
	var main := _read_file(path)
	if main != null:
		_prefs = main
		loaded_from = "file"
		return
	var bak := _read_file(path + ".bak")
	if bak != null:
		_prefs = bak
		loaded_from = "backup"
		return
	_prefs = {}
	loaded_from = "corrupt" if FileAccess.file_exists(path) else "empty"


## Reads a prefs file, or null if it is missing or not one of ours.
static func _read_file(file_path: String) -> Variant:
	if not FileAccess.file_exists(file_path):
		return null
	var bytes := FileAccess.get_file_as_bytes(file_path)
	# Ours always starts with "{": anything else is damaged (and isn't fed to the UTF-8 decoder).
	if bytes.is_empty() or bytes[0] != 0x7B:
		return null
	var json := JSON.new()
	if json.parse(bytes.get_string_from_utf8()) != OK:
		return null
	var root: Variant = json.data
	if not (root is Dictionary) or root.get("format") != FORMAT or not (root.get("prefs") is Dictionary):
		return null
	return decode(root["prefs"])


## A typed map back from its JSON form; entries of an unknown kind are skipped.
static func decode(raw: Dictionary) -> Dictionary:
	var out: Dictionary = {}
	for key in raw:
		var cell: Variant = raw[key]
		if not (cell is Dictionary) or cell.size() != 1:
			continue
		var kind: String = cell.keys()[0]
		var v: Variant = cell[kind]
		match kind:
			"i":
				if v is String and (v as String).is_valid_int():
					out[str(key)] = (v as String).to_int()
				elif v is float or v is int:
					out[str(key)] = int(v)
			"b":
				if v is bool:
					out[str(key)] = v
			"s":
				if v is String:
					out[str(key)] = v
			"ss":
				if v is Array:
					var arr: Array[String] = []
					for s in v:
						if s is String and not arr.has(s):
							arr.append(s)
					out[str(key)] = arr
	return out


## A typed map in its JSON form.
static func encode(prefs: Dictionary) -> Dictionary:
	var out: Dictionary = {}
	for key in prefs:
		var v: Variant = prefs[key]
		match typeof(v):
			TYPE_INT:
				out[key] = {"i": str(v)}
			TYPE_BOOL:
				out[key] = {"b": v}
			TYPE_STRING, TYPE_STRING_NAME:
				out[key] = {"s": str(v)}
			TYPE_ARRAY, TYPE_PACKED_STRING_ARRAY:
				var arr: Array = []
				for s in v:
					arr.append(str(s))
				out[key] = {"ss": arr}
			TYPE_FLOAT:
				# Nothing saved is fractional; a float here is a whole number that lost its type.
				out[key] = {"i": str(int(v))}
	return out


func _write(p: Dictionary) -> bool:
	if fail_writes:
		return false
	var dir := path.get_base_dir()
	if not DirAccess.dir_exists_absolute(dir):
		DirAccess.make_dir_recursive_absolute(dir)
	var text := JSON.stringify({"format": FORMAT, "version": VERSION, "prefs": encode(p)})
	var tmp := path + ".tmp"
	var f := FileAccess.open(tmp, FileAccess.WRITE)
	if f == null:
		return false
	f.store_string(text)
	f.flush()
	var err := f.get_error()
	f.close()
	if err != OK:
		DirAccess.remove_absolute(tmp)
		return false
	# Keep the last good generation: never back up a file we couldn't read.
	if _read_file(path) != null:
		var bak_tmp := path + ".bak.tmp"
		if DirAccess.copy_absolute(path, bak_tmp) == OK:
			_replace(bak_tmp, path + ".bak")
	return _replace(tmp, path)


## Renames [param from] over [param to] (replacing it where the platform's rename won't).
static func _replace(from: String, to: String) -> bool:
	if DirAccess.rename_absolute(from, to) == OK:
		return true
	if FileAccess.file_exists(to):
		DirAccess.remove_absolute(to)
	return DirAccess.rename_absolute(from, to) == OK
