extends PaTest
## data/FirstPersonPrefTest.kt: the hall's camera choice survives a save and a reload, and a fresh
## save starts overhead.


func test_the_view_choice_round_trips() -> void:
	var p := {}
	assert_false(ArcadeRepository.read(p).first_person)
	ArcadeRepository.write_first_person(p, true)
	var loaded := ArcadeRepository.read(p)
	assert_true(loaded.first_person)
	assert_true(loaded.loaded)
	# Other settings are untouched.
	assert_false(loaded.muted)
	ArcadeRepository.write_first_person(p, false)
	assert_false(ArcadeRepository.read(p).first_person)
