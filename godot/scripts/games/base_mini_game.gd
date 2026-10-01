class_name BaseMiniGame
extends MiniGame
## games/BaseMiniGame.kt: convenience base for mini-games. It owns the juice (particles, shake,
## popups, flash), the score and the round clock, so a game only implements [method reset],
## [method step] and [method render] (and [method cancel_input]).

const HIT_STOP_DEFAULT := 0.06
const SLOW_MO_SPEED := 0.4
const SLOW_MO_SECONDS := 0.45
const PUNCH_DEFAULT := 0.4
const IMPACT_SHAKE_MIN := 0.12
const IMPACT_SHAKE_RANGE := 0.4
const IMPACT_STOP_MIN := 0.03
const IMPACT_STOP_RANGE := 0.06
const IMPACT_PUNCH_MIN := 0.15
const IMPACT_PUNCH_RANGE := 0.55
const BIG_MOMENT_FLASH := 0.5
const FLASH_ALPHA := 0.55

var fx: GameFx = null
var particles := Particles.new(700)
var shake := ScreenShake.new(16.0)
var popups := FloatingTexts.new()
var flash := Flash.new(3.0)
var rng := KRandom.new(0)
## Fixed seed for reproducible rounds in headless tests; null means a fresh random round.
var fixed_seed: Variant = null
## Seconds since the round started.
var time := 0.0
var time_left := 0.0
## Set once when the clock hits zero.
var time_up := false
## Set by games that can end before the clock does (e.g. out of coins).
var ended_early := false


func start(p_fx: GameFx) -> void:
	fx = p_fx
	rng = KRandom.new(fixed_seed if fixed_seed != null else Time.get_ticks_usec() ^ (Time.get_unix_time_from_system() as int))
	particles.clear()
	shake.reset()
	popups.clear()
	score = 0
	bonus_tickets = 0
	time = 0.0
	time_left = round_seconds
	time_up = false
	ended_early = false
	reset()


func update(dt: float, p_time_left: float) -> void:
	time += dt
	time_left = p_time_left
	if not time_up and p_time_left <= 0.0:
		time_up = true
		on_time_up()
	step(dt)
	particles.update(dt)
	shake.update(dt)
	popups.update(dt)
	flash.update(dt)


func draw(scope: DrawScope) -> void:
	GameViewport.shake_x = shake.offset_x
	GameViewport.shake_y = shake.offset_y
	# Offer the particles to the GPU picture, where they glow in the bloom; a game with a 3D stage
	# takes them in its present step, and only if nothing did are they painted in 2D.
	GameViewport.particles = particles
	GameViewport.particles_in_gl = false
	scope.push()
	scope.translate(shake.offset_x, shake.offset_y)
	render(scope)
	GameViewport.shake_x = 0.0
	GameViewport.shake_y = 0.0
	GameViewport.particles = null
	if not GameViewport.particles_in_gl:
		particles.draw(scope)
	popups.draw(scope, 0.0, 0.0, 1.0)
	scope.pop()
	# Flashes are motion too: reduce motion (intensity 0) turns them off.
	var flash_alpha := flash.value * FLASH_ALPHA * clampf(ScreenShake.intensity, 0.0, 1.0)
	if flash_alpha > 0.0:
		scope.draw_rect(Color.WHITE, Vector2(-40, -40), Vector2(GAME_W + 80.0, GAME_H + 80.0), flash_alpha)


func finished() -> bool:
	return (time_up or ended_early) and is_settled()


## Resets the game's own state for a fresh round.
func reset() -> void:
	pass


## Advances the game's own simulation by [param dt].
func step(_dt: float) -> void:
	pass


## Draws the game in field units.
func render(_scope: DrawScope) -> void:
	pass


## Called once when the clock hits zero.
func on_time_up() -> void:
	pass


## Whether nothing is still in motion (balls in flight, coins sliding, claw mid-grab).
func is_settled() -> bool:
	return true


func add_score(points: int, x: float, y: float, color: Variant, label: String = "") -> void:
	score = maxi(score + points, 0)
	var text := label
	if text == "":
		text = ("+%d" % points) if points >= 0 else str(points)
	popups.add(text, x, y, color, 3.0)


func play(sfx: int, volume: float = 1.0, pitch: float = 1.0) -> void:
	if fx != null and fx.audio != null:
		fx.audio.play(sfx, volume, pitch)


# ---- game feel: requests the host turns into a freeze, a slow beat and a camera kick

## Freezes the game for a few frames as a big hit lands ([method GameFx.hit_stop]).
func hit_stop(seconds: float = HIT_STOP_DEFAULT) -> void:
	fx.hit_stop(seconds)


## A slow-motion beat with eased ramps ([method GameFx.slow_mo]). Off with reduce motion.
func slow_mo(speed: float = SLOW_MO_SPEED, seconds: float = SLOW_MO_SECONDS) -> void:
	fx.slow_mo(speed, seconds)


## Kicks the 3D camera in and springs it back ([method GameFx.punch]), [param amount] 0..1.
func punch(amount: float = PUNCH_DEFAULT) -> void:
	fx.punch(amount)


## A solid hit in one call: screen shake, a short freeze and a camera kick, all scaled by
## [param weight] (0 = a tap, 1 = the hardest hit a round has). Sound and particles stay the game's own.
func impact(weight: float) -> void:
	var w := clampf(weight, 0.0, 1.0)
	shake.add(IMPACT_SHAKE_MIN + IMPACT_SHAKE_RANGE * w)
	fx.hit_stop(IMPACT_STOP_MIN + IMPACT_STOP_RANGE * w)
	fx.punch(IMPACT_PUNCH_MIN + IMPACT_PUNCH_RANGE * w)


## The round's biggest moment (a jackpot, a perfect finish): the hardest impact, a white flash and a slow-motion beat.
func big_moment() -> void:
	impact(1.0)
	slow_mo()
	flash.trigger(BIG_MOMENT_FLASH)
