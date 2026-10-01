extends Node3D

const Hall = preload("res://scripts/hall.gd")
var hall: Node3D
var game: ArcadeGame
var layer: CanvasLayer
var hud: Control
var overlay: Control
var metrics: Label
var game_score: Label
var clock_label: Label
var status_label: Label
var prompt: Button
var countdown_label: Label
var countdown: float = -1
var selected: int = -1
var modal: bool = false
var joystick_id: int = -99
var look_id: int = -99
var joystick_origin: Vector2
var look_last: Vector2
var joystick_base: Panel
var joystick_tip: Panel
var ui_theme: Theme
var title_mode: bool = true
var snapshot_age: float = -1
var snapshot_path: String = ""
var game_touches: Dictionary = {}
var round_paid: bool = false

func _ready() -> void:
	get_tree().auto_accept_quit = false
	make_theme()
	hall = Hall.new()
	add_child(hall)
	hall.near_changed.connect(update_prompt)
	layer = CanvasLayer.new()
	layer.layer = 10
	add_child(layer)
	hud = Control.new()
	hud.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	hud.mouse_filter = Control.MOUSE_FILTER_IGNORE
	hud.theme = ui_theme
	layer.add_child(hud)
	overlay = Control.new()
	overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	overlay.theme = ui_theme
	overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(overlay)
	build_hud()
	show_title()
	for arg in OS.get_cmdline_user_args():
		if arg.begins_with("--play="):
			var id = arg.trim_prefix("--play=")
			for i in ArcadeCatalog.GAMES.size():
				if ArcadeCatalog.GAMES[i][0] == id:
					enter_hall()
					select_game(i)
		if arg.begins_with("--screenshot="):
			snapshot_path = arg.trim_prefix("--screenshot=")
			snapshot_age = 2
		if arg == "--hall":
			enter_hall()

func make_theme() -> void:
	ui_theme = Theme.new()
	ui_theme.default_font_size = 21
	ui_theme.set_color("font_color","Label",Color("eef0ff"))
	ui_theme.set_color("font_color","Button",Color("f4f0ff"))
	ui_theme.set_color("font_hover_color","Button",Color("fff3bf"))
	ui_theme.set_stylebox("normal","Button",style(Color("252640"),Color("504a75"),16))
	ui_theme.set_stylebox("hover","Button",style(Color("373254"),Color("a298e2"),16))
	ui_theme.set_stylebox("pressed","Button",style(Color("48416a"),Color("81e9dc"),16))
	ui_theme.set_stylebox("disabled","Button",style(Color("17192c"),Color("25263a"),16))
	ui_theme.set_stylebox("focus","Button",style(Color(0,0,0,0),Color("81e9dc"),16))
	ui_theme.set_constant("separation","VBoxContainer",12)
	ui_theme.set_constant("separation","HBoxContainer",10)

func style(fill: Color, border: Color, radius: int) -> StyleBoxFlat:
	var box = StyleBoxFlat.new()
	box.bg_color = fill
	box.border_color = border
	box.set_border_width_all(1)
	box.set_corner_radius_all(radius)
	box.content_margin_left = 15
	box.content_margin_right = 15
	box.content_margin_top = 11
	box.content_margin_bottom = 11
	return box

func label(words: String, font_size: int = 21, color: Color = Color("eef0ff")) -> Label:
	var node = Label.new()
	node.text = words
	node.add_theme_font_size_override("font_size",font_size)
	node.add_theme_color_override("font_color",color)
	node.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return node

func button(words: String, action: Callable, color: Color = Color("252640")) -> Button:
	var node = Button.new()
	node.text = words
	node.custom_minimum_size.y = 54
	node.add_theme_font_size_override("font_size",19)
	node.add_theme_stylebox_override("normal",style(color,Color("696186"),14))
	node.pressed.connect(action)
	return node

func place(control: Control, left: float, top: float, right: float, bottom: float) -> void:
	control.anchor_left = left
	control.anchor_top = top
	control.anchor_right = right
	control.anchor_bottom = bottom

func clear_children(node: Node) -> void:
	for child in node.get_children():
		# A Button may still be dispatching its pressed callback. Keep it in the
		# tree through the current input event, but remove it visually at once.
		if child is CanvasItem:
			child.hide()
		child.queue_free()

