class_name StripLayout
extends RefCounted
## share/PhotoStrip.kt StripLayout: the geometry of a photo strip whose four square shots are
## [member frame] pixels across: a header for the arcade's name, the four shots one under another, a
## footer for the date, and an even margin all round. Pure numbers, so the tests can check that
## everything fits and nothing overlaps; [PhotoStripArt] does the painting.
##
## Its own file (Kotlin declares it beside PhotoStrip) because the hall's photo wall uses it too.
## The sizes are Kotlin's `(frame * k).toInt()` with Float maths; 64-bit maths truncates to the
## same whole numbers for every frame from 0 to 20000 (checked against build-13's 32-bit values in
## tests/share/photo_strip_test.gd).

var frame: int
var margin: int
var gap: int
var header_height: int
var footer_height: int

var width: int
var height: int

## Where the arcade's name goes: the full width, above the first shot.
var header: PxRect

## The shots, first at the top.
var frames: Array[PxRect] = []

## Where the date goes: the full width, under the last shot.
var footer: PxRect


func _init(p_frame: int) -> void:
	frame = p_frame
	margin = maxi(int(frame * 0.08), 2)
	gap = maxi(int(frame * 0.045), 1)
	header_height = maxi(int(frame * 0.32), 4)
	footer_height = maxi(int(frame * 0.23), 4)
	width = frame + 2 * margin
	height = margin + header_height + PhotoStrip.SHOTS * frame + (PhotoStrip.SHOTS - 1) * gap + footer_height + margin
	header = PxRect.new(margin, margin, width - margin, margin + header_height)
	for i in PhotoStrip.SHOTS:
		var top := margin + header_height + i * (frame + gap)
		frames.append(PxRect.new(margin, top, margin + frame, top + frame))
	footer = PxRect.new(margin, frames[frames.size() - 1].bottom, width - margin, height - margin)
