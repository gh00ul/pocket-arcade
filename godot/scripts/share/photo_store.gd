class_name PhotoStore
extends RefCounted
## share/PhotoStore.kt: the photo strips kept on the phone: PNGs in the app's own files folder (so
## no storage permission is needed), only the newest [constant KEEP] of them. On Android `user://`
## is the app's files directory, the folder build-13 used, so strips saved by build-13 are kept
## and listed. The rotation and the writing work on plain paths, so the tests run them on a
## scratch folder.
##
## Kotlin's `File`s are path strings here (`list` gives full paths, newest first). Kotlin's writer
## threw to fail a write; here it returns why ("" when it wrote everything), and a failed
## [method write] returns "" with the reason in [member last_error].

## The folder under the app's files directory; the plugin's res/xml/pa_file_paths.xml shares
## exactly this one.
const DIR_NAME := "photos"

## How many strips are kept; each new one pushes the oldest out.
const KEEP := 4

## Why the last [method write] (or [method save]) failed; "" after one that worked.
static var last_error := ""


static func dir(files_dir: String) -> String:
	return files_dir.path_join(DIR_NAME)


## Which of the [param names] in the folder to delete so that only the newest [param keep] strips
## are left. Names sort in the order the strips were made ([method PhotoStrip.file_name]); anything
## that isn't a strip's name is never touched.
static func stale(names: Variant, keep: int = KEEP) -> PackedStringArray:
	var strips := PackedStringArray()
	for n: String in names:
		if PhotoStrip.is_strip_name(n):
			strips.append(n)
	strips.sort()
	return strips.slice(0, maxi(strips.size() - maxi(keep, 0), 0))


## Deletes every strip in [param folder] but the newest [param keep].
static func prune(folder: String, keep: int = KEEP) -> void:
	for name in stale(_names(folder), keep):
		DirAccess.remove_absolute(folder.path_join(name))


## The strips in [param folder], newest first (full paths).
static func list(folder: String) -> PackedStringArray:
	var strips := PackedStringArray()
	for n in _names(folder):
		if PhotoStrip.is_strip_name(n):
			strips.append(n)
	strips.sort()
	strips.reverse()
	for i in strips.size():
		strips[i] = folder.path_join(strips[i])
	return strips


## The files in [param folder] (none when it isn't there).
static func _names(folder: String) -> PackedStringArray:
	if not DirAccess.dir_exists_absolute(folder):
		return PackedStringArray()
	return DirAccess.get_files_at(folder)


## Writes a strip called [param name] into [param folder] with [param writer], then trims the
## folder to the newest [param keep]. The strip is written to a temporary file first, so a
## failure part way leaves neither a broken strip nor a trimmed folder. [param writer] is called
## with the open file and returns "" when it wrote everything, else what went wrong. Returns the
## strip's path, or "" when it failed ([member last_error] says why).
static func write(folder: String, name: String, keep: int, writer: Callable) -> String:
	last_error = ""
	if not DirAccess.dir_exists_absolute(folder) and DirAccess.make_dir_recursive_absolute(folder) != OK:
		last_error = "can't make the folder " + folder
		return ""
	var target := folder.path_join(name)
	var temp := folder.path_join(name + ".tmp")
	var problem := ""
	var f := FileAccess.open(temp, FileAccess.WRITE)
	if f == null:
		problem = "can't write %s (%s)" % [temp, error_string(FileAccess.get_open_error())]
	else:
		var said: Variant = writer.call(f)
		if not (said is String):
			problem = "the writer failed"
		elif not (said as String).is_empty():
			problem = said
		elif f.get_error() != OK:
			problem = "can't write %s (%s)" % [temp, error_string(f.get_error())]
		f.close()
		if problem.is_empty() and DirAccess.rename_absolute(temp, target) != OK:
			if DirAccess.copy_absolute(temp, target) != OK:
				problem = "can't move %s to %s" % [temp, target]
	if FileAccess.file_exists(temp):
		DirAccess.remove_absolute(temp)
	if not problem.is_empty():
		last_error = problem
		return ""
	prune(folder, keep)
	return target


## Saves [param strip] as a PNG named for [param now_millis] (-1: now) under [param files_dir]
## (the app's files: `user://`) and trims to the newest [constant KEEP]. Returns the strip's path,
## or "" when it couldn't be written. Safe on a worker thread.
static func save(files_dir: String, strip: Image, now_millis: int = -1) -> String:
	var millis := now_millis if now_millis >= 0 else int(Time.get_unix_time_from_system() * 1000.0)
	return write(dir(files_dir), PhotoStrip.file_name(millis), KEEP, func(f: FileAccess) -> String:
		var png := strip.save_png_to_buffer() if strip != null and not strip.is_empty() else PackedByteArray()
		if png.is_empty() or not f.store_buffer(png):
			return "couldn't write the photo strip"
		return "")
