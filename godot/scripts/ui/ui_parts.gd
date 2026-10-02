class_name UiParts
extends RefCounted
## ui/UiParts.kt: small pieces the screens are built from. The rule, heading, chip, bar, ring and
## switch are classes of their own ([ArcadeDivider], [SectionHeader], [ArcadeChip],
## [ArcadeProgressBar], [CountdownRing], [ArcadeToggle]); this holds Modifier.cardFrame and the
## progress easing they share.

## How long a bar or ring takes to catch up with its new value (ms).
const PROGRESS_MILLIS := 420.0


## Modifier.cardFrame: the frame of a card in a grid: glass lit from the top, with a hairline
## bevel, [param edge] round it (a strong edge when [param thick]), a glow of it at [param glow]
## strength when above 0 and, if given, a wash of [param tint] over the glass (rarity, ownership).
## Sets [param view]'s glow and background.
static func card_frame(view: UiView, edge: Color, thick: bool = false, glow: float = 0.0, tint: Color = Color(0, 0, 0, 0)) -> UiView:
	view.glow_color = edge
	view.glow_corner = UiRadius.card
	view.glow_reach = 7.0
	view.glow_strength = glow if glow > 0.0 else 0.0
	view.bg = func(ds: DrawScope, area: Vector2) -> void:
		paint_card_frame(ds, area, edge, thick, tint)
	view.queue_redraw()
	return view


static func paint_card_frame(ds: DrawScope, area: Vector2, edge: Color, thick: bool, tint: Color) -> void:
	var r := UiRadius.card
	var top := Color(tint, 0.24) if tint.a > 0.0 else UiColors.glass_hi
	var bottom := Color(tint, 0.07) if tint.a > 0.0 else UiColors.glass_lo
	ds.draw_round_rect(PaBrush.vertical([top, bottom], 0.0, area.y), Vector2.ZERO, area, r)
	Widgets.paint_glass_bevel(ds, area, r)
	Widgets.border(ds, area, UiEdge.strong if thick else UiEdge.hair, edge, r)


## How a bar or ring moves to a new value: an eased tween of [constant PROGRESS_MILLIS], or a snap
## with reduce motion.
static func animate_progress(anim: UiAnim.Animatable, target: float) -> void:
	if UiMotion.enabled:
		anim.animate_tween(target, PROGRESS_MILLIS, UiAnim.Easing.FAST_OUT_SLOW_IN)
	else:
		anim.snap_to(target)
