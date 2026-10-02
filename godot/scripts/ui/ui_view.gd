class_name UiView
extends Container
## The node every menu piece is built from, standing in for a Compose layout node and its
## modifiers. As a plain UiView it is Compose's Box: children stacked, each placed by its
## alignment. [UiColumn], [UiRow], [UiScroll] and [UiGrid] lay their children out as Column, Row,
## verticalScroll and LazyVerticalGrid do, and every widget of the kit ([ArcadeText],
## [ArcadeButton], [GlassBox]...) is one.
##
## Sizes follow Compose's rules, measured top-down with constraints by [method ui_measure]
## (wrap content, a fixed size, fill the max, a weight in a row or column, padding, a minimum
## height, an aspect ratio) instead of Godot's minimum sizes, so a label can shrink to the width
## it is offered as `shrinkToFit` lets it. Positions and sizes snap to whole screen pixels, as
## Compose's do. Everything is in dp (see [Display]).
##
## What Compose modifiers add on top: [member bg] (a drawing behind the content: background,
## border, drawBehind), a glow ([member glow_strength]), a graphics layer ([method set_layer]:
## alpha, scale and translation that move what is drawn but not the layout), a click with
## build-13's press spring ([member clickable]), and semantics for screen readers
## ([member accessibility_name], [member access_role], [member access_clear]...).
##
## Modifier-style setters return the view, so a tree reads like Compose:
## `UiColumn.new().fill_max_width().with_padding(12).with_weight(1.0)`.

## Emitted on a click (after [member on_click] runs).
signal clicked

# ---------------------------------------------------------------- layout modifiers

## Modifier.width / size, in dp (-1: wrap the content).
var width_dp := -1.0
var height_dp := -1.0
## widthIn(min) / heightIn(min), in dp.
var min_width_dp := 0.0
var min_height_dp := 0.0
## fillMaxWidth() / fillMaxHeight().
var fill_width := false
var fill_height := false
## aspectRatio(width / height), 0 for none.
var aspect_ratio := 0.0
## Padding outside what is drawn (a padding modifier before the background), left, top, right, bottom.
var margin := Vector4.ZERO
## Padding inside what is drawn (after the background): the content's inset.
var padding := Vector4.ZERO
## Modifier.weight in a [UiRow] or [UiColumn] (0: none), and its `fill`.
var weight := 0.0
var weight_fill := true
## Modifier.align inside a Box parent: (x, y) bias, -1 start, 0 centre, 1 end; NAN: the parent's
## [member content_align].
var align := Vector2(NAN, NAN)
## Box(contentAlignment): where children sit by default.
var content_align := Vector2(-1.0, -1.0)
## Keeps its place in the layout while hidden (Widgets.kt reserveSpace).
var reserve_space := false
## Takes a whole line of a [UiGrid] (GridItemSpan(maxLineSpan)).
var grid_span := false

# ---------------------------------------------------------------- entrances

## Things stepped with this view's frames (entrances, pop-ins): objects with step(dt) -> bool.
var animators: Array = []
## Widgets.stagger_in: the place this piece takes in its panel's entrance (-1: none), and its rise.
var stagger_index := -1
var stagger_rise := 14.0
## Claims the next place in its panel's entrance when it joins the tree (GlassBox).
var claims_entrance := false
var _claimed := -1

# ---------------------------------------------------------------- drawing

## Painted behind the content: Callable(ds: DrawScope, size: Vector2).
var bg := Callable()
## A glow round the box (Modifier.uiGlow): colour, corner, reach (dp) and strength (0: none);
## [member glow_fn] (a Callable() -> float), when set, gives the strength while drawing.
var glow_color := Color.WHITE
var glow_corner := 0.0
var glow_reach := UiGlow.REACH
var glow_strength := 0.0
var glow_fn := Callable()

# ---------------------------------------------------------------- layer and press

