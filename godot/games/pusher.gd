extends ArcadeGame

class Coin:
	var x: float
	var z: float
	var vx: float = 0
	var vz: float = 0
	var radius: float = 13
	var kind: int = 0
	var node: Node3D
	var falling: float = -1
	var drop: float = -1

var coins: Array[Coin] = []
var drops: Array[Coin] = []
var fallers: Array[Coin] = []
var coins_left: int = 25
var bonus_tickets: int = 0
var cooldown: float = 0
var since_drop: float = 0
var phase: float = 0
var shelf_front: float = 150
var shelf_speed: float = 0
var shelf: MeshInstance3D
var spill_times: Array[float] = []
var counter: Label3D
var won: int = 0
var status_timer: float = 0
var simulation_accumulator: float = 0

func _init() -> void:
	game_id = "pusher"
	title = "COIN PUSHER"
	instructions = "Tap across the deck to drop your 25 coins. Time the moving shelf to push coins over the FRONT edge. Side gutters lose coins. Gems and big coins score 50; ticket bundles give 10 tickets; stars trigger a coin shower!"
	duration = 50.0

func build_game() -> void:
	camera.position = Vector3(0, 6.4, 10.5)
	camera.look_at(Vector3(0, 0, 3.15))
	camera.fov = 44
	box(Vector3(0, -0.26, 3.1), Vector3(3.4, 0.5, 5.2), Color("5e293e"))
	box(Vector3(0, -0.025, 3.06), Vector3(3.0, 0.07, 5), Color("ba8752"))
	for x in [-1.57, 1.57]:
		box(Vector3(x, 0.13, 2.65), Vector3(0.12, 0.28, 4.4), Color("f7b743"))
		box(Vector3(x, -0.10, 5.32), Vector3(0.27, 0.15, 0.80), Color("131a31"))
	box(Vector3(0, -0.42, 5.93), Vector3(3.45, 0.1, 0.65), Color("eb9547"))
	box(Vector3(0, -0.23, 6.28), Vector3(3.45, 0.42, 0.10), Color("4d2642"))
	label3d("WIN TRAY", Vector3(0, 0.06, 6.18), 28, Color("ffdd86"))
	box(Vector3(0, 1.05, 0.5), Vector3(3.45, 2.2, 0.12), Color("853952"))
	label3d("COIN CASCADE", Vector3(0, 1.62, 0.85), 40, Color("fff0ab"))
	counter = label3d("25 COINS", Vector3(0, 1.03, 0.85), 34, Color("8de8ed"))
	for x in [-1.4, -0.93, -0.46, 0, 0.46, 0.93, 1.4]:
		sphere(Vector3(x, 2.13, 0.62), 0.065, Color("ffcf66"))
	shelf = box(Vector3(0, 0.14, 1.0), Vector3(2.99, 0.30, 1.0), Color("e2a144"))
	var row: int = 0
	var z: float = 552.2
	while z > 249:
		var x: float = 44 + (0 if row % 2 == 0 else 13.3)
		while x < 316:
			if rng.randf() > 0.06:
				_add_coin(0, x + rng.randf_range(-1.5, 1.5), z + rng.randf_range(-1.5, 1.5))
			x += 26.6
		z -= 23.0356
		row += 1
	for i in range(4):
		var old: Coin = coins[rng.randi_range(0, coins.size() - 1)]
		old.kind = i + 1
		old.radius = _radius(old.kind)
		old.node.queue_free()
		old.node = _visual(old.kind)
	for i in range(12):
		_solve()
	for i in range(coins.size() - 1, -1, -1):
		var c: Coin = coins[i]
		if c.z > 560 or (c.z > 492 and (c.x < 30 or c.x > 330)):
			c.node.queue_free()
			coins.remove_at(i)
		else:
			_place(c, 0.04)
	status = "25 coins • Tap across the deck"

func _radius(kind: int) -> float:
	return 20.0 if kind == 4 else (15.0 if kind == 2 else (12.0 if kind == 1 else 13.0))

