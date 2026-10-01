extends PaTest
## data/TokenGateTest.kt: the guard that stops a quick double tap spending a token twice.


func test_only_one_claim_gets_through_until_it_is_released() -> void:
	var gate := TokenGate.new()
	assert_false(gate.claimed)
	assert_true(gate.try_claim())
	assert_true(gate.claimed)
	assert_false(gate.try_claim())
	assert_false(gate.try_claim())
	gate.release()
	assert_false(gate.claimed)
	assert_true(gate.try_claim())


func test_releasing_an_unclaimed_gate_is_harmless() -> void:
	var gate := TokenGate.new()
	gate.release()
	assert_true(gate.try_claim())
	assert_false(gate.try_claim())


## Two taps land before the first spend has settled: through the gate exactly one spends.
func test_two_quick_taps_spend_one_token_through_the_gate_but_two_without() -> void:
	var fx := RepoFixture.new()
	var repo: ArcadeRepository = fx.repo(5)
	var gate := TokenGate.new()
	var queued: Array[Callable] = []
	var tap_gated := func() -> void:
		if gate.try_claim():
			queued.append(func() -> void:
				repo.spend_token()
				gate.release())
	# Both taps come in before either deferred spend gets to run.
	tap_gated.call()
	tap_gated.call()
	assert_eq(1, queued.size())
	for q in queued:
		q.call()
	assert_eq(4, repo.state().tokens)
	# A settled spend lets the next tap through.
	assert_false(gate.claimed)
	assert_true(gate.try_claim())
	gate.release()
	queued.clear()
	var tap_ungated := func() -> void:
		queued.append(func() -> void: repo.spend_token())
	tap_ungated.call()
	tap_ungated.call()
	for q in queued:
		q.call()
	assert_eq(2, repo.state().tokens)
	fx.cleanup()
