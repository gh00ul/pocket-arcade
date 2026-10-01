class_name PluginMusic
extends MusicControl.Backend
## The soundtrack in the Android plugin: build-13's Music engine, unchanged, on its own AudioTrack
## (see [MusicControl]). Each call crosses to the plugin.

var _p: Object


func _init(plugin: Object) -> void:
	_p = plugin


## The plugin's music, or null where there is no plugin.
static func create() -> PluginMusic:
	var p := AndroidBridge.plugin()
	return PluginMusic.new(p) if p != null else null


func set_scene(scene: String) -> void:
	_p.musicSetScene(scene)


func set_intensity(level: float) -> void:
	_p.musicSetIntensity(level)


func set_quiet(on: bool) -> void:
	_p.musicSetQuiet(on)


func stinger(which: int) -> void:
	_p.musicStinger(which)


func duck(depth: float, hold: float) -> void:
	_p.musicDuck(depth, hold)


func set_volume(volume: float) -> void:
	_p.musicSetVolume(volume)


func set_muted(muted: bool) -> void:
	_p.musicSetMuted(muted)


func set_active(active: bool) -> void:
	_p.musicSetActive(active)
