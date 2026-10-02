class_name UiText
extends RefCounted
## ui/UiTheme.kt UiText: the type scale. Text is set in the game's own face, sized in grid units
## ([ArcadeText]), so the hierarchy is size, weight (the display face for big words, the condensed
## face for small ones) and tracking (extra space between letters, in units: small capitals read
## better spaced out). Kotlin's enum entries are the static values below, each with its [member unit]
## (dp), [member tiny] and [member tracking].

var name: String
## Grid unit in dp (capitals are 7 units tall, tiny ones 5).
var unit: float
var tiny: bool
var tracking: float
var ordinal: int

## Hero numbers and one-word banners.
static var DISPLAY := UiText.new("DISPLAY", 0, 5.0, false, 0.0)
## A panel's title.
static var TITLE := UiText.new("TITLE", 1, 3.3, false, 0.2)
## A section or box heading, an item's name.
static var HEADING := UiText.new("HEADING", 2, 2.6, false, 0.1)
## Running words and values.
static var BODY := UiText.new("BODY", 3, 2.1, false, 0.0)
## A label above or beside a value; condensed and spaced.
static var LABEL := UiText.new("LABEL", 4, 2.1, true, 0.5)
## The smallest legible note: hints, counts, tags.
static var CAPTION := UiText.new("CAPTION", 5, 1.75, true, 0.45)

static var ENTRIES: Array[UiText] = [DISPLAY, TITLE, HEADING, BODY, LABEL, CAPTION]


func _init(p_name: String, p_ordinal: int, p_unit: float, p_tiny: bool, p_tracking: float) -> void:
	name = p_name
	ordinal = p_ordinal
	unit = p_unit
	tiny = p_tiny
	tracking = p_tracking


func _to_string() -> String:
	return name
