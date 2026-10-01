class_name SaveMigration
extends RefCounted
## First launch of the Godot edition: imports progress and options from the apps it replaces,
## reading their files and never touching them.
##
##  - build-13 (Kotlin): the two AndroidX DataStores in the app's files dir,
##    `datastore/pocket_arcade.preferences_pb` and `datastore/pocket_arcade_settings.preferences_pb`
##    (protobuf: PreferenceMap{ map<string, Value> preferences = 1 }, Value oneof
##    bool=1 float=2 int=3 long=4 string=5 string_set=6 double=7 bytes=8).
##  - 2.0.0 (Codex's Godot port): `pocket_arcade_godot.json` (or its `.bak`), whose numbers are
##    all floats. Its core fields are newer than the DataStore's (it imported the DataStore on its
##    own first launch and was played since), so they win: tokens, tickets, owned items (merged
##    with the DataStore's), hat, outfit, plush collection, total plays, refill and spare-token
##    times, muted and first person. High scores keep the higher of each. Everything build-13
##    added that 2.0.0 never had (stats, unlocks, collectibles, the arcade name, score tables)
##    comes from the DataStore.

const SAVE_FILE := "pocket_arcade_save_v1.json"
const SETTINGS_FILE := "pocket_arcade_settings_v1.json"
const DATASTORE_SAVE := "datastore/pocket_arcade.preferences_pb"
const DATASTORE_SETTINGS := "datastore/pocket_arcade_settings.preferences_pb"
const CODEX_SAVE := "pocket_arcade_godot.json"
## Written into the new save, so the import is visible (and never redone).
const MIGRATED_KEY := "pa_migrated_from"


# ---------------------------------------------------------------- the DataStore protobuf

static func _varint(bytes: PackedByteArray, cursor: Array) -> int:
	var value := 0
	var shift := 0
	while cursor[0] < bytes.size() and shift < 64:
		var b := bytes[cursor[0]]
		cursor[0] += 1
		value |= (b & 0x7F) << shift
		if b < 0x80:
			return value
		shift += 7
	cursor[0] = -1
	return 0


## The fields of one message as [field, value] pairs (value: int, or PackedByteArray for wire 2).
## A malformed message yields what parsed before the damage.
static func _fields(bytes: PackedByteArray) -> Array:
	var out: Array = []
	var cursor := [0]
	while cursor[0] >= 0 and cursor[0] < bytes.size():
		var tag := _varint(bytes, cursor)
		if cursor[0] < 0:
			break
		var wire := tag & 7
		var field := tag >> 3
		if field == 0:
			break
		if wire == 0:
			var v := _varint(bytes, cursor)
			if cursor[0] < 0:
				break
			out.append([field, v])
		elif wire == 1:
			if cursor[0] + 8 > bytes.size():
				break
			cursor[0] += 8
		elif wire == 2:
			var size := _varint(bytes, cursor)
			if cursor[0] < 0 or size < 0 or cursor[0] + size > bytes.size():
				break
			out.append([field, bytes.slice(cursor[0], cursor[0] + size)])
			cursor[0] += size
		elif wire == 5:
			if cursor[0] + 4 > bytes.size():
				break
			cursor[0] += 4
		else:
			break
	return out


