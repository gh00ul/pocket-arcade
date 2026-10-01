class_name AudioCache
extends RefCounted
## The synthesized sounds, kept between launches: build-13 synthesized everything at every start
## on its mixer thread; GDScript takes a few seconds for it on a phone, so the first launch renders
## the sound effects and the ambience loops on a worker thread and stores them (16-bit PCM) under
## user://cache, and later launches load them in a moment. Bump [constant SfxBank.VERSION] to rebuild.

const DIR := "user://cache"
## The file starts with this, then the payload's hash, then the payload.
const MAGIC := 0x43414150 # "PAAC"


## What one launch needs: a stream per sound effect and the two ambience loops.
class Sounds:
	extends RefCounted
	var rate := 48000
	var sfx: Array = []
	var hum: AudioStreamWAV
	var murmur: AudioStreamWAV


static func path_for(rate: int, dir: String = DIR) -> String:
	return "%s/audio_v%d_%d.bin" % [dir, SfxBank.VERSION, rate]


## The cached sounds for [param rate], or null when there are none (or they are unreadable).
static func read(rate: int, dir: String = DIR) -> Sounds:
	var path := path_for(rate, dir)
	if not FileAccess.file_exists(path):
		return null
	var bytes := FileAccess.get_file_as_bytes(path)
	if bytes.size() < 16 or bytes.decode_u32(0) != MAGIC:
		return null
	var payload := bytes.slice(8)
	if hash(payload) & 0xFFFFFFFF != bytes.decode_u32(4):
		return null
	var data: Variant = bytes_to_var(payload)
	if not (data is Dictionary):
		return null
	var d: Dictionary = data
	if d.get("version", -1) != SfxBank.VERSION or d.get("rate", -1) != rate:
		return null
	var list: Variant = d.get("sfx")
	if not (list is Array) or (list as Array).size() != Sfx.COUNT:
		return null
	var s := Sounds.new()
	s.rate = rate
	for pcm: Variant in list:
		if not (pcm is PackedByteArray):
			return null
		s.sfx.append(_wav(pcm, rate, false, -1))
	var hum: Variant = d.get("hum")
	var murmur: Variant = d.get("murmur")
	if not (hum is PackedByteArray) or not (murmur is PackedByteArray):
		return null
	s.hum = _wav(hum, rate, false, (hum as PackedByteArray).size() / 2)
	s.murmur = _wav(murmur, rate, true, (murmur as PackedByteArray).size() / 4)
	return s


## Synthesizes everything for [param rate] (slow: run it on a worker thread).
## The ambience loops render on their own threads while the sound effects are made.
static func build(rate: int) -> Sounds:
	var hum_thread := Thread.new()
	hum_thread.start(HallAmbience.render_hum_loop.bind(rate))
	var murmur_thread := Thread.new()
	murmur_thread.start(HallAmbience.render_babble_loop.bind(rate))
	var bank := SfxBank.new(rate)
	bank.generate_all()
	var s := Sounds.new()
	s.rate = rate
	for i in Sfx.COUNT:
		s.sfx.append(bank.stream_of(bank.samples(i)))
	var hum: PackedFloat32Array = hum_thread.wait_to_finish()
	s.hum = SfxBank.wav_stream(hum, rate, 1, hum.size())
	var murmur: PackedFloat32Array = murmur_thread.wait_to_finish()
	s.murmur = SfxBank.wav_stream(murmur, rate, 2, murmur.size() / 2)
	return s


## Stores [param s] for the next launch (written to a temporary file, then renamed into place).
static func save(s: Sounds, dir: String = DIR) -> bool:
	DirAccess.make_dir_recursive_absolute(dir)
	var pcm: Array = []
	for w: AudioStreamWAV in s.sfx:
		pcm.append(w.data)
	var d := {"version": SfxBank.VERSION, "rate": s.rate, "sfx": pcm, "hum": s.hum.data, "murmur": s.murmur.data}
	var path := path_for(s.rate, dir)
	var tmp := path + ".tmp"
	var f := FileAccess.open(tmp, FileAccess.WRITE)
	if f == null:
		return false
	var payload := var_to_bytes(d)
	f.store_32(MAGIC)
	f.store_32(hash(payload) & 0xFFFFFFFF)
	f.store_buffer(payload)
	f.close()
	return DirAccess.rename_absolute(tmp, path) == OK


static func _wav(pcm: PackedByteArray, rate: int, stereo: bool, loop_frames: int) -> AudioStreamWAV:
	var w := AudioStreamWAV.new()
	w.format = AudioStreamWAV.FORMAT_16_BITS
	w.mix_rate = rate
	w.stereo = stereo
	w.data = pcm
	if loop_frames > 0:
		w.loop_mode = AudioStreamWAV.LOOP_FORWARD
		w.loop_begin = 0
		w.loop_end = loop_frames
	return w
