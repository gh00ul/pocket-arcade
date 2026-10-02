class_name GameRound
extends RefCounted
## ui/GameHostScreen.kt's round flow, apart from the drawing (HostState, the frame loop,
## startResults, revealBest, stampGrade, pause and the exit button): intro card, countdown, the
## round clock, pause/quit, the ending, and the results screen where tickets print out of the
## machine with the counter climbing as they land. [GameHostScreen] draws it and feeds it frames,
## touches and taps; everything here is deterministic, so tests drive it frame by frame.

enum Phase { INTRO, COUNTDOWN, PLAYING, ENDING, RESULTS, PAUSED }

const COUNT_STEP := 0.65
## Countdown numerals: 3, 2, 1 warm up from pink to yellow, and GO! is lime.
const COUNT_COLORS := [Pal.PINK, Pal.ORANGE, Pal.YELLOW]
## Light camera kicks on each countdown number and a firmer one on GO!.
const COUNT_PUNCH := 0.12
const GO_PUNCH := 0.35
## The colours the NEW HIGH SCORE banner cycles through.
const RAINBOW := [Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME, Pal.ORANGE]
## A tap on the results this long after they begin skips the reveal.
const SKIP_AFTER := 0.6
## The NEW HIGH SCORE beat: a freeze on the slam, then slow motion while the confetti hangs.
const NEW_HIGH_STOP := 0.07
const NEW_HIGH_SLOW := 0.35
const NEW_HIGH_SLOW_SECONDS := 0.5
const NEW_HIGH_PUNCH := 0.9
const NEW_HIGH_FLASH := 0.6
## The results card, in field units.
const CARD_X := 24.0
const CARD_W := 312.0
const CARD_TOP := 28.0
const CARD_H := 276.0
## Where the grade badge sits on the card, from the card's top and the field's middle.
const BADGE_DX := -78.0
const BADGE_DY := 204.0
const BADGE_R := 30.0
## Simulation steps of ENDING every round gets (1.2 s at 120 Hz), however slow the last beat was.
const ENDING_STEPS := 144
## How long a ticket sprite takes from the printer to the counter, and the stagger between them.
const FLIGHT_SECONDS := 0.62
const FLIGHT_STAGGER := 0.035

## The host wants to leave: [param refund_token] is true when the round never started.
signal exit_requested(refund_token: bool)

var game: MiniGame
var audio: AudioSynth
var haptics: Haptics
var repo: ArcadeRepository
var fx: GameFx
## Held from the PLAY AGAIN tap until its token is spent (or refused).
var play_again_gate := TokenGate.new()

var phase := Phase.INTRO:
	set(v):
		if v == phase:
			return
		phase = v
		_music_for_phase()
var resume_phase := Phase.PLAYING
var phase_t := 0.0
var time_left := 0.0
var last_countdown := -1
var last_tick := -1
var time_up_played := false
var ended_early := false
var result_score := 0
var result_tickets := 0
var printed := 0
var print_acc := 0.0
var printing_done := false
var new_high := false
var best := 0
var go_t := 99.0
var host_time := 0.0
var celebrate_t := 0.0
var grade: int = ResultsPlan.Grade.C
## The ticket count when the round ended, so the counter can climb as each ticket lands.
var tickets_before := 0
## Tickets that have flown into the counter so far.
var landed := 0
## Tickets sent flying so far.
var launched := 0
var chunk := 1
## How far the staged reveal has got (see ResultsPlan.stage_at).
var reveal_stage := 0
var count_ticks := 0
## The player tapped through the reveal: its time control and shakes stay quiet.
var skipped := false
## A white flash over the field for the biggest moments (off with reduce motion).
var flash := Flash.new(2.4)
## Hit-stops and slow-mo beats the game asks for, and the steps they leave it.
var time := TimeScale.new()
var sim := SimClock.new()
## Game steps taken in ENDING, so a slow last beat can't shorten the settle the payout depends on.
var end_steps := 0
var particles := Particles.new(500)
var shake := ScreenShake.new(10.0)
## Tickets in the air: [count, launch time (host clock), landed?] each.
var flights: Array = []
## Where the field sits on screen (set by the screen each frame; dp and dp per field unit).
var gx := 0.0
var gy := 0.0
var gs := 1.0


func _init(p_game: MiniGame, p_audio: AudioSynth, p_haptics: Haptics, p_repo: ArcadeRepository) -> void:
	game = p_game
	audio = p_audio
	haptics = p_haptics
	repo = p_repo
	fx = GameFx.new(p_audio, p_haptics, func(item_id: String) -> void:
		# A prize won at the very end of a round is saved at once (build-13 persisted it).
		if repo != null:
			repo.add_prize(item_id))
	time_left = game.round_seconds
	game.start(fx)
	best = repo.state().high_score(game.id) if repo != null else 0
	_music_for_phase()


