extends PaTest
## First-launch import from build-13's DataStores and from 2.0.0's JSON save (Godot-only).

## The AndroidX Preferences fixture from 2.0.0's tests (tokens 123, tickets 4567, last refill day
## 20650, owned [hat_wizard, outfit_blue, decor_palm], hat_wizard, outfit_blue, collection
## plush_bear:2;plush_golden:1, muted, spare token at 1790800000000, 42 plays, first person,
## hs_shooter 9876, hs_pinball 4321).
const PREFERENCES_HEX := "0a0c0a06746f6b656e731202187b0a0e0a077469636b657473120318d7230a170a0f6c6173745f726566696c6c5f646179120420aaa1010a300a056f776e6564122732250a0a6861745f77697a6172640a0b6f75746669745f626c75650a0a6465636f725f70616c6d0a130a03686174120c2a0a6861745f77697a6172640a170a066f7574666974120d2a0b6f75746669745f626c75650a2b0a0a636f6c6c656374696f6e121d2a1b706c7573685f626561723a323b706c7573685f676f6c64656e3a310a0b0a056d75746564120208010a190a0e73706172655f746f6b656e5f617412072080e8fd9f8f340a110a0b746f74616c5f706c6179731202182a0a120a0c66697273745f706572736f6e120208010a110a0a68735f73686f6f746572120318944d0a110a0a68735f70696e62616c6c120318e121"

var fx: RepoFixture


func before_each() -> void:
	fx = RepoFixture.new()


func after_each() -> void:
	fx.cleanup()


func _write(rel: String, bytes: PackedByteArray) -> String:
	var p := fx.dir.path_join(rel)
	DirAccess.make_dir_recursive_absolute(p.get_base_dir())
	var f := FileAccess.open(p, FileAccess.WRITE)
	f.store_buffer(bytes)
	f.close()
	return p


## A build-13 save with every key ArcadeRepository.kt defines (plus per-game keys).
func _build13_prefs() -> Dictionary:
	return {
		"tokens": 31, "tickets": 777, "last_refill_day": 20700, "owned": ["outfit_red", "hat_cap", "decor_lava"],
		"hat": "hat_cap", "outfit": "outfit_red", "collection": "plush_bear:4;plush_cat:1", "muted": false,
		"spare_token_at": 1790000000000, "total_plays": 55, "first_person": false,
		"stats": "plays:racer:12;tickets:earned:5000000000;photos:3", "unlocked": "tutorial_done;first_win",
		"collectibles": "fish:perch:2", "arcade_name": "NEON NOOK",
		"hs_racer": 4100, "hs_pinball": 9000, "scores_racer": "ABC:4100,DEF:2000",
	}


func _build13_settings() -> Dictionary:
	return {
		"look_percent": 140, "invert_y": true, "left_handed": true, "fov_deg": 85, "run_latch": false,
		"reduce_motion": true, "haptics": false, "sfx_percent": 30, "ambience_percent": 0, "music_percent": 60,
		"haptics_percent": 40, "tilt_steering": true, "gfx_quality": 1, "gfx_frame_cap": 30,
	}


const INT64 := ["last_refill_day", "spare_token_at"]


func _codex_json() -> String:
	# As 2.0.0 wrote it: Godot's JSON.stringify, every number a float.
	return '{"tokens":17.0,"tickets":250.0,"last_refill_day":20727.0,"owned":["outfit_red","hat_cap","hat_crown"],"hat":"hat_crown","outfit":"outfit_blue","collection":{"plush_bear":5.0,"plush_whale":1.0},"high_scores":{"racer":3000.0,"pinball":12000.0,"claw":450.0},"muted":true,"spare_token_at":1790999999000.0,"total_plays":70.0,"first_person":true}'


func _stores() -> Array:
	return [PrefsStore.new(fx.dir + "/" + SaveMigration.SAVE_FILE), PrefsStore.new(fx.dir + "/" + SaveMigration.SETTINGS_FILE)]


# ---- the protobuf reader

func test_the_androidx_fixture_decodes_every_field() -> void:
	var p := SaveMigration.decode_datastore(PREFERENCES_HEX.hex_decode())
	assert_eq(123, p["tokens"])
	assert_eq(4567, p["tickets"])
	assert_eq(20650, p["last_refill_day"])
	assert_eq(1790800000000, p["spare_token_at"])
	assert_eq(true, p["muted"])
	assert_eq(true, p["first_person"])
	assert_eq(["hat_wizard", "outfit_blue", "decor_palm"], p["owned"])
	assert_eq("hat_wizard", p["hat"])
	assert_eq("outfit_blue", p["outfit"])
	assert_eq("plush_bear:2;plush_golden:1", p["collection"])
	assert_eq(42, p["total_plays"])
	assert_eq(9876, p["hs_shooter"])
	assert_eq(4321, p["hs_pinball"])


