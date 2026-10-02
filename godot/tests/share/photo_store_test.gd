extends PaTest
## share/PhotoStoreTest.kt: keeping only the newest four photo strips, on a scratch folder and as
## plain names. (Kotlin's TemporaryFolder is the repository tests' scratch folder; java.util.Random's
## shuffle is KRandom's, the order does not matter to the assertion; the writer that threw
## IOException("disk full") returns "disk full".) Plus Godot-only checks marked as such.

var fixture: RepoFixture
var tmp := ""


func before_each() -> void:
	fixture = RepoFixture.new()
	tmp = fixture.dir


func after_each() -> void:
	fixture.cleanup()


func _name(second: int) -> String:
	return PhotoStrip.file_name(1_800_000_000_000 + second * 1000)


func _names(from: int, to: int) -> PackedStringArray:
	var out := PackedStringArray()
	if from <= to:
		for i in range(from, to + 1):
			out.append(_name(i))
	else:
		for i in range(from, to - 1, -1):
			out.append(_name(i))
	return out


static func _file_names(paths: PackedStringArray) -> PackedStringArray:
	var out := PackedStringArray()
	for p in paths:
		out.append(p.get_file())
	return out


static func _sorted(names: PackedStringArray) -> PackedStringArray:
	var s := names.duplicate()
	s.sort()
	return s


static func _write_text(path: String, text: String) -> void:
	var f := FileAccess.open(path, FileAccess.WRITE)
	f.store_string(text)
	f.close()


func test_the_oldest_strips_are_the_ones_to_delete() -> void:
	var names := _names(1, 6)
	var oldest := PackedStringArray([_name(1), _name(2)])
	# Six strips, keep four: the two oldest go, whatever order the folder lists them in.
	assert_eq(oldest, PhotoStore.stale(names))
	var reversed := names.duplicate()
	reversed.reverse()
	assert_eq(oldest, PhotoStore.stale(reversed))
	var shuffled := Array(names)
	KRandom.new(5).shuffle(shuffled)
	assert_eq(oldest, PhotoStore.stale(shuffled))
	# Four or fewer: nothing to do.
	assert_eq(PackedStringArray(), PhotoStore.stale(names.slice(2)))
	assert_eq(PackedStringArray(), PhotoStore.stale(names.slice(5)))
	assert_eq(PackedStringArray(), PhotoStore.stale(PackedStringArray()))
	# Other numbers to keep.
	assert_eq(names.slice(0, 5), PhotoStore.stale(names, 1))
	assert_eq(names, PhotoStore.stale(names, 0))
	assert_eq(names, PhotoStore.stale(names, -3))
	assert_eq(PhotoStore.KEEP, 4)


func test_other_files_are_never_on_the_list() -> void:
	var strips := _names(1, 5)
	var others := PackedStringArray(["notes.txt", "strip-1.png", _name(1) + ".tmp", "readme"])
	var all := strips.duplicate()
	all.append_array(others)
	assert_eq(PackedStringArray([_name(1)]), PhotoStore.stale(all))
	assert_eq(PackedStringArray(), PhotoStore.stale(others))


func test_writing_a_strip_keeps_the_newest_four_and_leaves_nothing_else_behind() -> void:
	var dir := tmp.path_join("photos")
	DirAccess.make_dir_recursive_absolute(dir)
	var kept := dir.path_join("keep-me.txt")
	_write_text(kept, "mine")
	var files := PackedStringArray()
	for i in range(1, 7):
		files.append(PhotoStore.write(dir, _name(i), PhotoStore.KEEP, func(f: FileAccess) -> String:
			f.store_buffer(PackedByteArray([i]))
			return ""))
	assert_eq(_name(6), files[files.size() - 1].get_file())
	assert_eq(PackedByteArray([6]), FileAccess.get_file_as_bytes(files[files.size() - 1]), "the last strip holds what was written")
	# The newest four remain, newest first...
	assert_eq(_names(6, 3), _file_names(PhotoStore.list(dir)))
	# ...the two oldest are gone, no temporary file is left, and an unrelated file is untouched.
	assert_false(FileAccess.file_exists(dir.path_join(_name(1))))
	assert_false(FileAccess.file_exists(dir.path_join(_name(2))))
	var expected := _names(3, 6)
	expected.append("keep-me.txt")
	assert_eq(_sorted(expected), _sorted(DirAccess.get_files_at(dir)))
	assert_eq("mine", FileAccess.get_file_as_string(kept))


func test_the_folder_is_made_if_it_is_missing() -> void:
	var dir := tmp.path_join("deep/photos")
	assert_false(DirAccess.dir_exists_absolute(dir))
	var f := PhotoStore.write(dir, _name(1), PhotoStore.KEEP, func(file: FileAccess) -> String:
		file.store_8(1)
		return "")
	assert_true(FileAccess.file_exists(f))
	assert_eq(PackedStringArray([f.get_file()]), _file_names(PhotoStore.list(dir)))
	# Listing a folder that isn't there is just empty.
	assert_eq(PackedStringArray(), PhotoStore.list(tmp.path_join("nothing")))


