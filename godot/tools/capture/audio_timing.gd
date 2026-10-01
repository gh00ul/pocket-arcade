extends SceneTree
## Times the first-launch sound synthesis, part by part. Not part of the game.

func _init() -> void:
	var t := Time.get_ticks_usec()
	var bank := SfxBank.new(48000)
	bank.generate_all()
	var t1 := Time.get_ticks_usec()
	var total := 0
	for i in Sfx.COUNT:
		total += bank.samples(i).size()
	print("sfx bank: %d ms (%d samples)" % [(t1 - t) / 1000, total])
	var streams: Array = []
	for i in Sfx.COUNT:
		streams.append(bank.stream_of(bank.samples(i)))
	var t2 := Time.get_ticks_usec()
	print("sfx streams: %d ms" % ((t2 - t1) / 1000))
	var hum := HallAmbience.render_hum_loop(48000)
	var t3 := Time.get_ticks_usec()
	print("hum loop: %d ms" % ((t3 - t2) / 1000))
	var babble := HallAmbience.render_babble_loop(48000)
	var t4 := Time.get_ticks_usec()
	print("babble loop: %d ms (%d frames)" % [(t4 - t3) / 1000, babble.size() / 2])
	quit()
