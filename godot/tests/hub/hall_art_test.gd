extends PaTest
## Godot-only: every hall texture and shared part paints and builds without a script error, at the
## size build-13 maps it in, and asking again hands back the same object (the art is painted once).
## Headless runs record the painting (no pixels arrive), which is enough to run every line of it.


func test_every_hall_texture_paints_at_its_size() -> void:
	var sizes := {
		"tiles": [HallArt.tiles(), 256, 256], "concrete": [HallArt.concrete(), 256, 256],
		"asphalt": [HallArt.asphalt(), 256, 256], "floor_logo": [HallArt.floor_logo(), 512, 512],
		"mat": [HallArt.mat(), 256, 128], "wall": [HallArt.wall(), 256, 256],
		"upper_wall": [HallArt.upper_wall(), 256, 256], "mural": [HallArt.mural(), 512, 240],
		"mural_city": [HallArt.mural_city(), 384, 240], "mural_space": [HallArt.mural_space(), 384, 240],
		"race_sign": [HallArt.race_sign(), 640, 96], "ceiling_tiles": [HallArt.ceiling_tiles(), 128, 128],
		"troffer": [HallArt.troffer(), 64, 64], "duct": [HallArt.duct(), 64, 128],
		"street_backdrop": [HallArt.street_backdrop(), 1024, 320], "washer": [HallArt.washer(), 64, 256],
		"poster": [HallArt.poster(2), 160, 240], "brushed_metal": [HallArt.brushed_metal(), 128, 128],
		"dark_metal": [HallArt.dark_metal(), 64, 64], "chrome": [HallArt.chrome(), 64, 64],
		"wood": [HallArt.wood(), 256, 256], "glass": [HallArt.glass(), 128, 128],
		"shadow": [HallArt.shadow(), 64, 64], "glow": [HallArt.glow(), 64, 64],
		"beam": [HallArt.beam(), 64, 128], "shaft": [HallArt.shaft(), 64, 256],
		"neon": [HallArt.neon("JACKPOT", 0xFFFFB03D, 512, 128, 104.0), 512, 128],
		"lightbox": [HallArt.lightbox("TOKENS", Pal.GOLD, Pal.NIGHT), 512, 128],
		"prize_box": [HallArt.prize_box(3), 128, 128], "ticket_stack": [HallArt.ticket_stack(), 64, 64],
		"solid": [HallArt.solid(Pal.PINK), 4, 4], "paint": [HallArt.paint(Pal.PINK), 64, 64],
		"carpet": [HallArt.carpet(), 512, 512],
	}
	for name: String in sizes:
		var e: Array = sizes[name]
		var t: PaTexture = e[0]
		assert_not_null(t, name)
		if t != null:
			assert_eq(e[1], t.width, name + " width")
			assert_eq(e[2], t.height, name + " height")
	# Painted once: the same texture comes back.
	assert_true(HallArt.chrome() == HallArt.chrome())
	assert_true(HallArt.paint(Pal.PINK) == HallArt.paint(Pal.PINK))
	assert_true(HallArt.paint(Pal.PINK, 0.3) != HallArt.paint(Pal.PINK))
	assert_true(HallArt.wood(0xFFD69A5A, 5) == MachineKit.lane_wood())
	assert_true(HallArt.poster(1) == HallArt.poster(5), "four designs, shared")
	assert_true(HallArt.solid(-1) == HallArt.solid(0xFFFFFFFF), "a Kotlin -1 is white")


func test_the_colour_helpers_match_build_13() -> void:
	# dim mixes toward black, lift toward white, both keep alpha; alpha replaces it.
	assert_eq(0xFF7F0000, HallArt.dim(0xFFFF0000, 0.5))
	assert_eq(0x80FF7F7F, HallArt.lift(0x80FF0000, 0.5))
	assert_eq(0x3FFFFFFF, HallArt.alpha(0xFFFFFFFF, 0.25))
	assert_eq(0x00123456, HallArt.alpha(0xFF123456, -1.0))


func test_every_machine_kit_part_builds() -> void:
	for t: PaTexture in [MachineKit.net(), MachineKit.velvet(), MachineKit.skee_rings(), MachineKit.court(),
			MachineKit.hoop_board(), MachineKit.hockey_surface(), MachineKit.whack_top(Pal.GREEN), MachineKit.prize_chute(),
			MachineKit.deck(), MachineKit.gold(), MachineKit.coin_face(), MachineKit.seat_leather(), MachineKit.rubber(),
			MachineKit.mole_fur(), MachineKit.mole_face(), MachineKit.rear_panel(true), MachineKit.rear_panel(false),
			MachineKit.coin_door(), MachineKit.coin_slot_lit(), MachineKit.speaker_strip(), MachineKit.ticket_plate(),
			MachineKit.ticket_paper()]:
		assert_not_null(t)
	assert_eq(192, MachineKit.skee_rings().width)
	assert_eq(128, MachineKit.coin_door().width)
	assert_eq(160, MachineKit.coin_door().height)
	assert_eq(4, MachineKit.coin_door().scale, "plates are painted at 4x")
	assert_true(MachineKit.net().repeat)
	for m: Model in [MachineKit.ball(Pal.RED), MachineKit.basketball(), MachineKit.coin(), MachineKit.mole(),
			MachineKit.mallet(), MachineKit.claw(), MachineKit.puck(), MachineKit.hockey_mallet(Pal.BLUE),
			MachineKit.button(Pal.CYAN, 0.9), MachineKit.joystick(), MachineKit.joystick(Pal.GOLD), MachineKit.trackball()]:
		assert_gt(m.polys.size(), 0)
	assert_true(MachineKit.button(Pal.CYAN, 0.9) == MachineKit.button(Pal.CYAN, 0.9))
	assert_true(MachineKit.button(Pal.CYAN, 0.9) != MachineKit.button(Pal.CYAN, 2.3))
	assert_true(MachineKit.joystick() == MachineKit.joystick(0xFFE8323C), "the classic red-ball joystick")
	# A button's cap is a lit dome on a chrome-topped collar: about 60 polygons, some glowing.
	var lit := 0
	for p: Poly in MachineKit.button(Pal.CYAN, 0.9).polys:
		if p.emissive > 0.0:
			lit += 1
	assert_gt(lit, 0)
	# The jitter spreads either way within its range.
	for i in 50:
		var j := MachineKit.jitter(i, 3, 5.0)
		assert_true(j >= -5.0 and j <= 5.0)


