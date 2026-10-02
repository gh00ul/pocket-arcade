class_name PhotoBoothPlan
extends RefCounted
## ui/PhotoBoothPlan.kt: what the photo booth does and when: the four poses, and a timeline of one
## go: a 3-2-1 countdown, then a shot every [constant GAP] seconds, each with a flash, then a short
## hold before the strip prints. Pure functions of the time since SNAP, so the screen only has to
## draw them and the tests can check the timing.

## How one shot of the strip is posed: what the kid does, which way they turn, and what the booth
## calls out.
class PhotoPose:
	extends RefCounted
	## A [Pose].
	var pose: int
	## Radians the kid is turned from facing the camera.
	var yaw: float
	## The time fed to the pose's animation (the idle sway, the cheering arms), so the frame is
	## always the same one.
	var time: float
	var cry: String

	func _init(p_pose: int, p_yaw: float, p_time: float, p_cry: String) -> void:
		pose = p_pose
		yaw = p_yaw
		time = p_time
		cry = p_cry


## Where the go has got to. [member count] is the countdown number on show (0 once it is over),
## [member captured] how many shots have been taken, [member pose] the pose on show (the next one
## to be shot once a flash has passed, the last one after the final flash), [member flash] the
## white of a flash still fading (1 at the shot, 0 once gone) and [member done] whether it's all
## over.
class Step:
	extends RefCounted
	var count := 0
	var captured := 0
	var pose := 0
	var flash := 0.0
	var done := false

	func equals(o: Step) -> bool:
		return o != null and count == o.count and captured == o.captured and pose == o.pose and flash == o.flash and done == o.done

	func _to_string() -> String:
		return "Step(count=%d, captured=%d, pose=%d, flash=%s, done=%s)" % [count, captured, pose, flash, done]


## The countdown starts at this number and takes [constant TICK] seconds a number.
const COUNT := 3
const TICK := 0.9

## Seconds from one shot to the next.
const GAP := 1.5

## How long the flash takes to fade.
const FLASH := 0.3

## Seconds after the last flash before the strip is done.
const HOLD := 0.7

## One pose per shot: idle, cheer, sit down, cheers with a drink.
static var poses: Array[PhotoPose] = [
	PhotoPose.new(Pose.STAND, 0.0, 0.0, "SMILE!"),
	PhotoPose.new(Pose.CHEER, 0.2, 0.42, "HANDS UP!"),
	PhotoPose.new(Pose.SIT, -0.28, 0.0, "TAKE A SEAT!"),
	PhotoPose.new(Pose.HOLD, 0.3, 0.0, "CHEERS!"),
]

## When the strip is done.
static var duration: float = shot_time(PhotoStrip.SHOTS - 1) + FLASH + HOLD


## When shot [param i] is taken (seconds after SNAP): straight after the countdown, then every
## [constant GAP].
static func shot_time(i: int) -> float:
	return COUNT * TICK + i * GAP


## The step at [param t] seconds after SNAP, written into [param into] when given (the screen
## reuses one every frame) or a new one.
static func at(t: float, into: Step = null) -> Step:
	var s := into if into != null else Step.new()
	s.count = COUNT - int(maxf(t, 0.0) / TICK) if t < COUNT * TICK else 0
	var captured := 0
	var flash := 0.0
	for i in PhotoStrip.SHOTS:
		var shot_at := shot_time(i)
		if t < shot_at:
			break
		captured = i + 1
		if t < shot_at + FLASH:
			flash = 1.0 - (t - shot_at) / FLASH
	s.captured = captured
	s.pose = mini(captured, PhotoStrip.SHOTS - 1)
	s.flash = flash
	s.done = t >= duration
	return s