func test_the_encoder_writes_what_the_reader_reads() -> void:
	var p := _build13_prefs()
	var back := SaveMigration.decode_datastore(SaveMigration.encode_datastore(p, INT64))
	assert_eq(p.size(), back.size())
	for k in p:
		assert_eq(p[k], back[k], k)


func test_damaged_protobuf_is_read_safely() -> void:
	assert_true(SaveMigration.decode_datastore("0aff7f0102".hex_decode()).is_empty(), "truncated")
	assert_true(SaveMigration.decode_datastore("0a0b0a056f776e656412023001".hex_decode()).is_empty(), "wrong wire type for a string set")
	assert_true(SaveMigration.decode_datastore("0a0408011200".hex_decode()).is_empty(), "wrong wire type for a key")
	assert_eq(123, SaveMigration.decode_datastore("7d00000000".hex_decode() + PREFERENCES_HEX.hex_decode())["tokens"], "unknown fixed32 skipped")
	var junk := PackedByteArray()
	junk.resize(64)
	junk.fill(0xFF)
	assert_true(SaveMigration.decode_datastore(junk).is_empty())
	assert_true(SaveMigration.decode_datastore(PackedByteArray()).is_empty())


# ---- from build-13

func test_every_build13_key_is_imported_and_the_old_files_are_untouched() -> void:
	var save_bytes := SaveMigration.encode_datastore(_build13_prefs(), INT64)
	var settings_bytes := SaveMigration.encode_datastore(_build13_settings())
	var sp := _write(SaveMigration.DATASTORE_SAVE, save_bytes)
	var tp := _write(SaveMigration.DATASTORE_SETTINGS, settings_bytes)
	var stores := _stores()
	var r := SaveMigration.run(fx.dir, stores[0], stores[1])
	assert_true(r.save_imported)
	assert_true(r.settings_imported)
	assert_eq(PackedStringArray(["build-13"]), r.sources)
	# What the game sees after a relaunch.
	var s := ArcadeRepository.new(PrefsStore.new(stores[0].path)).state()
	assert_eq(31, s.tokens)
	assert_eq(777, s.tickets)
	assert_eq(20700, s.last_refill_day)
	assert_true(s.owns("hat_cap") and s.owns("decor_lava") and s.owns("outfit_red"))
	assert_eq("hat_cap", s.hat)
	assert_eq({"plush_bear": 4, "plush_cat": 1}, s.collection)
	assert_eq(1790000000000, s.spare_token_at)
	assert_eq(55, s.total_plays)
	assert_eq(5000000000, s.stat("tickets:earned"))
	assert_eq(3, s.stat("photos"))
	assert_eq(["tutorial_done", "first_win"], s.unlocked)
	assert_eq(2, s.collectible_count("fish:perch"))
	assert_eq("NEON NOOK", s.arcade_name)
	assert_eq(4100, s.high_score("racer"))
	assert_eq(9000, s.high_score("pinball"))
	assert_eq("ABC:4100,DEF:2000", ScoreTables.encode(s.score_table("racer")))
	var g := SettingsStore.new(PrefsStore.new(stores[1].path)).settings()
	var want := SettingsStore.read(_build13_settings())
	assert_true(want.equals(g), str(g))
	assert_eq(140, g.look_percent)
	assert_eq(30, g.frame_cap)
	# The DataStores are read, never written.
	assert_eq(save_bytes, FileAccess.get_file_as_bytes(sp))
	assert_eq(settings_bytes, FileAccess.get_file_as_bytes(tp))


func test_every_key_the_kotlin_stores_define_is_covered_by_the_fixture() -> void:
	var p := _build13_prefs()
	for k in ArcadeRepository.KEY_TYPES:
		assert_true(p.has(k), "save fixture lacks " + k)
	var sp := _build13_settings()
	for k in SettingsStore.KEY_TYPES:
		assert_true(sp.has(k), "settings fixture lacks " + k)


func test_the_import_runs_once() -> void:
	_write(SaveMigration.DATASTORE_SAVE, SaveMigration.encode_datastore(_build13_prefs(), INT64))
	var stores := _stores()
	assert_true(SaveMigration.run(fx.dir, stores[0], stores[1]).save_imported)
	ArcadeRepository.new(stores[0]).spend_token()
	var again := _stores()
	var r := SaveMigration.run(fx.dir, again[0], again[1])
	assert_false(r.save_imported)
	assert_eq(30, ArcadeRepository.new(again[0]).state().tokens, "progress since the import is kept")


