class_name KParse
extends RefCounted
## Kotlin's String.toIntOrNull / toLongOrNull: an optional sign then digits only (no spaces), and
## null when the value doesn't fit. Godot's to_int() would clamp an overflow and log an error.

const INT_MIN := -0x80000000
const INT_MAX := 0x7FFFFFFF
const LONG_MAX_DIGITS := "9223372036854775807"
const LONG_MIN_DIGITS := "9223372036854775808"


## toLongOrNull(): an int, or null.
static func to_long_or_null(s: String) -> Variant:
	if s.is_empty():
		return null
	var start := 0
	var negative := false
	var first := s[0]
	if first == "-" or first == "+":
		if s.length() == 1:
			return null
		negative = first == "-"
		start = 1
	var digits := s.substr(start)
	for i in digits.length():
		var ch := digits.unicode_at(i)
		if ch < 48 or ch > 57:
			return null
	var trimmed := digits.lstrip("0")
	if trimmed.is_empty():
		return 0
	if trimmed.length() > 19:
		return null
	if trimmed.length() == 19:
		var limit := LONG_MIN_DIGITS if negative else LONG_MAX_DIGITS
		if trimmed > limit:
			return null
		if negative and trimmed == LONG_MIN_DIGITS:
			return -0x7FFFFFFFFFFFFFFF - 1
	var v := trimmed.to_int()
	return -v if negative else v


## toIntOrNull(): a 32-bit int, or null.
static func to_int_or_null(s: String) -> Variant:
	var v: Variant = to_long_or_null(s)
	if v == null:
		return null
	var n: int = v
	if n < INT_MIN or n > INT_MAX:
		return null
	return n
