extends PaTest
## data/ArcadeRepositoryTest.kt: the token, ticket, shop, refill and score rules on a real save file.

var fx: RepoFixture


func before_each() -> void:
	fx = RepoFixture.new()


func after_each() -> void:
	fx.cleanup()


func _hat(id: String) -> Catalog.ShopItem:
	for h in Catalog.hats():
		if h.id == id:
			return h
	return null


func _outfit(id: String) -> Catalog.ShopItem:
	for o in Catalog.outfits():
		if o.id == id:
			return o
	return null


func _decor(id: String) -> Catalog.ShopItem:
	for d in Catalog.decor():
		if d.id == id:
			return d
	return null


func _fresh_loaded() -> SaveState:
	var s := SaveState.new()
	s.loaded = true
	return s


func test_a_fresh_save_reads_the_starting_defaults() -> void:
	var repo := fx.repo()
	assert_true(_fresh_loaded().equals(repo.state()), str(repo.state()))


# ---- tokens

func test_spend_token_takes_one_token_and_counts_the_play() -> void:
	var repo := fx.repo()
	assert_true(repo.spend_token())
	var s := repo.state()
	assert_eq(ArcadeRepository.STARTING_TOKENS - 1, s.tokens)
	assert_eq(1, s.total_plays)


func test_spend_token_with_no_tokens_fails_and_changes_nothing() -> void:
	var repo := fx.repo(0)
	assert_false(repo.spend_token())
	assert_eq(0, repo.state().tokens)
	assert_eq(0, repo.state().total_plays)


func test_the_last_token_can_be_spent() -> void:
	var repo := fx.repo(1)
	assert_true(repo.spend_token())
	assert_false(repo.spend_token())
	assert_eq(0, repo.state().tokens)


func test_refund_token_gives_the_token_and_the_play_back() -> void:
	var repo := fx.repo()
	repo.spend_token()
	repo.refund_token()
	assert_eq(ArcadeRepository.STARTING_TOKENS, repo.state().tokens)
	assert_eq(0, repo.state().total_plays)


func test_refund_token_never_takes_the_count_of_plays_below_zero() -> void:
	var repo := fx.repo()
	repo.refund_token()
	assert_eq(ArcadeRepository.STARTING_TOKENS + 1, repo.state().tokens)
	assert_eq(0, repo.state().total_plays)


func test_exchange_tickets_for_token_needs_the_full_price() -> void:
	var repo := fx.repo(3, ArcadeRepository.TICKETS_PER_TOKEN - 1)
	assert_false(repo.exchange_tickets_for_token())
	assert_eq(3, repo.state().tokens)
	assert_eq(ArcadeRepository.TICKETS_PER_TOKEN - 1, repo.state().tickets)
	repo.add_tickets(1)
	assert_true(repo.exchange_tickets_for_token())
	assert_eq(4, repo.state().tokens)
	assert_eq(0, repo.state().tickets)


func test_exchange_tickets_keeps_the_change() -> void:
	var repo := fx.repo(0, 100)
	assert_true(repo.exchange_tickets_for_token())
	assert_eq(1, repo.state().tokens)
	assert_eq(100 - ArcadeRepository.TICKETS_PER_TOKEN, repo.state().tickets)


func test_add_tickets_ignores_zero_and_negative_amounts() -> void:
	var repo := fx.repo()
	repo.add_tickets(0)
	repo.add_tickets(-5)
	assert_eq(0, repo.state().tickets)
	repo.add_tickets(7)
	repo.add_tickets(3)
	assert_eq(10, repo.state().tickets)


# ---- the shop

func test_buying_a_hat_pays_equips_and_owns_it() -> void:
	var repo := fx.repo(null, 100)
	var cap := _hat("hat_cap")
	assert_true(repo.buy(cap))
	var s := repo.state()
	assert_eq(100 - cap.price, s.tickets)
	assert_true(s.owns(cap.id))
	assert_eq(cap.id, s.hat)


