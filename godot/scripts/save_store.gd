extends Node

const SAVE_PATH = "user://pocket_arcade_godot.json"
const LEGACY_PATHS = ["user://datastore/pocket_arcade.preferences_pb", "user://../datastore/pocket_arcade.preferences_pb"]
var storage_path: String = SAVE_PATH
var data: Dictionary = {
	"tokens":20, "tickets":0, "last_refill_day":0, "owned":["outfit_red"],
	"hat":"", "outfit":"outfit_red", "collection":{}, "high_scores":{},
	"muted":false, "spare_token_at":0, "total_plays":0, "first_person":false
}
var migrated: bool = false

func _ready() -> void:
	load_progress()
	apply_daily()
	save()

func read_json(path: String) -> Variant:
	if not FileAccess.file_exists(path):
		return null
	var file = FileAccess.open(path, FileAccess.READ)
	if file == null:
		return null
	var bytes = file.get_buffer(file.get_length())
	file.close()
	if bytes.has(0):
		return null
	var json = JSON.new()
	var result = json.parse(bytes.get_string_from_utf8())
	if result != OK or not json.data is Dictionary:
		return null
	return json.data

func load_progress(legacy_paths: Array = LEGACY_PATHS) -> void:
	migrated = false
	var loaded = read_json(storage_path)
	if loaded == null:
		loaded = read_json(storage_path + ".bak")
	if loaded is Dictionary:
		data.merge(loaded, true)
	else:
		migrate_legacy(legacy_paths)

func apply_daily() -> void:
	var today = int(Time.get_unix_time_from_datetime_string(Time.get_date_string_from_system()) / 86400.0)
	var last = int(data.last_refill_day)
	if last > 0 and today > last:
		data.tokens += 10
	data.last_refill_day = today

func save() -> bool:
	var temporary = storage_path + ".tmp"
	var backup = storage_path + ".bak"
	var file = FileAccess.open(temporary, FileAccess.WRITE)
	if file == null:
		push_error("Could not save arcade progress")
		return false
	file.store_string(JSON.stringify(data))
	file.flush()
	var write_error = file.get_error()
	file.close()
	if write_error != OK:
		push_error("Could not flush arcade progress: " + error_string(write_error))
		return false
	# Retain a complete, valid generation before replacing the main save. Never
	# overwrite a usable backup with a damaged primary file after recovery.
	var backup_source = storage_path if read_json(storage_path) != null else temporary
	if backup_source == storage_path or read_json(backup) == null:
		var copy_error = DirAccess.copy_absolute(backup_source, backup + ".tmp")
		if copy_error != OK:
			push_error("Could not back up arcade progress: " + error_string(copy_error))
			return false
		var backup_error = DirAccess.rename_absolute(backup + ".tmp", backup)
		if backup_error != OK:
			push_error("Could not replace arcade backup: " + error_string(backup_error))
			return false
	var rename_error = DirAccess.rename_absolute(temporary, storage_path)
	if rename_error != OK:
		push_error("Could not replace arcade progress: " + error_string(rename_error))
		return false
	return true

func spend() -> bool:
	if data.tokens <= 0:
		return false
	data.tokens -= 1
	data.total_plays += 1
	save()
	return true

func reward(id: String, score: int, tickets: int, prizes: Array[String]) -> bool:
	var record = score > int(data.high_scores.get(id, 0))
	data.high_scores[id] = maxi(score, int(data.high_scores.get(id, 0)))
	data.tickets += maxi(0, tickets)
	for prize in prizes:
		data.collection[prize] = int(data.collection.get(prize, 0)) + 1
	save()
	return record

func exchange() -> bool:
	if data.tickets < 40:
		return false
	data.tickets -= 40
	data.tokens += 1
	save()
	return true

func spare() -> bool:
	var now = int(Time.get_unix_time_from_system() * 1000)
	if data.tokens > 0 or now < int(data.spare_token_at):
		return false
	data.tokens = 1
	data.spare_token_at = now + 180000
	save()
	return true

func buy(item: Dictionary) -> bool:
	if item.id not in data.owned:
		if data.tickets < item.price:
			return false
		data.tickets -= item.price
		data.owned.append(item.id)
	if item.kind == "hat":
		data.hat = "" if data.hat == item.id else item.id
	elif item.kind == "outfit":
		data.outfit = item.id
	save()
	return true

# Read AndroidX Preferences protobuf directly, leaving the original file untouched.
# Each map entry is {1:key, 2:Value}; Value is a typed oneof.
func varint(bytes: PackedByteArray, cursor: Array) -> int:
	var value: int = 0
	var shift: int = 0
	while cursor[0] < bytes.size() and shift < 64:
		var byte = bytes[cursor[0]]
		cursor[0] += 1
		value |= (byte & 127) << shift
		if byte < 128:
			break
		shift += 7
	return value

func proto_fields(bytes: PackedByteArray) -> Array:
	var result: Array = []
	var cursor = [0]
	while cursor[0] < bytes.size():
		var tag = varint(bytes, cursor)
		var wire = tag & 7
		var field = tag >> 3
		if field == 0:
			break
		if wire == 0:
			result.append([field, varint(bytes, cursor)])
		elif wire == 2:
			var size = varint(bytes, cursor)
			if size < 0 or cursor[0] + size > bytes.size():
				break
			result.append([field, bytes.slice(cursor[0], cursor[0] + size)])
			cursor[0] += size
		elif wire == 1:
			cursor[0] += 8
		elif wire == 5:
			cursor[0] += 4
		else:
			break
	return result

func decode_preferences(bytes: PackedByteArray) -> Dictionary:
	var prefs: Dictionary = {}
	for entry in proto_fields(bytes):
		if entry[0] != 1 or not entry[1] is PackedByteArray:
			continue
		var key = ""
		var value_bytes = PackedByteArray()
		for part in proto_fields(entry[1]):
			if part[0] == 1 and part[1] is PackedByteArray:
				key = part[1].get_string_from_utf8()
			elif part[0] == 2 and part[1] is PackedByteArray:
				value_bytes = part[1]
		if key.is_empty():
			continue
		for value in proto_fields(value_bytes):
			match value[0]:
				1:
					if value[1] is int:
						prefs[key] = bool(value[1])
				3, 4:
					if value[1] is int:
						prefs[key] = value[1]
				5:
					if value[1] is PackedByteArray:
						prefs[key] = value[1].get_string_from_utf8()
				6:
					if not value[1] is PackedByteArray:
						continue
					var strings: Array = []
					for part in proto_fields(value[1]):
						if part[0] == 1 and part[1] is PackedByteArray:
							strings.append(part[1].get_string_from_utf8())
					prefs[key] = strings
	return prefs

func import_preferences(prefs: Dictionary) -> void:
	for key in data:
		if key in prefs and key not in ["collection", "high_scores"]:
			data[key] = prefs[key]
	for key in prefs:
		if key.begins_with("hs_"):
			data.high_scores[key.trim_prefix("hs_")] = prefs[key]
	for pair in str(prefs.get("collection", "")).split(";", false):
		var parts = pair.split(":")
		if parts.size() == 2:
			data.collection[parts[0]] = int(parts[1])
	if "outfit_red" not in data.owned:
		data.owned.append("outfit_red")

func migrate_legacy(paths: Array = LEGACY_PATHS) -> void:
	for path in paths:
		if FileAccess.file_exists(path):
			var prefs = decode_preferences(FileAccess.get_file_as_bytes(path))
			if not prefs.is_empty():
				import_preferences(prefs)
				migrated = true
				return
