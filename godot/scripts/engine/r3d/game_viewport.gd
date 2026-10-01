class_name GameViewport
extends RefCounted
## engine/r3d/Stage3D.kt GameViewport: where the game field currently sits on the window, set by
## the game host every frame so a game's 3D picture lines up with the interface drawn over it.
## Units are dp (the host's drawing space).

## Slot the game's picture is submitted to.
const SLOT := "game"

## Top-left of the field and dp per field unit.
static var x := 0.0
static var y := 0.0
static var scale := 1.0
## Screen-shake offset in field units, applied while a game draws.
static var shake_x := 0.0
static var shake_y := 0.0
## The field's clip rectangle.
static var clip_x0 := 0.0
static var clip_y0 := 0.0
static var clip_x1 := 0.0
static var clip_y1 := 0.0
## The particle pool of the game being drawn, offered to the GPU picture: the host sets it before
## the game draws and Stage3D.present() puts it into the pass so particles glow in the bloom.
static var particles: Particles = null
## Set by Stage3D.present() when the offered particles are in the GPU picture; anything left
## false (a flat game with no Stage3D) paints them in 2D instead.
static var particles_in_gl := false
## A camera punch the host wants (0 for none); the next Stage3D.begin() takes it.
static var punch_request := 0.0


## Asks for a camera punch of [param amount] (0..1); the strongest request in a frame wins.
static func request_punch(amount: float) -> void:
	if amount > punch_request:
		punch_request = amount


## Hands the pending punch to a stage and clears it.
static func take_punch() -> float:
	var p := punch_request
	punch_request = 0.0
	return p
