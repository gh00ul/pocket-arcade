class_name PaintPump
extends Node
## Hosts TexPaint's offscreen jobs and reads each finished picture back into its texture once it
## has rendered (then frees the job). Lives under the scene root; created on first use.

static var _instance: PaintPump = null
var _pending: Array = []


static func instance() -> PaintPump:
	if _instance != null and is_instance_valid(_instance):
		return _instance
	var tree := Engine.get_main_loop() as SceneTree
	if tree == null or tree.root == null:
		return null
	_instance = PaintPump.new()
	_instance.name = "PaintPump"
	tree.root.add_child.call_deferred(_instance)
	return _instance


func _enter_tree() -> void:
	if not RenderingServer.frame_post_draw.is_connected(_on_post_draw):
		RenderingServer.frame_post_draw.connect(_on_post_draw)


func _exit_tree() -> void:
	if RenderingServer.frame_post_draw.is_connected(_on_post_draw):
		RenderingServer.frame_post_draw.disconnect(_on_post_draw)


## Reads [param job] back into [param tex] once it has been drawn.
static func queue(job: PaintJob, tex: PaTexture) -> void:
	var pump := instance()
	if pump == null:
		return
	pump._pending.append([job, tex, 0])


## Jobs still waiting (tests, loading plans).
static func pending() -> int:
	return 0 if _instance == null or not is_instance_valid(_instance) else _instance._pending.size()


func _on_post_draw() -> void:
	if _pending.is_empty():
		return
	var keep: Array = []
	for entry: Array in _pending:
		var job: PaintJob = entry[0]
		var tex: PaTexture = entry[1]
		entry[2] += 1
		if not is_instance_valid(job):
			continue
		if not job.is_inside_tree() or entry[2] < 2:
			keep.append(entry)
			continue
		var img := job.get_texture().get_image()
		if img != null and not img.is_empty():
			if img.get_format() != Image.FORMAT_RGBA8:
				img.convert(Image.FORMAT_RGBA8)
			# Box-filter the supersampled picture down to the texture's size.
			for i in PaintJob.SUPERSAMPLE / 2:
				img.shrink_x2()
			tex.image = img
			tex.premultiplied = true
			tex.touch()
		job.queue_free()
	_pending = keep
