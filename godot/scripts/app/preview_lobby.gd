class_name PreviewLobby
extends Control
## PREVIEW BUILDS ONLY, not in build-13: until the title screen and the hall are ported, the app
## opens on this list of the machines ported so far. A machine costs a token, as in the hall; it
## opens in the game host, and leaving comes back here. It goes away when the hall lands (see
## docs/PORT_PARITY.md, "Preview builds").

const ROW_H := 76.0
const ROW_GAP := 10.0
const SIDE := 16.0
const TAP_SLOP := 10.0

var main: Main
var games: Array[MiniGame] = []
var host: GameHostScreen = null
var gate := TokenGate.new()
## The machines' rows on screen: [Rect2, game index].
var rows: Array = []

var _scroll := 0.0
var _press_y := -1.0
var _press_scroll := 0.0
var _moved := false
var _msg := ""
var _msg_t := 0.0
var _list_top := 0.0


func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP
	games = GameRegistry.create_all()
	main.services.repo.apply_daily_refill()
	_enter()


func _enter() -> void:
	visible = true
	main.back_handler = Callable()
	main.audio.enter_scene(MusicScene.HALL)
	main.audio.music.set_intensity(0.5)
	main.audio.ambient_target = 0.0
	queue_redraw()


## Opens machine [param index] if a token can be spent on it.
func open(index: int) -> void:
	if host != null or not gate.try_claim():
		return
	var repo := main.services.repo
	if not repo.spend_token():
		if repo.claim_spare_token() and repo.spend_token():
			_say("A SPARE TOKEN, ON THE HOUSE")
		else:
			gate.release()
			_say("OUT OF TOKENS: MORE TOMORROW")
			main.audio.play(Sfx.ERROR)
			main.services.haptics.soft()
			return
	main.audio.play(Sfx.COIN)
	main.services.haptics.hit()
	var g: MiniGame = games[index].get_script().new()
	host = GameHostScreen.create(g, main.services)
	host.exited.connect(_on_exited)
	main.app_root.add_child(host)
	main.back_handler = host.handle_back
	visible = false


func _on_exited(refund: bool) -> void:
	if refund:
		main.services.repo.refund_token()
	if host != null:
		host.queue_free()
		host = null
	gate.release()
	_enter()


func _say(text: String) -> void:
	_msg = text
	_msg_t = 2.5
	queue_redraw()


func _process(delta: float) -> void:
	if _msg_t > 0.0:
		_msg_t = maxf(0.0, _msg_t - delta)
		queue_redraw()


func _max_scroll() -> float:
	var list_h := games.size() * (ROW_H + ROW_GAP)
	return maxf(0.0, list_h - (size.y - _list_top - Display.insets_dp(get_window()).w - 40.0))


func _gui_input(event: InputEvent) -> void:
	if host != null:
		return
	if event is InputEventScreenTouch:
		var t := event as InputEventScreenTouch
		if t.index != 0:
			return
		if t.pressed:
			_press_y = t.position.y
			_press_scroll = _scroll
			_moved = false
		elif _press_y >= 0.0:
			_press_y = -1.0
			if not _moved:
				for r: Array in rows:
					if (r[0] as Rect2).has_point(t.position):
						open(r[1])
						break
		accept_event()
	elif event is InputEventScreenDrag and _press_y >= 0.0:
		var d := event as InputEventScreenDrag
		if d.index != 0:
			return
		if absf(d.position.y - _press_y) > TAP_SLOP:
			_moved = true
		if _moved:
			_scroll = clampf(_press_scroll - (d.position.y - _press_y), 0.0, _max_scroll())
			queue_redraw()
		accept_event()


func _draw() -> void:
	var ds := DrawScope.new(self, size)
	var w := size.x
	var insets := Display.insets_dp(get_window())
	ds.draw_rect(Pal.c(Pal.NIGHT), Vector2.ZERO, size)
	var y := insets.y + 28.0
	var title_unit := minf(4.0, (w - SIDE * 2.0) / ArcadeFont.width("POCKET ARCADE", 1.0))
	ArcadeFont.draw_centered(ds, "POCKET ARCADE", w / 2.0, y, title_unit, Pal.PINK)
	y += title_unit * 7.0 + 12.0
	ArcadeFont.draw_centered(ds, "PREVIEW BUILD", w / 2.0, y, 2.0, Pal.CYAN)
	y += 20.0
	ArcadeFont.draw_centered(ds, "THE HALL IS STILL BEING PORTED", w / 2.0, y, 1.4, Pal.LAVENDER)
	y += 26.0
	var s := main.services.repo.state()
	var currency := "%s %d     %s %d" % [ArcadeFont.TOKEN, s.tokens, ArcadeFont.TICKET, s.tickets]
	ArcadeFont.draw_centered(ds, currency, w / 2.0, y, 2.5, Pal.GOLD)
	y += 30.0
	if _msg_t > 0.0:
		ArcadeFont.draw_centered(ds, _msg, w / 2.0, y, 1.6, Pal.ORANGE, minf(1.0, _msg_t / 0.4))
	y += 22.0
	_list_top = y
	rows.clear()
	ds.push()
	ds.clip_rect(0.0, _list_top, w, size.y)
	for i in games.size():
		var g := games[i]
		var top := _list_top + i * (ROW_H + ROW_GAP) - _scroll
		var r := Rect2(SIDE, top, w - SIDE * 2.0, ROW_H)
		rows.append([r, i])
		if r.end.y < _list_top or r.position.y > size.y:
			continue
		ds.draw_round_rect(Pal.c(g.look.body), r.position, r.size, 10.0, 0.85)
		ds.draw_round_rect(Pal.c(g.look.trim), r.position, r.size, 10.0, 1.0, PaStroke.new(2.0))
		ds.draw_rect(Pal.c(g.look.glow), Vector2(r.position.x + 12.0, r.end.y - 12.0), Vector2(r.size.x - 24.0, 2.0), 0.6)
		ArcadeFont.draw(ds, g.title, r.position.x + 16.0, r.position.y + 16.0, 2.6, Pal.WHITE)
		ArcadeFont.draw(ds, "BEST %d" % s.high_score(g.id), r.position.x + 16.0, r.position.y + 42.0, 1.6, Pal.YELLOW)
		var play := "%s 1 %s" % [ArcadeFont.TOKEN, ArcadeFont.PLAY]
		ArcadeFont.draw(ds, play, r.end.x - 16.0 - ArcadeFont.width(play, 2.2), r.position.y + 28.0, 2.2, g.look.glow)
	ds.pop()
	var foot := "%d OF %d MACHINES PORTED SO FAR" % [games.size(), GameRegistry.GAMES.size()]
	ArcadeFont.draw_centered(ds, foot, w / 2.0, size.y - insets.w - 22.0, 1.2, Pal.GRAY)
