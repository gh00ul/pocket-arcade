class_name ArcadeServices
extends RefCounted
## ArcadeApp.kt ArcadeServices and MainActivity's setup: the long-lived services every screen
## shares. The save and settings (after the one-time migration from build-13's DataStores and
## 2.0.0's JSON), the sound and the haptics.
##
## build-13's `persist` (saves launched in a scope that outlives the screen) has no counterpart:
## every repository change writes its file before returning (see docs/PORT_PARITY.md).

var repo: ArcadeRepository
var settings_store: SettingsStore
var audio: AudioSynth
var haptics: Haptics
## What the first launch imported (see [SaveMigration]).
var migration: SaveMigration.Result


## Opens the save and settings in [param user_dir] (migrating older saves on the first launch) and
## wires them to [param p_audio] and the phone's vibrator.
static func open(user_dir: String, p_audio: AudioSynth, device: Haptics.Device = null) -> ArcadeServices:
	var s := ArcadeServices.new()
	var save_store := PrefsStore.new(user_dir.path_join(SaveMigration.SAVE_FILE))
	var settings_prefs := PrefsStore.new(user_dir.path_join(SaveMigration.SETTINGS_FILE))
	s.migration = SaveMigration.run(user_dir, save_store, settings_prefs)
	s.repo = ArcadeRepository.new(save_store)
	s.settings_store = SettingsStore.new(settings_prefs)
	s.audio = p_audio
	s.haptics = Haptics.new(device if device != null else AndroidBridge.haptics_device())
	s.apply_settings(s.settings_store.settings())
	if p_audio != null:
		p_audio.muted = s.repo.state().muted
	return s


## Applies the player's options to the sound and the haptics (as build-13's settings screen did on
## every change).
func apply_settings(g: GameSettings) -> void:
	if audio != null:
		audio.sfx_volume = g.sfx_gain()
		audio.ambience_volume = g.ambience_gain()
		audio.music_volume = g.music_gain()
	haptics.enabled = g.haptics
	haptics.strength = g.haptics_strength()