func test_every_cabinet_print_paints_for_every_shape() -> void:
	for shape: int in MiniGame.CabinetShape.values():
		var t := CabinetPaint.side_art(CabinetPaint.SIDE_TALL_W, CabinetPaint.SIDE_TALL_H, Pal.PINK, Pal.YELLOW, Pal.HOTPINK, shape, "CLAW MACHINE")
		assert_eq(CabinetPaint.SIDE_TALL_W, t.width)
		assert_eq(CabinetPaint.PANEL_SCALE, t.scale)
		assert_not_null(CabinetPaint.marquee(Pal.PINK, Pal.YELLOW, Pal.HOTPINK, shape, "CLAW MACHINE"))
		assert_not_null(CabinetPaint.kick(Pal.PINK, Pal.YELLOW, Pal.HOTPINK, shape))
	assert_not_null(CabinetPaint.side_art(CabinetPaint.SIDE_LONG_W, CabinetPaint.SIDE_LONG_H, Pal.BLUE, Pal.YELLOW, Pal.SKY, MiniGame.CabinetShape.SKEEBALL, "SKEE-BALL"))
	assert_not_null(CabinetPaint.side_art(CabinetPaint.SIDE_SQUARE, CabinetPaint.SIDE_SQUARE, Pal.GREEN, Pal.BROWN, Pal.LIME, MiniGame.CabinetShape.WHACK, "WHACK-A-MOLE"))
	assert_eq(CabinetPaint.TOPPER_W, CabinetPaint.topper(Pal.VIOLET, Pal.CYAN, Pal.PURPLE, MiniGame.CabinetShape.TOWER, "STACK").width)
	assert_eq(256, CabinetPaint.panel(Pal.NAVY, Pal.ORANGE, Pal.ORANGE, "AIM AND TAP").width)
	assert_eq(128, CabinetPaint.panel_big(Pal.VIOLET, Pal.CYAN, Pal.PURPLE, "TAP TO DROP").height)
	assert_eq(128, CabinetPaint.bezel(Pal.CYAN, Pal.PURPLE, "STACK").width)


func test_machine_art_paints_a_games_cabinet_and_its_led_display() -> void:
	var g := HallGames.stand_in(HallGames.BUILD_13[7])
	var art := MachineArt.new(g)
	assert_eq(Pal.VIOLET, art.body)
	for t: PaTexture in [art.body_paint(), art.dark_paint(), art.black(), art.trim_tex(), art.glow_tex(), art.side_art(),
			art.side_art_square(), art.side_art_long(), art.marquee(), art.topper(), art.kick(), art.coin_door(),
			art.panel(), art.panel_big(), art.bezel(), art.display()]:
		assert_not_null(t)
	assert_true(art.marquee() == art.marquee(), "painted once per game")
	assert_true(art.coin_door() == MachineKit.coin_door(), "the same steel door for every machine")
	# The tower's screen is portrait; a game with its own design takes its design's.
	assert_eq(Vector2i(16, 24), art.screen_units())
	assert_eq(Vector2i(24, 18), MachineArt.new(HallGames.stand_in(HallGames.BUILD_13[0])).screen_units())
	assert_eq(Vector2i(40, 30), MachineArt.new(HallGames.stand_in(HallGames.BUILD_13[9])).screen_units())
	# The LED display shows the best score for three seconds, then PLAY! for three.
	art.update_display(450, 0.5)
	assert_eq("HI 450", art.display_text())
	art.update_display(450, 4.0)
	assert_eq("PLAY!", art.display_text())
	art.update_display(900, 7.0)
	assert_eq("HI 900", art.display_text(), "a new best shows at once")


func test_a_live_screen_paints_the_attract_loop_and_the_high_score_card() -> void:
	var g := HallGames.stand_in(HallGames.BUILD_13[0])
	var screen := LiveScreen.new(MachineArt.new(g), 3)
	assert_eq(240, screen.texture.width)
	assert_eq(180, screen.texture.height)
	screen.warm(120)
	for k in 6:
		screen.paint(120, k * 1.5)
	# The overlays are made once per size.
	assert_true(MachineArt.screen_glass(240, 180) == MachineArt.screen_glass(240, 180))
	var glass := MachineArt.screen_glass(240, 180).get_image()
	# Every third row is a scanline; the far corners are darkened by the vignette.
	assert_gt(glass.get_pixel(120, 90).a, glass.get_pixel(120, 91).a - 0.01)
	assert_gt(glass.get_pixel(0, 179).a, 0.25)
	var dots := MachineArt.dot_matrix(256, 64).get_image()
	assert_near(0.533, dots.get_pixel(0, 1).a, 0.01)
	assert_near(0.0, dots.get_pixel(1, 1).a, 0.01)
	assert_near(0.782, dots.get_pixel(4, 4).a, 0.01)
