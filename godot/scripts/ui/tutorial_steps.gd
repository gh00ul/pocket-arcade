class_name Tutorial
extends RefCounted
## ui/TutorialSteps.kt: the first-run tutorial as a step machine: WALK, LOOK, PLAY, PRIZES. Each step
## advances when the player actually does the thing (never on a timer, never on a button), shows a
## brief tick, then the next begins; the last is followed by a short thank-you. It never blocks
## anything: it only watches. The walk and the look are soft steps that count only what was done;
## playing a round and opening the prize counter are hard ones: if the player gets there first,
## everything before it is done too. [method skip_step] and [method skip_all] end things early.
##
## Time is fed in by [method update] with the fixed step, so the machine is deterministic.
##
## The file's other declarations live here too: `TUTORIAL_DONE` ([constant TUTORIAL_DONE]),
## `TutorialPolicy` ([Tutorial.TutorialPolicy]), `TutorialStep` ([enum TutorialStep]),
## `TutorialInput` ([Tutorial.TutorialInput]), `CoachText` ([Tutorial.CoachText]) and
## `GuideMarker` ([Tutorial.GuideMarker]).
##
## Spots (hub/HubMap.kt `Spot`) are read by duck typing: `type` (a SpotType value) and `area` (a
## Box with left/top/right/bottom), so this runs before the hall's port lands.

## The unlock id (in the save, through ArcadeRepository.unlock) that says the tutorial has been
## seen. Never change it: it is what existing installs have saved.
const TUTORIAL_DONE := "tutorial_done"

## The lesson steps, in order.
enum TutorialStep { WALK, LOOK, PLAY, PRIZES }

## Where the machine is: waiting for the step's action, showing its tick, saying goodbye, or over.
enum Phase { ACTIVE, CHECK, OUTRO, FINISHED }

## hub/HubMap.kt SpotType.MACHINE and SpotType.PRIZES (the enum's first and third values).
const SPOT_MACHINE := 0
const SPOT_PRIZES := 2

## Distance (world units) walked, in total, that counts as learning to walk. The hall is 608 across.
const WALK_DISTANCE := 90.0

## Radians of view turned by a finger, in total, that counts as learning to look (about 34 degrees).
const LOOK_ANGLE := 0.6

## A position jump bigger than this in one update is a nudge or a teleport, not walking.
const TELEPORT := 40.0

## How long the tick after a step stays, and how long the thank-you after the last stays.
const CHECK_SECONDS := 1.1
const OUTRO_SECONDS := 2.8

## How long a card takes to arrive.
const ENTER_SECONDS := 0.4

const STEP_COUNT := 4

var step: int = TutorialStep.WALK
var phase: int = Phase.ACTIVE

## Seconds since the phase began (a step's card arrives over [constant ENTER_SECONDS] from zero).
var phase_time := 0.0

## Whether the player ended it (or skipped its last step) rather than finishing it.
var skipped := false

## Total distance walked and total turn made by a finger, since the tutorial began.
var walked := 0.0
var looked := 0.0

var _played := false
var _prizes_seen := false
var _last_x := 0.0
var _last_y := 0.0
var _last_yaw := 0.0
var _has_baseline := false

var finished: bool:
	get:
		return phase == Phase.FINISHED

## Steps done so far, 0 to [member step_count]: for the progress dots.
var steps_done: int:
	get:
		return step if phase == Phase.ACTIVE else step + 1

var step_count: int:
	get:
		return STEP_COUNT


## A round of a machine has begun.
func on_played() -> void:
	_played = true


## The prize counter's screen has been opened.
func on_prizes_opened() -> void:
	_prizes_seen = true


## Ends the whole tutorial now.
func skip_all() -> void:
	if phase == Phase.FINISHED:
		return
	skipped = true
	_set_phase(Phase.FINISHED)


## Gives up on the current step: on to the next, or over if it was the last.
func skip_step() -> void:
	if phase != Phase.ACTIVE:
		return
	if step == STEP_COUNT - 1:
		skip_all()
	else:
		step += 1
		_set_phase(Phase.ACTIVE)


func _set_phase(p: int) -> void:
	phase = p
	phase_time = 0.0


func _triggered(s: int) -> bool:
	if s == TutorialStep.WALK:
		return walked >= WALK_DISTANCE
	if s == TutorialStep.LOOK:
		return looked >= LOOK_ANGLE
	if s == TutorialStep.PLAY:
		return _played
	return _prizes_seen


## Whether reaching [param s] proves everything before it (playing, the prize counter), rather than
## being one thing done.
static func _is_hard(s: int) -> bool:
	return s == TutorialStep.PLAY or s == TutorialStep.PRIZES


