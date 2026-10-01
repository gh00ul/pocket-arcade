class_name SpotType
extends RefCounted
## hub/HubMap.kt SpotType: what a spot is for. The machines, the token kiosk and the prize counter
## are placed by hand; every other type belongs to an interactive prop and is generated for it
## ([method HubLayout.spot_type_of], [method HubLayout.prop_spots]). The prompt's words are in
## HubRenderer's prompt drawing; what tapping it does is in ArcadeApp's spot handler.
## Kotlin's `SpotType?` null is [constant NONE].
enum {
	MACHINE,
	TOKENS,
	PRIZES,
	## The photo booth.
	PHOTO,
	## The trophy case (a bought decoration).
	TROPHY,
	## The fish tank (a bought decoration).
	TANK,
	## The café's service counter, at the till.
	CAFE,
	## A kiddie ride (the spot's prop's variant says which).
	RIDE,
	## The jukebox (a bought decoration).
	JUKEBOX,
	## A vending machine (the spot's prop's variant says which).
	VENDING,
}

const NONE := -1
const COUNT := 10
const NAMES := ["MACHINE", "TOKENS", "PRIZES", "PHOTO", "TROPHY", "TANK", "CAFE", "RIDE", "JUKEBOX", "VENDING"]


static func name_of(t: int) -> String:
	return NAMES[t] if t >= 0 and t < COUNT else "null"
