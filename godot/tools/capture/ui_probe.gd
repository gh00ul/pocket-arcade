extends Control
## Shows the ui kit's widget sheet or one of the overlay screens and saves a PNG. Non-headless:
##   godot --path godot res://tools/capture/ui_probe.tscn -- --screen=sheet [--size=1080x2400]
##       [--out=C:/path/shot.png] [--frames=90] [--insets=0,49,0,0] [--calm] [--scroll=300]
## Screens: sheet, hud, settings, profile, tokens, prizes, map. The picture is drawn in an
## off-screen viewport of --size pixels laid out at 420 dpi (411 dp across, as build-13's 1080 px
## emulator), so it doesn't depend on the desktop's screen. The insets stand in for build-13's
## emulator cutout (49 dp at the top, measured on the reference shots). Not part of the game.

const SAMPLE_GAMES := "res://tools/capture/ui_sample_games.gd"

var screen := "sheet"
var out := "user://ui_probe.png"
var frames := 90
var scroll := 0.0
var px_size := Vector2i(1080, 2400)
var _n := 0
var _root: Control
var _vp: SubViewport


func _ready() -> void:
	var insets := Vector4(0.0, 49.0, 0.0, 0.0)
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--screen="):
			screen = a.substr(9)
		elif a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--frames="):
			frames = a.substr(9).to_int()
		elif a.begins_with("--insets="):
			var p := a.substr(9).split(",")
			insets = Vector4(p[0].to_float(), p[1].to_float(), p[2].to_float(), p[3].to_float())
		elif a == "--calm":
			ScreenShake.intensity = 0.0
		elif a.begins_with("--scroll="):
			scroll = a.substr(9).to_float()
		elif a.begins_with("--size="):
			var p := a.substr(7).split("x")
			px_size = Vector2i(p[0].to_int(), p[1].to_int())
	# 420 dpi: the width in dp is the 1080 px emulator's 411 (whole dp, as the override needs).
	var dp_size := Vector2i(roundi(px_size.x / 2.625), roundi(px_size.y / 2.625))
	Display.density = float(px_size.x) / dp_size.x
	Widgets.forced_insets = insets
	_vp = SubViewport.new()
	_vp.size = px_size
	_vp.size_2d_override = dp_size
	_vp.size_2d_override_stretch = true
	_vp.oversampling_override = Display.density
	_vp.transparent_bg = false
	_vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(_vp)
	var backdrop := _Backdrop.new()
	backdrop.size = Vector2(dp_size)
	_vp.add_child(backdrop)
	_root = Control.new()
	_root.size = Vector2(dp_size)
	_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_vp.add_child(_root)
	match screen:
		"sheet":
			_root.add_child(_sheet())
		_:
			var s := _screen(screen)
			if s == null:
				push_error("no screen '%s'" % screen)
				get_tree().quit(1)
				return
			_root.add_child(s)


## A screen by name, built the way the app builds it (scripts that aren't there yet: null).
func _screen(name: String) -> Control:
	var path := "res://tools/capture/ui_screens.gd"
	if not ResourceLoader.exists(path):
		return null
	return load(path).call("build", name, self)


func _process(_dt: float) -> void:
	_n += 1
	if _n == 3 and scroll > 0.0:
		for s in find_children("*", "UiScroll", true, false):
			(s as UiScroll).scroll_to(scroll)
	if _n == frames:
		await RenderingServer.frame_post_draw
		var img := _vp.get_texture().get_image()
		img.save_png(out)
		print("saved %s %s" % [out, img.get_size()])
		get_tree().quit()


