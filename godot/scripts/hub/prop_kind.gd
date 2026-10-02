class_name PropKind
extends RefCounted
## hub/HubMap.kt PropKind: what a hall object is, for building its model.
enum {
	MACHINE,
	COUNTER,
	PRIZE_WALL,
	TOKENS,
	CHANGE,
	VENDING,
	CAFE_TABLE,
	STOOL,
	PILLAR,
	PLANT,
	TRASH,
	PHOTO_BOOTH,
	DECOR,
	BENCH,
	DOORS,
	KIDDIE_RIDE,
	## The café: its floor and lighting rig, back bar, service counter, booths and chairs.
	CAFE_FLOOR,
	CAFE_BAR,
	CAFE_COUNTER,
	BOOTH,
	CHAIR,
}

const COUNT := 21
const NAMES := [
	"MACHINE", "COUNTER", "PRIZE_WALL", "TOKENS", "CHANGE", "VENDING", "CAFE_TABLE", "STOOL", "PILLAR",
	"PLANT", "TRASH", "PHOTO_BOOTH", "DECOR", "BENCH", "DOORS", "KIDDIE_RIDE", "CAFE_FLOOR", "CAFE_BAR",
	"CAFE_COUNTER", "BOOTH", "CHAIR",
]


static func name_of(kind: int) -> String:
	return NAMES[kind] if kind >= 0 and kind < COUNT else str(kind)
