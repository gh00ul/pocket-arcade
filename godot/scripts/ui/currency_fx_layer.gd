class_name CurrencyFxLayer
extends Control
## ui/CurrencyFx.kt CurrencyFxLayer: draws a [CurrencyFx]'s flights over the whole screen and runs
## its frames while anything is in the air (and not otherwise). Put it above the screens whose
## counters the flights end at. It never takes a touch.

var fx: CurrencyFx


## CurrencyFxLayer(fx, modifier).
static func make(p_fx: CurrencyFx) -> CurrencyFxLayer:
	var l := CurrencyFxLayer.new()
	l.fx = p_fx
	return l


func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_process(false)


func _ready() -> void:
	if get_parent() is Control and not (get_parent() is Container):
		set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	if fx != null:
		fx.launched.connect(_on_launched)
		_measure()
	set_process(fx != null and fx.any())


func _notification(what: int) -> void:
	if what == NOTIFICATION_RESIZED:
		_measure()


func _measure() -> void:
	if fx == null:
		return
	fx.dp = Display.density
	fx.view_w = size.x * Display.density
	fx.view_h = size.y * Display.density


func _on_launched() -> void:
	_measure()
	set_process(true)
	queue_redraw()


func _process(dt: float) -> void:
	if fx == null:
		set_process(false)
		return
	fx.update(minf(dt, 0.05))
	queue_redraw()
	if not fx.any():
		set_process(false)


func _draw() -> void:
	if fx == null or not fx.any():
		return
	var ds := DrawScope.new(self, size)
	var k := 1.0 / maxf(Display.density, 0.01)
	ds.scale_by(k, k, Vector2.ZERO)
	fx.draw(ds)