var layer_alpha := 1.0
var layer_scale := Vector2.ONE
var layer_offset := Vector2.ZERO
## TransformOrigin (fractions of the size).
var layer_origin := Vector2(0.5, 0.5)
## How much of [constant Widgets.PRESS_SHRINK] the press shrinks this by (1 for a button, 0 none).
var press_shrink := 0.0

## A click target (Modifier.clickable): takes touches, springs when pressed, calls [member on_click].
var clickable := false:
	set(v):
		clickable = v
		_update_filter()
var enabled := true:
	set(v):
		if enabled == v:
			return
		enabled = v
		if not v:
			_set_pressed(false)
		_on_enabled_changed()
		queue_redraw()
		queue_accessibility_update()
var on_click := Callable()
## The press spring (0 at rest, 1 down; below 0 in the release overshoot). Null until first pressed.
var press: UiAnim.Animatable = null
## Blocks every touch behind it (a modal's scrim, Modifier.clickable {} over the whole screen).
var blocks_input := false:
	set(v):
		blocks_input = v
		_update_filter()

# ---------------------------------------------------------------- semantics

## The role a screen reader announces (DisplayServer.ROLE_*), -1 for the default.
var access_role := -1
## clearAndSetSemantics: what is inside is hidden from screen readers (this node speaks for it).
var access_clear := false
## A switch's state (0 off, 1 on, -1 not a switch) and a tab's selection (-1 not a tab).
var access_checked := -1
var access_selected := -1
## A progress bar's value (0..1), NAN for none.
var access_progress := NAN

## The touch slop (ViewConfiguration's 8 dp): a finger moving further is a drag, not a tap.
const TOUCH_SLOP := 8.0

## The view being pressed right now (one finger drives the menus), so a scroll can cancel it.
static var pressed_view: UiView = null

var _slot_pos := Vector2.ZERO
var _placed := false
var _awake := false
var _pressed := false
var _press_at := Vector2.ZERO
var _mc_keys := PackedVector4Array()
var _mc_vals := PackedVector2Array()


func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	focus_mode = Control.FOCUS_NONE
	set_process(false)


func _ready() -> void:
	set_process(_awake or _wants_process())


func _get_minimum_size() -> Vector2:
	# Sizes come from ui_measure; a Godot minimum would stop a label from shrinking.
	return Vector2.ZERO


func _update_filter() -> void:
	if blocks_input:
		mouse_filter = Control.MOUSE_FILTER_STOP
	elif clickable:
		mouse_filter = Control.MOUSE_FILTER_PASS
	else:
		mouse_filter = Control.MOUSE_FILTER_IGNORE


# ---------------------------------------------------------------- modifier-style setters

func with_size(w: float, h: float) -> UiView:
	width_dp = w
	height_dp = h
	ui_invalidate()
	return self


func with_width(w: float) -> UiView:
	width_dp = w
	ui_invalidate()
	return self


func with_height(h: float) -> UiView:
	height_dp = h
	ui_invalidate()
	return self


func with_min_height(h: float) -> UiView:
	min_height_dp = h
	ui_invalidate()
	return self


func with_min_width(w: float) -> UiView:
	min_width_dp = w
	ui_invalidate()
	return self


func fill_max_width() -> UiView:
	fill_width = true
	ui_invalidate()
	return self


func fill_max_height() -> UiView:
	fill_height = true
	ui_invalidate()
	return self


func fill_max_size() -> UiView:
	fill_width = true
	fill_height = true
	ui_invalidate()
	return self


func with_aspect(ratio: float) -> UiView:
	aspect_ratio = ratio
	ui_invalidate()
	return self


## Padding inside (after the background): all sides, or (horizontal, vertical), or four sides.
func with_padding(all_sides: float) -> UiView:
	padding = Vector4(all_sides, all_sides, all_sides, all_sides)
	ui_invalidate()
	return self


