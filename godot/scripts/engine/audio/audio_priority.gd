class_name AudioPriority
extends RefCounted
## engine/audio/HallAmbience.kt Priority: voice priorities. When the mixer is full, lower ones are
## stolen first.

## Machine bleeps and cafe clinks: the first to go.
const AMBIENT := 0
## Footsteps and other small things.
const MINOR := 1
## Everything a game plays.
const NORMAL := 2
## Jingles and fanfares: never stolen for a lesser sound.
const KEY := 3
