extends PaTest
## ui/UiThemeTest.kt: the design system's pure pieces: the type and spacing scales, shrink-to-fit
## and the token twinkle.


func test_the_type_scale_descends_from_display_to_caption() -> void:
	var sizes: Array = [UiText.DISPLAY.unit, UiText.TITLE.unit, UiText.HEADING.unit, UiText.BODY.unit]
	var desc := sizes.duplicate()
	desc.sort()
	desc.reverse()
	assert_eq(desc, sizes)
	var distinct: Array = []
	for s in sizes:
		if not distinct.has(s):
			distinct.append(s)
	assert_eq(distinct, sizes)
	# The small condensed styles are the spaced-out ones; big words are set tight.
	assert_true(UiText.LABEL.tiny and UiText.CAPTION.tiny)
	assert_true(UiText.LABEL.tracking > UiText.BODY.tracking)
	assert_true(UiText.CAPTION.unit < UiText.LABEL.unit)
	assert_near(0.0, UiText.DISPLAY.tracking, 0.0)


func test_the_spacing_and_radius_scales_climb() -> void:
	var space: Array = [UiSpace.xs, UiSpace.sm, UiSpace.md, UiSpace.lg, UiSpace.xl]
	var sorted := space.duplicate()
	sorted.sort()
	assert_eq(sorted, space)
	var distinct: Array = []
	for s in space:
		if not distinct.has(s):
			distinct.append(s)
	assert_eq(distinct, space)
	var edges: Array = [UiEdge.hair, UiEdge.line, UiEdge.strong]
	var se := edges.duplicate()
	se.sort()
	assert_eq(se, edges)
	# Boxes sit inside panels, cards inside boxes, chips inside cards.
	assert_true(UiRadius.panel > UiRadius.box and UiRadius.box > UiRadius.card and UiRadius.card > UiRadius.chip)


func test_shrink_to_fit_only_ever_shrinks() -> void:
	assert_near(1.0, Widgets.shrink_scale(100, 200), 0.0)
	assert_near(1.0, Widgets.shrink_scale(200, 200), 0.0)
	assert_near(0.5, Widgets.shrink_scale(200, 100), 1e-6)
	assert_near(1.0, Widgets.shrink_scale(500, Widgets.CONSTRAINTS_INFINITY), 0.0)
	assert_near(1.0, Widgets.shrink_scale(0, 10), 0.0)
	# Squeezed to nothing it goes to nothing rather than dividing by zero.
	assert_near(0.0, Widgets.shrink_scale(50, 0), 0.0)


func test_the_token_glint_rests_then_swells_and_fades() -> void:
	assert_near(0.5, UiIcons.twinkle(0.0), 1e-6)
	assert_near(0.5, UiIcons.twinkle(0.5), 1e-6)
	assert_near(0.5, UiIcons.twinkle(1.0), 1e-6)
	var peak := 0.0
	for i in range(0, 201):
		var v := UiIcons.twinkle(i / 200.0)
		assert_true(v >= 0.5 and v <= 1.0001, "glint %f out of range" % v)
		peak = maxf(peak, v)
	assert_true(peak > 0.98, "the glint should reach nearly full size (peak %f)" % peak)