func build_hud() -> void:
	clear_children(hud)
	var top = VBoxContainer.new()
	hud.add_child(top)
	place(top,0,0,1,0)
	top.offset_left = 20
	top.offset_right = -20
	top.offset_top = 18 if game != null else 32
	if game != null:
		top.add_theme_constant_override("separation",8)
	var row = HBoxContainer.new()
	top.add_child(row)
	var brand = label("POCKET ARCADE",19,Color("a5f3e4"))
	brand.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(brand)
	var menu_button = button("II" if game != null else "MENU",pause_game if game != null else show_arcade_menu)
	if game != null:
		menu_button.custom_minimum_size.y = 36
		menu_button.add_theme_font_size_override("font_size",18)
		for state in ["normal", "hover", "pressed", "disabled", "focus"]:
			var compact = menu_button.get_theme_stylebox(state).duplicate()
			compact.content_margin_top = 5
			compact.content_margin_bottom = 5
			menu_button.add_theme_stylebox_override(state,compact)
	row.add_child(menu_button)
	metrics = label("",19,Color("ffda89"))
	top.add_child(metrics)
	metrics.visible = game == null
	if game == null:
		var tools_row = HBoxContainer.new()
		top.add_child(tools_row)
		tools_row.add_child(button("VIEW",func(): hall.toggle_camera()))
		tools_row.add_child(button("PRIZES",func(): show_shop("hat")))
		tools_row.add_child(button("GAMES",show_game_list))
		prompt = button("",use_nearby,Color("49337c"))
		hud.add_child(prompt)
		place(prompt,0.08,0.73,0.92,0.73)
		prompt.custom_minimum_size.y = 64
		prompt.visible = false
		var help = label("LEFT THUMB TO WALK  ·  RIGHT TO LOOK",15,Color("a6a5c4"))
		hud.add_child(help)
		place(help,0,0.93,1,0.97)
		help.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		joystick_base = Panel.new()
		joystick_base.add_theme_stylebox_override("panel",style(Color(0.2,0.2,0.35,0.5),Color(0.7,0.8,1,0.5),60))
		joystick_base.size = Vector2(120,120)
		joystick_base.mouse_filter = Control.MOUSE_FILTER_IGNORE
		hud.add_child(joystick_base)
		joystick_base.hide()
		joystick_tip = Panel.new()
		joystick_tip.size = Vector2(48,48)
		joystick_tip.add_theme_stylebox_override("panel",style(Color("95e8dc"),Color("e0fff6"),24))
		joystick_tip.mouse_filter = Control.MOUSE_FILTER_IGNORE
		joystick_base.add_child(joystick_tip)
		update_prompt(hall.nearby)
	else:
		var score_row = HBoxContainer.new()
		top.add_child(score_row)
		game_score = label("0",32)
		game_score.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		score_row.add_child(game_score)
		clock_label = label("60s",28,Color("ffda89"))
		score_row.add_child(clock_label)
		status_label = label("",20,Color("d2d8fa"))
		status_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		status_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		hud.add_child(status_label)
		place(status_label,0.06,0.89,0.94,0.98)

func show_panel(heading: String, subtitle: String = "") -> VBoxContainer:
	clear_children(overlay)
	modal = true
	hall.walk = Vector2.ZERO
	joystick_id = -99
	look_id = -99
	game_touches.clear()
	if is_instance_valid(joystick_base):
		joystick_base.hide()
	var shade = ColorRect.new()
	shade.color = Color(0.025,0.023,0.08,0.78)
	overlay.add_child(shade)
	shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var panel = PanelContainer.new()
	panel.add_theme_stylebox_override("panel",style(Color("15182e"),Color("554779"),24))
	overlay.add_child(panel)
	place(panel,0.055,0.17,0.945,0.86)
	var column = VBoxContainer.new()
	column.add_theme_constant_override("separation",14)
	panel.add_child(column)
	var heading_label = label(heading,30,Color("f5e9ff"))
	heading_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(heading_label)
	if not subtitle.is_empty():
		var sub = label(subtitle,18,Color("aaaecf"))
		sub.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		column.add_child(sub)
	return column

func spacer(column: VBoxContainer) -> void:
	var node = Control.new()
	node.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(node)

func close_panel() -> void:
	clear_children(overlay)
	modal = false

func show_title() -> void:
	title_mode = true
	hud.hide()
	var col = show_panel("YOUR OWN\nLITTLE ARCADE", "Eleven machines. A pocket full of possibilities.")
	col.add_child(label("POCKET ARCADE",24,Color("83e9dc")))
	var body_label = label("Walk the neon floor, master the machines, and turn your tickets into something to take home.",22)
	body_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	col.add_child(body_label)
	spacer(col)
	col.add_child(label("20 starting tokens  ·  10 free each day",17,Color("ffda89")))
	col.add_child(button("LET'S PLAY",enter_hall,Color("6b46ad")))
	col.add_child(label("GODOT EDITION  /  2.0.0",14,Color("9195b4")))