func _visual(kind: int) -> Node3D:
	var root = Node3D.new()
	add_child(root)
	match kind:
		1:
			var gem = sphere(Vector3(0, 0.04, 0), 0.135, Color("4de7e7"), root)
			gem.scale = Vector3(0.9, 1.2, 0.9)
		2:
			box(Vector3.ZERO, Vector3(0.22, 0.12, 0.32), Color("ff8cb0"), 0, root)
			for z in [-0.10, 0.0, 0.1]:
				box(Vector3(0, 0.063, z), Vector3(0.14, 0.012, 0.018), Color("fff1dd"), 0, root)
		3:
			cylinder(Vector3.ZERO, 0.13, 0.05, Color("b497ff"), root)
			label3d("★", Vector3(0, 0.07, 0), 22, Color("fff6b0"), root)
		_:
			var radius: float = _radius(kind) / 100
			cylinder(Vector3.ZERO, radius, 0.036, Color("f2bd53"), root)
			cylinder(Vector3(0, 0.022, 0), radius * 0.76, 0.014, Color("ffdf79"), root)
	root.rotation.y = rng.randf_range(0, TAU)
	return root

func _add_coin(kind: int, x: float, z: float) -> Coin:
	var c = Coin.new()
	c.kind = kind
	c.x = x
	c.z = z
	c.radius = _radius(kind)
	c.node = _visual(kind)
	coins.append(c)
	return c

func _drop(x: float, kind: int = 0) -> void:
	if drops.size() >= 16:
		return
	var c = Coin.new()
	c.kind = kind
	c.radius = _radius(kind)
	c.x = x
	c.z = shelf_front + c.radius + rng.randf_range(4, 26)
	c.drop = 0
	c.node = _visual(kind)
	_place(c, 1.0)
	drops.append(c)

func pointer(action: String, pos: Vector2, _id: int) -> void:
	if action != "down" or not running or expired or coins_left <= 0 or cooldown > 0 or pos.y > 0.86:
		return
	cooldown = 0.18
	coins_left -= 1
	since_drop = 0
	var x: float = clampf(aim(pos).x * 100 + 180, 47, 313)
	_drop(x)
	if rng.randf() < 0.14:
		_drop(clampf(x + rng.randf_range(-40, 40), 54, 306), rng.randi_range(1, 4))
	beep(840, 0.045)

func step(dt: float) -> void:
	# Packed-deck collisions run at 60 Hz independently of the host's 120 Hz input tick.
	simulation_accumulator += dt
	while simulation_accumulator >= 1.0 / 60.0:
		simulation_accumulator -= 1.0 / 60.0
		_advance(1.0 / 60.0)

func _advance(dt: float) -> void:
	cooldown -= dt
	since_drop += dt
	status_timer -= dt
	for i in range(spill_times.size() - 1, -1, -1):
		spill_times[i] -= dt
		if spill_times[i] <= 0:
			spill_times.remove_at(i)
	phase += dt / 3.2 * TAU
	var front: float = 191 - cos(phase) * 41
	shelf_speed = (front - shelf_front) / maxf(dt, 0.001)
	shelf_front = front
	shelf.position.z = (front - 50) / 100
	for i in range(drops.size() - 1, -1, -1):
		var c: Coin = drops[i]
		c.drop += dt
		c.z = shelf_front + c.radius + 15
		_place(c, maxf(0.04, 1.0 - pow(c.drop / 0.32, 2)))
		if c.drop >= 0.32:
			c.vz = 120
			c.vx = rng.randf_range(-30, 30)
			coins.append(c)
			drops.remove_at(i)
	for c in coins:
		c.vx *= exp(-7 * dt)
		c.vz *= exp(-7 * dt)
		c.x += c.vx * dt
		c.z += c.vz * dt
	for i in range(3):
		_solve()
	for i in range(coins.size() - 1, -1, -1):
		var c: Coin = coins[i]
		var front_fall: bool = c.z > 560
		var side_fall: bool = c.z > 492 and (c.x < 30 or c.x > 330)
		if front_fall or side_fall:
			coins.remove_at(i)
			c.falling = 0
			fallers.append(c)
			if front_fall:
				_collect(c.kind)
			else:
				status = "Side gutter • Coin lost"
				status_timer = 0.8
		else:
			_place(c, 0.04)
	for i in range(fallers.size() - 1, -1, -1):
		var c: Coin = fallers[i]
		c.falling += dt
		c.z += 40 * dt
		_place(c, 0.04 - 3 * c.falling * c.falling)
		c.node.rotation.x += dt * 5
		if c.falling > 0.55:
			c.node.queue_free()
			fallers.remove_at(i)
	counter.text = "%d COINS" % coins_left
	if status_timer <= 0:
		status = "%d coins left • %d collected • +%d bonus tickets" % [coins_left, won, bonus_tickets]
	if coins_left == 0 and drops.is_empty() and since_drop > 3.5:
		finish()