func with_padding_hv(horizontal: float, vertical: float) -> UiView:
	padding = Vector4(horizontal, vertical, horizontal, vertical)
	ui_invalidate()
	return self


func with_padding4(left: float, top: float, right: float, bottom: float) -> UiView:
	padding = Vector4(left, top, right, bottom)
	ui_invalidate()
	return self


## Padding outside (before the background).
func with_margin(all_sides: float) -> UiView:
	margin = Vector4(all_sides, all_sides, all_sides, all_sides)
	ui_invalidate()
	return self


func with_margin_hv(horizontal: float, vertical: float) -> UiView:
	margin = Vector4(horizontal, vertical, horizontal, vertical)
	ui_invalidate()
	return self


func with_margin4(left: float, top: float, right: float, bottom: float) -> UiView:
	margin = Vector4(left, top, right, bottom)
	ui_invalidate()
	return self


func with_weight(w: float, fill: bool = true) -> UiView:
	weight = w
	weight_fill = fill
	ui_invalidate()
	return self


## Modifier.align in a Box: x and y bias (-1 start, 0 centre, 1 end).
func aligned(x_bias: float, y_bias: float) -> UiView:
	align = Vector2(x_bias, y_bias)
	ui_invalidate()
	return self


func with_content_align(x_bias: float, y_bias: float) -> UiView:
	content_align = Vector2(x_bias, y_bias)
	ui_invalidate()
	return self


func with_bg(painter: Callable) -> UiView:
	bg = painter
	queue_redraw()
	return self


## Modifier.uiGlow(color, corner, reach, strength).
func with_glow(color: Color, corner: float, reach: float = UiGlow.REACH, strength: float = UiGlow.IDLE) -> UiView:
	glow_color = color
	glow_corner = corner
	glow_reach = reach
	glow_strength = strength
	queue_redraw()
	return self


## Modifier.semantics { contentDescription = [param text] }.
func with_label(text: String) -> UiView:
	accessibility_name = text
	return self


## Adds [param child] and returns it (for building trees inline).
func add(child: Node) -> Node:
	add_child(child)
	return child


# ---------------------------------------------------------------- measuring

## A dp length snapped to whole screen pixels (Compose's roundToPx, back in dp).
static func px_round(v: float) -> float:
	if is_inf(v):
		return v
	var d := maxf(Display.density, 0.01)
	return floorf(v * d + 0.5) / d


## Compose's measure: the size this takes (its margin included) under the constraints
## [param max_w] × [param max_h] (INF: unbounded); [param exact_w] / [param exact_h] force that
## dimension (a filling weight, a grid cell).
func ui_measure(max_w: float, max_h: float, exact_w: bool = false, exact_h: bool = false) -> Vector2:
	var key := Vector4(max_w, max_h, 1.0 if exact_w else 0.0, 1.0 if exact_h else 0.0)
	for i in _mc_keys.size():
		if _mc_keys[i] == key:
			return _mc_vals[i]
	var mw := px_round(margin.x) + px_round(margin.z)
	var mh := px_round(margin.y) + px_round(margin.w)
	var aw := maxf(max_w - mw, 0.0)
	var ah := maxf(max_h - mh, 0.0)
	var w := -1.0
	var h := -1.0
	if exact_w:
		w = aw
	elif width_dp >= 0.0:
		w = minf(px_round(width_dp), aw)
	elif fill_width and not is_inf(aw):
		w = aw
	if exact_h:
		h = ah
	elif height_dp >= 0.0:
		h = minf(px_round(height_dp), ah)
	elif fill_height and not is_inf(ah):
		h = ah
	if aspect_ratio > 0.0:
		if w >= 0.0 and h < 0.0:
			h = minf(px_round(w / aspect_ratio), ah)
		elif h >= 0.0 and w < 0.0:
			w = minf(px_round(h * aspect_ratio), aw)
	if w < 0.0 or h < 0.0:
		var pw := px_round(padding.x) + px_round(padding.z)
		var ph := px_round(padding.y) + px_round(padding.w)
		var cw := (w if w >= 0.0 else aw) - pw
		var ch := (h if h >= 0.0 else ah) - ph
		var content := _measure_content(maxf(cw, 0.0), maxf(ch, 0.0), w >= 0.0, h >= 0.0)
		if w < 0.0:
			w = minf(maxf(px_round(content.x) + pw, px_round(min_width_dp)), aw)
		if h < 0.0:
			h = minf(maxf(px_round(content.y) + ph, px_round(min_height_dp)), ah)
	var out := Vector2(w + mw, h + mh)
	if _mc_keys.size() >= 4:
		_mc_keys.remove_at(0)
		_mc_vals.remove_at(0)
	_mc_keys.append(key)
	_mc_vals.append(out)
	return out


