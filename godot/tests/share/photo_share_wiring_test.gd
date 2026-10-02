extends PaTest
## share/PhotoShareWiringTest.kt, adapted to the Godot build: sharing goes through the Android
## plugin's FileProvider (android-plugin/), and a mismatch between the code and the manifest only
## shows on a phone, when SHARE does nothing. So the plugin's manifest, its paths file and the code
## that asks for the provider are read here and held to what [PhotoStore] expects. build-13
## declared androidx's FileProvider in the app's manifest with res/xml/file_paths.xml; the plugin
## declares its own subclass (com.pocketarcade.godot.PhotoProvider, see docs/PORT_PARITY.md) with
## res/xml/pa_file_paths.xml, under build-13's authority and folder. There is no storage
## permission to ask for.

const PLUGIN := "android-plugin/plugin/src/main/"


## A file of the repository (tests run from the Godot project, one folder down).
func _source(rel: String) -> String:
	var path := ProjectSettings.globalize_path("res://").path_join("..").path_join(rel).simplify_path()
	if not FileAccess.file_exists(path):
		fail("can't find %s from %s" % [rel, ProjectSettings.globalize_path("res://")])
		return ""
	return FileAccess.get_file_as_string(path)


func test_the_manifest_declares_the_provider_the_code_asks_for() -> void:
	var manifest := _source(PLUGIN + "AndroidManifest.xml")
	var provider := _source(PLUGIN + "java/com/pocketarcade/godot/PhotoProvider.kt")
	var plugin := _source(PLUGIN + "java/com/pocketarcade/godot/PocketArcadePlugin.kt")
	# The provider is androidx's FileProvider (the plugin's own subclass of it).
	assert_true(manifest.contains("android:name=\"com.pocketarcade.godot.PhotoProvider\""))
	assert_true(provider.contains("import androidx.core.content.FileProvider") and provider.contains("class PhotoProvider : FileProvider()"))
	# The authority the code asks for is the one declared: ${applicationId} + ".photos".
	assert_true(plugin.contains("const val AUTHORITY_SUFFIX = \".photos\""))
	assert_true(plugin.contains("FileProvider.getUriForFile(act, act.packageName + AUTHORITY_SUFFIX, file)"))
	assert_true(manifest.contains("android:authorities=\"${applicationId}.photos\""), "the authority must be ${applicationId}.photos")
	assert_true(manifest.contains("android:exported=\"false\""), "the provider must not be exported")
	assert_true(manifest.contains("android:grantUriPermissions=\"true\""))
	assert_true(manifest.contains("android.support.FILE_PROVIDER_PATHS"))
	assert_true(manifest.contains("@xml/pa_file_paths"))
	# Sharing needs no storage permission.
	assert_false(manifest.contains("STORAGE"))
	# The app is still com.pocketarcade, so the authority is build-13's "com.pocketarcade.photos"
	# (and user:// is the files folder build-13's strips are in).
	var presets := ConfigFile.new()
	assert_eq(OK, presets.load("res://export_presets.cfg"))
	assert_eq("com.pocketarcade", presets.get_value("preset.0.options", "package/unique_name", ""))
	assert_eq("com.pocketarcade.photos", String(presets.get_value("preset.0.options", "package/unique_name", "")) + ".photos")


func test_the_paths_file_shares_only_the_photos_folder() -> void:
	var paths := _source(PLUGIN + "res/xml/pa_file_paths.xml")
	var re := RegEx.create_from_string("<([a-z-]+)\\s+name=\"([^\"]*)\"\\s+path=\"([^\"]*)\"")
	var entries := re.search_all(paths)
	assert_eq(1, entries.size(), "only one folder is shared")
	if entries.size() == 1:
		# files-path is the app's own files directory (user://), the one PhotoStore writes under.
		assert_eq("files-path", entries[0].get_string(1))
		assert_eq(PhotoStore.DIR_NAME + "/", entries[0].get_string(3))
