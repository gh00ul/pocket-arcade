extends Node

# Wire fixture follows the official AndroidX Preferences schema:
# https://raw.githubusercontent.com/androidx/androidx/androidx-main/datastore/datastore-preferences-proto/src/main/proto/preferences.proto
# PreferenceMap.preferences=1; Value bool=1, int=3, long=4, string=5, string_set=6.
const PREFERENCES_HEX: String = "0a0c0a06746f6b656e731202187b0a0e0a077469636b657473120318d7230a170a0f6c6173745f726566696c6c5f646179120420aaa1010a300a056f776e6564122732250a0a6861745f77697a6172640a0b6f75746669745f626c75650a0a6465636f725f70616c6d0a130a03686174120c2a0a6861745f77697a6172640a170a066f7574666974120d2a0b6f75746669745f626c75650a2b0a0a636f6c6c656374696f6e121d2a1b706c7573685f626561723a323b706c7573685f676f6c64656e3a310a0b0a056d75746564120208010a190a0e73706172655f746f6b656e5f617412072080e8fd9f8f340a110a0b746f74616c5f706c6179731202182a0a120a0c66697273745f706572736f6e120208010a110a0a68735f73686f6f746572120318944d0a110a0a68735f70696e62616c6c120318e121"
var failures: int = 0
var checks: int = 0
var backup: Dictionary
var backup_bytes: PackedByteArray
var backup_exists: bool = false
var restored: bool = false
var host: Node
var original_storage: String
var test_storage: String

class ProbeGame extends ArcadeGame:
	var events: Array = []
	var canceled: int = 0
	func _init() -> void:
		game_id = "test_probe"
		title = "Test probe"
		duration = 60
	func pointer(action: String, point: Vector2, id: int) -> void:
		events.append([action, point, id])
	func cancel_input() -> void:
		canceled += 1
	func tickets_for_score() -> int:
		return 7

func _ready() -> void:
	backup = SaveStore.data.duplicate(true)
	original_storage = SaveStore.storage_path
	test_storage = "user://host_save_test_%d.json" % Time.get_ticks_usec()
	SaveStore.storage_path = test_storage
	backup_exists = FileAccess.file_exists(SaveStore.SAVE_PATH)
	if backup_exists:
		backup_bytes = FileAccess.get_file_as_bytes(SaveStore.SAVE_PATH)
	SaveStore.data = backup.duplicate(true)
	SaveStore.data.muted = true
	call_deferred("run_tests")

func _exit_tree() -> void:
	restore_save()

func restore_save() -> void:
	if restored or backup == null:
		return
	restored = true
	SaveStore.storage_path = original_storage
	SaveStore.data = backup.duplicate(true)
	for suffix: String in ["", ".bak", ".tmp", ".bak.tmp", ".legacy", ".broken_legacy"]:
		if FileAccess.file_exists(test_storage + suffix):
			DirAccess.remove_absolute(test_storage + suffix)
	if backup_exists:
		var file: FileAccess = FileAccess.open(SaveStore.SAVE_PATH, FileAccess.WRITE)
		file.store_buffer(backup_bytes)
		file.close()
	elif FileAccess.file_exists(SaveStore.SAVE_PATH):
		DirAccess.remove_absolute(SaveStore.SAVE_PATH)

func check(condition: bool, message: String) -> void:
	checks += 1
	if not condition:
		failures += 1
		push_error("HOST/SAVE TEST: " + message)

func reset_data() -> void:
	SaveStore.data = {"tokens":20, "tickets":0, "last_refill_day":0, "owned":["outfit_red"], "hat":"", "outfit":"outfit_red", "collection":{}, "high_scores":{}, "muted":true, "spare_token_at":0, "total_plays":0, "first_person":false}

func find_button(parent: Node, text: String) -> Button:
	for child: Node in parent.get_children():
		if child.is_queued_for_deletion():
			continue
		if child is Button and child.text == text:
			return child
		var found: Button = find_button(child, text)
		if found != null:
			return found
	return null

func send_touch(point: Vector2, id: int, pressed: bool) -> void:
	var event := InputEventScreenTouch.new()
	event.position = get_viewport().get_final_transform() * (point * get_viewport().get_visible_rect().size)
	event.index = id
	event.pressed = pressed
	Input.parse_input_event(event)

