class_name PaintJob
extends SubViewport
## The offscreen picture of one [TexPaint]: its recorded operations built into canvas layers,
## with extra viewports for what Android's Canvas did per pixel: grain (everything painted so far,
## through a noise shader), blur masks and glows (the shape's alpha, blurred and tinted), the
## DST_IN mask, and clips (a clip-only parent). Godot's Compatibility renderer has no 2D MSAA, so
## every job is drawn at SUPERSAMPLE times its size and box-filtered down when read back (a live
## texture is sampled from the larger picture), which anti-aliases every edge.

## Supersampling factor of every paint viewport.
const SUPERSAMPLE := 2

static var _grain_shader: Shader = null
static var _blur_h_shader: Shader = null
static var _blur_v_shader: Shader = null
static var _mask_shader: Shader = null
static var _mul_material: CanvasItemMaterial = null


## A job for [param tp] under the paint pump, or null when there is no scene tree.
static func create(tp: TexPaint, live: bool) -> PaintJob:
	var pump := PaintPump.instance()
	if pump == null:
		return null
	var job := PaintJob.new()
	job.size = Vector2i(tp.w, tp.h) * SUPERSAMPLE
	job.transparent_bg = true
	job.disable_3d = true
	job.render_target_clear_mode = SubViewport.CLEAR_MODE_ALWAYS
	job.gui_disable_input = true
	pump.add_child(job)
	job.rebuild(tp)
	return job


func texture() -> Texture2D:
	return get_texture()


## Builds the canvas layers from what [param tp] has recorded and asks for one render.
func rebuild(tp: TexPaint) -> void:
	for c in get_children():
		c.queue_free()
		remove_child(c)
	_build(self, tp.ops, Vector2i(tp.w, tp.h) * SUPERSAMPLE)
	render_target_update_mode = SubViewport.UPDATE_ONCE


static func _sub(parent: Node, px: Vector2i) -> SubViewport:
	var vp := SubViewport.new()
	vp.size = px
	vp.transparent_bg = true
	vp.disable_3d = true
	vp.render_target_clear_mode = SubViewport.CLEAR_MODE_ALWAYS
	vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	vp.gui_disable_input = true
	parent.add_child(vp)
	return vp


## Splits [param ops] at grain markers into stages; each stage draws the one before (through the
## grain shader) and then its own layers.
static func _build(vp: SubViewport, ops: Array, px: Vector2i) -> void:
	var stages: Array = [[]]
	var grains: Array = []
	for e: Array in ops:
		if e[0] == TexPaint.L_GRAIN:
			grains.append(e)
			stages.append([])
		else:
			stages[stages.size() - 1].append(e)
	if stages.size() == 1:
		_layers(vp, stages[0], px)
		return
	# Nest from the last stage down: stage k-1 renders inside stage k's viewport (first).
	var targets: Array = []
	targets.resize(stages.size())
	targets[stages.size() - 1] = vp
	for k in range(stages.size() - 1, 0, -1):
		targets[k - 1] = _sub(targets[k], px)
	for k in stages.size():
		var target: SubViewport = targets[k]
		if k > 0:
			var g: Array = grains[k - 1]
			var rect := TextureRect.new()
			rect.texture = (targets[k - 1] as SubViewport).get_texture()
			rect.size = Vector2(px)
			var m := ShaderMaterial.new()
			m.shader = _grain()
			m.set_shader_parameter("amount", g[1])
			m.set_shader_parameter("seed", float(g[2]))
			rect.material = m
			target.add_child(rect)
		_layers(target, stages[k], px)


## Turns one stage's operations into canvas layers in order.
static func _layers(vp: Node, ops: Array, px: Vector2i) -> void:
	var parents: Array = [vp]
	var current: PaintLayer = null
	for e: Array in ops:
		var kind: int = e[0]
		var parent: Node = parents[parents.size() - 1]
		if kind == TexPaint.L_CLIP:
			var clipper := _ClipShape.new()
			clipper.scale = Vector2(SUPERSAMPLE, SUPERSAMPLE)
			clipper.pts = e[1]
			clipper.clip_children = CanvasItem.CLIP_CHILDREN_ONLY
			parent.add_child(clipper)
			parents.append(clipper)
			current = null
		elif kind == TexPaint.L_UNCLIP:
			if parents.size() > 1:
				parents.pop_back()
			current = null
		elif kind == TexPaint.L_GLOW:
			_glow_layer(vp, parent, e, px)
			current = null
		elif kind == TexPaint.L_MASK:
			_mask_layer(vp, parent, e, px)
			current = null
		else:
			var p: PaPaint = _paint_of(e)
			var clear := p != null and p.xfer == PaPaint.Xfer.CLEAR
			if current == null or current.has_meta("clear") != clear:
				current = PaintLayer.new()
				current.scale = Vector2(SUPERSAMPLE, SUPERSAMPLE)
				if clear:
					current.set_meta("clear", true)
					current.material = _mul()
				parent.add_child(current)
			current.items.append(e)


static func _paint_of(e: Array) -> PaPaint:
	for v in e:
		if v is PaPaint:
			return v
	return null


