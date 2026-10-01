extends PaTest
## data/SaveCodecsTest.kt: the pure encodings and cleaners behind the save.

const LONG_MAX := 0x7FFFFFFFFFFFFFFF


# ---- stats

func test_stats_round_trip_and_keys_may_hold_colons() -> void:
	var map := {"plays:racer": 12, "tickets:earned": 5000000000}
	var raw := ArcadeRepository.encode_stats(map)
	assert_eq("plays:racer:12;tickets:earned:5000000000", raw)
	assert_eq(map, ArcadeRepository.decode_stats(raw))


func test_encode_stats_drops_zero_and_negative_counts() -> void:
	assert_eq("a:1", ArcadeRepository.encode_stats({"a": 1, "b": 0, "c": -4}))
	assert_eq("", ArcadeRepository.encode_stats({}))


func test_decode_stats_ignores_bad_parts() -> void:
	assert_eq({}, ArcadeRepository.decode_stats(null))
	assert_eq({}, ArcadeRepository.decode_stats(""))
	assert_eq({}, ArcadeRepository.decode_stats(" \n "))
	assert_eq({}, ArcadeRepository.decode_stats(";;;::;:"))
	var decoded := ArcadeRepository.decode_stats(";;ok:3;noColon;bad:text;:8;neg:-1;zero:0;huge:99999999999999999999;  spaced  : 7 ;last:1")
	assert_eq({"ok": 3, "spaced": 7, "last": 1}, decoded)


func test_decode_stats_lets_a_later_duplicate_win() -> void:
	assert_eq({"a": 9}, ArcadeRepository.decode_stats("a:2;a:9"))


# ---- id sets

func test_id_sets_round_trip_in_order() -> void:
	var ids: Array[String] = ["first_win", "racer:gold", "a"]
	assert_eq(["first_win", "racer:gold", "a"], ArcadeRepository.decode_ids(ArcadeRepository.encode_ids(ids)))
	assert_eq("", ArcadeRepository.encode_ids([]))


func test_decode_ids_skips_blanks_trims_and_drops_repeats() -> void:
	assert_eq([], ArcadeRepository.decode_ids(null))
	assert_eq([], ArcadeRepository.decode_ids(""))
	assert_eq([], ArcadeRepository.decode_ids(";;; ;"))
	assert_eq(["a", "b"], ArcadeRepository.decode_ids(" a;;b ;a;"))


# ---- the arcade name

func test_sanitize_arcade_name_uppercases_and_keeps_only_letters_digits_and_spaces() -> void:
	assert_eq("HELLO WORLD9", ArcadeRepository.sanitize_arcade_name("hello, world_9!"))
	assert_eq("ABC", ArcadeRepository.sanitize_arcade_name("a\tb\nc"))
	assert_eq("CAF", ArcadeRepository.sanitize_arcade_name("café"))


func test_sanitize_arcade_name_trims_and_cuts_to_fourteen() -> void:
	assert_eq("NEON NOOK", ArcadeRepository.sanitize_arcade_name("   neon nook   "))
	assert_eq("ABCDEFGHIJKLMN", ArcadeRepository.sanitize_arcade_name("abcdefghijklmnopqrstuvwxyz"))
	assert_eq("ABCDEFGHIJKLMN", ArcadeRepository.sanitize_arcade_name("ABCDEFGHIJKLMN"))
	# A cut that lands after a space leaves no trailing space.
	assert_eq("ABCDEFGHIJKLM", ArcadeRepository.sanitize_arcade_name("abcdefghijklm nopq"))
	assert_true(ArcadeRepository.sanitize_arcade_name("x".repeat(500)).length() <= ArcadeRepository.MAX_ARCADE_NAME_LENGTH)


func test_sanitize_arcade_name_falls_back_to_the_default_when_nothing_is_left() -> void:
	assert_eq("POCKET ARCADE", ArcadeRepository.sanitize_arcade_name(null))
	assert_eq("POCKET ARCADE", ArcadeRepository.sanitize_arcade_name(""))
	assert_eq("POCKET ARCADE", ArcadeRepository.sanitize_arcade_name("    "))
	assert_eq("POCKET ARCADE", ArcadeRepository.sanitize_arcade_name("!@#$%"))
	assert_eq("POCKET ARCADE", ArcadeRepository.DEFAULT_ARCADE_NAME)
	assert_true(ArcadeRepository.DEFAULT_ARCADE_NAME.length() <= ArcadeRepository.MAX_ARCADE_NAME_LENGTH)


func test_sanitize_arcade_name_is_idempotent() -> void:
	for raw in ["hello world", "  x  ", "A B C D E F G H I J K", "%%%", "POCKET ARCADE"]:
		var once := ArcadeRepository.sanitize_arcade_name(raw)
		assert_eq(once, ArcadeRepository.sanitize_arcade_name(once))


# ---- score tables

func _table(scores: Array) -> Array:
	var out: Array = []
	for i in scores.size():
		out.append(ScoreTables.ScoreEntry.new("P%dA" % i, scores[i]))
	return out


func _scores(entries: Array) -> Array:
	return entries.map(func(e: ScoreTables.ScoreEntry) -> int: return e.score)