## The natural size of what is inside (the content box, padding excluded) under these maxima.
## A Box: the largest child. Subclasses measure their own content.
func _measure_content(max_w: float, max_h: float, _fixed_w: bool, _fixed_h: bool) -> Vector2:
	var out := Vector2.ZERO
	for c in ui_children():
		var s := c.ui_measure(max_w, max_h)
		out = out.max(s)
	return out


## The children that take part in the layout: visible [UiView]s (and hidden ones keeping their place).
func ui_children() -> Array[UiView]:
	var out: Array[UiView] = []
	for c in get_children():
		var v := c as UiView
		if v != null and (v.visible or v.reserve_space):
			out.append(v)
	return out


## The natural size (unconstrained), for a root shown outside the kit's own layouts.
func natural_size() -> Vector2:
	return ui_measure(INF, INF)


## Forgets what was measured and lays this out again (and everything it sits in, which may change
## size with it). Call it whenever something that affects the size changes.
func ui_invalidate() -> void:
	_mc_keys.clear()
	_mc_vals.clear()
	queue_sort()
	var p := get_parent() as UiView
	if p != null:
		p.ui_invalidate()


# ---------------------------------------------------------------- placing

func _notification(what: int) -> void:
	match what:
		NOTIFICATION_SORT_CHILDREN:
			_sort()
		NOTIFICATION_ACCESSIBILITY_UPDATE:
			_access_update()
		NOTIFICATION_VISIBILITY_CHANGED:
			if not is_visible_in_tree():
				_set_pressed(false)
		NOTIFICATION_ENTER_TREE:
			if claims_entrance or stagger_index >= 0:
				join_entrance()


func _sort() -> void:
	var pl := px_round(padding.x)
	var pt := px_round(padding.y)
	var inner := Rect2(pl, pt, maxf(size.x - pl - px_round(padding.z), 0.0), maxf(size.y - pt - px_round(padding.w), 0.0))
	_layout_content(inner)
	for c in get_children():
		if c is Control and not (c is UiView):
			var ctl := c as Control
			ctl.position = Vector2.ZERO
			ctl.size = size


## Places the children inside [param rect] (the content box). A Box: each by its alignment.
func _layout_content(rect: Rect2) -> void:
	for c in ui_children():
		var s := c.ui_measure(rect.size.x, rect.size.y)
		var a := c.align if not is_nan(c.align.x) else content_align
		var x := rect.position.x + (rect.size.x - s.x) * (1.0 + a.x) * 0.5
		var y := rect.position.y + (rect.size.y - s.y) * (1.0 + a.y) * 0.5
		c.ui_place(Rect2(x, y, s.x, s.y))


## Puts this view's layout box (margin included) at [param box] in its parent.
func ui_place(box: Rect2) -> void:
	var ml := px_round(margin.x)
	var mt := px_round(margin.y)
	var p := box.position + Vector2(ml, mt)
	var s := box.size - Vector2(ml + px_round(margin.z), mt + px_round(margin.w))
	_slot_pos = Vector2(px_round(p.x), px_round(p.y))
	_placed = true
	var ns := Vector2(maxf(px_round(s.x), 0.0), maxf(px_round(s.y), 0.0))
	if size != ns:
		size = ns
	_apply_layer()


