class_name TokenGate
extends RefCounted
## data/TokenGate.kt: a guard for taps that spend or refund a token. [method try_claim] is taken
## on the tap itself, so a second tap before the action settles is turned away; [method release]
## lets the next one through.

## True while a claimed action is still waiting to settle.
var claimed := false


## Takes the guard. Returns false, changing nothing, if an earlier claim hasn't settled.
func try_claim() -> bool:
	if claimed:
		return false
	claimed = true
	return true


func release() -> void:
	claimed = false
