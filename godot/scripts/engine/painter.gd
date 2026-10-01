class_name Painter
extends RefCounted
## engine/Painter.kt: something that draws in "painter units", a coarse grid scaled up by some
## factor. Games' attract-mode screens are written against this so they can be painted into a
## texture that the 3D renderer maps onto a cabinet's screen (the hall supplies the painter).
## Colours are ARGB ints or Godot Colors.

func fill(_x: float, _y: float, _w: float, _h: float, _color: Variant, _alpha: float = 1.0) -> void:
	pass


func px(x: float, y: float, color: Variant, alpha: float = 1.0) -> void:
	fill(x, y, 1.0, 1.0, color, alpha)


func disc(_cx: float, _cy: float, _r: float, _color: Variant, _alpha: float = 1.0) -> void:
	pass


func frame(_x: float, _y: float, _w: float, _h: float, _color: Variant, _alpha: float = 1.0) -> void:
	pass


## Text in the game's type; [param size] scales it (1 = capitals about 7 units tall).
func text(_s: String, _x: float, _y: float, _color: Variant, _tiny: bool = false, _alpha: float = 1.0, _size: float = 1.0) -> void:
	pass


func text_centered(_s: String, _cx: float, _y: float, _color: Variant, _tiny: bool = false, _alpha: float = 1.0, _size: float = 1.0) -> void:
	pass


## A painter that records its calls (tests, and anything that wants to replay an attract frame).
class Recording:
	extends Painter
	var calls: Array = []

	func fill(x: float, y: float, w: float, h: float, color: Variant, alpha: float = 1.0) -> void:
		calls.append(["fill", x, y, w, h, color, alpha])

	func disc(cx: float, cy: float, r: float, color: Variant, alpha: float = 1.0) -> void:
		calls.append(["disc", cx, cy, r, color, alpha])

	func frame(x: float, y: float, w: float, h: float, color: Variant, alpha: float = 1.0) -> void:
		calls.append(["frame", x, y, w, h, color, alpha])

	func text(s: String, x: float, y: float, color: Variant, tiny: bool = false, alpha: float = 1.0, size: float = 1.0) -> void:
		calls.append(["text", s, x, y, color, tiny, alpha, size])

	func text_centered(s: String, cx: float, y: float, color: Variant, tiny: bool = false, alpha: float = 1.0, size: float = 1.0) -> void:
		calls.append(["text_centered", s, cx, y, color, tiny, alpha, size])