func test_a_failed_write_leaves_no_broken_strip_and_deletes_nothing() -> void:
	var dir := tmp.path_join("photos")
	for i in range(1, 5):
		PhotoStore.write(dir, _name(i), PhotoStore.KEEP, func(f: FileAccess) -> String:
			f.store_8(i)
			return "")
	var result := PhotoStore.write(dir, _name(5), PhotoStore.KEEP, func(f: FileAccess) -> String:
		f.store_buffer(PackedByteArray([1, 2, 3]))
		return "disk full")
	assert_eq("", result, "the write should fail")
	assert_eq("disk full", PhotoStore.last_error, "the error should be passed on")
	# The four good strips are all still there, and neither the new one nor its temp file.
	assert_eq(_sorted(_names(1, 4)), _sorted(DirAccess.get_files_at(dir)))


func test_pruning_an_overfull_folder_trims_it_to_four() -> void:
	var dir := tmp.path_join("photos")
	DirAccess.make_dir_recursive_absolute(dir)
	for i in range(1, 10):
		_write_text(dir.path_join(_name(i)), "x")
	PhotoStore.prune(dir)
	var listed := _file_names(PhotoStore.list(dir))
	listed.reverse()
	assert_eq(_names(6, 9), listed)
	PhotoStore.prune(dir, 2)
	assert_eq(2, PhotoStore.list(dir).size())
	# A folder that isn't there is fine.
	PhotoStore.prune(tmp.path_join("nothing"))


func test_the_store_lives_in_the_folder_the_share_sheet_reads() -> void:
	assert_eq("/data/files/photos", PhotoStore.dir("/data/files"))


# ---------------------------------------------------------------- Godot-only

## Godot-only: on the phone the store is user://photos, which is the app's files/photos (the folder
## build-13 wrote and the plugin's FileProvider shares), so a phone's strips survive the update.
func test_the_phone_folder_is_user_photos() -> void:
	assert_eq("user://photos", PhotoStore.dir("user://"))
	assert_eq(OS.get_user_data_dir().path_join("photos"), ProjectSettings.globalize_path(PhotoStore.dir("user://")))


## Godot-only: a saved strip is a PNG named for its time that loads back as the same picture, and
## strips build-13 left in the folder count with the new ones.
func test_a_saved_strip_is_a_png_that_loads_back_and_old_strips_count() -> void:
	var files_dir := tmp.path_join("files")
	var photos := PhotoStore.dir(files_dir)
	DirAccess.make_dir_recursive_absolute(photos)
	# Three strips from build-13 (same names, same folder).
	for i in range(1, 4):
		_write_text(photos.path_join(_name(i)), "b13")
	var img := Image.create_empty(40, 90, false, Image.FORMAT_RGBA8)
	img.fill(Color(0.2, 0.1, 0.5, 1.0))
	img.set_pixel(3, 4, Color(1, 0, 0, 1))
	var millis := 1_800_000_000_000 + 10 * 1000
	var path := PhotoStore.save(files_dir, img, millis)
	assert_eq(photos.path_join(PhotoStrip.file_name(millis)), path)
	var back := Image.load_from_file(path)
	assert_not_null(back)
	if back != null:
		assert_eq(Vector2i(40, 90), back.get_size())
		assert_eq(Color(1, 0, 0, 1).to_rgba32(), back.get_pixel(3, 4).to_rgba32())
		assert_eq(img.get_pixel(20, 50).to_rgba32(), back.get_pixel(20, 50).to_rgba32())
	var listed := _file_names(PhotoStore.list(photos))
	assert_eq(PackedStringArray([PhotoStrip.file_name(millis), _name(3), _name(2), _name(1)]), listed)
	# A fifth pushes the oldest of build-13's out.
	PhotoStore.save(files_dir, img, millis + 1000)
	assert_eq(PackedStringArray([PhotoStrip.file_name(millis + 1000), PhotoStrip.file_name(millis), _name(3), _name(2)]), _file_names(PhotoStore.list(photos)))


## Godot-only: a picture that can't be encoded is a failed save, and nothing is left behind.
func test_an_empty_picture_is_not_saved() -> void:
	var files_dir := tmp.path_join("files")
	assert_eq("", PhotoStore.save(files_dir, Image.new(), 1_800_000_000_000))
	assert_eq("couldn't write the photo strip", PhotoStore.last_error)
	assert_eq(PackedStringArray(), DirAccess.get_files_at(PhotoStore.dir(files_dir)))
