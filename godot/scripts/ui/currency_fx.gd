class_name CurrencyFx
extends RefCounted
## ui/CurrencyFx.kt CurrencyFx: coins and tickets that arc across the screen: a token flying from
## the HUD counter toward the machine you are entering, tickets tossed from the results printer
## into the counter. Each flight calls back when it lands (a counter ticks up, a pill pops), so
## what you see arrive is what the number does. A small fixed pool, no allocation while it plays.
##
## With reduce motion on nothing flies; the landing callback runs at once so the counters still
## update. Draw it with [CurrencyFxLayer].
##
## Positions are in screen pixels, as build-13's were (so the arcs keep their pixel sizes); a
## control's centre is `Vector2` dp × [member Display.density] ([method anchor_of] does it).

enum Kind { TOKEN, TICKET }

const MAX_FLIES := 32
## Offset.Unspecified.
const UNSPECIFIED := Vector2(NAN, NAN)

## Emitted when a flight starts, so the layer runs its frames.
signal launched


## The arc a flying coin or ticket follows: a quadratic curve lifted above the straight line.
class FlyPath:
	extends RefCounted

	## Eased progress: a slow lift-off, quick through the middle, a soft landing.
	static func ease(t: float) -> float:
		var x := clampf(t, 0.0, 1.0)
		if x < 0.5:
			return 4.0 * x * x * x
		var k := -2.0 * x + 2.0
		return 1.0 - k * k * k / 2.0

	## How high the arc rises above the midpoint of a flight of ([param dx], [param dy]) pixels.
	static func lift(dx: float, dy: float) -> float:
		return maxf(56.0, sqrt(dx * dx + dy * dy) * 0.32)

	## One coordinate of the quadratic curve from [param a] via [param control] to [param b] at eased progress [param e].
	static func bezier(a: float, control: float, b: float, e: float) -> float:
		var k := 1.0 - e
		return k * k * a + 2.0 * k * e * control + e * e * b


class Fly:
	extends RefCounted
	var active := false
	var kind := Kind.TOKEN
	var x0 := 0.0
	var y0 := 0.0
	var x1 := 0.0
	var y1 := 0.0
	var cx := 0.0
	var cy := 0.0
	var delay := 0.0
	var t := 0.0
	var dur := 0.6
	var spin := 0.0
	var on_land := Callable()


var _flies: Array[Fly] = []
var _active_count := 0

## Centre of the HUD's token icon in screen pixels, set by the counter; unspecified until it is on screen.
var token_anchor := UNSPECIFIED
## Centre of a ticket counter's icon in screen pixels (the results pill), set by the counter.
var ticket_anchor := UNSPECIFIED
## Optional live sources of the anchors: when set, the anchor is read from the control's centre at
## launch (Compose updated it from onGloballyPositioned).
var token_anchor_node: Control = null
var ticket_anchor_node: Control = null

## Size of the layer in pixels and of one dp, set by [CurrencyFxLayer].
var view_w := 0.0
var view_h := 0.0
var dp := 1.0


func _init() -> void:
	for i in MAX_FLIES:
		_flies.append(Fly.new())


## Whether anything is in the air.
func any() -> bool:
	return _active_count > 0


## The centre of [param node] in screen pixels.
static func anchor_of(node: Control) -> Vector2:
	if node == null or not is_instance_valid(node) or not node.is_inside_tree():
		return UNSPECIFIED
	var r := node.get_global_rect()
	return r.get_center() * Display.density


## The HUD token icon's centre now (from [member token_anchor_node] when set).
func token_anchor_now() -> Vector2:
	if token_anchor_node != null:
		var a := anchor_of(token_anchor_node)
		if not is_nan(a.x):
			token_anchor = a
	return token_anchor


func ticket_anchor_now() -> Vector2:
	if ticket_anchor_node != null:
		var a := anchor_of(ticket_anchor_node)
		if not is_nan(a.x):
			ticket_anchor = a
	return ticket_anchor