func reset_round() -> void:
	phase_t = 0.0
	time_left = game.round_seconds
	last_countdown = -1
	last_tick = -1
	time_up_played = false
	ended_early = false
	printed = 0
	print_acc = 0.0
	printing_done = false
	new_high = false
	go_t = 99.0
	celebrate_t = 0.0
	end_steps = 0
	grade = ResultsPlan.Grade.C
	landed = 0
	launched = 0
	chunk = 1
	reveal_stage = 0
	count_ticks = 0
	skipped = false
	flash.reset()
	flights.clear()
	time.reset()
	sim.reset()
	particles.clear()
	shake.reset()


# ------------------------------------------------------------------ the player's actions

## The intro card's START.
func start_round() -> void:
	if phase != Phase.INTRO:
		return
	_play(Sfx.SELECT)
	reset_round()
	phase = Phase.COUNTDOWN


## The pause card's RESUME.
func resume() -> void:
	if phase != Phase.PAUSED:
		return
	phase = resume_phase
	_play(Sfx.SELECT)


## The pause card's QUIT ROUND (the round is forfeited; its token is not refunded).
func quit_round() -> void:
	exit_requested.emit(false)


func pause() -> void:
	if phase == Phase.PLAYING or phase == Phase.COUNTDOWN or phase == Phase.ENDING:
		resume_phase = phase
		phase = Phase.PAUSED
		# Touches stop reaching the game here, so a finger lifted while paused never sends its UP.
		game.cancel_input()
		_play(Sfx.SELECT, 0.6, 0.8)


## Leaves from the results screen, unless PLAY AGAIN is still spending its token (it would be lost).
func leave_results() -> void:
	if not play_again_gate.claimed:
		exit_requested.emit(false)


## The close button and Android Back: leave from the intro card (refunding the token), leave from
## the results, resume from the pause card, and pause a round in progress.
func on_exit_pressed() -> void:
	match phase:
		Phase.INTRO:
			exit_requested.emit(true)
		Phase.RESULTS:
			leave_results()
		Phase.PAUSED:
			phase = resume_phase
		_:
			pause()


## PLAY AGAIN: spends a token and starts a fresh round at the countdown; refused without one.
func play_again() -> bool:
	if phase != Phase.RESULTS or not play_again_gate.try_claim():
		return false
	var ok := false
	if repo != null and repo.spend_token():
		_play(Sfx.TOKEN)
		haptics.tick()
		best = maxi(best, result_score)
		game.start(fx)
		reset_round()
		phase = Phase.COUNTDOWN
		ok = true
	else:
		_play(Sfx.ERROR)
	play_again_gate.release()
	return ok


## A touch at window position ([param x], [param y]) (dp): to the game while playing (in field
## units), or a tap on the results that skips the reveal.
func touch(type: int, id: int, x: float, y: float, time_ms: int) -> void:
	if phase == Phase.PLAYING:
		game.on_touch(type, id, (x - gx) / gs, (y - gy) / gs, time_ms)
	elif phase == Phase.RESULTS and type == TouchType.DOWN and phase_t > SKIP_AFTER:
		if reveal_stage < 4:
			# A tap jumps the reveal to the printing.
			skipped = true
			phase_t = maxf(phase_t, ResultsPlan.PRINT_AT)
		elif not printing_done:
			printed = result_tickets


## Whether touches go to the game now (the screen asks for unbuffered input while they do).
func playing() -> bool:
	return phase == Phase.PLAYING


# ------------------------------------------------------------------ the frame loop

