class_name PhotoStripArt
extends RefCounted
## share/PhotoStripArt.kt: paints a photo strip: the arcade's name over four framed shots one
## under another, and the date underneath, on the arcade's night-purple card. The geometry comes
## from [StripLayout].
##
## build-13 painted into a Bitmap with Android's Canvas, there and then. Here the same calls are
## recorded on a [TexPaint] and drawn by Godot's 2D renderer off screen: [method compose] returns
## the strip's [PaTexture] at once (it shows the painter's picture straight away) and its pixels
## ([member PaTexture.image], what is saved) arrive after the next rendered frame.

## The shadow under each shot, and the paint the shot is then drawn with: Android's drawBitmap
## takes the paint's alpha, and build-13 drew the shots with the shadow's paint (0x66000000), so
## a strip's shots are 40% over their shadow. That is how build-13's strips look; it is kept.
const SHADOW := 0x66000000
const SHOT_PAINT := 0x66FFFFFF

## The type's shadow under every word on the strip.
const TEXT_SHADOW := 0xFF05030A


## Composes the strip from [param shots] (Texture2D or Image; a null one, not developed in time,
## is a dark frame), under [param arcade_name] and [param date]. Shots are cropped to fit their
## frames, never squashed.
static func compose(shots: Array, arcade_name: String, date: String) -> PaTexture:
	var layout := StripLayout.new(PhotoStrip.FRAME)
	var tp := TexPaint.new(layout.width, layout.height)
	paint(tp, layout, shots, arcade_name, date)
	return tp.to_texture()


## Records the strip on [param tp] (its size is [param layout]'s).
static func paint(tp: TexPaint, layout: StripLayout, shots: Array, arcade_name: String, date: String) -> void:
	var w := float(layout.width)
	var h := float(layout.height)
	var m := float(layout.margin)

	# The card: the arcade's night, lit a little from the top.
	tp.vgrad(0.0, 0.0, w, h, [Pal.INDIGO, Pal.NIGHT])
	# A neon rim just inside the edge.
	var rim := m * 0.4
	tp.stroke_round(rim, rim, w - rim * 2.0, h - rim * 2.0, m * 0.7, m * 0.16, Pal.PINK)

	# The header: the name as big as fits, and what this is.
	var head := layout.header
	var name_unit := PhotoStrip.fit_unit(ArcadeFont.width(arcade_name, 1.0), float(head.width), head.height * 0.5 / ArcadeFont.CAP)
	var name_h := ArcadeFont.height(name_unit)
	var sub_unit := head.height * 0.14 / ArcadeFont.TINY_CAP
	var sub_h := ArcadeFont.height(sub_unit, true)
	var block_top := head.top + (head.height - (name_h + sub_h + head.height * 0.12)) / 2.0
	var cx := head.left + head.width / 2.0
	_centred(tp, arcade_name, cx, block_top, name_unit, Pal.YELLOW, false)
	var sub := "%s PHOTO BOOTH %s" % [ArcadeFont.STAR, ArcadeFont.STAR]
	_centred(tp, sub, cx, block_top + name_h + head.height * 0.12, sub_unit, Pal.SKY, true)

	# The shots, each in a white frame with a soft shadow under it.
	var border := m * 0.16
	for i in layout.frames.size():
		var r := layout.frames[i]
		var dst := Rect2(r.left, r.top, r.width, r.height)
		tp.rect(dst.position.x + border, dst.position.y + border * 1.4, dst.size.x, dst.size.y, SHADOW)
		var shot := _texture_of(shots[i] if i < shots.size() else null)
		if shot != null:
			var crop := PhotoStrip.crop_to_aspect(shot.get_width(), shot.get_height(), r.width, r.height)
			var part := AtlasTexture.new()
			part.atlas = shot
			part.region = Rect2(crop.left, crop.top, crop.width, crop.height)
			part.filter_clip = true
			# Canvas.drawBitmap(shot, crop, dst, paint) with the shadow's paint: its alpha.
			var p := PaPaint.new(SHOT_PAINT)
			tp._push([TexPaint.K_IMAGE, part, dst], p)
		else:
			tp.rect(dst.position.x, dst.position.y, dst.size.x, dst.size.y, Pal.BLACK)
		# The white frame: a stroke [border] wide centred half a border outside the shot, i.e.
		# a band from the shot's edge out by one border (four bands keep its corners square).
		var x0 := dst.position.x - border
		var y0 := dst.position.y - border
		var x1 := dst.end.x + border
		var y1 := dst.end.y + border
		tp.rect(x0, y0, x1 - x0, border, Pal.WHITE)
		tp.rect(x0, dst.end.y, x1 - x0, border, Pal.WHITE)
		tp.rect(x0, dst.position.y, border, dst.size.y, Pal.WHITE)
		tp.rect(dst.end.x, dst.position.y, border, dst.size.y, Pal.WHITE)

	# The date at the foot.
	var foot := layout.footer
	var date_unit := PhotoStrip.fit_unit(ArcadeFont.width(date, 1.0), float(foot.width), foot.height * 0.3 / ArcadeFont.CAP)
	_centred(tp, date, foot.left + foot.width / 2.0, foot.top + (foot.height - ArcadeFont.height(date_unit)) / 2.0, date_unit, Pal.LAVENDER, false)


## A shot as a texture: a Texture2D as it is, an Image uploaded, anything else (or an empty
## picture) none.
static func _texture_of(shot: Variant) -> Texture2D:
	if shot is Texture2D:
		var t := shot as Texture2D
		return t if t.get_width() > 0 and t.get_height() > 0 else null
	if shot is Image:
		var img := shot as Image
		return ImageTexture.create_from_image(img) if not img.is_empty() else null
	return null


## Text centred on [param cx] with its capitals' top at [param top], in the type's shadow.
static func _centred(tp: TexPaint, text: String, cx: float, top: float, unit: float, color: int, tiny: bool) -> void:
	tp.label(text, cx, top, unit * (ArcadeFont.TINY_CAP if tiny else ArcadeFont.CAP), color, true, tiny, TEXT_SHADOW)