func enter_hall() -> void:
	title_mode = false
	close_panel()
	hud.show()
	hall.camera.current = true

func show_arcade_menu() -> void:
	var col = show_panel("TAKE A BREATHER", "Your arcade, your pace.")
	col.add_child(button("BACK TO THE FLOOR",close_panel,Color("49337c")))
	col.add_child(button("PICK A GAME",show_game_list))
	col.add_child(button("TOKENS",show_tokens))
	col.add_child(button("COLLECTION & RECORDS",show_profile))
	col.add_child(button("SOUND: " + ("OFF" if SaveStore.data.muted else "ON"),func():
		SaveStore.data.muted = not SaveStore.data.muted
		SaveStore.save()
		show_arcade_menu()))
	spacer(col)
	col.add_child(button("TITLE SCREEN",show_title))

func scroll_column(parent: VBoxContainer) -> VBoxContainer:
	var scroll = ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	parent.add_child(scroll)
	var content = VBoxContainer.new()
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(content)
	return content

func show_game_list() -> void:
	var col = show_panel("PICK YOUR MACHINE", "Each round costs 1 token. Your best scores are saved.")
	var list = scroll_column(col)
	for i in ArcadeCatalog.GAMES.size():
		var row = ArcadeCatalog.GAMES[i]
		var node = button(row[1] + "   ›",func(): select_game(i))
		node.add_theme_color_override("font_color",Color(row[3]))
		list.add_child(node)
	col.add_child(button("BACK TO THE FLOOR",close_panel))

func update_prompt(index: int) -> void:
	if not is_instance_valid(prompt) or game != null:
		return
	prompt.visible = index >= 0
	if index >= 0 and index < 11:
		prompt.text = "PLAY " + ArcadeCatalog.GAMES[index][1] + "  ·  1 TOKEN"
	elif index == 11:
		prompt.text = "VISIT THE PRIZE COUNTER"
	elif index == 12:
		prompt.text = "GET TOKENS"

func use_nearby() -> void:
	if hall.nearby < 11 and hall.nearby >= 0:
		select_game(hall.nearby)
	elif hall.nearby == 11:
		show_shop("hat")
	elif hall.nearby == 12:
		show_tokens()

func select_game(index: int) -> void:
	selected = index
	var script = load("res://games/" + ArcadeCatalog.GAMES[index][0] + ".gd")
	var preview: ArcadeGame = script.new()
	var col = show_panel(preview.title,preview.instructions)
	col.add_child(label(ArcadeCatalog.GAMES[index][2],22,Color(ArcadeCatalog.GAMES[index][3])))
	spacer(col)
	col.add_child(label("BEST  " + str(SaveStore.data.high_scores.get(preview.game_id,0)),22,Color("ffda89")))
	col.add_child(label("1 token  ·  " + str(int(preview.duration)) + " second round",18))
	col.add_child(button("INSERT TOKEN & PLAY" if SaveStore.data.tokens > 0 else "GET MORE TOKENS",start_game if SaveStore.data.tokens > 0 else show_tokens,Color("64419e")))
	col.add_child(button("BACK",show_game_list))
	preview.free()

func start_game() -> void:
	if is_instance_valid(game) or selected < 0 or selected >= ArcadeCatalog.GAMES.size():
		return
	if not SaveStore.spend():
		show_tokens()
		return
	close_panel()
	hall.walk = Vector2.ZERO
	hall.hide()
	hall.process_mode = Node.PROCESS_MODE_DISABLED
	var script = load("res://games/" + ArcadeCatalog.GAMES[selected][0] + ".gd")
	game = script.new()
	round_paid = false
	add_child(game)
	game.round_finished.connect(round_complete)
	build_hud()
	countdown = 3.1
	countdown_label = label("3",100,Color("fff0a9"))
	overlay.add_child(countdown_label)
	place(countdown_label,0,0.35,1,0.6)
	countdown_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER

func round_complete() -> void:
	if not is_instance_valid(game) or not game.finished or round_paid:
		return
	round_paid = true
	countdown = -1
	var tickets = maxi(1,game.tickets_for_score())
	var record = SaveStore.reward(game.game_id,game.score,tickets,game.prizes)
	var col = show_panel("NEW HIGH SCORE!" if record else "NICE PLAYING",game.title)
	col.add_child(label(str(game.score),64,Color("e7e3ff")))
	col.add_child(label("+ " + str(tickets) + " TICKETS",32,Color("ffda89")))
	if not game.prizes.is_empty():
		col.add_child(label("A new plush for your collection!",19,Color("83e9dc")))
	spacer(col)
	col.add_child(button("PLAY AGAIN  ·  1 TOKEN",func():
		leave_game()
		select_game(selected),Color("64419e")))
	col.add_child(button("BACK TO THE ARCADE",leave_game))