## One step of the host's loop: build-13 ran it once per fixed 120 Hz step ([param dt] is
## GameLoop.FIXED_DT), as many times per display frame as GameLoop.steps_for says.
func frame(dt: float) -> void:
	host_time += dt
	# The results run on the time-scaled clock too, so the high-score beat that stretches the reveal
	# also stretches its confetti; everything else here is real time.
	var vdt := time.update(dt) if phase == Phase.RESULTS else dt
	particles.update(vdt)
	shake.update(vdt)
	flash.update(dt)
	_land_flights()
	# Time control lives here and only here: the game asks (GameFx), the clock decides how many of
	# these real steps it gets, and each one it gets is still exactly FIXED_DT. Only the round
	# itself is ever slowed: intro, countdown, pause and results run in real time.
	var is_playing := phase == Phase.PLAYING
	var punch := fx.flush(time if is_playing else null)
	# The camera kick isn't time control: it plays in real time, even through a freeze.
	if punch > 0.0:
		GameViewport.request_punch(punch)
	var step_game := (is_playing or phase == Phase.ENDING) and sim.advance(time.update(dt))
	match phase:
		Phase.COUNTDOWN:
			phase_t += dt
			var step := int(phase_t / COUNT_STEP)
			if step != last_countdown:
				last_countdown = step
				if audio != null:
					audio.music.stinger(Stinger.COUNTDOWN if step < 3 else Stinger.GO)
				if step < 3:
					_play(Sfx.COUNTDOWN)
					GameViewport.request_punch(COUNT_PUNCH)
				else:
					_play(Sfx.GO)
					haptics.tick()
					GameViewport.request_punch(GO_PUNCH)
			if phase_t >= COUNT_STEP * 3.0:
				phase = Phase.PLAYING
				go_t = 0.0
		Phase.PLAYING:
			go_t += dt
			if step_game:
				# The round clock is game time: it slows and freezes with the game.
				time_left = maxf(time_left - GameLoop.FIXED_DT, 0.0)
				game.update(GameLoop.FIXED_DT, time_left)
			if audio != null:
				audio.music.set_intensity(RoundMusic.intensity(time_left, game.round_seconds))
			var sec := ceili(time_left)
			if time_left > 0.0 and sec <= 5 and sec != last_tick:
				last_tick = sec
				_play(Sfx.COUNTDOWN, 0.5, 1.5)
			if time_left <= 0.0 and not time_up_played:
				time_up_played = true
				if audio != null:
					audio.music.stinger(Stinger.TIME_UP)
				_play(Sfx.BUZZER)
				haptics.hit()
			if game.finished():
				ended_early = time_left > 0.0
				if ended_early:
					_play(Sfx.BUZZER, 0.7, 1.2)
				game.cancel_input()
				phase = Phase.ENDING
				phase_t = 0.0
				end_steps = 0
		Phase.ENDING:
			phase_t += dt
			if step_game:
				game.update(GameLoop.FIXED_DT, 0.0)
				end_steps += 1
			if phase_t > 1.2 and end_steps >= ENDING_STEPS:
				_start_results()
		Phase.RESULTS:
			_results_frame(vdt)


func _results_frame(vdt: float) -> void:
	phase_t += vdt
	var t := phase_t
	var total := result_tickets
	# The staged reveal: score counts up, the best line lands, the grade is stamped, tickets print.
	if reveal_stage < 1 and t >= ResultsPlan.SCORE_AT:
		reveal_stage = 1
	if reveal_stage == 1 and t < ResultsPlan.SCORE_AT + ResultsPlan.SCORE_SECONDS:
		var ticks := int((t - ResultsPlan.SCORE_AT) / ResultsPlan.COUNT_TICK_SECONDS)
		if ticks > count_ticks:
			count_ticks = ticks
			var climb := MathUtil.clamp01((t - ResultsPlan.SCORE_AT) / ResultsPlan.SCORE_SECONDS)
			_play(Sfx.BLIP, 0.2, 0.9 + 0.9 * climb)
	if reveal_stage < 2 and t >= ResultsPlan.BEST_AT:
		reveal_stage = 2
		_reveal_best()
	if reveal_stage < 3 and t >= ResultsPlan.GRADE_AT:
		reveal_stage = 3
		_stamp_grade()
	if reveal_stage < 4 and t >= ResultsPlan.PRINT_AT:
		reveal_stage = 4
	if not printing_done and reveal_stage >= 4:
		var rate := maxf(12.0, total / 2.2)
		print_acc += vdt * rate
		while print_acc >= 1.0 and printed < total:
			print_acc -= 1.0
			printed += 1
			_play(Sfx.TICKET, 0.7, 0.9 + (printed % 6) * 0.05)
			if printed % 3 == 0:
				_play(Sfx.PRINT, 0.5)
			if printed % 4 == 0:
				haptics.tick()
		if printed >= total:
			printing_done = true
			print_acc = 0.0
			_play(Sfx.COIN, 0.8, 0.8)
			haptics.hit()
	# Printed tickets fly into the counter, one sprite per chunk and the remainder once printing is
	# done; after a skip they go up in a quick stagger.
	if reveal_stage >= 4:
		var k := 0
		while printed - launched >= chunk:
			launched += chunk
			_fly_tickets(chunk, FLIGHT_STAGGER * k)
			k += 1
		if printing_done and launched < total:
			var rest := total - launched
			launched = total
			_fly_tickets(rest, FLIGHT_STAGGER * k)
	if new_high:
		celebrate_t += vdt
		if celebrate_t > 0.7 and phase_t < 5.0 and reveal_stage >= 2:
			celebrate_t = 0.0
			particles.confetti(0.0, 0.0, MiniGame.GAME_W, 30, 6.0)