func test_buying_an_outfit_equips_it() -> void:
	var repo := fx.repo(null, 100)
	var blue := _outfit("outfit_blue")
	assert_true(repo.buy(blue))
	assert_eq(blue.id, repo.state().outfit)
	assert_eq("", repo.state().hat)


func test_buying_decor_owns_it_but_equips_nothing() -> void:
	var repo := fx.repo(null, 100)
	var palm := _decor("decor_palm")
	assert_true(repo.buy(palm))
	var s := repo.state()
	assert_true(s.owns(palm.id))
	assert_eq("", s.hat)
	assert_eq(Catalog.DEFAULT_OUTFIT, s.outfit)
	assert_eq([Catalog.DecorStyle.PALM], s.owned_decor())


func test_buying_something_already_owned_fails_and_costs_nothing() -> void:
	var repo := fx.repo(null, 200)
	var cap := _hat("hat_cap")
	assert_true(repo.buy(cap))
	var after := repo.state().tickets
	assert_false(repo.buy(cap))
	assert_eq(after, repo.state().tickets)


func test_buying_what_you_cannot_afford_fails_and_changes_nothing() -> void:
	var cap := _hat("hat_cap")
	var repo := fx.repo(null, cap.price - 1)
	assert_false(repo.buy(cap))
	var s := repo.state()
	assert_eq(cap.price - 1, s.tickets)
	assert_false(s.owns(cap.id))
	assert_eq("", s.hat)


func test_an_item_can_be_bought_with_exactly_its_price() -> void:
	var cap := _hat("hat_cap")
	var repo := fx.repo(null, cap.price)
	assert_true(repo.buy(cap))
	assert_eq(0, repo.state().tickets)


func test_equip_switches_hats_takes_the_worn_one_off_and_ignores_unowned_items() -> void:
	var repo := fx.repo(null, 500)
	var cap := _hat("hat_cap")
	var beanie := _hat("hat_beanie")
	repo.buy(cap)
	repo.buy(beanie)
	assert_eq(beanie.id, repo.state().hat)
	repo.equip(cap)
	assert_eq(cap.id, repo.state().hat)
	repo.equip(cap)
	assert_eq("", repo.state().hat)
	repo.equip(_hat("hat_halo"))
	assert_eq("", repo.state().hat)


# ---- the daily refill

func test_the_first_launch_starts_the_clock_without_granting_tokens() -> void:
	var repo := fx.repo()
	assert_eq(0, repo.apply_daily_refill(20000))
	assert_eq(20000, repo.state().last_refill_day)
	assert_eq(ArcadeRepository.STARTING_TOKENS, repo.state().tokens)


func test_a_new_day_grants_the_daily_tokens_once() -> void:
	var repo := fx.repo()
	repo.apply_daily_refill(20000)
	assert_eq(ArcadeRepository.DAILY_TOKENS, repo.apply_daily_refill(20001))
	assert_eq(ArcadeRepository.STARTING_TOKENS + ArcadeRepository.DAILY_TOKENS, repo.state().tokens)
	assert_eq(20001, repo.state().last_refill_day)
	# The same day again, and the days after a long absence, grant one bonus each visit.
	assert_eq(0, repo.apply_daily_refill(20001))
	assert_eq(ArcadeRepository.DAILY_TOKENS, repo.apply_daily_refill(20100))
	assert_eq(ArcadeRepository.STARTING_TOKENS + 2 * ArcadeRepository.DAILY_TOKENS, repo.state().tokens)


func test_the_same_day_grants_nothing() -> void:
	var repo := fx.repo()
	repo.apply_daily_refill(20000)
	assert_eq(0, repo.apply_daily_refill(20000))
	assert_eq(ArcadeRepository.STARTING_TOKENS, repo.state().tokens)


func test_a_clock_moved_backwards_resyncs_without_granting() -> void:
	var repo := fx.repo()
	repo.apply_daily_refill(20000)
	assert_eq(0, repo.apply_daily_refill(19990))
	assert_eq(19990, repo.state().last_refill_day)
	assert_eq(ArcadeRepository.STARTING_TOKENS, repo.state().tokens)


# ---- the spare token

