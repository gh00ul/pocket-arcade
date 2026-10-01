class_name Stinger
extends RefCounted
## engine/audio/Music.kt Stinger: short musical hits that play over the scene and duck it while
## they sound (same order as build-13).

enum {
	## One count of the 3-2-1: a soft thump and tick.
	COUNTDOWN,
	## The round starts: a hit, a rising sparkle.
	GO,
	## The clock ran out: a falling power-down.
	TIME_UP,
	## The results appear: a resolving chime.
	RESULTS,
	## A new high score: a bright fanfare.
	HIGH_SCORE,
}

const COUNT := 5
const NAMES := ["COUNTDOWN", "GO", "TIME_UP", "RESULTS", "HIGH_SCORE"]
