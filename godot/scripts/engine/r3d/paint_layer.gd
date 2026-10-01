class_name PaintLayer
extends Node2D
## Draws a run of TexPaint's recorded operations (one blend mode) into its paint viewport.

var items: Array = []


func _draw() -> void:
	for e: Array in items:
		var kind: int = e[0]
		match kind:
			TexPaint.K_POLY:
				var pts: PackedVector2Array = e[1]
				var p: PaPaint = e[2]
				draw_set_transform_matrix(e[3])
				if pts.size() < 3:
					continue
				if p.shader != null:
					var b := p.shader
					var bounds := Rect2(pts[0], Vector2.ZERO)
					for q in pts:
						bounds = bounds.expand(q)
					var uvs := PackedVector2Array()
					uvs.resize(pts.size())
					for i in pts.size():
						uvs[i] = b.uv(pts[i], bounds)
					var tint := Pal.c(p.color)
					draw_polygon(pts, PackedColorArray([tint]), uvs, b.texture())
				else:
					draw_colored_polygon(pts, Pal.c(p.color))
			TexPaint.K_CIRCLE:
				var p: PaPaint = e[3]
				draw_set_transform_matrix(e[4])
				draw_circle(e[1], e[2], Pal.c(p.color), true, -1.0, true)
			TexPaint.K_LINE:
				var p: PaPaint = e[3]
				draw_set_transform_matrix(e[4])
				var width := p.stroke_width if p.stroke_width > 0.0 else 1.0
				var c := Pal.c(p.color)
				draw_line(e[1], e[2], c, width, true)
				if p.round_cap and width > 1.0:
					draw_circle(e[1], width / 2.0, c, true, -1.0, true)
					draw_circle(e[2], width / 2.0, c, true, -1.0, true)
			TexPaint.K_POLYLINE:
				var pts: PackedVector2Array = e[1]
				var closed: bool = e[2]
				var p: PaPaint = e[3]
				draw_set_transform_matrix(e[4])
				if pts.size() < 2:
					continue
				var line := pts
				if closed:
					line = pts.duplicate()
					line.append(pts[0])
				var width := p.stroke_width if p.stroke_width > 0.0 else 1.0
				draw_polyline(line, Pal.c(p.color), width, true)
			TexPaint.K_TEXT:
				TextKit.draw(self, e[4], e[1], e[2], e[3])
			TexPaint.K_LABEL:
				var p: PaPaint = e[6]
				var at: Vector2 = e[2]
				draw_set_transform_matrix(e[7])
				ArcadeFont.draw_to(self, e[7], e[1], at.x, at.y, e[3], p.color, 1.0, e[4], e[5])
			TexPaint.K_IMAGE:
				draw_set_transform_matrix(e[4])
				draw_texture_rect(e[1], e[2], false, Pal.c((e[3] as PaPaint).color))
