class_name PaTest
extends RefCounted
## Base of every test file. Each `func test_...()` is one test; it may `await`.
## The runner gives a test the scene tree in [member tree] and a scratch parent node in
## [member host], which is emptied after each test. Checks never throw: a failed one is
## recorded and the test runs on, so one run reports every broken expectation.

var tree: SceneTree
var host: Node
var _failures: PackedStringArray = PackedStringArray()
## Engine/script errors this test provokes on purpose (substrings); any other error fails it.
var expected_errors: PackedStringArray = PackedStringArray()


## Called before / after every test of the file.
func before_each() -> void:
	pass


func after_each() -> void:
	pass


func failures() -> PackedStringArray:
	return _failures


func _reset_failures() -> void:
	_failures = PackedStringArray()
	expected_errors = PackedStringArray()


## Declares that an error containing [param fragment] is part of what this test checks.
func expect_error(fragment: String) -> void:
	expected_errors.append(fragment)


func fail(msg: String) -> void:
	_failures.append(msg)


func assert_true(cond: bool, msg: String = "") -> void:
	if not cond:
		fail("expected true" + _m(msg))


func assert_false(cond: bool, msg: String = "") -> void:
	if cond:
		fail("expected false" + _m(msg))


## Strict equality: an int and a float never match (see bug 2, numbers coming back as decimals).
func assert_eq(expected: Variant, actual: Variant, msg: String = "") -> void:
	if not _same(expected, actual):
		fail("expected <%s> (%s) but was <%s> (%s)%s" % [str(expected), type_string(typeof(expected)), str(actual), type_string(typeof(actual)), _m(msg)])


func assert_ne(unexpected: Variant, actual: Variant, msg: String = "") -> void:
	if _same(unexpected, actual):
		fail("expected anything but <%s>%s" % [str(actual), _m(msg)])


func assert_near(expected: float, actual: float, delta: float, msg: String = "") -> void:
	if is_nan(actual) or absf(expected - actual) > delta:
		fail("expected %s ±%s but was %s%s" % [str(expected), str(delta), str(actual), _m(msg)])


func assert_gt(a: float, b: float, msg: String = "") -> void:
	if not (a > b):
		fail("expected %s > %s%s" % [str(a), str(b), _m(msg)])


func assert_ge(a: float, b: float, msg: String = "") -> void:
	if not (a >= b):
		fail("expected %s >= %s%s" % [str(a), str(b), _m(msg)])


func assert_lt(a: float, b: float, msg: String = "") -> void:
	if not (a < b):
		fail("expected %s < %s%s" % [str(a), str(b), _m(msg)])


func assert_le(a: float, b: float, msg: String = "") -> void:
	if not (a <= b):
		fail("expected %s <= %s%s" % [str(a), str(b), _m(msg)])


func assert_null(v: Variant, msg: String = "") -> void:
	if v != null:
		fail("expected null but was <%s>%s" % [str(v), _m(msg)])


func assert_not_null(v: Variant, msg: String = "") -> void:
	if v == null:
		fail("expected a value but was null" + _m(msg))


func assert_is_int(v: Variant, msg: String = "") -> void:
	if typeof(v) != TYPE_INT:
		fail("expected an int but was <%s> (%s)%s" % [str(v), type_string(typeof(v)), _m(msg)])


func assert_has(container: Variant, item: Variant, msg: String = "") -> void:
	if not container.has(item):
		fail("expected <%s> to contain <%s>%s" % [str(container), str(item), _m(msg)])


func assert_not_has(container: Variant, item: Variant, msg: String = "") -> void:
	if container.has(item):
		fail("expected <%s> not to contain <%s>%s" % [str(container), str(item), _m(msg)])


## Waits [param n] process frames (GUI tests: let layout and deferred calls settle).
func frames(n: int = 1) -> void:
	for i in n:
		await tree.process_frame


func _m(msg: String) -> String:
	return "" if msg.is_empty() else ": " + msg


static func _same(a: Variant, b: Variant) -> bool:
	var ta := typeof(a)
	var tb := typeof(b)
	if ta != tb:
		# Typed and untyped arrays, String vs StringName are still comparable.
		var arrays := [TYPE_ARRAY]
		if ta == TYPE_STRING_NAME or tb == TYPE_STRING_NAME:
			return str(a) == str(b)
		if ta in arrays and tb in arrays:
			return a == b
		return false
	if ta == TYPE_ARRAY:
		var x: Array = a
		var y: Array = b
		if x.size() != y.size():
			return false
		for i in x.size():
			if not _same(x[i], y[i]):
				return false
		return true
	if ta == TYPE_DICTIONARY:
		var dx: Dictionary = a
		var dy: Dictionary = b
		if dx.size() != dy.size():
			return false
		for k in dx:
			if not dy.has(k) or not _same(dx[k], dy[k]):
				return false
		return true
	return a == b
