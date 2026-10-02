extends PaTest
## Regression (2.0.0 bug 2): numbers came back from the save as decimals, so screens read
## "0.0 tickets" and "BEST 450.0". A 2.0.0 save (every number a JSON float) is migrated, then every
## string the game host draws through a whole round is checked for whole numbers.

const CODEX_JSON := '{"tokens":17.0,"tickets":0.0,"last_refill_day":20727.0,"owned":[],"hat":"","outfit":"outfit_blue","collection":{"plush_bear":2.0},"high_scores":{"test":450.0},"muted":false,"spare_token_at":0.0,"total_plays":3.0,"first_person":false}'


class TestGame:
	extends BaseMiniGame

	func _init() -> void:
		id = "test"
		title = "TEST"
		marquee = "TEST"
		round_seconds = 3.0
		look = MiniGame.CabinetLook.new(Pal.PURPLE, Pal.YELLOW, Pal.PINK)

	func step(_dt: float) -> void:
		pass

	func on_touch(type: int, _pid: int, x: float, y: float, _t: int) -> void:
		if type == TouchType.DOWN:
			add_score(160, x, y, Pal.WHITE)

	func cancel_input() -> void:
		pass

	func tickets_for(s: int) -> int:
		return s / 10


var fx: RepoFixture
var decimal := RegEx.create_from_string("[0-9][.][0-9]")


func before_each() -> void:
	fx = RepoFixture.new()
	TextLog.lines = []


func after_each() -> void:
	TextLog.lines = null
	fx.cleanup()


func _services() -> ArcadeServices:
	var f := FileAccess.open(fx.dir.path_join(SaveMigration.CODEX_SAVE), FileAccess.WRITE)
	f.store_string(CODEX_JSON)
	f.close()
	return ArcadeServices.open(fx.dir, null)


func _decimals() -> Array:
	var bad: Array = []
	for s: String in TextLog.lines:
		if decimal.search(s) != null:
			bad.append(s)
	return bad


func test_the_migrated_numbers_are_whole() -> void:
	var s := _services().repo.state()
	for v in [s.tokens, s.tickets, s.total_plays, s.high_score("test"), s.collection["plush_bear"]]:
		assert_is_int(v)
	assert_eq("0", str(s.tickets))
	assert_eq("450", str(s.high_score("test")))


func test_every_number_the_host_draws_through_a_round_is_whole() -> void:
	var services := _services()
	var screen := GameHostScreen.create(TestGame.new(), services)
	host.add_child(screen)
	await frames(2)
	assert_has(TextLog.lines, "BEST: 450", "the intro card's best")
	var r := screen.round
	r.start_round()
	for i in 120 * 3:
		r.frame(GameLoop.FIXED_DT)
		if i % 40 == 0:
			await frames(1)
	await frames(1)
	assert_has(TextLog.lines, "450", "the top bar's best")
	# Score in the round so the results print tickets and a new best.
	r.touch(TouchType.DOWN, 1, r.gx + 100.0 * r.gs, r.gy + 300.0 * r.gs, 0)
	r.touch(TouchType.UP, 1, r.gx + 100.0 * r.gs, r.gy + 300.0 * r.gs, 40)
	r.touch(TouchType.DOWN, 1, r.gx + 120.0 * r.gs, r.gy + 320.0 * r.gs, 80)
	r.touch(TouchType.UP, 1, r.gx + 120.0 * r.gs, r.gy + 320.0 * r.gs, 120)
	r.touch(TouchType.DOWN, 1, r.gx + 140.0 * r.gs, r.gy + 340.0 * r.gs, 160)
	r.touch(TouchType.UP, 1, r.gx + 140.0 * r.gs, r.gy + 340.0 * r.gs, 200)
	var steps := 0
	while steps < 120 * 20 and not (r.phase == GameRound.Phase.RESULTS and r.phase_t > 4.5):
		r.frame(GameLoop.FIXED_DT)
		steps += 1
		if steps % 30 == 0:
			await frames(1)
	await frames(2)
	assert_eq(GameRound.Phase.RESULTS, r.phase)
	assert_eq(480, screen.game.score)
	assert_has(TextLog.lines, "480", "the counted score")
	assert_has(TextLog.lines, "%s 48" % ArcadeFont.TICKET, "the printed tickets")
	# The host doesn't take the token (the hall did when the machine was entered).
	assert_has(TextLog.lines, "%s 17" % ArcadeFont.TOKEN, "the token counter")
	assert_eq([], _decimals(), "no number drawn with a decimal point")
	var s := services.repo.state()
	assert_is_int(s.tickets)
	assert_eq(48, s.tickets)
	assert_eq(480, s.high_score("test"))