## A DataStore Preferences file as a typed map: bool, int (int32 and int64), String, Array[String].
## Floats, doubles and bytes are skipped (none of the game's keys use them).
static func decode_datastore(bytes: PackedByteArray) -> Dictionary:
	var prefs: Dictionary = {}
	for entry in _fields(bytes):
		if entry[0] != 1 or not (entry[1] is PackedByteArray):
			continue
		var key := ""
		var value_bytes := PackedByteArray()
		var has_value := false
		for part in _fields(entry[1]):
			if part[0] == 1 and part[1] is PackedByteArray:
				key = (part[1] as PackedByteArray).get_string_from_utf8()
			elif part[0] == 2 and part[1] is PackedByteArray:
				value_bytes = part[1]
				has_value = true
		if key.is_empty() or not has_value:
			continue
		for v in _fields(value_bytes):
			match int(v[0]):
				1:
					if v[1] is int:
						prefs[key] = (v[1] as int) != 0
				3:
					if v[1] is int:
						prefs[key] = MathUtil.i32(v[1])
				4:
					if v[1] is int:
						prefs[key] = v[1]
				5:
					if v[1] is PackedByteArray:
						prefs[key] = (v[1] as PackedByteArray).get_string_from_utf8()
				6:
					if v[1] is PackedByteArray:
						var strings: Array[String] = []
						for s in _fields(v[1]):
							if s[0] == 1 and s[1] is PackedByteArray:
								strings.append((s[1] as PackedByteArray).get_string_from_utf8())
						prefs[key] = strings
	return prefs


## The other way (tests and fixtures): a typed map as DataStore protobuf bytes.
static func encode_datastore(prefs: Dictionary, int64_keys: Array = []) -> PackedByteArray:
	var out := PackedByteArray()
	for key in prefs:
		var v: Variant = prefs[key]
		var value := PackedByteArray()
		match typeof(v):
			TYPE_BOOL:
				value.append_array(_tag(1, 0))
				value.append_array(_enc_varint(1 if v else 0))
			TYPE_INT:
				var field := 4 if int64_keys.has(key) else 3
				value.append_array(_tag(field, 0))
				value.append_array(_enc_varint(v))
			TYPE_STRING:
				value.append_array(_len_field(5, (v as String).to_utf8_buffer()))
			TYPE_ARRAY:
				var set_bytes := PackedByteArray()
				for s in v:
					set_bytes.append_array(_len_field(1, str(s).to_utf8_buffer()))
				value.append_array(_len_field(6, set_bytes))
		var entry := _len_field(1, str(key).to_utf8_buffer())
		entry.append_array(_len_field(2, value))
		out.append_array(_len_field(1, entry))
	return out


static func _tag(field: int, wire: int) -> PackedByteArray:
	return _enc_varint((field << 3) | wire)


static func _enc_varint(v: int) -> PackedByteArray:
	var out := PackedByteArray()
	var x := v
	for i in 10:
		var b := x & 0x7F
		x = (x >> 7) & 0x01FFFFFFFFFFFFFF
		if x == 0:
			out.append(b)
			return out
		out.append(b | 0x80)
	return out


static func _len_field(field: int, payload: PackedByteArray) -> PackedByteArray:
	var out := _tag(field, 2)
	out.append_array(_enc_varint(payload.size()))
	out.append_array(payload)
	return out


# ---------------------------------------------------------------- the 2.0.0 JSON save

## Codex's 2.0.0 save as typed values, or null if missing or unreadable. Its numbers were saved by
## Godot's JSON as floats; every one of them is a whole number and comes back as an int.
static func read_codex(path: String) -> Variant:
	for p in [path, path + ".bak"]:
		if not FileAccess.file_exists(p):
			continue
		var bytes := FileAccess.get_file_as_bytes(p)
		if bytes.is_empty() or bytes.has(0) or bytes[0] != 0x7B:
			continue
		var json := JSON.new()
		if json.parse(bytes.get_string_from_utf8()) != OK or not (json.data is Dictionary):
			continue
		return _codex_typed(json.data)
	return null


static func _num(v: Variant, d: int = 0) -> int:
	if v is float or v is int:
		return int(v)
	if v is String and KParse.to_long_or_null(v) != null:
		return KParse.to_long_or_null(v)
	return d


