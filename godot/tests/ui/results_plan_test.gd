extends PaTest
## ui/ResultsPlanTest.kt: the results screen's grade, count-up and timeline.

const G := ResultsPlan.Grade


func grade(score: int, best: int, tickets: int) -> int:
	return ResultsPlan.grade(score, best, tickets)


func test_a_zero_score_is_always_a_c_whatever_else_happened() -> void:
	assert_eq(G.C, grade(0, 0, 50))
	assert_eq(G.C, grade(0, 100, 50))


func test_a_first_round_is_judged_by_the_haul_alone() -> void:
	# No best yet: par tickets is a solid A, a fat haul is an S, a tiny one a C.
	assert_eq(G.A, grade(300, 0, ResultsPlan.PAR_TICKETS))
	assert_eq(G.S, grade(900, 0, int(ResultsPlan.PAR_TICKETS * 1.4)))
	assert_eq(G.C, grade(20, 0, 3))
	assert_eq(G.B, grade(120, 0, ResultsPlan.PAR_TICKETS * 6 / 10))


func test_a_new_best_on_a_poor_haul_is_not_an_s() -> void:
	var g := grade(160, 100, 8)
	assert_true(g == G.B or g == G.A, "got %d" % g)
	assert_true(g != G.S)


func test_an_s_needs_both_a_best_beating_score_and_a_healthy_haul() -> void:
	assert_eq(G.S, grade(150, 100, 30))
	# Only tying the best on a par haul is an A, and a great score with a tiny haul is no S either.
	assert_eq(G.A, grade(100, 100, ResultsPlan.PAR_TICKETS))
	assert_true(grade(150, 100, 2) > G.S)


func test_a_weak_round_against_a_high_best_is_a_c_and_a_strong_one_against_a_low_best_is_better() -> void:
	assert_eq(G.C, grade(20, 400, 3))
	assert_true(grade(400, 400, 24) <= G.A)


func test_more_score_or_more_tickets_never_lowers_the_grade() -> void:
	# Grade is ordered S(0) < A < B < C, so "never worse" means the ordinal never rises.
	for best: int in [0, 50, 400]:
		var prev: int = G.C
		for score in range(1, 801, 7):
			var g := grade(score, best, 20)
			assert_true(g <= prev, "score %d best %d: %d after %d" % [score, best, g, prev])
			prev = g
		prev = G.C
		for tickets in 81:
			var g := grade(200, best, tickets)
			assert_true(g <= prev, "tickets %d best %d: %d after %d" % [tickets, best, g, prev])
			prev = g
	# A better personal best (higher bar) never makes the same round grade higher.
	var last := -1
	for best in range(10, 601, 10):
		var o := grade(200, best, 20)
		assert_true(o >= last, "best %d" % best)
		last = o


func test_the_skill_is_clamped_so_a_fluke_1000x_best_cannot_run_away() -> void:
	assert_true(ResultsPlan.skill(1000000, 10, 100000) <= 1.4)
	assert_eq(0.0, ResultsPlan.skill(0, 50, 0))


func test_the_count_up_starts_at_zero_climbs_and_lands_on_the_exact_score() -> void:
	assert_eq(0, ResultsPlan.counted_score(1234, 0.0))
	assert_eq(0, ResultsPlan.counted_score(1234, ResultsPlan.SCORE_AT))
	var prev := 0
	var t := 0.0
	while t < ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS + 0.5:
		var shown := ResultsPlan.counted_score(1234, t)
		assert_true(shown >= prev and shown <= 1234)
		prev = shown
		t += 0.01
	assert_eq(1234, ResultsPlan.counted_score(1234, ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS))
	assert_eq(1234, ResultsPlan.counted_score(1234, 99.0))
	assert_eq(0, ResultsPlan.counted_score(0, 5.0))
	# Eased out: most of the number is on screen well before the end.
	assert_true(ResultsPlan.counted_score(1000, ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS * 0.5) > 800)


func test_the_reveal_runs_in_order_and_skip_jumps_to_printing() -> void:
	var times := [ResultsPlan.SCORE_AT, ResultsPlan.BEST_AT, ResultsPlan.GRADE_AT, ResultsPlan.PRINT_AT]
	for i in range(1, times.size()):
		assert_true(times[i] > times[i - 1])
	assert_eq(0, ResultsPlan.stage_at(0.0))
	assert_eq(1, ResultsPlan.stage_at(ResultsPlan.SCORE_AT))
	assert_eq(2, ResultsPlan.stage_at(ResultsPlan.BEST_AT + 0.01))
	assert_eq(3, ResultsPlan.stage_at(ResultsPlan.GRADE_AT))
	assert_eq(4, ResultsPlan.stage_at(ResultsPlan.PRINT_AT))
	assert_eq(4, ResultsPlan.stage_at(ResultsPlan.PRINT_AT + 100.0))
	# The count-up finishes before the best line lands, and the stamp finishes before printing starts.
	assert_true(ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS <= ResultsPlan.BEST_AT)
	assert_true(ResultsPlan.GRADE_AT + ResultsPlan.STAMP_SECONDS <= ResultsPlan.PRINT_AT + 0.05)
	# And the whole reveal stays short enough not to nag on repeat rounds.
	assert_true(ResultsPlan.PRINT_AT <= 2.2)


func test_tickets_fly_in_at_most_the_max_number_of_sprites_and_all_of_them_arrive() -> void:
	for total: int in [0, 1, 5, 18, 19, 40, 71, 500]:
		var chunk := ResultsPlan.flight_chunk(total)
		assert_true(chunk >= 1)
		var left := total
		var flights := 0
		while left > 0:
			left -= mini(chunk, left)
			flights += 1
		assert_true(flights <= ResultsPlan.MAX_FLIGHTS, "%d tickets in %d flights" % [total, flights])
	assert_eq(1, ResultsPlan.flight_chunk(0))
	assert_eq(1, ResultsPlan.flight_chunk(12))
	assert_eq(2, ResultsPlan.flight_chunk(19))
