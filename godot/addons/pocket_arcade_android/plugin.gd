@tool
extends EditorPlugin
## Adds the Pocket Arcade Android plugin (built from android-plugin/ into bin/) to Android Gradle
## exports, with the AndroidX library its share sheet needs.

var _export: AndroidExport


func _enter_tree() -> void:
	_export = AndroidExport.new()
	add_export_plugin(_export)


func _exit_tree() -> void:
	remove_export_plugin(_export)
	_export = null


class AndroidExport:
	extends EditorExportPlugin

	## Relative to res://addons, as Godot expects.
	const AAR := "pocket_arcade_android/bin/pocket_arcade_android-release.aar"

	func _get_name() -> String:
		return "PocketArcadeAndroid"

	func _supports_platform(platform: EditorExportPlatform) -> bool:
		return platform is EditorExportPlatformAndroid

	func _get_android_libraries(_platform: EditorExportPlatform, _debug: bool) -> PackedStringArray:
		return PackedStringArray([AAR])

	func _get_android_dependencies(_platform: EditorExportPlatform, _debug: bool) -> PackedStringArray:
		return PackedStringArray(["androidx.core:core:1.13.1"])
