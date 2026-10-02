class_name TextLog
extends RefCounted
## Tests: every string drawn, on screen or into a texture, while [member lines] is an Array. Null
## (the normal case) keeps nothing.

static var lines: Variant = null


static func note(s: String) -> void:
	(lines as Array).append(s)