static func _codex_typed(raw: Dictionary) -> Dictionary:
	var out: Dictionary = {}
	for k in ["tokens", "tickets", "last_refill_day", "spare_token_at", "total_plays"]:
		if raw.has(k):
			out[k] = _num(raw[k])
	for k in ["muted", "first_person"]:
		if raw.has(k) and raw[k] is bool:
			out[k] = raw[k]
	for k in ["hat", "outfit"]:
		if raw.has(k) and raw[k] is String:
			out[k] = raw[k]
	if raw.get("owned") is Array:
		var owned: Array[String] = []
		for id in raw["owned"]:
			if id is String and not owned.has(id):
				owned.append(id)
		out["owned"] = owned
	for k in ["collection", "high_scores"]:
		if raw.get(k) is Dictionary:
			var m: Dictionary = {}
			for id in raw[k]:
				var n := _num(raw[k][id], -1)
				if n >= 0:
					m[str(id)] = n
			out[k] = m
	return out


# ---------------------------------------------------------------- merging

## The new save's preferences from what was found ([param datastore] typed DataStore map, or {};
## [param codex] typed 2.0.0 save, or null). Keys and encodings are ArcadeRepository's.
static func merge(datastore: Dictionary, codex: Variant) -> Dictionary:
	var p: Dictionary = {}
	# Everything the DataStore holds, as it was (build-13's own keys, untouched).
	for k in datastore:
		p[k] = datastore[k]
	if codex == null:
		return p
	var c: Dictionary = codex
	for k in ["tokens", "tickets", "total_plays", "last_refill_day", "spare_token_at"]:
		if c.has(k):
			p[k] = c[k]
	for k in ["muted", "first_person", "hat", "outfit"]:
		if c.has(k):
			p[k] = c[k]
	if c.has("owned"):
		var owned: Array[String] = []
		for id in datastore.get(ArcadeRepository.OWNED, []):
			if not owned.has(str(id)):
				owned.append(str(id))
		for id in c["owned"]:
			if not owned.has(id):
				owned.append(id)
		p[ArcadeRepository.OWNED] = owned
	if c.has("collection"):
		p[ArcadeRepository.COLLECTION] = ArcadeRepository.encode_collection(c["collection"])
	if c.has("high_scores"):
		var hs: Dictionary = c["high_scores"]
		for id in hs:
			var key := ArcadeRepository.high_score_key(str(id))
			var old := int(p.get(key, 0)) if p.get(key) is int else 0
			p[key] = maxi(old, int(hs[id]))
	return p


# ---------------------------------------------------------------- the first launch

class Result:
	extends RefCounted
	var save_imported := false
	var settings_imported := false
	## Which old saves were found: "build-13", "2.0.0".
	var sources: PackedStringArray = PackedStringArray()


## Runs once per install: if the Godot save (or settings) file doesn't exist yet, builds it from
## whatever older saves exist in [param dir] (the app's files dir, `user://` on device).
static func run(dir: String, save_store: PrefsStore, settings_store: PrefsStore) -> Result:
	var r := Result.new()
	if not save_store.exists_on_disk():
		var ds: Dictionary = {}
		var ds_path := dir.path_join(DATASTORE_SAVE)
		if FileAccess.file_exists(ds_path):
			ds = decode_datastore(FileAccess.get_file_as_bytes(ds_path))
			if not ds.is_empty():
				r.sources.append("build-13")
		var codex: Variant = read_codex(dir.path_join(CODEX_SAVE))
		if codex != null:
			r.sources.append("2.0.0")
		if not ds.is_empty() or codex != null:
			var merged := merge(ds, codex)
			merged[MIGRATED_KEY] = "+".join(r.sources)
			r.save_imported = save_store.edit(func(p: Dictionary) -> void:
				p.clear()
				p.merge(merged, true))
	if not settings_store.exists_on_disk():
		var sp := dir.path_join(DATASTORE_SETTINGS)
		if FileAccess.file_exists(sp):
			var sprefs := decode_datastore(FileAccess.get_file_as_bytes(sp))
			if not sprefs.is_empty():
				var s := SettingsStore.read(sprefs)
				r.settings_imported = settings_store.edit(func(p: Dictionary) -> void:
					SettingsStore.write(p, s))
	return r
