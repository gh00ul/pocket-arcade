class_name ArcadePanel
extends UiView
## Widgets.kt ArcadePanel: a full-screen modal: dims the hall, blocks touches behind it and shows
## a layered glass card: a violet gradient with a bloom of the [member accent] from the top, a fine
## scan texture, a lit inner hairline, a glowing accent edge and its shadow, and a title row (an
## accent bar, the title, a close button) over a fading rule. It arrives: the scrim fades in, the
## card rises and settles with a small overshoot, and the title row lands just after it. Every
## [GlassBox] inside then slides up in turn, in the order it joins. With reduce motion it simply
## fades in.
##
## `var p := ArcadePanel.make("SETTINGS", Pal.c(Pal.CYAN), close, true)`, then put the content in
## with [method add_content] (Compose's ColumnScope content, below the title row and the rule).
## Add it full screen over the hall; it keeps inside the safe area itself.

## How long a panel takes to arrive, all its staggered pieces included.
const PANEL_ENTRANCE_MILLIS := 620.0
## The card's shadow (Modifier.shadow(24.dp)): Android's spot shadow from a light 600 dp above the
## top of the screen with an 800 dp radius, at the platform's 0.19 alpha, tinted with the accent;
## and the faint ambient one (0.039).
const ELEVATION := 24.0
const LIGHT_Z := 600.0
const LIGHT_RADIUS := 800.0
const SPOT_ALPHA := 0.19
const AMBIENT_ALPHA := 0.039

var title := ""
var accent := Pal.c(Pal.PINK)
var on_close := Callable()
var fill_height_panel := false
## The entrance the pieces inside join (LocalEntrance).
var panel_entrance: PanelEntrance
var entrance: UiEntrance
## The card (a Column): the title row, the rule, then the content.
var body: UiColumn
var title_row: UiRow
var close_button: RoundButton
var _surface: _Surface


## ArcadePanel(title, accent, onClose, modifier, fillHeight, content).
static func make(p_title: String, p_accent: Color, p_on_close: Callable, p_fill_height: bool = false) -> ArcadePanel:
	var p := ArcadePanel.new()
	p.title = p_title
	p.accent = p_accent
	p.on_close = p_on_close
	p.fill_height_panel = p_fill_height
	p._build()
	return p


func _init() -> void:
	super()
	blocks_input = true
	content_align = Vector2(0.0, 0.0)
	entrance = UiEntrance.new(PANEL_ENTRANCE_MILLIS)
	panel_entrance = PanelEntrance.new(entrance)
	add_animator(entrance)


func _build() -> void:
	body = UiColumn.new()
	body.h_align = 0.0
	body.fill_width = true
	body.fill_height = fill_height_panel
	body.padding = Vector4(UiSpace.lg, UiSpace.lg, UiSpace.lg, UiSpace.lg)
	body.bg = _paint_body
	add_child(body)
	_surface = _Surface.new()
	_surface.accent = accent
	body.add_child(_surface)
	title_row = UiRow.new()
	title_row.fill_width = true
	title_row.v_align = 0.0
	body.add_child(title_row)
	# An accent bar leads the title, like the tab of a file folder.
	var bar := UiView.new()
	bar.width_dp = 5.0
	bar.height_dp = 26.0
	var acc := accent
	bar.bg = func(ds: DrawScope, area: Vector2) -> void:
		ds.draw_round_rect(PaBrush.vertical([Widgets.lift(acc, 0.35), Widgets.shade(acc, 0.7)], 0.0, area.y), Vector2.ZERO, area, minf(area.x, area.y) / 2.0)
	title_row.add_child(bar)
	title_row.add_child(UiSpacer.w(UiSpace.sm + 2.0))
	var t := ArcadeText.styled(title, UiText.TITLE, Widgets.lift(accent, 0.2))
	t.weight = 1.0
	title_row.add_child(t)
	title_row.add_child(UiSpacer.w(UiSpace.sm))
	close_button = RoundButton.make(UiIcon.CLOSE, _close, Pal.c(Pal.RED), 48.0, "Close")
	title_row.add_child(close_button)
	var rule := ArcadeDivider.make(accent)
	rule.margin = Vector4(0.0, UiSpace.sm, 0.0, UiSpace.md)
	body.add_child(rule)
	entrance.stage(title_row, 0.1, 0.5, 10.0)
	accessibility_name = Widgets.spoken_text(title)


func _close() -> void:
	if on_close.is_valid():
		on_close.call()


## Adds [param view] to the card, below what is there, and returns it.
func add_content(view: UiView) -> UiView:
	body.add_child(view)
	return view


func _ready() -> void:
	super()
	var parent := get_parent()
	if parent is Control and not (parent is UiView):
		set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	entrance.start()
	_apply_entrance()


func _sort() -> void:
	var ins := Widgets.safe_insets(self)
	padding = Vector4(ins.x + UiSpace.md, ins.y + UiSpace.md, ins.z + UiSpace.md, ins.w + UiSpace.md)
	super()
	_apply_entrance()


