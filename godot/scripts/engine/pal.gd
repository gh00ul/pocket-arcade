class_name Pal
extends RefCounted
## engine/Palette.kt: the one palette every texture, effect and menu draws from. Values are ARGB
## ints (always positive here: 0xAARRGGBB) so they go straight into painted textures; [method c]
## gives a Godot [Color].

const NIGHT := 0xFF120A24
const DEEP := 0xFF1E1440
const PLUM := 0xFF2E1F5E
const INDIGO := 0xFF3B2A7A
const VIOLET := 0xFF5B2DB0
const PURPLE := 0xFF8A4FFF
const LAVENDER := 0xFFC3A6FF
const PINK := 0xFFFF3FA4
const HOTPINK := 0xFFFF77C8
const RED := 0xFFFF4D4D
const DARKRED := 0xFFB0213A
const ORANGE := 0xFFFF9A3C
const GOLD := 0xFFFFC83D
const YELLOW := 0xFFFFE14D
const CREAM := 0xFFFFF4D6
const LIME := 0xFFA6F04A
const GREEN := 0xFF3DDC84
const DARKGREEN := 0xFF1E7A4E
const TEAL := 0xFF1FA89A
const CYAN := 0xFF3DF5FF
const SKY := 0xFF4DA6FF
const BLUE := 0xFF2F5BE0
const NAVY := 0xFF1A2A6C
const WHITE := 0xFFFFFFFF
const LIGHTGRAY := 0xFFC8C8E0
const GRAY := 0xFF7A7A9A
const DARKGRAY := 0xFF3A3A55
const BLACK := 0xFF08060F
const BROWN := 0xFF8B5A2B
const DARKBROWN := 0xFF5A3418
const TAN := 0xFFD9A066
const WOOD := 0xFFC07A3A
const SKIN_LIGHT := 0xFFFFD1A6
const SKIN_MID := 0xFFE0A87A
const SKIN_TAN := 0xFFB5764C
const SKIN_DARK := 0xFF7A4A2A
const CLEAR := 0

## Kotlin's ARGB Ints are signed: -1 is white, -0x1000000 is 0xFF000000. Ported code that
## compares a colour with -1 ("no tint") should pass it through [method argb] first.
static func argb(c: int) -> int:
	return c & 0xFFFFFFFF


## An ARGB int as a Godot Color (sRGB values, no conversion).
static func c(argb_value: int) -> Color:
	var v := argb_value & 0xFFFFFFFF
	return Color8((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, (v >> 24) & 0xFF)


## A Godot Color back to ARGB.
static func to_argb(col: Color) -> int:
	return (col.a8 << 24) | (col.r8 << 16) | (col.g8 << 8) | col.b8


## Multiplies the RGB channels by [param f] (0..1 darkens), keeping alpha.
static func shade(argb_value: int, f: float) -> int:
	var a := (argb_value >> 24) & 0xFF
	var r := clampi(int(((argb_value >> 16) & 0xFF) * f), 0, 255)
	var g := clampi(int(((argb_value >> 8) & 0xFF) * f), 0, 255)
	var b := clampi(int((argb_value & 0xFF) * f), 0, 255)
	return (a << 24) | (r << 16) | (g << 8) | b


## Blends [param a] toward [param b] by [param t] (every channel, alpha too).
static func mix(a: int, b: int, t: float) -> int:
	var u := clampf(t, 0.0, 1.0)
	var out := 0
	for shift: int in [24, 16, 8, 0]:
		var ch := int(((a >> shift) & 0xFF) * (1.0 - u) + ((b >> shift) & 0xFF) * u)
		out |= ch << shift
	return out


static func with_alpha(argb_value: int, alpha: float) -> int:
	return (int(clampf(alpha, 0.0, 1.0) * 255.0) << 24) | (argb_value & 0xFFFFFF)


## engine/r3d/Renderer3D.kt mixArgb: blends RGB, keeps [param a]'s alpha.
static func mix_argb(a: int, b: int, t: float) -> int:
	var u := clampf(t, 0.0, 1.0)
	var ar := (a >> 16) & 255
	var ag := (a >> 8) & 255
	var ab := a & 255
	var br := (b >> 16) & 255
	var bg := (b >> 8) & 255
	var bb := b & 255
	var r := int(ar + (br - ar) * u)
	var g := int(ag + (bg - ag) * u)
	var bl := int(ab + (bb - ab) * u)
	return (a & 0xFF000000) | (r << 16) | (g << 8) | bl