## The blurred alpha of [param e]'s operations, tinted, as a layer.
static func _glow_layer(vp: Node, parent: Node, e: Array, px: Vector2i) -> void:
	var radius: float = e[1]
	var color: int = e[2]
	var inner: Array = e[3]
	var offset: Vector2 = e[4]
	# Skia's BlurMaskFilter: sigma = radius / sqrt(3) + 0.5.
	var sigma := (radius * 0.57735 + 0.5) * SUPERSAMPLE
	var blur_h := _sub(vp, px)
	var source := _sub(blur_h, px)
	_layers(source, inner, px)
	var h_rect := TextureRect.new()
	h_rect.texture = source.get_texture()
	h_rect.size = Vector2(px)
	var hm := ShaderMaterial.new()
	hm.shader = _blur_h()
	hm.set_shader_parameter("sigma", sigma)
	h_rect.material = hm
	blur_h.add_child(h_rect)
	var out := TextureRect.new()
	out.texture = blur_h.get_texture()
	out.size = Vector2(px)
	out.position = offset * SUPERSAMPLE
	var vm := ShaderMaterial.new()
	vm.shader = _blur_v()
	vm.set_shader_parameter("sigma", sigma)
	vm.set_shader_parameter("tint", Pal.c(color))
	out.material = vm
	parent.add_child(out)


## DST_IN: what has been painted is kept only where the shape covers it.
static func _mask_layer(vp: Node, parent: Node, e: Array, px: Vector2i) -> void:
	var entry: Array = e[1]
	var p: PaPaint = (e[2] as PaPaint).copy()
	p.xfer = PaPaint.Xfer.NONE
	var shape_vp := _sub(vp, px)
	var layer := PaintLayer.new()
	layer.scale = Vector2(SUPERSAMPLE, SUPERSAMPLE)
	layer.items.append(entry + [p, e[3]])
	shape_vp.add_child(layer)
	var rect := TextureRect.new()
	rect.texture = shape_vp.get_texture()
	rect.size = Vector2(px)
	var m := ShaderMaterial.new()
	m.shader = _mask()
	rect.material = m
	parent.add_child(rect)


class _ClipShape:
	extends Node2D
	var pts := PackedVector2Array()

	func _draw() -> void:
		if pts.size() >= 3:
			draw_colored_polygon(pts, Color.WHITE)


static func _grain() -> Shader:
	if _grain_shader == null:
		_grain_shader = Shader.new()
		_grain_shader.code = """shader_type canvas_item;
render_mode blend_premul_alpha;
uniform float amount = 0.1;
uniform float seed = 1.0;
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233)) + seed * 17.13) * 43758.5453); }
void fragment() {
	vec4 c = texture(TEXTURE, UV);
	if (c.a > 0.0) {
		float k = 1.0 + (hash(floor(FRAGCOORD.xy)) - 0.5) * 2.0 * amount;
		c.rgb = clamp(c.rgb * k, vec3(0.0), vec3(c.a));
	}
	COLOR = c;
}
"""
	return _grain_shader


static func _blur_h() -> Shader:
	if _blur_h_shader == null:
		_blur_h_shader = Shader.new()
		_blur_h_shader.code = _blur_code(true)
	return _blur_h_shader


static func _blur_v() -> Shader:
	if _blur_v_shader == null:
		_blur_v_shader = Shader.new()
		_blur_v_shader.code = _blur_code(false)
	return _blur_v_shader


static func _blur_code(horizontal: bool) -> String:
	var dir := "vec2(TEXTURE_PIXEL_SIZE.x, 0.0)" if horizontal else "vec2(0.0, TEXTURE_PIXEL_SIZE.y)"
	var out := "COLOR = vec4(0.0, 0.0, 0.0, a);" if horizontal else "COLOR = vec4(tint.rgb * tint.a * a, tint.a * a);"
	return """shader_type canvas_item;
render_mode blend_premul_alpha;
uniform float sigma = 2.0;
uniform vec4 tint : source_color = vec4(1.0);
void fragment() {
	// A Gaussian over the alpha only (Android's extractAlpha), up to 3 sigma each way.
	int taps = int(min(ceil(sigma * 3.0), 48.0));
	float sum = 0.0;
	float wsum = 0.0;
	for (int i = -taps; i <= taps; i++) {
		float w = exp(-float(i * i) / (2.0 * sigma * sigma));
		sum += texture(TEXTURE, UV + %s * float(i)).a * w;
		wsum += w;
	}
	float a = sum / max(wsum, 1e-5);
	%s
}
""" % [dir, out]


static func _mask() -> Shader:
	if _mask_shader == null:
		_mask_shader = Shader.new()
		_mask_shader.code = """shader_type canvas_item;
render_mode blend_mul;
void fragment() {
	float a = texture(TEXTURE, UV).a;
	COLOR = vec4(a, a, a, a);
}
"""
	return _mask_shader


static func _mul() -> CanvasItemMaterial:
	if _mul_material == null:
		_mul_material = CanvasItemMaterial.new()
		_mul_material.blend_mode = CanvasItemMaterial.BLEND_MODE_MUL
	return _mul_material
