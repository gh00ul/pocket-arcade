extends PaTest
## games/pinball/PinballStuckBallTest.kt: the stuck-ball failsafe serves a ball the kicks can't free,
## and spam-flipping never freezes a live ball.


## A ball the kicks can't free is served again after the third kick, even though each kick sends it
## flying.
func test_failsafe_reserves_a_ball_kicks_cannot_free() -> void:
	var g := PinballGame.new()
	var d := SimHarness.RoundDriver.new(g, 11)
	d.play(0.2)
	for i in 3:
		g.bot_remove(i)
	g.bot_place(0, 38.0, 469.0, 0.0, 0.0)
	var state := [g.bot_search_kicks(), -99.0, -1.0]  # kicks seen, time of the last kick, trip time
	d.play(16.0, func(t: float, _ms: int) -> void:
		if g.bot_search_kicks() != state[0]:
			state[0] = g.bot_search_kicks()
			state[1] = t
		# Let each kick fly fast for half a second, then drop the ball back in its "pocket".
		if g.bot_failsafe_trips() == 0 and t - state[1] > 0.5:
			g.bot_hold(0, 38.0, 469.0)
		if state[2] < 0.0 and g.bot_failsafe_trips() > 0:
			state[2] = t)
	print("pinball stuck: %d kicks, trip at %.1f s" % [g.bot_search_kicks(), state[2]])
	assert_true(g.bot_failsafe_trips() > 0, "the failsafe never served the stuck ball again")
	assert_eq(PinballTuning.STUCK_KICKS, g.bot_search_kicks())
	assert_true(state[2] < 15.0, "took %ss to serve again" % state[2])


## Seed 500, full plunges and flippers toggled at 5 Hz used to wedge the ball in the left inlane for 40 s.
func test_spam_flipping_never_freezes_the_ball() -> void:
	var g := PinballGame.new()
	# k, held, next plunger id, last score, last change, worst gap
	var s := [0, false, 1, -1, 0.0, 0.0]
	SimHarness.play_round(self, g, 500, null, func(t: float, ms: int) -> void:
		if g.bot_lane_ready() and not g.bot_plunger_held():
			g.on_touch(TouchType.DOWN, s[2], 320.0, 520.0, ms)
			g.on_touch(TouchType.MOVE, s[2], 320.0, 630.0, ms + 50)
			g.on_touch(TouchType.UP, s[2], 320.0, 630.0, ms + 100)
			s[2] += 1
		s[0] += 1
		var k: int = s[0]
		if k % 12 == 0:
			if not s[1]:
				g.on_touch(TouchType.DOWN, 2000 + k, 70.0, 560.0, ms)
				g.on_touch(TouchType.DOWN, 3000 + k, 230.0, 560.0, ms)
			else:
				g.on_touch(TouchType.UP, 2000 + k - 12, 70.0, 560.0, ms)
				g.on_touch(TouchType.UP, 3000 + k - 12, 230.0, 560.0, ms)
			s[1] = not s[1]
		if t >= g.round_seconds:
			return
		var live := g.bot_ball_in_play(0) or g.bot_ball_in_play(1) or g.bot_ball_in_play(2)
		if g.score != s[3] or not live:
			s[3] = g.score
			s[4] = t
		s[5] = maxf(s[5], t - s[4]))
	print("pinball spam-flip seed 500: score %d, kicks %d, trips %d, longest scoreless live ball %.1f s" % [g.score, g.bot_search_kicks(), g.bot_failsafe_trips(), s[5]])
	assert_true(s[5] < 12.0, "a live ball went %ss without scoring" % s[5])
