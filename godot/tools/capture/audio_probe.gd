extends Node
## Plays a few sounds through the real mixer and prints the buses' peak levels (a check that the
## voice pool, panners, reverb send and ambience loops reach the output). Not part of the game.

var audio: AudioSynth
var frame := 0
var peaks := {}


func _ready() -> void:
	var t0 := Time.get_ticks_msec()
	audio = AudioSynth.new()
	add_child(audio)
	audio.sounds_ready.connect(func() -> void: print("sounds ready after %d ms" % (Time.get_ticks_msec() - t0)))
	audio.start()


func _process(_delta: float) -> void:
	frame += 1
	if not audio.is_ready():
		if frame > 3000:
			print("FAIL: sounds never became ready")
			get_tree().quit(1)
		return
	if not peaks.has("start"):
		peaks["start"] = frame
		audio.enter_scene(MusicScene.HALL)
		audio.ambient_target = 1.0
		audio.set_listener(300.0, 500.0, PI)
		audio.play(Sfx.JACKPOT)
		audio.play_at(Sfx.COIN, 380.0, 500.0)
	var f: int = frame - peaks["start"]
	for bus: StringName in [AudioBuses.SFX, AudioBuses.SEND, AudioBuses.AMBIENCE, AudioBuses.OUT, AudioBuses.voice_bus(1)]:
		var i := AudioBuses.index(bus)
		var l := AudioServer.get_bus_peak_volume_left_db(i, 0)
		var r := AudioServer.get_bus_peak_volume_right_db(i, 0)
		var key := String(bus)
		var best: Vector2 = peaks.get(key, Vector2(-200, -200))
		peaks[key] = Vector2(maxf(best.x, l), maxf(best.y, r))
	if f == 180:
		for k: String in peaks:
			if k != "start":
				print("%-12s peak L %6.1f dB  R %6.1f dB" % [k, peaks[k].x, peaks[k].y])
		print("hum gain %.4f murmur gain %.4f voices %d" % [audio.engine.ambience.hum_gain, audio.engine.ambience.murmur_gain, audio.engine.active_voices()])
		get_tree().quit(0)
