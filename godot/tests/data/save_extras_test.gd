extends PaTest
## data/SaveExtrasTest.kt: ticket spending, stats, unlocks, collectibles, the arcade's name and the
## score tables, each checked through the repository and a relaunch.

const LONG_MAX := 0x7FFFFFFFFFFFFFFF

var fx: RepoFixture


func before_each() -> void:
	fx = RepoFixture.new()


func after_each() -> void:
	fx.cleanup()


func _relaunched() -> SaveState:
	return ArcadeRepository.new(fx.new_store()).state()


func _scores(entries: Array) -> Array:
	return entries.map(func(e: ScoreTables.ScoreEntry) -> int: return e.score)


func _initials(entries: Array) -> Array:
	return entries.map(func(e: ScoreTables.ScoreEntry) -> String: return e.initials)


func _raw(key: String, value: String) -> PrefsStore:
	var store := fx.new_store()
	store.edit(func(p: Dictionary) -> void:
		p[key] = value)
	return store


# ---- spendTickets

func test_spend_tickets_takes_exactly_that_many_and_fails_when_short() -> void:
	var repo := fx.repo(null, 50)
	assert_true(repo.spend_tickets(20))
	assert_eq(30, repo.state().tickets)
	assert_false(repo.spend_tickets(31))
	assert_eq(30, repo.state().tickets)
	assert_true(repo.spend_tickets(30))
	assert_eq(0, repo.state().tickets)
	assert_false(repo.spend_tickets(1))
	assert_eq(0, repo.state().tickets)


func test_spend_tickets_never_pays_out_for_a_negative_amount() -> void:
	var repo := fx.repo(null, 5)
	assert_false(repo.spend_tickets(-10))
	assert_eq(5, repo.state().tickets)
	assert_true(repo.spend_tickets(0))
	assert_eq(5, repo.state().tickets)


# ---- stats

func test_add_stat_counts_up_each_key_and_survives_a_relaunch() -> void:
	var repo := fx.repo()
	repo.add_stat("plays:racer")
	repo.add_stat("plays:racer")
	repo.add_stat("tickets:earned", 340)
	repo.add_stat("plays:claw")
	assert_eq({"plays:racer": 2, "tickets:earned": 340, "plays:claw": 1}, repo.state().stats)
	var s := _relaunched()
	assert_eq(2, s.stat("plays:racer"))
	assert_eq(340, s.stat("tickets:earned"))
	assert_eq(0, s.stat("plays:pinball"))


func test_add_stat_holds_big_numbers_and_never_goes_negative() -> void:
	var repo := fx.repo()
	repo.add_stat("big", 5000000000)
	repo.add_stat("big", 5000000000)
	assert_eq(10000000000, repo.state().stat("big"))
	repo.add_stat("big", LONG_MAX)
	assert_eq(LONG_MAX, repo.state().stat("big"))
	assert_eq(LONG_MAX, _relaunched().stat("big"), "a saturated counter survives the file exactly")
	repo.add_stat("small", 3)
	repo.add_stat("small", -10)
	assert_eq(0, repo.state().stat("small"))
	assert_false(repo.state().stats.has("small"))


func test_add_stat_ignores_blank_keys_keys_with_the_separator_and_zero_deltas() -> void:
	var repo := fx.repo()
	repo.add_stat("")
	repo.add_stat("   ")
	repo.add_stat("a;b")
	repo.add_stat("zero", 0)
	repo.add_stat("  padded  ")
	assert_eq({"padded": 1}, repo.state().stats)


func test_add_stat_keeps_the_good_parts_of_a_damaged_stats_string() -> void:
	var repo := ArcadeRepository.new(_raw("stats", "plays:racer:4;;junk;x:notANumber;:9;neg:-3"))
	assert_eq({"plays:racer": 4}, repo.state().stats)
	repo.add_stat("plays:racer")
	assert_eq({"plays:racer": 5}, repo.state().stats)


# ---- unlocks

