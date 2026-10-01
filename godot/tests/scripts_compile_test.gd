extends PaTest
## Every script of the game compiles (a parse error anywhere fails here, with its message).


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
		elif name.ends_with(".gd"):
			out.append(dir.path_join(name))
		name = d.get_next()
	d.list_dir_end()


func test_every_script_compiles() -> void:
	var files := PackedStringArray()
	_collect("res://scripts", files)
	assert_gt(files.size(), 10, "scripts found")
	for f in files:
		var s: Script = load(f)
		if s == null or not s.can_instantiate():
			fail("does not compile: " + f)


func test_every_shader_compiles() -> void:
	var d := DirAccess.open("res://shaders")
	for name in d.get_files():
		if name.ends_with(".gdshader"):
			var sh: Shader = load("res://shaders/" + name)
			assert_not_null(sh, name)
	for blend in [Blend.OPAQUE, Blend.ALPHA, Blend.ADD]:
		for inst in [false, true]:
			var sh := SceneShader.get_shader(blend, true, true, false, inst)
			assert_true(sh.code.contains("shader_type spatial"))
