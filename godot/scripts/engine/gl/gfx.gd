class_name Gfx
extends RefCounted
## engine/gl/Gfx.kt + GlSurface.kt: the 3D pictures on screen. Scenes record a [RenderPass] with
## [Renderer3D] and submit it to a named slot ("game", "hall", "title", ...); each slot is a Godot
## SubViewport scene ([GfxSlot]) composited under the interface at the pass's rectangle.
## A skipped pass (frame-rate cap) is dropped, so the slot keeps its last picture.
##
## Without a host node (headless tests) submissions are only remembered, for inspection.

## The Control the slots' pictures are drawn in (under every other piece of UI), set by the app.
static var host: Control = null
static var _slots := {}
static var _last := {}
## Render scale of the 3D pictures (the pacer moves it within the rung's floor and ceiling).
static var render_scale := 0.8
## Set when the GPU can't draw the pictures (GfxFailureNotice shows it).
static var failure := ""
## Submissions per slot since start (tests).
static var submissions := {}


static func submit(slot_name: String, rp: RenderPass) -> void:
	if rp == null or rp.skipped:
		return
	submissions[slot_name] = int(submissions.get(slot_name, 0)) + 1
	_last[slot_name] = rp
	if host == null or not is_instance_valid(host):
		return
	var slot: GfxSlot = _slots.get(slot_name)
	if slot == null or not is_instance_valid(slot):
		slot = GfxSlot.new()
		slot.slot_name = slot_name
		slot.name = "Slot_" + slot_name
		host.add_child(slot)
		_slots[slot_name] = slot
	slot.apply(rp)


## Stops drawing [param slot_name] (a screen that went away).
static func remove(slot_name: String) -> void:
	_last.erase(slot_name)
	var slot: GfxSlot = _slots.get(slot_name)
	if slot != null and is_instance_valid(slot):
		slot.queue_free()
	_slots.erase(slot_name)


static func slot(slot_name: String) -> GfxSlot:
	var s: GfxSlot = _slots.get(slot_name)
	return s if s != null and is_instance_valid(s) else null


## The last pass submitted to [param slot_name] (tests).
static func last_pass(slot_name: String) -> RenderPass:
	return _last.get(slot_name)


static func has_slot(slot_name: String) -> bool:
	return _last.has(slot_name)


## Forgets every slot (tests, and the app shutting down).
static func reset() -> void:
	for s in _slots.values():
		if is_instance_valid(s):
			(s as Node).queue_free()
	_slots.clear()
	_last.clear()
	submissions.clear()