## Every piece of the kit on one panel.
func _sheet() -> Control:
	var p := ArcadePanel.make("UI KIT", Pal.c(Pal.CYAN), Callable(), true)
	var scroller := UiScroll.new()
	scroller.fill_width = true
	scroller.weight = 1.0
	p.add_content(scroller)
	var col := UiColumn.new()
	col.fill_width = true
	col.h_align = 0.0
	scroller.add_child(col)
	col.add_child(CurrencyRow.make(12, 345, 2.5))
	col.add_child(UiSpacer.h(UiSpace.sm))
	var icons := UiRow.new()
	icons.v_align = 0.0
	icons.spacing = 4.0
	col.add_child(icons)
	for ic: int in [UiIcon.CAMERA, UiIcon.EYE, UiIcon.TROPHY, UiIcon.SOUND, UiIcon.MUTED, UiIcon.MAP, UiIcon.GEAR]:
		var colors := {UiIcon.CAMERA: Pal.BLUE, UiIcon.EYE: Pal.BLUE, UiIcon.TROPHY: Pal.PURPLE, UiIcon.SOUND: Pal.TEAL, UiIcon.MUTED: Pal.TEAL, UiIcon.MAP: Pal.SKY, UiIcon.GEAR: Pal.ORANGE}
		icons.add_child(RoundButton.make(ic, Callable(), Pal.c(colors[ic]), 46.0))
	col.add_child(UiSpacer.h(UiSpace.sm))
	var box := GlassBox.make()
	box.fill_width = true
	col.add_child(box)
	box.add_child(SectionHeader.make("HIGH SCORES", Pal.c(Pal.YELLOW), "0/11 PLAYED"))
	box.add_child(UiSpacer.h(UiSpace.sm))
	var buttons := UiRow.new()
	buttons.fill_width = true
	buttons.v_align = 0.0
	box.add_child(buttons)
	buttons.add_child(ArcadeButton.make("-", Callable(), Pal.c(Pal.PURPLE)).with_width(60.0))
	var mid := UiColumn.new()
	mid.h_align = 0.0
	mid.weight = 1.0
	buttons.add_child(mid)
	mid.add_child(ArcadeText.plain("LOOK SPEED", Pal.c(Pal.LAVENDER), 1.8, true, true, false, 1.0, 150.0))
	mid.add_child(UiSpacer.h(4.0))
	mid.add_child(ArcadeText.plain("100%", Color.WHITE, 3.0))
	buttons.add_child(ArcadeButton.make("+", Callable(), Pal.c(Pal.PURPLE), Color.WHITE, false).with_width(60.0))
	box.add_child(UiSpacer.h(UiSpace.sm))
	var choice := UiRow.new()
	choice.fill_width = true
	choice.v_align = 0.0
	box.add_child(choice)
	var words := UiColumn.new()
	words.weight = 1.0
	choice.add_child(words)
	words.add_child(ArcadeText.plain("RUN MODE", Color.WHITE, 2.3, true, true, false, 1.0, 165.0))
	words.add_child(UiSpacer.h(4.0))
	words.add_child(ArcadeText.plain("PUSH TO THE RIM TO\nLOCK A RUN, EASE OFF", Pal.c(Pal.LAVENDER), 1.5, true, true, false, 1.0, 165.0))
	choice.add_child(UiSpacer.w(8.0))
	choice.add_child(ArcadeButton.make("LATCH", Callable(), Pal.c(Pal.PURPLE), Color.WHITE, true, 2.0).with_width(104.0))
	col.add_child(UiSpacer.h(UiSpace.md))
	var hl := GlassBox.make(UiColors.good)
	hl.fill_width = true
	col.add_child(hl)
	var ring_row := UiRow.new()
	ring_row.v_align = 0.0
	hl.add_child(ring_row)
	var ring := CountdownRing.make(0.66, UiColors.good, 88.0)
	ring.add_child(ArcadeText.styled("7H 59M", UiText.LABEL, Color.WHITE, true).with_padding_hv(12.0, 0.0))
	ring_row.add_child(ring)
	ring_row.add_child(UiSpacer.w(UiSpace.md))
	var t_col := UiColumn.new()
	t_col.weight = 1.0
	ring_row.add_child(t_col)
	t_col.add_child(ArcadeText.styled("DAILY BONUS", UiText.HEADING, UiColors.good))
	t_col.add_child(UiSpacer.h(UiSpace.xs))
	t_col.add_child(ArcadeText.styled("+10 FREE TOKENS EVERY DAY", UiText.CAPTION, Color.WHITE))
	t_col.add_child(UiSpacer.h(UiSpace.xs))
	t_col.add_child(ArcadeText.styled("NEXT REFILL AT MIDNIGHT", UiText.CAPTION, UiColors.text_mid))
	hl.add_child(UiSpacer.h(UiSpace.sm))
	hl.add_child(ArcadeProgressBar.make(0.1, UiColors.ticket))
	hl.add_child(UiSpacer.h(UiSpace.sm))
	hl.add_child(ArcadeProgressBar.make(0.6, UiColors.info))
	col.add_child(UiSpacer.h(UiSpace.md))
	var chips := UiRow.new()
	chips.v_align = 0.0
	chips.spacing = 6.0
	col.add_child(chips)
	chips.add_child(ArcadeChip.make("LEGENDARY", Pal.c(Pal.GOLD), true))
	chips.add_child(ArcadeChip.make("RARE", Pal.c(Pal.SKY)))
	chips.add_child(ArcadeChip.make("×3", UiColors.gold))
	chips.add_child(ArcadeToggle.make(true, Callable(), "On"))
	chips.add_child(ArcadeToggle.make(false, Callable(), "Off"))
	col.add_child(UiSpacer.h(UiSpace.md))
	for st: UiText in UiText.ENTRIES:
		col.add_child(ArcadeText.styled(st.name, st, Widgets.lift(Pal.c(Pal.PINK), 0.2)))
	col.add_child(UiSpacer.h(UiSpace.md))
	var glyphs := UiRow.new()
	glyphs.v_align = 0.0
	glyphs.spacing = 6.0
	col.add_child(glyphs)
	glyphs.add_child(TokenIcon.make(40.0, true))
	glyphs.add_child(TicketIcon.make(30.0))
	var glyphs2 := UiRow.new()
	glyphs2.v_align = 0.0
	glyphs2.spacing = 6.0
	col.add_child(UiSpacer.h(UiSpace.sm))
	col.add_child(glyphs2)
	for ic in range(UiIcon.COUNT):
		var v := UiView.new()
		v.width_dp = 22.0
		v.height_dp = 22.0
		var i: int = ic
		v.bg = func(ds: DrawScope, area: Vector2) -> void: UiIcons.draw_ui_icon(ds, i, area / 2.0, area.x, Color.WHITE)
		(glyphs if ic < 6 else glyphs2).add_child(v)
	col.add_child(UiSpacer.h(UiSpace.md))
	col.add_child(ArcadeDivider.make(Pal.c(Pal.CYAN)))
	col.add_child(UiSpacer.h(UiSpace.md))
	var banner := ArcadeBanner.new()
	col.add_child(banner)
	banner.set_text("DAILY BONUS")
	return p


## Something to sit behind the overlays: a dark hall-coloured wash (the 3D hall isn't drawn here).
class _Backdrop:
	extends Control

	func _draw() -> void:
		var ds := DrawScope.new(self, size)
		ds.draw_rect(PaBrush.vertical([Pal.c(0xFF2A1F45), Pal.c(0xFF140C26)], 0.0, size.y), Vector2.ZERO, size)
		for i in 14:
			var y := size.y * (i + 0.5) / 14.0
			ds.draw_rect(Pal.c(0x22FFFFFF), Vector2(0.0, y), Vector2(size.x, 1.0))