## Starts a flight of [param kind] from [param from] to [param to] (unspecified: the middle of the
## layer, a little above centre, where a machine's screen ends up), after [param delay] seconds,
## taking [param duration]. [param on_land] runs when it arrives. If the pool is full or motion is
## off, it lands at once.
func launch(kind: int, from: Vector2, to: Vector2 = UNSPECIFIED, delay: float = 0.0, duration: float = 0.6, on_land: Callable = Callable()) -> void:
	if not UiMotion.enabled or is_nan(from.x) or is_nan(from.y):
		if on_land.is_valid():
			on_land.call()
		return
	var f: Fly = null
	for c in _flies:
		if not c.active:
			f = c
			break
	if f == null:
		if on_land.is_valid():
			on_land.call()
		return
	f.active = true
	f.kind = kind
	f.x0 = from.x
	f.y0 = from.y
	# An unspecified target is NaN here and resolved against the layer's size (see _aim).
	f.x1 = to.x
	f.y1 = to.y
	f.delay = delay
	f.t = 0.0
	f.dur = maxf(duration, 0.1)
	f.spin = 9.0 + fmod(f.x0, 3.0) if kind == Kind.TOKEN else 0.0
	f.on_land = on_land
	_active_count += 1
	_aim(f)
	launched.emit()


func _aim(f: Fly) -> void:
	if is_nan(f.x1) or is_nan(f.y1):
		f.x1 = view_w * 0.5
		f.y1 = view_h * 0.42
	var lift := FlyPath.lift(f.x1 - f.x0, f.y1 - f.y0)
	f.cx = (f.x0 + f.x1) / 2.0
	f.cy = minf(f.y0, f.y1) - lift * 0.6 + absf(f.y1 - f.y0) * 0.15


## Advances every flight by [param dt] seconds.
func update(dt: float) -> void:
	for f in _flies:
		if not f.active:
			continue
		var step := dt
		if f.delay > 0.0:
			f.delay -= step
			if f.delay > 0.0:
				continue
			step = -f.delay
			f.delay = 0.0
			_aim(f)
		f.t += step
		if f.t >= f.dur:
			f.active = false
			_active_count -= 1
			var land := f.on_land
			f.on_land = Callable()
			if land.is_valid():
				land.call()


## Drops every flight without landing it (the screen is going away).
func clear() -> void:
	for f in _flies:
		f.active = false
		f.on_land = Callable()
	_active_count = 0


const _TOKEN_GLOW := Color(1.0, 0xD3 / 255.0, 0x5A / 255.0, 1.0)
const _TICKET_GLOW := Color(1.0, 0xA2 / 255.0, 0x4A / 255.0, 1.0)


## Paints every flight; [param ds] must draw in screen pixels (the layer scales it).
func draw(ds: DrawScope) -> void:
	var token_r := 11.0 * dp
	var ticket_w := 30.0 * dp
	for f in _flies:
		if not f.active or f.delay > 0.0:
			continue
		var t := clampf(f.t / f.dur, 0.0, 1.0)
		var e := FlyPath.ease(t)
		var c := Vector2(FlyPath.bezier(f.x0, f.cx, f.x1, e), FlyPath.bezier(f.y0, f.cy, f.y1, e))
		# Pops out of where it starts, then shrinks a little as it heads into the counter.
		var sz := MathUtil.ease_out_back(clampf(t / 0.18, 0.0, 1.0)) * (1.0 - 0.4 * e * e)
		var fade := (1.0 - t) / 0.1 if t > 0.9 else 1.0
		if f.kind == Kind.TOKEN:
			ds.draw_circle(Color(_TOKEN_GLOW, 0.22 * fade), token_r * 1.9 * sz, c)
			# A coin turning over as it goes.
			var flip := maxf(absf(cos(t * f.spin)), 0.28)
			ds.push()
			ds.scale_by(flip, 1.0, c)
			UiIcons.draw_token(ds, c, token_r * sz)
			ds.pop()
		else:
			ds.draw_circle(Color(_TICKET_GLOW, 0.2 * fade), ticket_w * 0.6 * sz, c)
			ds.push()
			ds.rotate_deg(-24.0 + 40.0 * e, c)
			UiIcons.draw_ticket(ds, c, ticket_w * sz)
			ds.pop()
