class_name Fonts
extends RefCounted
## engine/r3d/TexPaint.kt Fonts: the app's typefaces, the phone's own as in build-13: heavy black
## sans for signs, headings and numbers ("sans-serif-black"), a condensed bold for labels, a bold
## and a medium sans. Godot finds them through SystemFont (on Android, from /system/etc/fonts.xml,
## i.e. Roboto); desktop runs fall back to the nearest installed sans.

## Cap height as a share of the em in Roboto (1456 / 2048 units in every weight and width): text
## is sized so its capitals stand exactly the asked height (ArcadeFont.capRatio measured "H").
const CAP_RATIO := 0.711

static var _display: SystemFont = null
static var _heavy: SystemFont = null
static var _condensed: SystemFont = null
static var _body: SystemFont = null


static func _make(names: PackedStringArray, weight: int, stretch: int = 100) -> SystemFont:
	var f := SystemFont.new()
	f.font_names = names
	f.font_weight = weight
	f.font_stretch = stretch
	f.antialiasing = TextServer.FONT_ANTIALIASING_GRAY
	f.hinting = TextServer.HINTING_LIGHT
	f.subpixel_positioning = TextServer.SUBPIXEL_POSITIONING_AUTO
	f.allow_system_fallback = true
	return f


## "sans-serif-black": headings, numbers, signs.
static func display() -> Font:
	if _display == null:
		_display = _make(PackedStringArray(["sans-serif-black", "sans-serif", "Roboto Black", "Roboto", "Arial Black", "Arial"]), 900)
	return _display


## "sans-serif" bold.
static func heavy() -> Font:
	if _heavy == null:
		_heavy = _make(PackedStringArray(["sans-serif", "Roboto", "Arial"]), 700)
	return _heavy


## "sans-serif-condensed" bold: small labels.
static func condensed() -> Font:
	if _condensed == null:
		_condensed = _make(PackedStringArray(["sans-serif-condensed", "Roboto Condensed", "Arial Narrow", "sans-serif", "Arial"]), 700, 75)
	return _condensed


## "sans-serif-medium": body text.
static func body() -> Font:
	if _body == null:
		_body = _make(PackedStringArray(["sans-serif-medium", "sans-serif", "Roboto Medium", "Roboto", "Arial"]), 500)
	return _body
