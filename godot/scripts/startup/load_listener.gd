class_name LoadListener
extends RefCounted
## startup/LoadPlan.kt LoadListener: told about each step as it finishes (the startup log, the
## tests). Override what you need; both do nothing by default.


## [param step] (number [param index]) finished after [param ns] nanoseconds of the driver's clock;
## [param failure] is the message its work returned (Kotlin's exception), or "" if it went well.
func on_step_done(_step: LoadStep, _index: int, _ns: int, _failure: String) -> void:
	pass


## [param step], a wait, gave up after its timeout.
func on_timeout(_step: LoadStep.Wait) -> void:
	pass
