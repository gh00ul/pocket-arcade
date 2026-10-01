extends BaseMiniGame
## A stand-in machine for checking the capture tools (not part of the game).

var stage := Stage3D.new(360, 640)
var model: Model


func _init() -> void:
	id = "dummy"
	title = "DUMMY"
	marquee = "DUMMY"
	round_seconds = 30.0
	instructions = PackedStringArray(["TAP ANYWHERE"])
	look = MiniGame.CabinetLook.new(Pal.PURPLE, Pal.YELLOW, Pal.PINK)


func reset() -> void:
	var b := ModelBuilder.new()
	b.box(-60, 0, -60, 60, 40, 60, BoxFaces.all(TexKit.white().full(), 0.5), Pal.CYAN)
	model = b.build()


func step(_dt: float) -> void:
	pass


func render(scope: DrawScope) -> void:
	stage.look(0, 140, 260, 0, 20, 0, 50)
	var r := stage.begin()
	r.clear(Pal.NIGHT)
	r.gradient(0xFF07030F, 0xFF3A1650, 0, 400)
	model.draw(r, -1, 1.0, Xform.new().set_xf(0, 0, 0, time))
	stage.present()
	scope.draw_circle(Pal.c(Pal.YELLOW), 20.0, Vector2(180, 560))
	ArcadeFont.draw_centered(scope, "SCORE %d" % score, 180, 600, 2.0, Pal.WHITE)


func on_touch(type: int, _id: int, x: float, y: float, _t: int) -> void:
	if type == TouchType.DOWN:
		add_score(10, x, y, Pal.LIME)
		particles.burst(x, y, 12, 40.0, 160.0, [Pal.PINK, Pal.YELLOW])


func cancel_input() -> void:
	pass


func tickets_for(s: int) -> int:
	return s / 10