## The layout position (before the graphics layer moves it).
func slot_position() -> Vector2:
	return _slot_pos


## Graphics layer: [param alpha], [param scale_v] round [member layer_origin] and a translation
## [param offset] (dp), applied to what is drawn (this and everything inside) without moving the
## layout.
func set_layer(alpha: float, scale_v: Vector2, offset: Vector2) -> void:
	layer_alpha = alpha
	layer_scale = scale_v
	layer_offset = offset
	_apply_layer()


func _apply_layer() -> void:
	if _placed:
		position = _slot_pos + layer_offset
	pivot_offset = size * layer_origin
	var ps := 1.0
	if press_shrink > 0.0 and press != null:
		ps = 1.0 - Widgets.PRESS_SHRINK * press_shrink * press.value
	scale = layer_scale * ps
	var m := modulate
	if m.a != layer_alpha:
		m.a = layer_alpha
		modulate = m


# ---------------------------------------------------------------- drawing

func _draw() -> void:
	var ds := DrawScope.new(self, size)
	if bg.is_valid():
		bg.call(ds, size)
	var g := glow_strength
	if glow_fn.is_valid():
		g = float(glow_fn.call())
	if g > 0.0:
		UiTheme.glow_round_rect(ds, glow_color, Vector2.ZERO, size, glow_corner, glow_reach, g)
	_paint(ds)


## The view's own drawing, after [member bg] and the glow, before the children.
func _paint(_ds: DrawScope) -> void:
	pass


# ---------------------------------------------------------------- frames

## Steps [param animator] (an object with step(dt) -> bool) with this view's frames.
func add_animator(animator: Object) -> void:
	if not animators.has(animator):
		animators.append(animator)
	wake()


## The entrance of the panel this sits in (LocalEntrance), or null outside one.
func find_panel_entrance() -> PanelEntrance:
	var p := get_parent()
	while p != null:
		var pe: Variant = p.get("panel_entrance")
		if pe is PanelEntrance:
			return pe
		p = p.get_parent()
	return null


## Joins the entrance of the panel this sits in: a GlassBox takes the next place in the queue,
## a stagger_in piece its given place; each slides up a beat after the one before.
func join_entrance() -> void:
	var pe := find_panel_entrance()
	if pe == null or pe.progress == null:
		return
	var index := stagger_index
	if claims_entrance:
		if _claimed < 0:
			_claimed = pe.claim()
		index = _claimed
	if index < 0:
		return
	var start := pe.slot_start(index)
	pe.progress.stage(self, start, start + 0.3, stagger_rise)


## Starts the frame loop (it stops itself when nothing moves).
func wake() -> void:
	_awake = true
	if not is_processing():
		set_process(true)


func _process(dt: float) -> void:
	ui_step(dt)


## Advances every animation of this view by [param dt] seconds (the frame loop calls it; tests may
## too). Stops the loop when nothing is moving.
func ui_step(dt: float) -> void:
	var busy := false
	if press != null and press.is_running():
		press.step(dt)
		_on_press_changed()
		busy = press.is_running()
	var i := 0
	while i < animators.size():
		if animators[i].step(dt):
			busy = true
		i += 1
	if _animate(dt):
		busy = true
	if not busy and not _wants_process():
		_awake = false
		set_process(false)


## A subclass's own animations; true while any is still moving.
func _animate(_dt: float) -> bool:
	return false


## Whether the loop should run from the start (a subclass with a continuous animation).
func _wants_process() -> bool:
	return false


# ---------------------------------------------------------------- press and click