## Advances by [param dt] seconds with the hall as [param input] shows it.
func update(dt: float, input: TutorialInput) -> void:
	if phase == Phase.FINISHED or input.blocked:
		return
	_track(input)
	phase_time += dt
	if phase == Phase.ACTIVE:
		# The furthest hard step already done wins: it settles everything before it.
		var reached := -1
		for i in range(step, STEP_COUNT):
			if _is_hard(i) and _triggered(i):
				reached = i
		if reached >= 0:
			step = reached
			_set_phase(Phase.CHECK)
		elif _triggered(step):
			_set_phase(Phase.CHECK)
	elif phase == Phase.CHECK:
		if phase_time >= CHECK_SECONDS:
			if step == STEP_COUNT - 1:
				_set_phase(Phase.OUTRO)
			else:
				step += 1
				_set_phase(Phase.ACTIVE)
	elif phase == Phase.OUTRO:
		if phase_time >= OUTRO_SECONDS:
			_set_phase(Phase.FINISHED)


## Adds this update's walking and looking to the totals.
func _track(input: TutorialInput) -> void:
	if not _has_baseline:
		_has_baseline = true
		_last_x = input.x
		_last_y = input.y
		_last_yaw = input.yaw
	var dx := input.x - _last_x
	var dy := input.y - _last_y
	var moved := sqrt(dx * dx + dy * dy)
	if moved < TELEPORT:
		walked += moved
	_last_x = input.x
	_last_y = input.y
	var turn := absf(wrap_angle(input.yaw - _last_yaw))
	# Only a finger turning the view counts, not the view turning itself to face a machine.
	if input.first_person and input.look_dragging:
		looked += turn
	_last_yaw = input.yaw


## hub/Camera.kt HubCamera.wrap: an angle into (-PI, PI].
static func wrap_angle(a: float) -> float:
	var r := fmod(a, TAU)
	if r > PI:
		r -= TAU
	if r <= -PI:
		r += TAU
	return r


## What the card says now for [param input]: the step's lesson (in words that fit the current view
## and the player's hand), the tick after it, or the thank-you at the end.
func text(input: TutorialInput) -> CoachText:
	if phase == Phase.OUTRO or phase == Phase.FINISHED:
		return CoachText.new("YOU'RE ALL SET!", "HAVE FUN. COME BACK EVERY DAY\nFOR FREE TOKENS.", true)
	if phase == Phase.CHECK:
		var body := ""
		if step == TutorialStep.WALK:
			body = "THAT'S HOW YOU GET AROUND."
		elif step == TutorialStep.LOOK:
			body = "NOW YOU CAN TAKE IT ALL IN."
		elif step == TutorialStep.PLAY:
			body = "TICKETS PRINT OUT OF EVERY MACHINE."
		else:
			body = "TICKETS BUY HATS, OUTFITS\nAND DECORATIONS."
		return CoachText.new("NICE!", body, true)
	var walk_side := "RIGHT" if input.left_handed else "LEFT"
	var look_side := "LEFT" if input.left_handed else "RIGHT"
	if step == TutorialStep.WALK:
		return CoachText.new("WALK AROUND",
			("DRAG ON THE %s OF THE SCREEN\nTO WALK. TAP A MACHINE TO GO THERE." % walk_side) if input.first_person else "DRAG ANYWHERE TO WALK,\nOR TAP THE FLOOR.")
	if step == TutorialStep.LOOK:
		return CoachText.new("LOOK AROUND",
			("DRAG ON THE %s OF THE SCREEN\nTO LOOK AROUND." % look_side) if input.first_person else "TAP THE EYE BUTTON (TOP RIGHT)\nTO SEE THROUGH YOUR KID'S EYES.")
	if step == TutorialStep.PLAY:
		return CoachText.new("PLAY A MACHINE",
			"TAP THE PLAY BUBBLE.\nIT COSTS ONE TOKEN." if input.at_machine else "WALK UP TO ANY MACHINE.\nFOLLOW THE ARROW.")
	return CoachText.new("VISIT THE PRIZE COUNTER",
		"TAP THE SHOP BUBBLE." if input.at_prizes else "IT'S AT THE BACK OF THE HALL.\nTAP IT TO WALK THERE.")


## The spot the arrow should point at for [param input], or null (no arrow): the nearest machine
## while playing and not yet at one, the prize counter while visiting it and not yet there.
func guide_target(spots: Array, input: TutorialInput) -> Object:
	if phase != Phase.ACTIVE:
		return null
	if step == TutorialStep.PLAY:
		return null if input.at_machine else _nearest(spots, SPOT_MACHINE, input.x, input.y)
	if step == TutorialStep.PRIZES:
		return null if input.at_prizes else _nearest(spots, SPOT_PRIZES, input.x, input.y)
	return null