func test_a_fresh_install_imports_nothing_and_writes_nothing() -> void:
	var stores := _stores()
	var r := SaveMigration.run(fx.dir, stores[0], stores[1])
	assert_false(r.save_imported)
	assert_false(r.settings_imported)
	assert_false(FileAccess.file_exists(stores[0].path))
	assert_true(ArcadeRepository.new(stores[0]).state().equals(_fresh()))


func _fresh() -> SaveState:
	var s := SaveState.new()
	s.loaded = true
	return s


# ---- from 2.0.0

func test_a_2_0_0_save_comes_back_with_whole_numbers() -> void:
	var cp := _write(SaveMigration.CODEX_SAVE, _codex_json().to_utf8_buffer())
	var stores := _stores()
	var r := SaveMigration.run(fx.dir, stores[0], stores[1])
	assert_true(r.save_imported)
	assert_eq(PackedStringArray(["2.0.0"]), r.sources)
	var s := ArcadeRepository.new(PrefsStore.new(stores[0].path)).state()
	for v in [s.tokens, s.tickets, s.last_refill_day, s.total_plays, s.spare_token_at, s.high_score("claw"), s.collection["plush_bear"]]:
		assert_is_int(v)
	assert_eq(17, s.tokens)
	assert_eq(250, s.tickets)
	assert_eq(20727, s.last_refill_day)
	assert_eq(1790999999000, s.spare_token_at)
	assert_eq(70, s.total_plays)
	assert_eq("hat_crown", s.hat)
	assert_eq("outfit_blue", s.outfit)
	assert_true(s.muted and s.first_person)
	assert_eq({"plush_bear": 5, "plush_whale": 1}, s.collection)
	assert_eq(450, s.high_score("claw"))
	assert_eq("17", str(s.tokens))
	assert_eq(_codex_json(), FileAccess.get_file_as_string(cp), "2.0.0's file is untouched")


func test_a_damaged_2_0_0_save_falls_back_to_its_backup() -> void:
	_write(SaveMigration.CODEX_SAVE, "{not json".to_utf8_buffer())
	_write(SaveMigration.CODEX_SAVE + ".bak", _codex_json().to_utf8_buffer())
	var stores := _stores()
	SaveMigration.run(fx.dir, stores[0], stores[1])
	assert_eq(17, ArcadeRepository.new(stores[0]).state().tokens)


# ---- from both

func test_both_saves_merge_by_the_rules() -> void:
	_write(SaveMigration.DATASTORE_SAVE, SaveMigration.encode_datastore(_build13_prefs(), INT64))
	_write(SaveMigration.CODEX_SAVE, _codex_json().to_utf8_buffer())
	var stores := _stores()
	var r := SaveMigration.run(fx.dir, stores[0], stores[1])
	assert_eq(PackedStringArray(["build-13", "2.0.0"]), r.sources)
	var s := ArcadeRepository.new(PrefsStore.new(stores[0].path)).state()
	# 2.0.0's core fields win.
	assert_eq(17, s.tokens)
	assert_eq(250, s.tickets)
	assert_eq(70, s.total_plays)
	assert_eq(20727, s.last_refill_day)
	assert_eq(1790999999000, s.spare_token_at)
	assert_eq("hat_crown", s.hat)
	assert_eq("outfit_blue", s.outfit)
	assert_true(s.muted)
	assert_true(s.first_person)
	assert_eq({"plush_bear": 5, "plush_whale": 1}, s.collection)
	# Owned items are the union of both lists.
	for id in ["outfit_red", "hat_cap", "decor_lava", "hat_crown"]:
		assert_true(s.owns(id), id)
	# The higher of each high score.
	assert_eq(4100, s.high_score("racer"))
	assert_eq(12000, s.high_score("pinball"))
	assert_eq(450, s.high_score("claw"))
	# build-13-only data comes from the DataStore.
	assert_eq(12, s.stat("plays:racer"))
	assert_eq(["tutorial_done", "first_win"], s.unlocked)
	assert_eq(2, s.collectible_count("fish:perch"))
	assert_eq("NEON NOOK", s.arcade_name)
	assert_eq("ABC:4100,DEF:2000", ScoreTables.encode(s.score_table("racer")))
	assert_eq("build-13+2.0.0", stores[0].read()[SaveMigration.MIGRATED_KEY])
