class_name Pose
extends RefCounted
## hub/Figures.kt Pose: what a figure is doing, which sets its pose. With a café treat: CARRY
## walking with it, HOLD standing with it, SIP sitting and sipping; WIPE is the barista wiping the
## counter. WAVE is a kid greeting the player and CLAP applauding (the prize counter's crowd).
## Poses cross-fade (see [PoseBlender]), so switching between them never snaps.
##
## Kotlin's enum is an int here (its ordinal); [constant NONE] stands for Kotlin's null `Pose?`.

enum { STAND, WALK, PLAY, CHEER, SIT, WIPE, CARRY, HOLD, SIP, WAVE, CLAP }

## How many poses there are (the size of a [PoseBlender]'s weights).
const COUNT := 11
## No pose (Kotlin's null).
const NONE := -1
## Every pose by ordinal (Kotlin's `Pose.ALL`).
const ALL: Array[int] = [STAND, WALK, PLAY, CHEER, SIT, WIPE, CARRY, HOLD, SIP, WAVE, CLAP]
const NAMES: Array[String] = ["STAND", "WALK", "PLAY", "CHEER", "SIT", "WIPE", "CARRY", "HOLD", "SIP", "WAVE", "CLAP"]


## The Kotlin name of [param pose] (for messages), or "null" for [constant NONE].
static func name_of(pose: int) -> String:
	if pose < 0 or pose >= COUNT:
		return "null"
	return NAMES[pose]
