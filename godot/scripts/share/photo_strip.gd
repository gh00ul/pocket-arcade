class_name PhotoStrip
extends RefCounted
## share/PhotoStrip.kt: what goes on a photo strip and what it's called: how many shots, the words,
## the date, the file's name. Pure, so it is tested without a device. The strip's geometry is
## [StripLayout] and its rectangles [PxRect] (files of their own: the hall's photo wall uses them).

## Four shots make a strip.
const SHOTS := 4

## How many pixels across each shot is photographed.
const FRAME := 360

const _PREFIX := "strip-"
const _SUFFIX := ".png"
const _MONTHS: PackedStringArray = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]

## [method date_label]'s zone when none is given: the phone's own (Kotlin's ZoneId.systemDefault()).
const SYSTEM_ZONE := -1000000

static var _name_re: RegEx = RegEx.create_from_string("^strip-\\d{8}-\\d{6}-\\d{3}\\.png$")


## The file a strip made at [param millis] (ms since 1970, UTC) is saved as:
## "strip-yyyyMMdd-HHmmss-SSS.png". The name is the UTC time, so names sort in the order strips
## were made whatever the time zone or daylight saving does.
static func file_name(millis: int) -> String:
	var secs := _floor_div(millis, 1000)
	var ms := millis - secs * 1000
	var d := Time.get_datetime_dict_from_unix_time(secs)
	return "%s%04d%02d%02d-%02d%02d%02d-%03d%s" % [_PREFIX, d["year"], d["month"], d["day"], d["hour"], d["minute"], d["second"], ms, _SUFFIX]


## Whether [param name] is one of ours (so other files in the folder are never touched).
static func is_strip_name(name: String) -> bool:
	var m := _name_re.search(name)
	# The whole name must match (Kotlin's Regex.matches): PCRE's `$` would also allow a final newline.
	return m != null and m.get_start() == 0 and m.get_end() == name.length()


## The date printed at the foot of the strip: "SEP 28 2026", in the type's capitals, on the
## calendar of the zone [param zone_minutes] east of UTC ([constant SYSTEM_ZONE]: the phone's
## zone now).
static func date_label(millis: int, zone_minutes: int = SYSTEM_ZONE) -> String:
	var offset := system_zone_minutes() if zone_minutes == SYSTEM_ZONE else zone_minutes
	var d := Time.get_date_dict_from_unix_time(_floor_div(millis + offset * 60000, 1000))
	return "%s %d %d" % [_MONTHS[int(d["month"]) - 1], d["day"], d["year"]]


## The phone's offset from UTC in minutes, east positive (its zone's rule as it stands now).
static func system_zone_minutes() -> int:
	return int(Time.get_time_zone_from_system().get("bias", 0))


## Integer division rounding down (Instant.ofEpochMilli's split of a time before 1970).
static func _floor_div(a: int, b: int) -> int:
	var q := a / b
	if a % b != 0 and (a < 0) != (b < 0):
		q -= 1
	return q


## The part of a [param src_w] by [param src_h] picture that fills a [param dst_w] by
## [param dst_h] frame without squashing it: the whole picture trimmed evenly at the sides (or top
## and bottom) to the frame's shape.
static func crop_to_aspect(src_w: int, src_h: int, dst_w: int, dst_h: int) -> PxRect:
	if src_w <= 0 or src_h <= 0 or dst_w <= 0 or dst_h <= 0:
		return PxRect.new(0, 0, maxi(src_w, 0), maxi(src_h, 0))
	# Compare src_w/src_h with dst_w/dst_h without dividing.
	if src_w * dst_h > src_h * dst_w:
		var w := clampi(src_h * dst_w / dst_h, 1, src_w)
		var left := (src_w - w) / 2
		return PxRect.new(left, 0, left + w, src_h)
	var h := clampi(src_w * dst_h / dst_w, 1, src_h)
	var top := (src_h - h) / 2
	return PxRect.new(0, top, src_w, top + h)


## The text size (in the type's grid units) that fits a line [param natural] wide at one unit into
## [param available] pixels, never bigger than [param max_unit]: a long arcade name shrinks to fit.
static func fit_unit(natural: float, available: float, max_unit: float) -> float:
	if natural <= 0.0 or available <= 0.0:
		return max_unit
	return minf(max_unit, available / natural)
