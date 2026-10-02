extends PaTest
## ui/SpokenTextTest.kt: what a screen reader is told for the game's painted text.


func test_words_are_lowered_so_they_are_read_not_spelled() -> void:
	assert_eq("quit round", Widgets.spoken_text("QUIT ROUND"))
	assert_eq("paused", Widgets.spoken_text("PAUSED"))


func test_line_breaks_become_spaces() -> void:
	assert_eq("play again 1 token", Widgets.spoken_text("PLAY AGAIN\n1 TOKEN"))
	assert_eq("no tokens left", Widgets.spoken_text("NO TOKENS\nLEFT"))


func test_the_play_triangle_is_dropped_and_other_symbols_are_named() -> void:
	assert_eq("start", Widgets.spoken_text("%s START" % ArcadeFont.PLAY))
	assert_eq("star new high score! star", Widgets.spoken_text("%s NEW HIGH SCORE! %s" % [ArcadeFont.STAR, ArcadeFont.STAR]))
	assert_eq("you have tickets 12", Widgets.spoken_text("YOU HAVE %s12" % ArcadeFont.TICKET))
	assert_eq("buy tickets 40", Widgets.spoken_text("BUY %s40" % ArcadeFont.TICKET))
	assert_eq("token 5 tokens tickets 9 tickets", Widgets.spoken_text("%s 5 TOKENS %s 9 TICKETS" % [ArcadeFont.TOKEN, ArcadeFont.TICKET]))
	assert_eq("hold left right to move the claw", Widgets.spoken_text("HOLD %s %s TO MOVE THE CLAW" % [ArcadeFont.LEFT, ArcadeFont.RIGHT]))
	assert_eq("flick up", Widgets.spoken_text("FLICK %s" % ArcadeFont.UP))
	assert_eq("tap to drop down", Widgets.spoken_text("TAP TO DROP %s" % ArcadeFont.DOWN))
	assert_eq("best with sound on", Widgets.spoken_text("BEST WITH SOUND ON %s" % ArcadeFont.NOTE))


func test_a_bare_symbol_is_still_said() -> void:
	assert_eq("heart", Widgets.spoken_text(ArcadeFont.HEART))
	assert_eq("", Widgets.spoken_text(""))
	assert_eq("", Widgets.spoken_text(ArcadeFont.PLAY))