static func _nearest(spots: Array, type: int, x: float, y: float) -> Object:
	var best: Object = null
	var best_d := INF
	for i in spots.size():
		var s: Object = spots[i]
		if int(s.get("type")) != type:
			continue
		var area: Object = s.get("area")
		var dx: float = (float(area.get("left")) + float(area.get("right"))) / 2.0 - x
		var dy: float = (float(area.get("top")) + float(area.get("bottom"))) / 2.0 - y
		var d := dx * dx + dy * dy
		if d < best_d:
			best_d = d
			best = s
	return best


## ui/TutorialSteps.kt TutorialPolicy: when the first-run tutorial starts by itself.
class TutorialPolicy:
	## A new player who hasn't seen it: never seen ([param seen]) and never played a round
	## ([param total_plays] is 0). Anyone who has played already knows their way round and is left
	## alone; Settings can still replay it for them.
	static func should_auto_start(seen: bool, total_plays: int) -> bool:
		return not seen and total_plays <= 0


## ui/TutorialSteps.kt TutorialInput: what the tutorial reads from the hall each frame: a plain
## snapshot, so tests can drive the step machine without a hall. (That a round was started or the
## prize counter opened come as events instead, [method Tutorial.on_played] and
## [method Tutorial.on_prizes_opened], since neither is on screen when it happens.)
class TutorialInput:
	## Where the player's feet are (world units).
	var x := 0.0
	var y := 0.0
	## Whether the hall is in first person, and the view's heading (radians) and whether a finger is turning it.
	var first_person := false
	var yaw := 0.0
	var look_dragging := false
	## Whether the player is standing at a machine's play spot, or the prize counter's.
	var at_machine := false
	var at_prizes := false
	## Whether the player is left-handed (the walk and look halves swap).
	var left_handed := false
	## A panel or a transition has the screen: nothing counts and the card waits.
	var blocked := false


## ui/TutorialSteps.kt CoachText: what a coach card says: a title and up to two lines under it.
class CoachText:
	var title: String
	var body: String
	var check: bool

	func _init(p_title: String, p_body: String, p_check: bool = false) -> void:
		title = p_title
		body = p_body
		check = p_check

	func equals(o: CoachText) -> bool:
		return o != null and o.title == title and o.body == body and o.check == check


## ui/TutorialSteps.kt GuideMarker: where to draw a guiding arrow for a point in the camera's view space.
class GuideMarker:
	## How far to the side a point behind the camera has to be (view units) for its arrow to be half
	## way to that edge.
	const BEHIND_SOFTNESS := 60.0

	## Places a marker for a point at view-space ([param vx], [param vy], [param vz]) (x right, y up,
	## z ahead) seen through a camera with focal length [param focal] and image centre ([param cx],
	## [param cy]) on a [param w] × [param h] screen. On screen (inside [param margin] of the sides
	## and between [param top] and [param bottom]) it sits on the point and points down at it. Off
	## screen, or behind the camera, it sits on the edge of that rectangle in the direction of the
	## point, pointing that way (behind you it sits along the bottom edge, on the side the point is
	## on). Writes x, y and the angle to point at (radians, 0 right, clockwise on screen) into
	## [param out] and returns whether it is on the point.
	static func place(vx: float, vy: float, vz: float, focal: float, cx: float, cy: float, near: float,
			w: float, h: float, margin: float, top: float, bottom: float, out: PackedFloat32Array) -> bool:
		var mid_x := w / 2.0
		var mid_y := (top + bottom) / 2.0
		var half_w := maxf(w / 2.0 - margin, 1.0)
		var half_h := maxf((bottom - top) / 2.0, 1.0)
		var dx: float
		var dy: float
		if vz >= near:
			var sx := cx + vx / vz * focal
			var sy := cy - vy / vz * focal
			if sx >= margin and sx <= w - margin and sy >= top and sy <= bottom:
				out[0] = sx
				out[1] = sy
				out[2] = PI / 2.0
				return true
			dx = sx - mid_x
			dy = sy - mid_y
		else:
			# Behind: along the bottom edge, pointing down ("turn round"), nearer the side the point is on.
			out[0] = mid_x + vx / (absf(vx) + BEHIND_SOFTNESS) * half_w
			out[1] = bottom
			out[2] = PI / 2.0
			return false
		if absf(dx) < 1e-3 and absf(dy) < 1e-3:
			dy = 1.0
		var kx := INF if absf(dx) < 1e-6 else half_w / absf(dx)
		var ky := INF if absf(dy) < 1e-6 else half_h / absf(dy)
		var k := minf(kx, ky)
		out[0] = mid_x + dx * k
		out[1] = mid_y + dy * k
		out[2] = atan2(dy, dx)
		return false