func _start_results() -> void:
	var score := game.score
	var total := game.tickets_for(score) + game.bonus_tickets
	result_score = score
	result_tickets = total
	new_high = score > best and score > 0
	grade = ResultsPlan.grade(score, best, total)
	tickets_before = repo.state().tickets if repo != null else 0
	landed = 0
	launched = 0
	chunk = ResultsPlan.flight_chunk(total)
	reveal_stage = 0
	count_ticks = 0
	skipped = false
	printed = 0
	print_acc = 0.0
	printing_done = total == 0
	phase = Phase.RESULTS
	phase_t = 0.0
	if audio != null:
		audio.music.stinger(Stinger.HIGH_SCORE if new_high else Stinger.RESULTS)
	# The payout is saved at once.
	if repo != null:
		repo.add_tickets(total)
		repo.record_score(game.id, score)
		# The profile's per-machine "played" counts, and lifetime tickets for later goals.
		repo.add_stat("plays:" + game.id)
		repo.add_stat("tickets:earned", total)
	# The card sweeping in; the fanfares wait for their place in the reveal.
	_play(Sfx.WHOOSH, 0.45, 1.25)


## The best line lands: a plain BEST, or the NEW HIGH SCORE slam with its freeze, slow motion,
## confetti and camera kick.
func _reveal_best() -> void:
	if new_high:
		_play(Sfx.HIGHSCORE)
		_play(Sfx.CHEER, 0.7)
		haptics.jackpot()
		particles.confetti(0.0, 0.0, MiniGame.GAME_W, 140, 6.0)
		shake.add(0.5)
		if not skipped:
			time.hit_stop(NEW_HIGH_STOP)
			time.slow_mo(NEW_HIGH_SLOW, NEW_HIGH_SLOW_SECONDS)
			GameViewport.request_punch(NEW_HIGH_PUNCH)
			flash.trigger(NEW_HIGH_FLASH)
	else:
		_play(Sfx.WIN, 0.7)


## The grade is stamped: a thud that grows with the grade, and a spray of sparks for an S.
func _stamp_grade() -> void:
	var g := grade
	_play(Sfx.THUD, 0.9, 1.25 if g == ResultsPlan.Grade.S else (1.1 if g == ResultsPlan.Grade.A else 0.95))
	if g == ResultsPlan.Grade.S or g == ResultsPlan.Grade.A:
		haptics.hit()
	else:
		haptics.tick()
	shake.add(0.35 if g == ResultsPlan.Grade.S else (0.25 if g == ResultsPlan.Grade.A else 0.12))
	if g == ResultsPlan.Grade.S:
		_play(Sfx.COIN, 0.7, 1.4)
		particles.burst(MiniGame.GAME_W / 2.0 + BADGE_DX, CARD_TOP + BADGE_DY, 30, 50.0, 190.0,
			[Pal.GOLD, Pal.YELLOW, Pal.WHITE], 0.8, 3.0, 0.0, 2.4, Particles.SPARKLE)
	if not skipped and g != ResultsPlan.Grade.C:
		time.hit_stop(0.07 if g == ResultsPlan.Grade.S else 0.04)
		GameViewport.request_punch(0.5 if g == ResultsPlan.Grade.S else 0.25)


## Sends [param n] tickets flying from the printer's slot to the counter, [param delay] seconds
## from now; the counter climbs as they land.
func _fly_tickets(n: int, delay: float) -> void:
	flights.append([n, host_time + delay, false])


func _land_flights() -> void:
	for f: Array in flights:
		if not f[2] and host_time >= f[1] + FLIGHT_SECONDS:
			f[2] = true
			landed += f[0]
			_play(Sfx.CLINK, 0.22, 1.2 + 0.02 * (landed % 10))


## How far ticket flight [param f] is from the printer (0) to the counter (1), or -1 before it starts.
func flight_progress(f: Array) -> float:
	var p := (host_time - float(f[1])) / FLIGHT_SECONDS
	return -1.0 if p < 0.0 else minf(p, 1.0)


## The best shown on the top bar: the new best stays hidden until the results reveal it.
func live_best() -> int:
	return maxi(best, result_score if phase == Phase.RESULTS and reveal_stage >= 2 else 0)


## The soundtrack follows the round: this machine's theme, sat back under the intro card and the
## pause menu, then the results' resolving loop (see RoundMusic).
func _music_for_phase() -> void:
	if audio == null:
		return
	match phase:
		Phase.INTRO:
			RoundMusic.intro(audio, game.id)
		Phase.COUNTDOWN:
			RoundMusic.countdown(audio, game.id)
		Phase.RESULTS:
			RoundMusic.results(audio)
		Phase.PAUSED:
			RoundMusic.pause(audio)
		_:
			RoundMusic.play(audio)


func _play(sfx: int, volume: float = 1.0, pitch: float = 1.0) -> void:
	if audio != null:
		audio.play(sfx, volume, pitch)
