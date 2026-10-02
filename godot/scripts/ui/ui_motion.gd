class_name UiMotion
extends RefCounted
## ui/RollingNumber.kt UiMotion: whether the interface may move: false with the reduce-motion
## setting (the same switch that silences the screen shake). Read where the motion is decided, not
## remembered, so the setting takes effect at once.

static var enabled: bool:
	get:
		return ScreenShake.intensity > 0.0