## Widgets.kt rememberPress: 0 at rest, 1 fully pressed. It springs down fast and back up with a
## little overshoot (below 0: the cap pops up past rest), so a release feels like a real button.
## With reduce motion it jumps between the two with no overshoot.
func press_value() -> float:
	return press.value if press != null else 0.0


func _set_pressed(on: bool) -> void:
	if _pressed == on:
		return
	_pressed = on
	if on:
		pressed_view = self
	elif pressed_view == self:
		pressed_view = null
	if press == null:
		press = UiAnim.Animatable.new(0.0)
	var down := on and enabled
	if not UiMotion.enabled:
		press.snap_to(1.0 if down else 0.0)
	elif down:
		press.animate_spring(1.0, 1.0, Widgets.PRESS_STIFFNESS)
	else:
		press.animate_spring(0.0, Widgets.RELEASE_DAMPING, Widgets.RELEASE_STIFFNESS)
	_on_press_changed()
	wake()


## Lets go without clicking (a scroll took the finger, the screen went away).
func cancel_press() -> void:
	_set_pressed(false)


## [member enabled] changed (a subclass restyles).
func _on_enabled_changed() -> void:
	pass


## The press moved: re-applies the shrink and redraws.
func _on_press_changed() -> void:
	_apply_layer()
	queue_redraw()


## Whether [param p] (local, in the transformed space) is inside the layout box: the touch area is
## the layout box, the press shrink only moves what is drawn.
func _inside(p: Vector2) -> bool:
	var q := (p - pivot_offset) * scale + pivot_offset
	return Rect2(Vector2.ZERO, size).has_point(q)


func _gui_input(event: InputEvent) -> void:
	if not clickable:
		return
	var mb := event as InputEventMouseButton
	if mb != null and mb.button_index == MOUSE_BUTTON_LEFT:
		if mb.pressed:
			if enabled and _inside(mb.position):
				_press_at = mb.position
				_set_pressed(true)
		elif _pressed:
			var hit := _inside(mb.position)
			_set_pressed(false)
			if hit and enabled:
				perform_click()
		return
	var mm := event as InputEventMouseMotion
	if mm != null and _pressed and not _inside(mm.position):
		_set_pressed(false)


## What a click does (also a screen reader's click action).
func perform_click() -> void:
	if not enabled:
		return
	if on_click.is_valid():
		on_click.call()
	clicked.emit()


# ---------------------------------------------------------------- semantics

func _hidden_from_access() -> bool:
	var p := get_parent()
	while p != null:
		var v := p as UiView
		if v != null and v.access_clear:
			return true
		p = p.get_parent()
	return false


func _access_update() -> void:
	var ae := get_accessibility_element()
	if not ae.is_valid():
		return
	if _hidden_from_access():
		DisplayServer.accessibility_update_set_flag(ae, DisplayServer.FLAG_HIDDEN, true)
		return
	var role := access_role
	if role < 0 and clickable:
		role = DisplayServer.ROLE_BUTTON
	if role >= 0:
		DisplayServer.accessibility_update_set_role(ae, role)
	if clickable:
		DisplayServer.accessibility_update_add_action(ae, DisplayServer.ACTION_CLICK, _access_click)
		if not enabled:
			DisplayServer.accessibility_update_set_flag(ae, DisplayServer.FLAG_DISABLED, true)
	if access_checked >= 0:
		DisplayServer.accessibility_update_set_checked(ae, access_checked == 1)
	if access_selected >= 0:
		DisplayServer.accessibility_update_set_list_item_selected(ae, access_selected == 1)
	if not is_nan(access_progress):
		DisplayServer.accessibility_update_set_num_range(ae, 0.0, 1.0)
		DisplayServer.accessibility_update_set_num_value(ae, access_progress)


func _access_click(_data: Variant) -> void:
	perform_click()


## The role a screen reader is given (the explicit one, else a button when clickable).
func effective_role() -> int:
	if access_role >= 0:
		return access_role
	return DisplayServer.ROLE_BUTTON if clickable else -1