func _animate(_dt: float) -> bool:
	_apply_entrance()
	return entrance.is_running()


func _apply_entrance() -> void:
	var p := entrance.value()
	var e := Widgets.stage_of(p, 0.0, 0.55)
	var alpha := clampf(e * 2.4, 0.0, 1.0)
	if UiMotion.enabled:
		var k := MathUtil.ease_out_back(e)
		var sc := 0.95 + 0.05 * k
		body.set_layer(alpha, Vector2(sc, sc), Vector2(0.0, (1.0 - k) * 44.0))
	else:
		body.set_layer(alpha, Vector2.ONE, Vector2.ZERO)
	queue_redraw()


## The scrim: the hall dims as the panel arrives.
func _paint(ds: DrawScope) -> void:
	ds.draw_rect(UiColors.scrim, Vector2.ZERO, size, clampf(entrance.value() * 3.2, 0.0, 1.0))


## The card behind its content: the accent shadow, the violet gradient and the bloom of the accent.
func _paint_body(ds: DrawScope, area: Vector2) -> void:
	var r := UiRadius.panel
	paint_shadow(ds, body, area, r, accent)
	ds.draw_round_rect(PaBrush.vertical([UiColors.panel_top, UiColors.panel_bottom], 0.0, area.y), Vector2.ZERO, area, r)
	ds.draw_round_rect(PaBrush.radial([Color(accent, 0.2), Color(0, 0, 0, 0)], Vector2(area.x * 0.28, 0.0), area.x * 0.95), Vector2.ZERO, area, r)


## Modifier.shadow(24.dp, shape, ambientColor = [param color], spotColor = [param color]) under
## [param view]: the spot shadow is the shape cast from a light at the top centre of the screen
## (so it reaches further below than above), softened over its penumbra; the ambient one rings
## it evenly. Drawn as layered translucent shapes (Android blurs a shadow mesh).
static func paint_shadow(ds: DrawScope, view: Control, area: Vector2, corner: float, color: Color) -> void:
	var vp := view.get_viewport_rect().size if view.is_inside_tree() else area
	var at := view.get_global_rect().position if view.is_inside_tree() else Vector2.ZERO
	var k := LIGHT_Z / (LIGHT_Z - ELEVATION)
	var light := Vector2(vp.x / 2.0, 0.0)
	# The projected shape, in the view's own coordinates.
	var tl := (at - light) * k + light - at
	var sz := area * k
	var blur := ELEVATION * LIGHT_RADIUS / (LIGHT_Z - ELEVATION)
	var layers := 10
	for i in layers:
		var f := (i + 0.5) / layers
		var grow := blur * (0.5 - f)
		ds.draw_round_rect(Color(color, SPOT_ALPHA / layers * 1.15), tl - Vector2(grow, grow), sz + Vector2(grow * 2.0, grow * 2.0), corner + maxf(grow, 0.0))
	var amb := ELEVATION * 0.5
	for i in 4:
		var f := (i + 0.5) / 4.0
		var grow := amb * f
		ds.draw_round_rect(Color(color, AMBIENT_ALPHA / 4.0 * 1.15), -Vector2(grow, grow), area + Vector2(grow * 2.0, grow * 2.0), corner + grow)


## The card's surface over its gradient: the scan texture (tiled one texel per screen pixel, so it
## needs a canvas item of its own with repeat on), the lit hairline inside the top edge, and the
## accent border.
class _Surface:
	extends Control
	var accent := Color.WHITE

	func _init() -> void:
		mouse_filter = Control.MOUSE_FILTER_IGNORE
		texture_repeat = CanvasItem.TEXTURE_REPEAT_ENABLED
		texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST

	func _draw() -> void:
		var corner := UiRadius.panel
		var d := maxf(Display.density, 0.01)
		var pts := PaPath.round_rect_points(0.0, 0.0, size.x, size.y, corner, corner, 8)
		var uvs := PackedVector2Array()
		uvs.resize(pts.size())
		var tile := float(UiTheme.UiTexture.TILE)
		for i in pts.size():
			uvs[i] = pts[i] * d / tile
		draw_polygon(pts, PackedColorArray([Color(1, 1, 1, UiTheme.UiTexture.PANEL_ALPHA)]), uvs, UiTheme.UiTexture.scan())
		var ds := DrawScope.new(self, size)
		var inset := 1.5
		UiDraw.stroke_round_rect_vgradient(ds, Vector2(inset, inset), size - Vector2(inset * 2.0, inset * 2.0), maxf(corner - inset, 0.0), 1.0,
			PackedColorArray([UiColors.bevel_light, Color(1, 1, 1, 0.02)]), 0.0, size.y * 0.4)
		Widgets.border_vgradient(ds, size, UiEdge.strong, PackedColorArray([accent, Widgets.shade(accent, 0.45)]), corner)