func leave_game() -> void:
	countdown = -1
	game_touches.clear()
	if is_instance_valid(game):
		game.cancel_input()
		remove_child(game)
		game.queue_free()
	game = null
	hall.show()
	hall.process_mode = Node.PROCESS_MODE_INHERIT
	hall.camera.current = true
	close_panel()
	build_hud()

func pause_game() -> void:
	if game == null or game.finished:
		return
	game.running = false
	game.cancel_input()
	var col = show_panel("PAUSED",game.title)
	spacer(col)
	col.add_child(button("KEEP PLAYING",func():
		close_panel()
		if countdown < 0:
			game.running = true
		else:
			countdown_label = label("3",100,Color("fff0a9"))
			overlay.add_child(countdown_label)
			place(countdown_label,0,0.35,1,0.6)
			countdown_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER,Color("64419e")))
	col.add_child(button("END ROUND & COLLECT TICKETS",func(): game.finish()))

func show_tokens() -> void:
	var col = show_panel("TOKEN KIOSK", "10 free tokens arrive each new day. Trade tickets, or grab a spare when you're out.")
	col.add_child(label(str(SaveStore.data.tokens) + " TOKENS",38,Color("ffda89")))
	col.add_child(label(str(SaveStore.data.tickets) + " tickets in your pocket",21))
	spacer(col)
	var exchange = button("40 TICKETS → 1 TOKEN",func():
		SaveStore.exchange()
		show_tokens())
	exchange.disabled = SaveStore.data.tickets < 40
	col.add_child(exchange)
	var wait = maxi(0,int((int(SaveStore.data.spare_token_at)-Time.get_unix_time_from_system()*1000)/1000))
	var spare_button = button("FREE SPARE TOKEN" if wait == 0 else "SPARE READY IN " + str(wait) + "s",func():
		SaveStore.spare()
		show_tokens())
	spare_button.disabled = SaveStore.data.tokens > 0 or wait > 0
	col.add_child(spare_button)
	col.add_child(button("BACK TO THE FLOOR",close_panel))

func show_shop(kind: String) -> void:
	var col = show_panel("THE PRIZE COUNTER", str(SaveStore.data.tickets) + " tickets to spend. Owned clothing can be equipped here.")
	var tabs = HBoxContainer.new()
	col.add_child(tabs)
	for pair in [["hat","HATS"],["outfit","OUTFITS"],["decor","DECOR"]]:
		var tab = button(pair[1],func(): show_shop(pair[0]))
		tab.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		tabs.add_child(tab)
	var list = scroll_column(col)
	for item in ArcadeCatalog.items(kind):
		var owned = item.id in SaveStore.data.owned
		var equipped = item.id == SaveStore.data.hat or item.id == SaveStore.data.outfit
		var suffix = "  ✓" if equipped else "  OWNED" if owned else "  ·  " + str(item.price)
		var buy_button = button(item.name + suffix,func():
			if SaveStore.buy(item):
				hall.refresh_avatar()
				hall.refresh_decor()
			show_shop(kind))
		buy_button.disabled = not owned and SaveStore.data.tickets < item.price
		list.add_child(buy_button)
	col.add_child(button("BACK TO THE FLOOR",close_panel))

func show_profile() -> void:
	var col = show_panel("YOUR ARCADE STORY",str(SaveStore.data.total_plays) + " games played  ·  " + str(SaveStore.data.collection.size()) + " / 11 plushies found")
	var list = scroll_column(col)
	list.add_child(label("HIGH SCORES",21,Color("ffda89")))
	for row in ArcadeCatalog.GAMES:
		list.add_child(label(row[1] + "   " + str(SaveStore.data.high_scores.get(row[0],0)),18))
	list.add_child(label("PLUSH COLLECTION",21,Color("ffda89")))
	for i in ArcadeCatalog.PLUSH_IDS.size():
		var count = int(SaveStore.data.collection.get("plush_"+ArcadeCatalog.PLUSH_IDS[i],0))
		list.add_child(label(ArcadeCatalog.PLUSH_NAMES[i]+"   ×"+str(count),18,Color("83e9dc") if count > 0 else Color("858ba9")))
	col.add_child(button("BACK",show_arcade_menu))