func test_unlock_is_true_only_the_first_time() -> void:
	var repo := fx.repo()
	assert_true(repo.unlock("first_win"))
	assert_false(repo.unlock("first_win"))
	assert_true(repo.unlock("racer:gold"))
	var s := repo.state()
	assert_eq(["first_win", "racer:gold"], s.unlocked)
	assert_true(s.is_unlocked("first_win"))
	assert_false(s.is_unlocked("nope"))
	assert_eq(["first_win", "racer:gold"], _relaunched().unlocked)


func test_unlock_keeps_the_order_they_were_earned_in() -> void:
	var repo := fx.repo()
	for id in ["zeta", "alpha", "mid"]:
		repo.unlock(id)
	assert_eq(["zeta", "alpha", "mid"], repo.state().unlocked)


func test_unlock_refuses_blank_ids_and_ids_with_the_separator() -> void:
	var repo := fx.repo()
	assert_false(repo.unlock(""))
	assert_false(repo.unlock("  "))
	assert_false(repo.unlock("a;b"))
	assert_true(repo.state().unlocked.is_empty())
	assert_true(repo.unlock("  trimmed "))
	assert_false(repo.unlock("trimmed"))
	assert_eq(["trimmed"], repo.state().unlocked)


func test_unlocked_reads_through_a_damaged_string() -> void:
	var repo := ArcadeRepository.new(_raw("unlocked", ";;a; ;b;;a;"))
	assert_eq(["a", "b"], repo.state().unlocked)
	assert_false(repo.unlock("a"))
	assert_true(repo.unlock("c"))
	assert_eq(["a", "b", "c"], repo.state().unlocked)


# ---- collectibles

func test_add_collectible_counts_namespaced_ids_apart_from_the_plush_collection() -> void:
	var repo := fx.repo()
	repo.add_collectible("fish:trout")
	repo.add_collectible("fish:trout")
	repo.add_collectible("fish:golden")
	repo.add_prize("bear")
	var s := repo.state()
	assert_eq({"fish:trout": 2, "fish:golden": 1}, s.collectibles)
	assert_eq({"bear": 1}, s.collection)
	assert_eq(2, s.collectible_count("fish:trout"))
	assert_eq(0, s.collectible_count("fish:whale"))
	assert_eq({"fish:trout": 2, "fish:golden": 1}, _relaunched().collectibles)


func test_add_collectible_ignores_blank_and_separator_ids() -> void:
	var repo := fx.repo()
	repo.add_collectible("")
	repo.add_collectible("x;y")
	repo.add_collectible(" fish:carp ")
	assert_eq({"fish:carp": 1}, repo.state().collectibles)


# ---- the arcade's name

func test_the_arcade_name_defaults_then_takes_a_cleaned_name() -> void:
	var repo := fx.repo()
	assert_eq("POCKET ARCADE", repo.state().arcade_name)
	repo.set_arcade_name("  fun house 99! ")
	assert_eq("FUN HOUSE 99", repo.state().arcade_name)
	assert_eq("FUN HOUSE 99", _relaunched().arcade_name)


func test_a_blank_name_resets_to_the_default() -> void:
	var repo := fx.repo()
	repo.set_arcade_name("Neon Nook")
	assert_eq("NEON NOOK", repo.state().arcade_name)
	repo.set_arcade_name("   ")
	assert_eq("POCKET ARCADE", repo.state().arcade_name)
	repo.set_arcade_name("Neon Nook")
	repo.set_arcade_name("!!!")
	assert_eq("POCKET ARCADE", repo.state().arcade_name)


func test_a_tampered_stored_name_is_cleaned_on_read() -> void:
	var name := ArcadeRepository.new(_raw("arcade_name", "hello <b>world</b> and then some more")).state().arcade_name
	assert_true(name.length() <= ArcadeRepository.MAX_ARCADE_NAME_LENGTH)
	assert_eq(name, ArcadeRepository.sanitize_arcade_name(name))
	for i in name.length():
		var ch := name[i]
		assert_true((ch >= "A" and ch <= "Z") or (ch >= "0" and ch <= "9") or ch == " ")


# ---- score tables

