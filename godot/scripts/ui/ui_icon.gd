class_name UiIcon
extends RefCounted
## ui/UiIcons.kt UiIcon: the vector icons of the menus' round buttons and badges (drawn by
## [method UiIcons.draw_ui_icon]). Kotlin's enum entries are the int constants below.

const TROPHY := 0
const SOUND := 1
const MUTED := 2
const CLOSE := 3
const PAUSE := 4
## First person: walking the hall through your own eyes.
const EYE := 5
## The overhead camera following you round the hall.
const CAMERA := 6
## The quick-travel map of the hall.
const MAP := 7
## The settings screen.
const GEAR := 8
## A padlock: something not yet unlocked.
const LOCK := 9
## A tick: owned, done, on.
const CHECK := 10
## A five-point star: favourites, rarity, best scores.
const STAR := 11
## A twinkle: new, special.
const SPARKLE := 12
## A play triangle.
const PLAY := 13

## Each entry's Kotlin name (Enum.name), by value.
const NAMES: PackedStringArray = ["TROPHY", "SOUND", "MUTED", "CLOSE", "PAUSE", "EYE", "CAMERA", "MAP", "GEAR", "LOCK", "CHECK", "STAR", "SPARKLE", "PLAY"]
const COUNT := 14