func _process(dt: float) -> void:
	if is_instance_valid(metrics):
		metrics.text = str(int(SaveStore.data.tokens)) + " TOKENS    /    " + str(int(SaveStore.data.tickets)) + " TICKETS"
	if game != null:
		game_score.text = "SCORE  " + str(game.score)
		clock_label.text = str(ceili(game.time_left)) + "s"
		status_label.text = game.status
		if countdown >= 0 and not modal:
			countdown -= dt
			countdown_label.text = str(ceili(countdown)) if countdown > 0.5 else "GO!"
			if countdown < 0:
				clear_children(overlay)
				game.running = true
	elif not modal:
		var keys = Vector2(float(Input.is_physical_key_pressed(KEY_D) or Input.is_physical_key_pressed(KEY_RIGHT))-float(Input.is_physical_key_pressed(KEY_A) or Input.is_physical_key_pressed(KEY_LEFT)),float(Input.is_physical_key_pressed(KEY_S) or Input.is_physical_key_pressed(KEY_DOWN))-float(Input.is_physical_key_pressed(KEY_W) or Input.is_physical_key_pressed(KEY_UP)))
		if joystick_id == -99:
			hall.walk = keys.normalized()
	if snapshot_age >= 0:
		snapshot_age -= dt
		if snapshot_age < 0:
			await RenderingServer.frame_post_draw
			get_viewport().get_texture().get_image().save_png(snapshot_path)
			get_tree().quit()

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		back()
		return
	if modal:
		return
	if event is InputEventKey and event.pressed and event.keycode == KEY_E and game == null:
		use_nearby()
	if event is InputEventScreenTouch:
		pointer_event("down" if event.pressed else "up",event.position,event.index)
	elif event is InputEventScreenDrag:
		pointer_event("move",event.position,event.index)

# A release may land over a HUD button after starting on the playfield.
# Always clear it, even when GUI handling consumes the rest of the touch.
func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch and event.canceled:
		# Android gesture cancellation must not release a charged cast or shot.
		if is_instance_valid(game):
			game.cancel_input()
		game_touches.clear()
		joystick_id = -99
		look_id = -99
		if is_instance_valid(hall):
			hall.walk = Vector2.ZERO
		if is_instance_valid(joystick_base):
			joystick_base.hide()
		get_viewport().set_input_as_handled()
		return
	if event is InputEventScreenTouch and not event.pressed and not modal:
		if event.index in game_touches or event.index == joystick_id or event.index == look_id:
			pointer_event("up",event.position,event.index)
			game_touches.erase(event.index)
			get_viewport().set_input_as_handled()

func pointer_event(action: String, position_2d: Vector2, id: int) -> void:
	var viewport_size = get_viewport().get_visible_rect().size
	var point = position_2d / viewport_size
	if game != null:
		if game.running and not game.finished:
			if action == "down":
				game_touches[id] = true
			game.pointer(action,point,id)
		return
	if action == "down":
		if hall.first_person and point.x > 0.55:
			look_id = id
			look_last = position_2d
		elif joystick_id == -99:
			joystick_id = id
			joystick_origin = position_2d
			joystick_base.position = position_2d-Vector2(60,60)
			joystick_tip.position = Vector2(36,36)
			joystick_base.show()
	elif action == "move":
		if id == joystick_id:
			var delta = (position_2d-joystick_origin)/60.0
			hall.walk = delta.limit_length()
			joystick_tip.position = Vector2(36,36)+delta.limit_length()*40
		elif id == look_id:
			var delta = (position_2d-look_last)/viewport_size
			hall.yaw -= delta.x*4
			hall.pitch = clampf(hall.pitch-delta.y*3,-1.15,0.8)
			look_last = position_2d
	elif action == "up":
		if id == joystick_id:
			joystick_id = -99
			hall.walk = Vector2.ZERO
			joystick_base.hide()
		if id == look_id:
			look_id = -99

func back() -> void:
	if game != null:
		if game.finished:
			leave_game()
		else:
			pause_game()
	elif title_mode:
		get_tree().quit()
	elif modal:
		close_panel()
	else:
		show_arcade_menu()

func _notification(what: int) -> void:
	if what == NOTIFICATION_APPLICATION_RESUMED or what == NOTIFICATION_APPLICATION_FOCUS_IN:
		SaveStore.apply_daily()
		SaveStore.save()
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		back()
	if what == NOTIFICATION_APPLICATION_PAUSED or what == NOTIFICATION_APPLICATION_FOCUS_OUT:
		if game != null and not game.finished:
			pause_game()
		if hall != null:
			hall.walk = Vector2.ZERO
		SaveStore.save()
	if what == NOTIFICATION_WM_CLOSE_REQUEST:
		SaveStore.save()
		get_tree().quit()
