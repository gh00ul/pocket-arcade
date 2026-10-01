class_name PaTestEq
extends RefCounted
## Small equality helpers shared by data classes (Kotlin data-class equals).


static func same_set(a: Array, b: Array) -> bool:
	if a.size() != b.size():
		return false
	for x in a:
		if not b.has(x):
			return false
	return true