func run_tests() -> void:
	reset_data()
	SaveStore.apply_daily()
	var today: int = SaveStore.data.last_refill_day
	check(today > 20000 and SaveStore.data.tokens == 20, "first launch records day without bonus")
	SaveStore.apply_daily()
	check(SaveStore.data.tokens == 20, "same day does not refill twice")
	SaveStore.data.last_refill_day = today - 8
	SaveStore.apply_daily()
	check(SaveStore.data.tokens == 30, "eight missed days grant one daily refill")
	SaveStore.data.last_refill_day = today + 1
	SaveStore.apply_daily()
	check(SaveStore.data.tokens == 30 and SaveStore.data.last_refill_day == today, "clock rollback resyncs without bonus")
	SaveStore.data.tokens = 1
	check(SaveStore.spend() and SaveStore.data.tokens == 0 and SaveStore.data.total_plays == 1, "spend updates token and plays")
	check(not SaveStore.spend() and SaveStore.data.tokens == 0 and SaveStore.data.total_plays == 1, "empty balance cannot spend")
	check(SaveStore.spare() and SaveStore.data.tokens == 1, "broke player receives a spare")
	check(not SaveStore.spare(), "spare requires broke balance")
	SaveStore.data.tokens = 0
	check(not SaveStore.spare(), "spare cooldown enforced after spending")
	SaveStore.data.spare_token_at = 0
	check(SaveStore.spare(), "expired spare cooldown can be claimed")
	SaveStore.data.tickets = 39
	check(not SaveStore.exchange() and SaveStore.data.tickets == 39, "exchange requires 40 tickets")
	SaveStore.data.tickets = 40
	check(SaveStore.exchange() and SaveStore.data.tokens == 2 and SaveStore.data.tickets == 0, "40-ticket exchange exact balances")
	var hat: Dictionary = ArcadeCatalog.items("hat")[0]
	check(not SaveStore.buy(hat) and hat.id not in SaveStore.data.owned, "unaffordable shop item changes nothing")
	SaveStore.data.tickets = hat.price
	check(SaveStore.buy(hat) and SaveStore.data.hat == hat.id and SaveStore.data.tickets == 0, "hat purchase equips and charges once")
	check(SaveStore.buy(hat) and SaveStore.data.hat == "" and SaveStore.data.tickets == 0, "owned hat toggle is free")
	check(SaveStore.buy(hat) and SaveStore.data.hat == hat.id, "owned hat can be reequipped")
	var outfit: Dictionary = ArcadeCatalog.items("outfit")[1]
	SaveStore.data.tickets = outfit.price
	check(SaveStore.buy(outfit) and SaveStore.data.outfit == outfit.id, "outfit purchase equips")
	check(SaveStore.buy(outfit) and SaveStore.data.outfit == outfit.id and SaveStore.data.tickets == 0, "owned outfit stays worn without charge")
	var prizes: Array[String] = ["plush_bear", "plush_bear"]
	check(SaveStore.reward("pinball", 500, 7, prizes), "first score is record")
	check(SaveStore.data.collection.plush_bear == 2 and SaveStore.data.tickets == 7, "round prizes and tickets are accumulated")
	check(not SaveStore.reward("pinball", 400, -5, []), "lower score cannot overwrite record")
	check(SaveStore.data.high_scores.pinball == 500 and SaveStore.data.tickets == 7, "negative ticket award is ignored")
	var on_disk: Dictionary = JSON.parse_string(FileAccess.get_file_as_string(SaveStore.storage_path))
	check(on_disk.high_scores.pinball == 500 and on_disk.collection.plush_bear == 2, "save replacement persists current progress")
	var prefs: Dictionary = SaveStore.decode_preferences(PREFERENCES_HEX.hex_decode())
	check(prefs.tokens == 123 and prefs.tickets == 4567, "protobuf int32 fields")
	check(prefs.last_refill_day == 20650 and prefs.spare_token_at == 1790800000000, "protobuf int64 retains millisecond timestamp")
	check(prefs.muted and prefs.first_person, "protobuf boolean fields")
	check(prefs.owned == ["hat_wizard", "outfit_blue", "decor_palm"] and prefs.hat == "hat_wizard", "protobuf strings and string sets")
	reset_data()
	SaveStore.import_preferences(prefs)
	check(SaveStore.data.high_scores == {"shooter":9876, "pinball":4321}, "legacy scores migrate by hs_ prefix")
	check(SaveStore.data.collection == {"plush_bear":2, "plush_golden":1}, "legacy collection migrates")
	check(SaveStore.data.tokens == 123 and SaveStore.data.total_plays == 42 and SaveStore.data.outfit == "outfit_blue", "legacy progress and outfit preserved")
	check("outfit_red" in SaveStore.data.owned and "decor_palm" in SaveStore.data.owned, "migration preserves owned decor and adds default outfit")
	check(SaveStore.decode_preferences("0aff7f0102".hex_decode()).is_empty(), "truncated protobuf safely ignored")
	check(SaveStore.decode_preferences("0a0b0a056f776e656412023001".hex_decode()).is_empty(), "wrong wire type for string set safely ignored")
	check(SaveStore.decode_preferences("0a0408011200".hex_decode()).is_empty(), "wrong wire type for map key safely ignored")
	# An unknown fixed-width field does not disrupt following map entries.
	check(SaveStore.decode_preferences("7d00000000".hex_decode() + PREFERENCES_HEX.hex_decode()).tokens == 123, "unknown protobuf fixed32 skipped")
	# Recover a corrupted primary from its valid backup, then fall back to the
	# legacy AndroidX file if both JSON generations are unavailable.
	reset_data()
	SaveStore.data.tokens = 11
	check(SaveStore.save(), "first save creates valid primary and backup")
	SaveStore.data.tokens = 12
	check(SaveStore.save(), "second save replaces primary atomically")
	check(SaveStore.read_json(test_storage + ".bak").tokens == 11, "backup retains previous complete generation")
	write_fixture(test_storage, "{broken".to_utf8_buffer())
	reset_data()
	SaveStore.load_progress([])
	check(SaveStore.data.tokens == 11, "corrupted primary recovers valid backup without parse error")
	check(SaveStore.save() and SaveStore.read_json(test_storage).tokens == 11, "recovered state repairs primary")
	check(SaveStore.read_json(test_storage + ".bak").tokens == 11, "recovery does not overwrite valid backup with corrupt data")
	write_fixture(test_storage, PackedByteArray([0, 123, 98, 114, 111, 107, 101, 110]))
	write_fixture(test_storage + ".bak", "[]".to_utf8_buffer())
	write_fixture(test_storage + ".legacy", PREFERENCES_HEX.hex_decode())
	write_fixture(test_storage + ".broken_legacy", "0aff7f0102".hex_decode())
	reset_data()
	SaveStore.load_progress([test_storage + ".broken_legacy", test_storage + ".legacy"])
	check(SaveStore.migrated and SaveStore.data.tokens == 123 and SaveStore.data.tickets == 4567, "damaged JSON falls back to usable legacy progress")
	check(FileAccess.get_file_as_bytes(test_storage + ".legacy") == PREFERENCES_HEX.hex_decode(), "migration leaves legacy file untouched")
	check(SaveStore.save() and SaveStore.read_json(test_storage).tokens == 123, "migrated recovery saves valid JSON")
	reset_data()
	SaveStore.load_progress([test_storage + ".broken_legacy"])
	check(SaveStore.data.tokens == 123 and not SaveStore.migrated, "valid JSON wins on subsequent launch")
	reset_data()
	var main_script: Script = load("res://scripts/main.gd")
	host = main_script.new()
	add_child(host)
	host.set_process(false)
	host.enter_hall()
	host.start_game()
	check(host.game == null and SaveStore.data.tokens == 20, "no selected game cannot spend")
	host.selected = 9
	host.start_game()
	var active_game: ArcadeGame = host.game
	active_game.set_physics_process(false)
	check(SaveStore.data.tokens == 19 and SaveStore.data.total_plays == 1, "host start spends exactly one token")
	check(not active_game.running and host.countdown > 0, "host countdown holds gameplay")
	host.start_game()
	check(host.game == active_game and SaveStore.data.tokens == 19, "duplicate start cannot replace game or spend again")
	# Recover enough state to test remaining paths even against a failing host.
	active_game = host.game
	active_game.set_physics_process(false)
	host._process(3.2)
	check(active_game.running and host.countdown < 0, "countdown starts the round")
	host.pause_game()
	check(not active_game.running and host.modal, "pause stops simulation and shows modal")
	var resume: Button = find_button(host.overlay, "KEEP PLAYING")
	check(resume != null, "pause has resume action")
	if resume != null:
		resume.pressed.emit()
	check(active_game.running and not host.modal, "resume restarts active round")
	host.leave_game()
	check(host.game == null and host.hall.visible and not host.modal, "leave restores arcade")
	await get_tree().process_frame
	var probe := ProbeGame.new()
	host.add_child(probe)
	probe.set_physics_process(false)
	probe.running = true
	host.game = probe
	probe.round_finished.connect(host.round_complete)
	host.build_hud()
	await get_tree().process_frame
	check(not host.metrics.visible and host.game_score.global_position.y + host.game_score.size.y <= 120, "game HUD fits inside reserved top 120 pixels (bottom=%s)" % (host.game_score.global_position.y + host.game_score.size.y))
	send_touch(Vector2(0.35, 0.5), 12, true)
	await get_tree().process_frame
	check(probe.events.size() == 1 and probe.events[0][0] == "down" and host.game_touches.has(12), "playfield touch reaches game")
	var pause: Button = find_button(host.hud, "II")
	var pause_center: Vector2 = (pause.global_position + pause.size * 0.5) / get_viewport().get_visible_rect().size
	send_touch(pause_center, 12, false)
	await get_tree().process_frame
	check(probe.events.size() == 2 and probe.events[1][0] == "up" and not host.game_touches.has(12), "release over HUD clears held game touch")
	check(not host.modal, "playfield release over pause does not activate button")
	send_touch(pause_center, 15, true)
	await get_tree().process_frame
	send_touch(pause_center, 15, false)
	await get_tree().process_frame
	check(host.modal and not probe.running, "touch starting on pause button activates normal GUI")
	resume = find_button(host.overlay, "KEEP PLAYING")
	if resume != null:
		var resume_center: Vector2 = (resume.global_position + resume.size * 0.5) / get_viewport().get_visible_rect().size
		send_touch(resume_center, 15, true)
		await get_tree().process_frame
		send_touch(resume_center, 15, false)
		await get_tree().process_frame
	check(not host.modal and probe.running, "touch resumes through normal GUI button")
	send_touch(Vector2(0.4, 0.55), 14, true)
	await get_tree().process_frame
	var events_before_cancel: int = probe.events.size()
	var cancels_before: int = probe.canceled
	var cancellation := InputEventScreenTouch.new()
	cancellation.index = 14
	cancellation.canceled = true
	cancellation.pressed = false
	cancellation.position = get_viewport().get_visible_rect().size * Vector2(0.4, 0.55)
	Input.parse_input_event(cancellation)
	await get_tree().process_frame
	check(probe.canceled == cancels_before + 1 and probe.events.size() == events_before_cancel and host.game_touches.is_empty(), "Android touch cancellation cancels input without firing release action")
	send_touch(Vector2(0.4, 0.55), 13, true)
	await get_tree().process_frame
	host.pause_game()
	check(probe.canceled > 0 and host.game_touches.is_empty(), "pause cancels outstanding touches")
	resume = find_button(host.overlay, "KEEP PLAYING")
	resume.pressed.emit()
	var before_tickets: int = SaveStore.data.tickets
	probe.score = 50
	probe.finish()
	check(SaveStore.data.tickets == before_tickets + 7 and host.modal, "completed round pays tickets")
	probe.finish()
	check(SaveStore.data.tickets == before_tickets + 7, "finish signal is emitted only once")
	host.round_complete()
	check(SaveStore.data.tickets == before_tickets + 7, "host ignores duplicate payout callback")
	host.back()
	check(host.game == null and not host.modal, "Android back leaves completed round")
	host.selected = 9
	host.start_game()
	host.game.set_physics_process(false)
	host.pause_game()
	var paused_countdown: float = host.countdown
	host._process(2.0)
	check(host.countdown == paused_countdown and not host.game.running, "pausing countdown preserves remaining countdown")
	resume = find_button(host.overlay, "KEEP PLAYING")
	resume.pressed.emit()
	host._process(3.2)
	check(host.game.running and host.countdown < 0, "paused countdown resumes without stale label access")
	host.leave_game()
	SaveStore.data.tokens = 0
	host.start_game()
	check(host.game == null and host.modal and SaveStore.data.tokens == 0, "broke start opens token kiosk without creating game")
	host.close_panel()
	SaveStore.data.last_refill_day = today - 1
	var before_tokens: int = SaveStore.data.tokens
	host._notification(NOTIFICATION_APPLICATION_RESUMED)
	check(SaveStore.data.tokens == before_tokens + 10, "foreground return grants next-day refill")
	host.queue_free()
	await get_tree().process_frame
	restore_save()
	check(SaveStore.data == backup, "original save dictionary restored")
	check(FileAccess.get_file_as_bytes(SaveStore.SAVE_PATH) == backup_bytes if backup_exists else not FileAccess.file_exists(SaveStore.SAVE_PATH), "original save bytes restored")
	print("Host/save checks: %d checks, %d failures" % [checks, failures])
	get_tree().quit(1 if failures else 0)

func write_fixture(path: String, bytes: PackedByteArray) -> void:
	var file: FileAccess = FileAccess.open(path, FileAccess.WRITE)
	file.store_buffer(bytes)
	file.close()