func test_a_spare_token_is_only_for_the_completely_broke() -> void:
	var repo := fx.repo(1)
	assert_false(repo.claim_spare_token(1000))
	assert_eq(1, repo.state().tokens)


func test_a_spare_token_comes_back_only_after_the_cooldown() -> void:
	var repo := fx.repo(0)
	var cooldown := ArcadeRepository.SPARE_TOKEN_COOLDOWN_MS
	assert_true(repo.claim_spare_token(1000))
	assert_eq(1, repo.state().tokens)
	assert_eq(1000 + cooldown, repo.state().spare_token_at)
	repo.spend_token()
	assert_false(repo.claim_spare_token(1000 + cooldown - 1))
	assert_eq(0, repo.state().tokens)
	assert_true(repo.claim_spare_token(1000 + cooldown))
	assert_eq(1, repo.state().tokens)
	assert_eq(1000 + 2 * cooldown, repo.state().spare_token_at)


# ---- scores and prizes

func test_record_score_keeps_only_the_best_per_machine() -> void:
	var repo := fx.repo()
	assert_false(repo.record_score("racer", 0))
	assert_true(repo.record_score("racer", 50))
	assert_false(repo.record_score("racer", 50))
	assert_false(repo.record_score("racer", 40))
	assert_true(repo.record_score("racer", 60))
	assert_true(repo.record_score("claw", 5))
	var s := repo.state()
	assert_eq(60, s.high_score("racer"))
	assert_eq(5, s.high_score("claw"))
	assert_eq(0, s.high_score("pinball"))


func test_add_prize_counts_each_plush_and_the_collection_survives_a_reload() -> void:
	var repo := fx.repo()
	repo.add_prize("bear")
	repo.add_prize("bear")
	repo.add_prize("cat")
	assert_eq({"bear": 2, "cat": 1}, repo.state().collection)
	# A relaunch reads the same collection back from the file.
	assert_eq({"bear": 2, "cat": 1}, ArcadeRepository.new(fx.new_store()).state().collection)


func test_add_prize_keeps_the_good_parts_of_a_damaged_collection() -> void:
	var store := fx.new_store()
	store.edit(func(p: Dictionary) -> void:
		p["collection"] = "bear:2;;junk;cat:x;:4")
	var repo := ArcadeRepository.new(store)
	repo.add_prize("bear")
	repo.add_prize("dino")
	assert_eq({"bear": 3, "dino": 1}, repo.state().collection)


func test_collection_encoding_round_trips_and_drops_empty_counts() -> void:
	var map := {"bear": 3, "cat": 1, "ghost": 0, "dino": -2}
	assert_eq("bear:3;cat:1", ArcadeRepository.encode_collection(map))
	assert_eq({"bear": 3, "cat": 1}, ArcadeRepository.decode_collection(ArcadeRepository.encode_collection(map)))
	assert_eq("", ArcadeRepository.encode_collection({}))


func test_decode_collection_skips_garbage_and_keeps_the_rest() -> void:
	assert_eq({}, ArcadeRepository.decode_collection(null))
	assert_eq({}, ArcadeRepository.decode_collection(""))
	assert_eq({}, ArcadeRepository.decode_collection("   "))
	assert_eq({}, ArcadeRepository.decode_collection(";;;:::;"))
	var decoded := ArcadeRepository.decode_collection(";;bear:2;noColon;cat:notANumber;:9;dino:99999999999;fox:1;a:b:3;")
	assert_eq(2, decoded.get("bear"))
	assert_eq(1, decoded.get("fox"))
	# The last colon splits, so an id may hold colons of its own.
	assert_eq(3, decoded.get("a:b"))
	assert_null(decoded.get("noColon"))
	assert_null(decoded.get("cat"))
	assert_null(decoded.get("dino"))
	assert_null(decoded.get(""))


func test_muted_and_first_person_are_remembered() -> void:
	var repo := fx.repo()
	repo.set_muted(true)
	repo.set_first_person(true)
	assert_true(repo.state().muted)
	assert_true(repo.state().first_person)
	repo.set_muted(false)
	assert_false(repo.state().muted)
	assert_true(repo.state().first_person)
