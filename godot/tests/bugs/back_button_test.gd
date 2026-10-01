extends PaTest
## Regression (2.0.0 bug 1): Android Back quit the app. Back must never quit: Godot's
## quit_on_go_back is off, Back arrives through the root window's go_back_requested, a screen may
## claim it (close an overlay, pause a round), and otherwise the app goes to the background as
## build-13's Activity did.


class FakeBridge:
	var backs := 0

	func moveTaskToBack() -> void:
		backs += 1

	func takeLaunchGame() -> String:
		return ""

	func hapticsInfo() -> PackedInt32Array:
		return PackedInt32Array([0, 0, 0, 0, 0])

	# The soundtrack's controls (the app attaches the plugin's music): accepted and ignored.
	func musicSetScene(_s: String) -> void:
		pass

	func musicSetIntensity(_v: float) -> void:
		pass

	func musicSetQuiet(_v: bool) -> void:
		pass

	func musicStinger(_v: int) -> void:
		pass

	func musicDuck(_d: float, _h: float) -> void:
		pass

	func musicSetVolume(_v: float) -> void:
		pass

	func musicSetMuted(_v: bool) -> void:
		pass

	func musicSetActive(_v: bool) -> void:
		pass


var _main: Main
var _fake: FakeBridge


func before_each() -> void:
	_fake = FakeBridge.new()
	AndroidBridge.fake = _fake
	Main.user_dir = "user://test_tmp/back_button"
	Main.start_audio = false
	Main.configure_display = false
	DirAccess.make_dir_recursive_absolute(Main.user_dir)


func after_each() -> void:
	if _main != null and is_instance_valid(_main):
		_main.queue_free()
	_main = null
	AndroidBridge.fake = null
	Main.user_dir = "user://"
	Main.start_audio = true
	Main.configure_display = true
	for f in DirAccess.get_files_at("user://test_tmp/back_button"):
		DirAccess.remove_absolute("user://test_tmp/back_button".path_join(f))


func test_the_project_never_quits_on_back() -> void:
	assert_false(ProjectSettings.get_setting("application/config/quit_on_go_back"))
	# ...and the export doesn't override it.
	assert_false(ProjectSettings.get_setting("application/config/quit_on_go_back.android", false))


func test_back_goes_to_the_screen_first_then_to_the_background() -> void:
	_main = (load("res://main.tscn") as PackedScene).instantiate() as Main
	host.add_child(_main)
	await frames(2)
	var claimed := [0]
	_main.back_handler = func() -> bool:
		claimed[0] += 1
		return true
	tree.root.go_back_requested.emit()
	assert_eq(1, claimed[0], "the screen was asked")
	assert_eq(0, _fake.backs, "a claimed Back stays in the app")
	_main.back_handler = func() -> bool: return false
	tree.root.go_back_requested.emit()
	assert_eq(1, _fake.backs, "nothing claimed it: the app goes to the background")
	_main.back_handler = Callable()
	tree.root.go_back_requested.emit()
	assert_eq(2, _fake.backs)
	await frames(2)
	assert_true(is_instance_valid(_main) and _main.is_inside_tree(), "still running")
