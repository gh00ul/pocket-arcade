extends Node
## The single headless test runner:
##   godot --headless --path godot res://tests/run_tests.tscn [-- --filter=<substring>]
## Finds every `*_test.gd` under res://tests, runs each `test_*` method (awaiting coroutines),
## counts any script or engine error raised during a test as that test's failure, prints a
## summary and quits with exit code 1 if anything failed (or nothing ran).

class ErrorTrap extends Logger:
	var errors: PackedStringArray = PackedStringArray()
	var mutex := Mutex.new()

	func _log_error(function: String, file: String, line: int, code: String, rationale: String, _editor_notify: bool, error_type: int, _script_backtraces: Array[ScriptBacktrace]) -> void:
		# Warnings are not failures; errors (engine or script) are.
		if error_type == ERROR_TYPE_WARNING:
			return
		var text := "%s (%s:%d in %s)" % [rationale if not rationale.is_empty() else code, file, line, function]
		mutex.lock()
		errors.append(text)
		mutex.unlock()

	func take() -> PackedStringArray:
		mutex.lock()
		var out := errors
		errors = PackedStringArray()
		mutex.unlock()
		return out


var trap := ErrorTrap.new()


func _ready() -> void:
	OS.add_logger(trap)
	call_deferred("_run")


func _filter() -> String:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--filter="):
			return a.substr(9)
	return ""


func _collect(dir: String, out: PackedStringArray) -> void:
	var d := DirAccess.open(dir)
	if d == null:
		return
	d.list_dir_begin()
	var name := d.get_next()
	while name != "":
		if d.current_is_dir():
			if not name.begins_with("."):
				_collect(dir.path_join(name), out)
		elif name.ends_with("_test.gd"):
			out.append(dir.path_join(name))
		name = d.get_next()
	d.list_dir_end()


func _run() -> void:
	var files := PackedStringArray()
	_collect("res://tests", files)
	files.sort()
	var filter := _filter()
	var total := 0
	var failed := 0
	var failed_names := PackedStringArray()
	var started := Time.get_ticks_msec()
	trap.take()
	for path in files:
		var script: Script = load(path)
		var load_errors := trap.take()
		if script == null or not script.can_instantiate():
			total += 1
			failed += 1
			failed_names.append(path + " (does not load)")
			printerr("FAIL %s: script does not load %s" % [path, str(load_errors)])
			continue
		var methods: Array[String] = []
		for m in script.get_script_method_list():
			var n: String = m["name"]
			if n.begins_with("test_") and not methods.has(n):
				methods.append(n)
		methods.sort()
		for method in methods:
			var id := path.trim_prefix("res://tests/") + "::" + method
			if not filter.is_empty() and not id.contains(filter):
				continue
			total += 1
			var t: PaTest = script.new()
			var host := Node.new()
			host.name = "TestHost"
			add_child(host)
			t.tree = get_tree()
			t.host = host
			t._reset_failures()
			trap.take()
			t.before_each()
			await Callable(t, method).call()
			t.after_each()
			for c in host.get_children():
				c.queue_free()
			host.queue_free()
			await get_tree().process_frame
			var errs := trap.take()
			var problems := t.failures()
			for e in errs:
				var expected := false
				for frag in t.expected_errors:
					if e.contains(frag):
						expected = true
				if not expected:
					problems.append("error raised: " + e)
			if problems.is_empty():
				print("ok   ", id)
			else:
				failed += 1
				failed_names.append(id)
				printerr("FAIL ", id)
				for p in problems:
					printerr("       ", p)
	var secs := (Time.get_ticks_msec() - started) / 1000.0
	print("")
	print("%d tests, %d failed (%.1f s)" % [total, failed, secs])
	if failed > 0:
		print("Failed:")
		for n in failed_names:
			print("  ", n)
	OS.remove_logger(trap)
	get_tree().quit(1 if failed > 0 or total == 0 else 0)
