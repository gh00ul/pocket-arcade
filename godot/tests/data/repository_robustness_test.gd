extends PaTest
## data/RepositoryRobustnessTest.kt: a save that is damaged or can't be written must never crash
## the game, and never lose a token twice. (DataStore's coroutine races have no Godot counterpart:
## saves are synchronous on one thread. The two concurrency tests keep their assertions on the
## same sequence of calls.)

var fx: RepoFixture


func before_each() -> void:
	fx = RepoFixture.new()


func after_each() -> void:
	fx.cleanup()


func _corrupt_the_file() -> void:
	var bytes := PackedByteArray()
	bytes.resize(64)
	bytes.fill(0xFF)
	fx.write_raw(bytes)


func _fresh() -> SaveState:
	var s := SaveState.new()
	s.loaded = true
	return s


# ---- a corrupt save file

func test_a_corrupt_file_is_replaced_so_reads_give_defaults_and_writes_succeed() -> void:
	_corrupt_the_file()
	var repo := ArcadeRepository.new(fx.new_store())
	assert_true(_fresh().equals(repo.state()))
	assert_eq(0, repo.apply_daily_refill(20000))
	assert_true(repo.spend_token())
	repo.add_tickets(12)
	var s := repo.state()
	assert_eq(ArcadeRepository.STARTING_TOKENS - 1, s.tokens)
	assert_eq(12, s.tickets)
	assert_eq(20000, s.last_refill_day)


func test_a_corrupt_file_that_is_replaced_stays_fixed_across_a_relaunch() -> void:
	_corrupt_the_file()
	var repo := ArcadeRepository.new(fx.new_store())
	repo.apply_daily_refill(20000)
	repo.add_tickets(5)
	var s := ArcadeRepository.new(fx.new_store()).state()
	assert_eq(5, s.tickets)
	assert_eq(20000, s.last_refill_day)


## Kotlin: without the corruption handler, reads fall back to defaults and every write fails
## quietly. Here the store always recovers; a store refusing writes stands in for the case.
func test_without_the_handler_a_corrupt_file_still_reads_defaults_and_writes_fail_quietly() -> void:
	_corrupt_the_file()
	var store := fx.new_store()
	store.fail_writes = true
	var repo := ArcadeRepository.new(store)
	assert_true(_fresh().equals(repo.state()))
	assert_eq(0, repo.apply_daily_refill(20000))
	assert_false(repo.spend_token())
	assert_false(repo.exchange_tickets_for_token())
	assert_false(repo.record_score("racer", 10))
	repo.add_tickets(3)
	repo.refund_token()
	repo.add_prize("bear")
	repo.set_muted(true)


# ---- a disk that won't take a write

func test_a_failed_write_reports_failure_even_though_the_edits_function_ran() -> void:
	var store := fx.new_store()
	store.fail_writes = true
	var repo := ArcadeRepository.new(store)
	assert_false(repo.spend_token())
	assert_true(repo.spend_tickets(0))
	assert_false(repo.spend_tickets(1))
	assert_false(repo.exchange_tickets_for_token())
	assert_false(repo.buy(Catalog.hats()[0]))
	assert_false(repo.record_score("racer", 10))
	assert_false(repo.claim_spare_token(1000))
	assert_false(repo.unlock("first_win"))
	assert_eq(-1, repo.record_score_entry("racer", 10, "ABC"))
	assert_eq(0, repo.apply_daily_refill(20000))
	repo.refund_token()
	repo.add_tickets(4)
	repo.add_prize("bear")
	repo.add_stat("plays:racer")
	repo.add_collectible("fish:trout")
	repo.set_arcade_name("Fun House")
	repo.equip(Catalog.hats()[0])
	repo.set_muted(true)
	repo.set_first_person(true)
	# Nothing landed.
	assert_true(_fresh().equals(repo.state()))
	assert_false(FileAccess.file_exists(fx.path))


func test_a_disk_that_fails_everything_never_throws_from_the_repository() -> void:
	# A folder that can't exist (a file is in the way), so reads and writes both fail.
	expect_error("Could not create directory")
	fx.write_raw("x".to_utf8_buffer())
	var store := PrefsStore.new(fx.path + "/sub/save.json")
	var repo := ArcadeRepository.new(store)
	assert_true(_fresh().equals(repo.state()))
	assert_false(repo.spend_token())
	assert_eq(0, repo.apply_daily_refill(1))
	repo.add_tickets(1)
	repo.set_muted(true)


# ---- atomic spending

func test_two_concurrent_spends_of_the_only_token_succeed_exactly_once() -> void:
	var store := fx.new_store()
	var repo := ArcadeRepository.new(store)
	for round in 10:
		store.edit(func(p: Dictionary) -> void:
			p.clear())
		fx.seed_store(store, 1)
		repo = ArcadeRepository.new(store)
		var results := [repo.spend_token(), repo.spend_token()]
		assert_eq(1, results.count(true), "round %d" % round)
		assert_eq(0, repo.state().tokens)
		assert_eq(1, repo.state().total_plays)


func test_many_concurrent_spends_never_overdraw_the_tokens_or_tickets() -> void:
	var repo := fx.repo(5, 10)
	var tokens := 0
	var tickets := 0
	for i in 20:
		if repo.spend_token():
			tokens += 1
		if repo.spend_tickets(3):
			tickets += 1
	assert_eq(5, tokens)
	assert_eq(3, tickets)
	assert_eq(0, repo.state().tokens)
	assert_eq(1, repo.state().tickets)


func test_the_saved_keys_keep_their_names_so_existing_saves_still_load() -> void:
	var store := fx.new_store()
	store.edit(func(p: Dictionary) -> void:
		p["tokens"] = 7
		p["tickets"] = 123
		p["collection"] = "bear:2")
	var s := ArcadeRepository.new(store).state()
	assert_eq(7, s.tokens)
	assert_eq(123, s.tickets)
	assert_eq({"bear": 2}, s.collection)


# ---- Godot-only: the file itself

func test_a_damaged_primary_falls_back_to_its_backup() -> void:
	var repo := fx.repo()
	repo.add_tickets(9)
	repo.add_tickets(1)
	# The backup holds the generation before the last write.
	_corrupt_the_file()
	var store := fx.new_store()
	assert_eq(9, ArcadeRepository.new(store).state().tickets)
	assert_eq("backup", store.loaded_from)


func test_every_number_comes_back_as_an_int() -> void:
	var repo := fx.repo()
	repo.apply_daily_refill(20650)
	repo.spend_token()
	repo.add_tickets(4567)
	repo.record_score("pinball", 450)
	repo.add_prize("plush_bear")
	repo.add_stat("plays:pinball")
	repo.add_collectible("fish:perch")
	repo.claim_spare_token(1790800000000)
	var s := ArcadeRepository.new(fx.new_store()).state()
	for v in [s.tokens, s.tickets, s.last_refill_day, s.total_plays, s.spare_token_at, s.high_score("pinball"), s.collection["plush_bear"], s.stat("plays:pinball"), s.collectible_count("fish:perch")]:
		assert_is_int(v)
	assert_eq("4567", str(s.tickets))
	assert_eq("450", str(s.high_score("pinball")))