func _solve() -> void:
	# A 40-unit grid bounds checks to neighboring coins on the densely packed deck.
	var grid: Dictionary = {}
	for i in range(coins.size()):
		var c: Coin = coins[i]
		if c.z < shelf_front + c.radius:
			c.z = shelf_front + c.radius
			c.vz = maxf(c.vz, shelf_speed)
		if c.z < 492:
			c.x = clampf(c.x, 30 + c.radius, 330 - c.radius)
		var key: int = floori(c.x / 40) + floori(c.z / 40) * 64
		if not grid.has(key):
			grid[key] = []
		grid[key].append(i)
	for i in range(coins.size()):
		var a: Coin = coins[i]
		var key: int = floori(a.x / 40) + floori(a.z / 40) * 64
		for offset in [-65, -64, -63, -1, 0, 1, 63, 64, 65]:
			if not grid.has(key + offset):
				continue
			var neighbors: Array = grid[key + offset]
			for j in neighbors:
				if j <= i:
					continue
				_resolve_pair(a, coins[j])

func _resolve_pair(a: Coin, b: Coin) -> void:
	var span: float = a.radius + b.radius
	var dx: float = b.x - a.x
	var dz: float = b.z - a.z
	if absf(dx) >= span or absf(dz) >= span:
		return
	var d2: float = dx * dx + dz * dz
	if d2 >= span * span or d2 < 0.001:
		return
	var distance: float = sqrt(d2)
	var nx: float = dx / distance
	var nz: float = dz / distance
	var overlap: float = (span - distance) * 0.5
	a.x -= nx * overlap
	a.z -= nz * overlap
	b.x += nx * overlap
	b.z += nz * overlap
	var velocity: float = (b.vx - a.vx) * nx + (b.vz - a.vz) * nz
	if velocity < 0:
		var impulse: float = -velocity * 0.525
		a.vx -= nx * impulse
		a.vz -= nz * impulse
		b.vx += nx * impulse
		b.vz += nz * impulse

func _place(c: Coin, y: float) -> void:
	c.node.position = Vector3((c.x - 180) / 100, y, c.z / 100)

func _collect(kind: int) -> void:
	won += 1
	spill_times.append(0.9)
	match kind:
		1, 4:
			award(50)
			status = "GEM +50!" if kind == 1 else "BIG COIN +50!"
		2:
			bonus_tickets += 10
			status = "+10 BONUS TICKETS!"
		3:
			award(10)
			status = "COIN SHOWER!"
			for i in range(6):
				_drop(60 + i * 48)
			since_drop = 0
		_:
			award(10)
			status = "+10 • Into the win tray!"
	if spill_times.size() >= 5:
		spill_times.clear()
		award(30)
		status = "AVALANCHE! +30 BONUS"
	status_timer = 1.2
	beep(900 if kind > 0 else 670, 0.045)

func is_settled() -> bool:
	return drops.is_empty() and fallers.is_empty()

func tickets_for_score() -> int:
	return 1 + int(score / 15) + bonus_tickets