func test_record_score_entry_ranks_and_keeps_the_top_five_best_first() -> void:
	var repo := fx.repo()
	assert_eq(0, repo.record_score_entry("racer", 500, "ann"))
	assert_eq(1, repo.record_score_entry("racer", 300, "bob"))
	assert_eq(0, repo.record_score_entry("racer", 900, "cy"))
	assert_eq(3, repo.record_score_entry("racer", 100, "dee"))
	assert_eq(4, repo.record_score_entry("racer", 50, "eve"))
	assert_eq("CYA:900,ANN:500,BOB:300,DEE:100,EVE:50", ScoreTables.encode(repo.state().score_table("racer")))
	# Full: a low score is turned away and changes nothing, a good one pushes the last off.
	assert_eq(-1, repo.record_score_entry("racer", 40, "zed"))
	assert_eq(2, repo.record_score_entry("racer", 400, "fay"))
	var table := repo.state().score_table("racer")
	assert_eq(5, table.size())
	assert_eq([900, 500, 400, 300, 100], _scores(table))
	assert_eq("FAY", table[2].initials)
	assert_eq(ScoreTables.encode(table), ScoreTables.encode(_relaunched().score_table("racer")))


func test_a_tied_score_goes_below_the_earlier_one() -> void:
	var repo := fx.repo()
	repo.record_score_entry("claw", 200, "one")
	assert_eq(1, repo.record_score_entry("claw", 200, "two"))
	assert_eq(["ONE", "TWO"], _initials(repo.state().score_table("claw")))


func test_a_tied_score_is_turned_away_from_a_full_table_of_equal_scores() -> void:
	var repo := fx.repo()
	for i in 5:
		assert_eq(i, repo.record_score_entry("claw", 100, "aaa"))
	assert_eq(-1, repo.record_score_entry("claw", 100, "bbb"))
	for e in repo.state().score_table("claw"):
		assert_eq("AAA", e.initials)


func test_record_score_entry_turns_away_zero_negative_and_nameless_scores() -> void:
	var repo := fx.repo()
	assert_eq(-1, repo.record_score_entry("racer", 0, "abc"))
	assert_eq(-1, repo.record_score_entry("racer", -5, "abc"))
	assert_eq(-1, repo.record_score_entry("  ", 50, "abc"))
	assert_true(repo.state().score_tables.is_empty())


func test_score_tables_keep_machines_apart_and_leave_the_high_score_alone() -> void:
	var repo := fx.repo()
	repo.record_score_entry("racer", 500, "abc")
	repo.record_score_entry("claw", 20, "xyz")
	var s := repo.state()
	var keys := s.score_tables.keys()
	keys.sort()
	assert_eq(["claw", "racer"], keys)
	assert_eq(1, s.score_table("racer").size())
	assert_eq(500, s.score_table("racer")[0].score)
	assert_eq([], s.score_table("pinball"))
	# hs_<id> is a separate record, written by recordScore.
	assert_eq(0, s.high_score("racer"))
	repo.record_score("racer", 500)
	s = repo.state()
	assert_eq(500, s.high_score("racer"))
	assert_eq(1, s.score_table("racer").size())
	assert_eq({"racer": 500}, s.high_scores)


func test_initials_are_cleaned_to_three_characters() -> void:
	var repo := fx.repo()
	repo.record_score_entry("g", 90, "a-b!c9z")
	repo.record_score_entry("g", 80, "")
	repo.record_score_entry("g", 70, "q")
	repo.record_score_entry("g", 60, "  ")
	repo.record_score_entry("g", 50, "  9x ")
	assert_eq(["ABC", "AAA", "QAA", "AAA", "9XA"], _initials(repo.state().score_table("g")))


func test_a_damaged_score_table_keeps_its_good_entries() -> void:
	var repo := ArcadeRepository.new(_raw("scores_racer", "ABC:100,junk,DEF:x,GH:5,IJK:-3,LMN:40,,OPQ:0"))
	assert_eq("ABC:100,LMN:40", ScoreTables.encode(repo.state().score_table("racer")))
	assert_eq(1, repo.record_score_entry("racer", 60, "new"))
	assert_eq([100, 60, 40], _scores(repo.state().score_table("racer")))
