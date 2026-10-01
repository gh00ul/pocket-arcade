class_name PaTexture
extends RefCounted
## engine/r3d/Texture.kt: an image the 3D renderer samples. [member width] × [member height] is the
## size in texels as drawing code sees it (texture coordinates are measured in them); the stored
## image can be [member scale] times finer, so art painted in high detail maps the same way.
##
## A texture's pixels come from code: an ARGB array (TexKit, SceneFx...), a [TexPaint] job (painted
## by Godot's 2D renderer, then read back), or a live painter that repaints it while shown. The
## GPU copy is premultiplied (like build-13's upload), so filtering never pulls dark fringes out
## of transparent texels; the scene shader undoes it.

static var _next_id := 1

## Stable identity for material caches.
var id: int
var width: int
var height: int
var scale: int = 1
## Linear filtering with mipmaps (true) or nearest-neighbour (false).
var smooth := true
## Tiles when texture coordinates run past its edges (set by wrapping regions).
var repeat := false
## Bumped by [method touch] whenever the pixels change.
var version := 0
## Bumped whenever [method gpu] would hand out a different texture object.
var gpu_version := 0

## Straight-alpha RGBA8 pixels at the stored size, or null while a painter hasn't delivered them.
var image: Image = null
var _gpu: Texture2D = null
var _uploaded := -1
var _full: Region = null


func _init(p_width: int, p_height: int, p_image: Image = null, p_scale: int = 1) -> void:
	id = _next_id
	_next_id += 1
	width = maxi(1, p_width)
	height = maxi(1, p_height)
	scale = maxi(1, p_scale)
	image = p_image


## Stored image size in pixels.
func pixel_width() -> int:
	return width * scale


func pixel_height() -> int:
	return height * scale


## A texture from ARGB ints (Kotlin's `Texture(w, h, pixels)`), [param s] times finer than its size.
static func from_argb(w: int, h: int, argb: PackedInt32Array, s: int = 1) -> PaTexture:
	var t := PaTexture.new(w, h, null, s)
	t.set_argb(argb)
	return t


## A texture of one colour (TexKit.solid).
static func solid(w: int, h: int, argb_color: int) -> PaTexture:
	var img := Image.create_empty(w, h, false, Image.FORMAT_RGBA8)
	img.fill(Pal.c(argb_color))
	return PaTexture.new(w, h, img)


## Replaces every pixel with [param argb] (row-major ARGB ints, the stored size).
func set_argb(argb: PackedInt32Array) -> void:
	var pw := pixel_width()
	var ph := pixel_height()
	var n := mini(argb.size(), pw * ph)
	var bytes := PackedByteArray()
	bytes.resize(pw * ph * 4)
	var o := 0
	for i in n:
		var c := argb[i]
		bytes[o] = (c >> 16) & 0xFF
		bytes[o + 1] = (c >> 8) & 0xFF
		bytes[o + 2] = c & 0xFF
		bytes[o + 3] = (c >> 24) & 0xFF
		o += 4
	image = Image.create_from_data(pw, ph, false, Image.FORMAT_RGBA8, bytes)
	touch()


## The pixels as ARGB ints (empty while none have arrived).
func get_argb() -> PackedInt32Array:
	var out := PackedInt32Array()
	if image == null:
		return out
	var img := image
	if img.get_format() != Image.FORMAT_RGBA8:
		img = image.duplicate()
		img.convert(Image.FORMAT_RGBA8)
	var bytes := img.get_data()
	var n := bytes.size() / 4
	out.resize(n)
	var o := 0
	for i in n:
		out[i] = MathUtil.i32((bytes[o + 3] << 24) | (bytes[o] << 16) | (bytes[o + 1] << 8) | bytes[o + 2])
		o += 4
	return out


## Marks the pixels as changed so the GPU copy is refreshed.
func touch() -> void:
	version += 1


## Hands the texture a GPU image made elsewhere (a painter's viewport while it is read back).
func set_gpu(t: Texture2D) -> void:
	_gpu = t
	_uploaded = version
	gpu_version += 1


## What materials sample: built from [member image] on first use and after [method touch].
func gpu() -> Texture2D:
	if image != null and _uploaded != version:
		var img := image.duplicate() as Image
		if img.get_format() != Image.FORMAT_RGBA8:
			img.convert(Image.FORMAT_RGBA8)
		img.premultiply_alpha()
		if smooth:
			img.generate_mipmaps()
		if _gpu is ImageTexture and _gpu.get_width() == img.get_width() and _gpu.get_height() == img.get_height() and (_gpu as ImageTexture).get_format() == img.get_format() and img.has_mipmaps() == _gpu_has_mips:
			(_gpu as ImageTexture).update(img)
		else:
			_gpu = ImageTexture.create_from_image(img)
			_gpu_has_mips = img.has_mipmaps()
			gpu_version += 1
		_uploaded = version
	return _gpu


var _gpu_has_mips := false


func region(x: int = 0, y: int = 0, w: int = -1, h: int = -1, wrap: bool = false) -> Region:
	if wrap:
		repeat = true
	return Region.new(self, x, y, width if w < 0 else w, height if h < 0 else h, wrap)


## The whole texture as a region.
func full() -> Region:
	if _full == null:
		_full = Region.new(self, 0, 0, width, height, false)
	return _full
