class_name AudioFixture
extends RefCounted
## Shared by the audio tests: one generated sound bank (generating it takes about a second).

static var _bank: SfxBank = null


static func bank() -> SfxBank:
	if _bank == null:
		_bank = SfxBank.new(48000)
		_bank.generate_all()
	return _bank


## A mixer under [param host] with the bank loaded, its clock driven by the test, the ambience off.
static func engine(host: Node) -> MixEngine:
	var e := MixEngine.new()
	e.manual_clock = true
	host.add_child(e)
	e.load_bank(bank())
	e.ambient_target = 0.0
	return e


## [param seconds] of a constant 1.0, as a mono stream (build-13's tests used such a buffer).
static func steady(seconds: float) -> AudioStreamWAV:
	var s := PackedFloat32Array()
	s.resize(int(seconds * 48000))
	s.fill(1.0)
	return SfxBank.wav_stream(s, 48000, 1)


## The voice playing [param sfx] most recently started, or null.
static func voice_of(e: MixEngine, sfx: int) -> MixEngine.Voice:
	var best: MixEngine.Voice = null
	for v in e.voices():
		if v.active and v.sfx == sfx and (best == null or v.started >= best.started):
			best = v
	return best
