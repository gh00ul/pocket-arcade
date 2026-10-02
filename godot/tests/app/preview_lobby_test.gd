extends PaTest
## Godot-only, preview builds: the lobby that stands in for the title screen and the hall lists the
## machines, takes a token to open one in the game host (refunded if it's left from the intro card),
## and comes back when the machine is left.


class TestGame:
	extends BaseMiniGame

	func _init() -> void:
		id = "lobbytest"
		title = "LOBBY TEST"
		marquee = "LOBBY"
		round_seconds = 3.0
		look = MiniGame.CabinetLook.new(Pal.PURPLE, Pal.YELLOW, Pal.PINK)

	func step(_dt: float) -> void:
		pass

	func on_touch(_type: int, _pid: int, _x: float, _y: float, _t: int) -> void:
		pass

	func cancel_input() -> void:
		pass

	func tickets_for(_s: int) -> int:
		return 0


var _main: Main
var _dir := "user://test_tmp/preview_lobby"


func before_each() -> void:
	Main.user_dir = _dir
	Main.start_audio = false
	Main.configure_display = false
	DirAccess.make_dir_recursive_absolute(_dir)


func after_each() -> void:
	if _main != null and is_instance_valid(_main):
		_main.queue_free()
	_main = null
	Main.user_dir = "user://"
	Main.start_audio = true
	Main.configure_display = true
	for f in DirAccess.get_files_at(_dir):
		DirAccess.remove_absolute(_dir.path_join(f))


func _lobby() -> PreviewLobby:
	_main = (load("res://main.tscn") as PackedScene).instantiate() as Main
	host.add_child(_main)
	await frames(2)
	var lobby := _main.app_root.get_child(0) as PreviewLobby
	assert_not_null(lobby, "the app opens on the lobby until the hall is ported")
	lobby.games = [TestGame.new()]
	lobby.queue_redraw()
	await frames(1)
	return lobby


func test_a_machine_costs_a_token_and_leaving_comes_back() -> void:
	var lobby: PreviewLobby = await _lobby()
	var repo := _main.services.repo
	var before := repo.state().tokens
	assert_eq(1, lobby.rows.size())
	var r: Rect2 = lobby.rows[0][0]
	var down := InputEventScreenTouch.new()
	down.position = r.get_center()
	down.pressed = true
	lobby._gui_input(down)
	var up := InputEventScreenTouch.new()
	up.position = r.get_center()
	lobby._gui_input(up)
	assert_not_null(lobby.host, "the tap opened the machine")
	assert_eq(before - 1, repo.state().tokens)
	assert_false(lobby.visible)
	assert_true(_main.back_handler.is_valid(), "Back goes to the machine")
	# Back on the intro card leaves the machine and refunds its token.
	tree.root.go_back_requested.emit()
	await frames(2)
	assert_null(lobby.host)
	assert_true(lobby.visible)
	assert_eq(before, repo.state().tokens)
	assert_false(_main.back_handler.is_valid(), "Back in the lobby sends the app to the background")


func test_out_of_tokens_opens_nothing() -> void:
	var lobby: PreviewLobby = await _lobby()
	var repo := _main.services.repo
	while repo.state().tokens > 0:
		repo.spend_token()
	# The spare token is only for the completely broke after a cooldown; use it up first if it's there.
	if repo.claim_spare_token():
		repo.spend_token()
	lobby.open(0)
	assert_null(lobby.host)
	assert_eq(0, repo.state().tokens)
	assert_false(lobby.gate.claimed, "the next tap gets through")
