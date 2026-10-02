class_name Display
extends RefCounted
## The screen in Compose's terms. Every Godot UI and 2D coordinate is a dp (as Compose lays out),
## and [member density] is physical pixels per dp (Android's DisplayMetrics.density). Kotlin code
## that worked in pixels converts with it: px = dp × density.
##
## On Android density comes from the screen's dpi (dpi / 160). Elsewhere (desktop runs, captures
## at 1080 × 2400) the window is treated as a phone 411.4 dp wide, so layouts match a 420-dpi phone.

## The reference phone width in dp for desktop runs (1080 px at 420 dpi).
const DESKTOP_DP_WIDTH := 1080.0 / 2.625

static var density := 1.0
## Overrides the computed density (tests, captures); 0 = computed.
static var forced_density := 0.0
## Overrides the safe-area insets (left, top, right, bottom, dp) for tests and captures of phones
## with cutouts; negative x = use the display's own.
static var forced_insets := Vector4(-1.0, 0.0, 0.0, 0.0)


## The density for a window [param px_size] in physical pixels.
static func compute_density(px_size: Vector2i) -> float:
	if forced_density > 0.0:
		return forced_density
	if OS.has_feature("android") or OS.has_feature("ios"):
		var dpi := DisplayServer.screen_get_dpi()
		if dpi > 0:
			return dpi / 160.0
	return maxf(px_size.x / DESKTOP_DP_WIDTH, 0.25)


## Sets the root window up so its content is laid out in dp and drawn at full resolution, filling
## the screen edge to edge at any aspect (no letterboxing: bug 3 of 2.0.0).
static func configure(window: Window) -> void:
	var px := window.size
	density = compute_density(px)
	window.content_scale_mode = Window.CONTENT_SCALE_MODE_CANVAS_ITEMS
	window.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_EXPAND
	window.content_scale_size = Vector2i(maxi(1, roundi(px.x / density)), maxi(1, roundi(px.y / density)))
	window.content_scale_factor = 1.0


## The window size in dp.
static func size_dp(window: Window) -> Vector2:
	return Vector2(window.size) / density


## The safe area (no cutout, no system bars) in dp, relative to the window.
static func safe_area_dp(window: Window) -> Rect2:
	var safe := DisplayServer.get_display_safe_area()
	var win_pos := window.position
	var r := Rect2(Vector2(safe.position - win_pos), Vector2(safe.size))
	var full := Rect2(Vector2.ZERO, Vector2(window.size))
	r = r.intersection(full)
	if r.size.x <= 0.0 or r.size.y <= 0.0:
		r = full
	return Rect2(r.position / density, r.size / density)


## Insets (left, top, right, bottom) in dp from the safe area: Compose's safeDrawing.
static func insets_dp(window: Window) -> Vector4:
	if forced_insets.x >= 0.0:
		return forced_insets
	var safe := safe_area_dp(window)
	var full := size_dp(window)
	return Vector4(safe.position.x, safe.position.y, full.x - safe.end.x, full.y - safe.end.y)