func _initials(entries: Array) -> Array:
	return entries.map(func(e: ScoreTables.ScoreEntry) -> String: return e.initials)


func test_rank_for_finds_the_place_in_a_sorted_table() -> void:
	var entries := _table([900, 500, 300])
	assert_eq(0, ScoreTables.rank_for(entries, 1000))
	assert_eq(1, ScoreTables.rank_for(entries, 600))
	assert_eq(2, ScoreTables.rank_for(entries, 400))
	assert_eq(3, ScoreTables.rank_for(entries, 100))
	assert_eq(0, ScoreTables.rank_for([], 1))


func test_rank_for_puts_a_tie_below_the_earlier_score() -> void:
	var entries := _table([900, 500, 300])
	assert_eq(1, ScoreTables.rank_for(entries, 900))
	assert_eq(2, ScoreTables.rank_for(entries, 500))
	assert_eq(3, ScoreTables.rank_for(entries, 300))


func test_rank_for_turns_away_scores_that_miss_a_full_table() -> void:
	var full := _table([500, 400, 300, 200, 100])
	assert_eq(-1, ScoreTables.rank_for(full, 99))
	assert_eq(-1, ScoreTables.rank_for(full, 100))
	assert_eq(4, ScoreTables.rank_for(full, 101))
	assert_eq(0, ScoreTables.rank_for(full, 501))


func test_rank_for_turns_away_scores_at_or_below_zero() -> void:
	assert_eq(-1, ScoreTables.rank_for([], 0))
	assert_eq(-1, ScoreTables.rank_for([], -3))
	assert_eq(-1, ScoreTables.rank_for(_table([5]), -0x80000000))


func test_rank_for_copes_with_a_table_that_is_not_sorted() -> void:
	assert_eq(1, ScoreTables.rank_for(_table([100, 900, 300]), 600))


func test_insert_places_the_entry_and_cuts_to_five() -> void:
	var full := _table([500, 400, 300, 200, 100])
	var inserted := ScoreTables.insert(full, ScoreTables.ScoreEntry.new("NEW", 350))
	assert_eq([500, 400, 350, 300, 200], _scores(inserted))
	assert_eq("NEW", inserted[2].initials)
	# A miss leaves the table as it was.
	assert_eq(ScoreTables.encode(full), ScoreTables.encode(ScoreTables.insert(full, ScoreTables.ScoreEntry.new("LOW", 50))))
	assert_eq("ONE:7", ScoreTables.encode(ScoreTables.insert([], ScoreTables.ScoreEntry.new("ONE", 7))))


func test_score_tables_round_trip() -> void:
	var entries := [ScoreTables.ScoreEntry.new("ABC", 900), ScoreTables.ScoreEntry.new("X9Z", 50)]
	assert_eq("ABC:900,X9Z:50", ScoreTables.encode(entries))
	assert_eq("ABC:900,X9Z:50", ScoreTables.encode(ScoreTables.decode(ScoreTables.encode(entries))))
	assert_eq("", ScoreTables.encode([]))


func test_decode_skips_bad_parts_sorts_and_cuts_to_five() -> void:
	assert_eq([], ScoreTables.decode(null))
	assert_eq([], ScoreTables.decode(""))
	assert_eq([], ScoreTables.decode("   "))
	assert_eq([], ScoreTables.decode(",,,:::,:,"))
	var decoded := ScoreTables.decode("AAA:10,junk,BB:20,CCCC:30,dd1:40,EEE:x,FFF:0,GGG:-5,HHH:99999999999,,III:50,J K:60,LLL:70,MMM:5,NNN:80,OOO:90")
	assert_eq(["OOO", "NNN", "LLL", "III", "AAA"], _initials(decoded))
	assert_eq([90, 80, 70, 50, 10], _scores(decoded))


func test_decode_keeps_ties_in_the_order_they_were_saved() -> void:
	assert_eq(["AAA", "BBB", "CCC"], _initials(ScoreTables.decode("AAA:5,BBB:5,CCC:5")))


# ---- initials

func test_sanitize_initials_gives_exactly_three_characters_of_letters_and_digits() -> void:
	assert_eq("ABC", ScoreTables.sanitize_initials("abc"))
	assert_eq("ABC", ScoreTables.sanitize_initials("a-b c!"))
	assert_eq("ABC", ScoreTables.sanitize_initials("abcdef"))
	assert_eq("A9Z", ScoreTables.sanitize_initials("a9z"))
	assert_eq("BAA", ScoreTables.sanitize_initials("b"))
	assert_eq("XYA", ScoreTables.sanitize_initials(" x y "))
	assert_eq("AAA", ScoreTables.sanitize_initials(""))
	assert_eq("AAA", ScoreTables.sanitize_initials(null))
	assert_eq("AAA", ScoreTables.sanitize_initials("!?-"))
	assert_eq(ScoreTables.DEFAULT_INITIALS, ScoreTables.sanitize_initials("   "))
	for raw in ["", "q", "qwerty", "  9 ", "ééé", "\n\t"]:
		var out := ScoreTables.sanitize_initials(raw)
		assert_eq(3, out.length())
		for i in out.length():
			var ch := out[i]
			assert_true((ch >= "A" and ch <= "Z") or (ch >= "0" and ch <= "9"), out)
