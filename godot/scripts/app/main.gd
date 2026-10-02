class_name Main
extends Node
## MainActivity.kt: starts the sound, opens the save (migrating older ones on the first launch),
## sizes the UI in dp, routes Android's Back, follows the app's pauses and the launch intent's
## "play" extra, and hosts the app ([member app_root]).
##
## Back: build-13's Activity let each screen claim Back (closing an overlay, pausing a round); when
## none did, Android's default sent the app to the background. Godot's quit_on_go_back is off, so
## Back arrives here through the root window's go_back_requested; [member back_handler] decides,
## and if it declines the app goes to the background through the plugin.

## Emitted after a pause (false) or resume (true) of the app.
signal app_active_changed(active: bool)

static var instance: Main = null
## Where the save lives, and whether to start the sound and size the UI (tests turn these off).
static var user_dir := "user://"
static var start_audio := true
static var configure_display := true

var audio: AudioSynth
var services: ArcadeServices
## Returns true when it handled Back (an overlay closed, a round paused).
var back_handler: Callable = Callable()
## A machine id to walk straight into (the launch intent's "play" extra), until taken.
var launch_game := ""
## Where screens go.
var app_root: Control
## Where the 3D pictures go (Gfx.host), under the screens.
var gfx_layer: Control


func _enter_tree() -> void:
	instance = self


func _exit_tree() -> void:
	if instance == self:
		instance = null


func _ready() -> void:
	var window := get_window()
	if configure_display:
		Display.configure(window)
		window.size_changed.connect(func() -> void: Display.configure(window))
	window.go_back_requested.connect(_on_back)
	audio = AudioSynth.new()
	audio.name = "Audio"
	add_child(audio)
	if start_audio:
		audio.start()
	services = ArcadeServices.open(user_dir, audio)
	launch_game = AndroidBridge.take_launch_game()
	# The 3D pictures (hall, title, machines) draw in this layer, under every piece of UI.
	gfx_layer = Control.new()
	gfx_layer.name = "Gfx"
	gfx_layer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	gfx_layer.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(gfx_layer)
	Gfx.host = gfx_layer
	app_root = Control.new()
	app_root.name = "App"
	app_root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	add_child(app_root)
	_start_app()


## Builds the first screen. Until the title screen and the hall are ported, preview builds open
## on [PreviewLobby].
func _start_app() -> void:
	var path := "res://scripts/app/arcade_app.gd"
	if ResourceLoader.exists(path):
		var app: Node = load(path).new()
		app_root.add_child(app)
		if app.has_method("start"):
			app.call("start", self)
		return
	var lobby := PreviewLobby.new()
	lobby.main = self
	app_root.add_child(lobby)


func _on_back() -> void:
	if back_handler.is_valid() and back_handler.call():
		return
	AndroidBridge.move_task_to_back()


func _notification(what: int) -> void:
	match what:
		NOTIFICATION_APPLICATION_PAUSED:
			if audio != null:
				audio.set_active(false)
			app_active_changed.emit(false)
		NOTIFICATION_APPLICATION_RESUMED:
			if audio != null:
				audio.set_active(true)
			var id := AndroidBridge.take_launch_game()
			if id != "":
				launch_game = id
			app_active_changed.emit(true)


## The machine id to walk straight into, once.
func take_launch_game() -> String:
	var id := launch_game
	launch_game = ""
	return id
