extends ArcadeGame

signal near_changed(index: int)
var body: CharacterBody3D
var avatar: Node3D
var spots: Array[Vector3] = []
var spot_games: Array[int] = []
var cabinets: Array[Node3D] = []
var nearby: int = -1
var walk: Vector2 = Vector2.ZERO
var yaw: float = 0
var pitch: float = -0.08
var first_person: bool = false
var npcs: Array = []
var decor_root: Node3D
var age: float = 0
var overhead_hidden: Array[Node3D] = []

func build_game() -> void:
	first_person = SaveStore.data.first_person
	var floor_node = box(Vector3(0,-0.16,0), Vector3(22,0.3,43), Color("181331"))
	var shader = Shader.new()
	shader.code = "shader_type spatial; varying vec3 p; void vertex(){p=VERTEX;} void fragment(){vec2 q=p.xz; vec2 c=floor(q*3.0); float h=fract(sin(dot(c,vec2(127.1,311.7)))*43758.5453); vec2 f=fract(q*3.0)-0.5; float fleck=step(abs(f.x)+abs(f.y),0.12)*step(0.58,h); vec3 col=mix(vec3(0.035,0.018,0.08),mix(vec3(0.1,0.85,0.8),vec3(0.9,0.15,0.6),h),fleck*0.75); ALBEDO=col; EMISSION=col*0.32; ROUGHNESS=0.9;}"
	var carpet = ShaderMaterial.new()
	carpet.shader = shader
	floor_node.material_override = carpet
	solid(Vector3(0,-0.25,0),Vector3(22,0.3,43))
	for x in [-11.0,11.0]:
		box(Vector3(x,3.2,0),Vector3(0.3,6.4,43),Color("191a33"))
		solid(Vector3(x,3,0),Vector3(0.4,6,43))
		box(Vector3(x * 0.985,0.22,0),Vector3(0.08,0.08,43),Color("8862e8"),1)
		box(Vector3(x * 0.985,4.6,0),Vector3(0.08,0.08,43),Color("fb74c9"),1)
	for z in [-21.5,21.5]:
		var wall = box(Vector3(0,3,z),Vector3(22,6,0.3),Color("171a30"))
		if z > 0:
			overhead_hidden.append(wall)
		solid(Vector3(0,3,z),Vector3(22,6,0.3))
	# A ceiling remains visible at kid's-eye height but never obscures the overhead view.
	var ceiling = box(Vector3(0,7,0),Vector3(22,0.12,43),Color("141827"))
	ceiling.name = "Ceiling"
	for z in range(-18,21,6):
		overhead_hidden.append(box(Vector3(0,5.7,z),Vector3(21.7,0.1,0.1),Color("3b3d57")))
		for x in [-7,0,7]:
			overhead_hidden.append(box(Vector3(x,5.65,z),Vector3(2.8,0.04,0.16),Color("88bbde"),0.7))
	label3d("POCKET ARCADE",Vector3(0,4.5,-20.8),72,Color("ff8acb"))
	label3d("PLAY • WIN • REPEAT",Vector3(0,3.5,-20.8),30,Color("7beee0"))
	box(Vector3(0,0.85,-19),Vector3(5,1.7,1.3),Color("55447c"))
	solid(Vector3(0,0.85,-19),Vector3(5,1.7,1.3))
	label3d("PRIZE COUNTER",Vector3(0,2.1,-18.4),38,Color("ffd675"))
	for row in 2:
		for n in 9:
			var c = Color.from_hsv(float(n)/9,0.5,1)
			sphere(Vector3(-3.6+n*0.9,2.6+row*0.6,-20.7),0.23,c)
			box(Vector3(0,2.25+row*0.6,-20.6),Vector3(8.4,0.1,0.55),Color("5a4980"))
	# Two clear banks face their cross-aisles. Every original game has an
	# inner cabinet; duplicate cabinets sit farther out toward the walls.
	var locations = [Vector3(-3.8,0,-16),Vector3(-3.8,0,-10),Vector3(-3.8,0,-4),Vector3(3.8,0,-10),Vector3(3.8,0,-16),Vector3(3.8,0,-4),Vector3(-3.8,0,2),Vector3(-3.8,0,8),Vector3(3.8,0,2),Vector3(3.8,0,14),Vector3(3.8,0,8)]
	for i in ArcadeCatalog.GAMES.size():
		var pos: Vector3 = locations[i]
		cabinet(i,pos)
		if i in [0,1,2,3,4,6,8,10]:
			cabinet(i,pos + Vector3(-3.2 if pos.x < 0 else 3.2,0,0))
	spots.append(Vector3(0,0,-17.2))
	spot_games.append(11)
	box(Vector3(3.3,1.15,18.7),Vector3(1.3,2.3,0.9),Color("936137"))
	solid(Vector3(3.3,1.15,18.7),Vector3(1.3,2.3,0.9))
	label3d("TOKENS",Vector3(3.3,2.5,19.1),28,Color("ffd675"))
	spots.append(Vector3(3.3,0,20.1))
	spot_games.append(12)
	cafe()
	label3d("WELCOME IN",Vector3(0,3.1,21),36,Color("75ddd1"))
	box(Vector3(0,1.4,21.1),Vector3(3.7,2.8,0.08),Color(0.35,0.6,0.8,0.3))
	body = CharacterBody3D.new()
	add_child(body)
	body.position = Vector3(0,0,14)
	var collider = CollisionShape3D.new()
	var shape = CapsuleShape3D.new()
	shape.radius = 0.27
	shape.height = 1.35
	collider.shape = shape
	collider.position.y = 0.7
	body.add_child(collider)
	refresh_avatar()
	for n in 8:
		var npc = figure(Color.from_hsv(float(n)/8,0.55,0.95))
		npc.position = Vector3(-1.5 if n%2 else 1.5,0,12-n*4)
		npcs.append({"node":npc,"target":Vector3(npc.position.x,0,-16 if n%2 else 16),"wait":0.0})
	decor_root = Node3D.new()
	add_child(decor_root)
	refresh_decor()
	update_camera(1.0)

func solid(pos: Vector3, dimensions: Vector3) -> void:
	var obstacle = StaticBody3D.new()
	add_child(obstacle)
	obstacle.position = pos
	var collider = CollisionShape3D.new()
	var shape = BoxShape3D.new()
	shape.size = dimensions
	collider.shape = shape
	obstacle.add_child(collider)

func cabinet(index: int, pos: Vector3) -> void:
	var row = ArcadeCatalog.GAMES[index]
	var color = Color(row[3])
	var root = Node3D.new()
	add_child(root)
	root.position = pos
	cabinets.append(root)
	var wide = 1.8 if index in [1,4,5,6,9,10] else 1.5
	var depth = 2.5 if index in [1,4,5,9] else 1.5
	spots.append(pos + Vector3(0,0,depth * 0.5 + 0.85))
	spot_games.append(index)
	solid(pos + Vector3(0,1,0),Vector3(wide,2,depth))
	box(Vector3(0,0.5,0),Vector3(wide,1,depth),color.darkened(0.6),0,root)
	box(Vector3(0,0.06,0),Vector3(wide+0.1,0.12,depth+0.1),color,0.8,root)
	if index in [1,4,5,9,10]:
		box(Vector3(0,1.04,0.2),Vector3(wide-0.15,0.1,depth),Color("193e60"),0,root)
		for x in [-1,1]:
			box(Vector3(x*wide*0.5,1.2,0.1),Vector3(0.08,0.3,depth),color,0.4,root)
	else:
		box(Vector3(0,1.65,-0.15),Vector3(wide,1.3,1.2),color.darkened(0.4),0,root)
		box(Vector3(0,1.75,0.47),Vector3(wide-0.22,0.83,0.04),Color("13223c"),0,root)
		box(Vector3(0,1.05,0.6),Vector3(wide+0.05,0.13,0.65),color,0,root)
		cylinder(Vector3(-0.32,1.18,0.7),0.055,0.2,Color("bcd9ea"),root)
		sphere(Vector3(-0.32,1.3,0.7),0.095,color,root)
		for n in 3:
			cylinder(Vector3(0.1+n*0.18,1.15,0.73),0.06,0.07,Color("ffe49c"),root)
	if index == 0:
		for n in 6:
			sphere(Vector3(-0.5+(n%3)*0.5,1.5,-0.1+int(n/3.0)*0.4),0.22,Color.from_hsv(n/6.0,0.5,1),root)
		box(Vector3(0,1.75,0.5),Vector3(1.25,0.85,0.025),Color(0.5,0.9,1,0.13),0,root)
	if index == 4:
		box(Vector3(0,2,-0.9),Vector3(1.5,1.2,0.1),Color("e6edf1"),0,root)
		var rim = TorusMesh.new()
		rim.inner_radius = 0.3
		rim.outer_radius = 0.36
		mesh_node(rim,Vector3(0,1.8,-0.45),Color("ff985a"),root)
	if index == 6:
		box(Vector3(0,0.7,1),Vector3(0.8,0.3,0.8),Color("774390"),0,root)
		box(Vector3(0,1,1.3),Vector3(0.8,0.7,0.15),Color("b983f7"),0,root)
	if index == 10:
		cylinder(Vector3(0,1.1,0),0.75,0.12,Color("63d4e4"),root)
	box(Vector3(0,2.65,-depth*0.4),Vector3(wide+0.1,0.5,0.25),color.darkened(0.6),0,root)
	label3d(row[1],Vector3(0,2.68,-depth*0.4+0.16),22,color,root)
	label3d("HI " + str(SaveStore.data.high_scores.get(row[0],0)),Vector3(0,1.85,0.52),16,Color("dbe4ff"),root)
	box(Vector3(0,0.6,depth*0.5+0.02),Vector3(0.34,0.35,0.035),Color("202035"),0,root)
	box(Vector3(0,0.66,depth*0.5+0.045),Vector3(0.1,0.035,0.04),Color("ffc56b"),1,root)
	for n in 7:
		sphere(Vector3(-wide*0.45+n*wide*0.15,2.93,-depth*0.4),0.035,color,root)

func figure(shirt: Color) -> Node3D:
	var root = Node3D.new()
	add_child(root)
	box(Vector3(0,0.75,0),Vector3(0.48,0.5,0.27),shirt,0,root)
	sphere(Vector3(0,1.25,0),0.26,Color("f3b889"),root)
	box(Vector3(0,1.44,-0.015),Vector3(0.47,0.14,0.42),Color("48304c"),0,root)
	for side in [-1,1]:
		box(Vector3(side*0.14,0.29,0),Vector3(0.19,0.45,0.2),Color("444b82"),0,root)
		box(Vector3(side*0.15,0.08,0.06),Vector3(0.23,0.13,0.32),Color("eef0ff"),0,root)
		box(Vector3(side*0.34,0.73,0),Vector3(0.16,0.48,0.16),Color("edb188"),0,root)
		sphere(Vector3(side*0.095,1.28,0.23),0.033,Color("22223e"),root)
	return root

func refresh_avatar() -> void:
	if is_instance_valid(avatar):
		avatar.free()
	avatar = figure(ArcadeCatalog.shirt())
	avatar.reparent(body)
	avatar.position = Vector3.ZERO
	var hat = str(SaveStore.data.hat)
	# Each owned hat has its own silhouette, visible in both the hall and
	# wardrobe previews. The ten outfit colours come from ArcadeCatalog.
	match hat:
		"hat_cap", "hat_propeller":
			var cap = sphere(Vector3(0,1.5,0),0.29,Color("57cfdc"),avatar)
			cap.scale.y = 0.5
			var brim = cylinder(Vector3(0,1.48,0.22),0.25,0.045,Color("4ba5d6"),avatar)
			brim.scale.z = 1.35
			if hat == "hat_propeller":
				cylinder(Vector3(0,1.73,0),0.022,0.20,Color("ffdb78"),avatar)
				var rotor = Node3D.new()
				rotor.name = "Propeller"
				avatar.add_child(rotor)
				rotor.position = Vector3(0,1.85,0)
				box(Vector3.ZERO,Vector3(0.65,0.025,0.10),Color("fa698f"),0,rotor)
				box(Vector3.ZERO,Vector3(0.10,0.03,0.65),Color("ffdb78"),0,rotor)
		"hat_beanie":
			var cap = sphere(Vector3(0,1.53,0),0.29,Color("bd79ec"),avatar)
			cap.scale.y = 0.75
			cylinder(Vector3(0,1.46,0),0.30,0.12,Color("e6a2f4"),avatar)
			sphere(Vector3(0,1.77,0),0.085,Color("ffd6ed"),avatar)
		"hat_party":
			cone_shape(Vector3(0,1.76,0),0.27,0.62,Color("fa8db2"),avatar)
			sphere(Vector3(0,2.08,0),0.065,Color("ffdd73"),avatar)
			for i in range(7):
				var angle: float = TAU * i / 7.0
				sphere(Vector3(cos(angle)*0.19,1.62,sin(angle)*0.19),0.035,Color("ffdc73"),avatar)
		"hat_phones":
			box(Vector3(0,1.58,0),Vector3(0.66,0.10,0.16),Color("363a60"),0,avatar)
			for side in [-1,1]:
				box(Vector3(side*0.30,1.42,0),Vector3(0.08,0.30,0.15),Color("363a60"),0,avatar)
				box(Vector3(side*0.31,1.28,0),Vector3(0.14,0.30,0.26),Color("68e6df"),0.3,avatar)
		"hat_cowboy":
			var brim = cylinder(Vector3(0,1.49,0),0.43,0.055,Color("bd8649"),avatar)
			brim.scale.z = 0.83
			cylinder(Vector3(0,1.64,0),0.25,0.29,Color("dfb56d"),avatar)
			cylinder(Vector3(0,1.54,0),0.255,0.055,Color("765037"),avatar)
		"hat_wizard":
			cylinder(Vector3(0,1.47,0),0.39,0.045,Color("6558bf"),avatar)
			cone_shape(Vector3(0,1.88,0),0.27,0.80,Color("6f61d7"),avatar)
			for i in range(5):
				var y: float = 1.64+i*0.10
				sphere(Vector3(0.04 if i%2 else -0.06,y,0.25-(y-1.5)*0.33),0.035,Color("fff09b"),avatar)
		"hat_tophat":
			cylinder(Vector3(0,1.48,0),0.37,0.055,Color("27283d"),avatar)
			cylinder(Vector3(0,1.73,0),0.25,0.48,Color("27283d"),avatar)
			cylinder(Vector3(0,1.55,0),0.255,0.075,Color("e26796"),avatar)
		"hat_crown":
			cylinder(Vector3(0,1.54,0),0.30,0.17,Color("ffcf65"),avatar)
			for i in range(7):
				var angle: float = TAU*i/7.0
				cone_shape(Vector3(cos(angle)*0.25,1.72,sin(angle)*0.25),0.07,0.25,Color("ffdc80"),avatar)
				sphere(Vector3(cos(angle)*0.304,1.54,sin(angle)*0.304),0.035,Color("fa5782"),avatar)
		"hat_halo":
			var ring = TorusMesh.new()
			ring.inner_radius = 0.30
			ring.outer_radius = 0.345
			mesh_node(ring,Vector3(0,1.82,0),Color("ffe996"),avatar,1.8)
	if SaveStore.data.outfit == "outfit_gold":
		sphere(Vector3(0,0.85,0.16),0.07,Color("ffe694"),avatar)
	elif SaveStore.data.outfit == "outfit_neon":
		box(Vector3(0,0.63,0.145),Vector3(0.44,0.035,0.015),Color("e7ffea"),0.5,avatar)
	elif SaveStore.data.outfit == "outfit_midnight":
		box(Vector3(0,0.80,0.145),Vector3(0.36,0.06,0.015),Color("a09ad8"),0,avatar)
	elif SaveStore.data.outfit == "outfit_arctic":
		box(Vector3(0,0.80,0.145),Vector3(0.36,0.06,0.015),Color("69abc7"),0,avatar)

func cone_shape(pos: Vector3, radius: float, height: float, color: Color, parent: Node) -> MeshInstance3D:
	var mesh = CylinderMesh.new()
	mesh.top_radius = 0.0
	mesh.bottom_radius = radius
	mesh.height = height
	mesh.radial_segments = 16
	return mesh_node(mesh,pos,color,parent)

func refresh_decor() -> void:
	for child in decor_root.get_children():
		child.queue_free()
	var positions: Array[Vector3] = [Vector3(-9.7,0,19.5),Vector3(-7.8,0,19.8),Vector3(-5.9,0,19.8),Vector3(-4.1,0,19.8),Vector3(-8.4,0,6.4),Vector3(8.8,0,19.3),Vector3(6.3,0,19.7),Vector3(0,3.8,9.0),Vector3(9.3,0,13.2)]
	var n: int = 0
	for item in ArcadeCatalog.items("decor"):
		var p: Vector3 = positions[n]
		n += 1
		if item.id not in SaveStore.data.owned:
			continue
		var prop = Node3D.new()
		prop.name = item.id
		decor_root.add_child(prop)
		prop.position = p
		build_decoration(item.id,prop)
		if item.id != "decor_disco":
			var dimensions = Vector3(0.8,1.5,0.8)
			if item.id in ["decor_fish","decor_trophy"]:
				dimensions = Vector3(1.7,2.1,0.85)
			elif item.id == "decor_bear":
				dimensions = Vector3(1.45,2.0,0.90)
			elif item.id == "decor_jukebox":
				dimensions = Vector3(1.2,2.2,0.7)
			elif item.id == "decor_palm":
				dimensions = Vector3(0.70,0.60,0.70)
			var obstacle = StaticBody3D.new()
			prop.add_child(obstacle)
			var collider = CollisionShape3D.new()
			var shape = BoxShape3D.new()
			shape.size = dimensions
			collider.shape = shape
			collider.position.y = dimensions.y*0.5
			obstacle.add_child(collider)
			label3d(item.name,Vector3(0,2.45,0),16,Color("fae1ad"),prop)

func rod(a: Vector3, b: Vector3, radius: float, color: Color, parent: Node) -> void:
	var segment = cylinder((a+b)*0.5,radius,a.distance_to(b),color,parent)
	segment.quaternion = Quaternion(Vector3.UP,(b-a).normalized())

func build_decoration(id: String, prop: Node3D) -> void:
	match id:
		"decor_palm":
			cylinder(Vector3(0,0.27,0),0.34,0.54,Color("ce7f74"),prop)
			cylinder(Vector3(0,0.54,0),0.35,0.055,Color("e89e87"),prop)
			cylinder(Vector3(0,0.57,0),0.29,0.04,Color("513e46"),prop)
			rod(Vector3(0,0.57,0),Vector3(0.12,1.8,0),0.09,Color("b79161"),prop)
			for i in range(7):
				var angle: float = TAU*i/7.0
				var leaf = box(Vector3(0.12+cos(angle)*0.39,1.85,sin(angle)*0.39),Vector3(0.85,0.04,0.19),Color("5bc99a"),0,prop)
				leaf.rotation.y = -angle
				leaf.rotation.z = -0.17
				var end = box(Vector3(0.12+cos(angle)*0.74,1.64,sin(angle)*0.74),Vector3(0.48,0.035,0.13),Color("70dc9c"),0,prop)
				end.rotation.y = -angle
				end.rotation.z = -0.62
		"decor_lava":
			cylinder(Vector3(0,0.38,0),0.30,0.76,Color("5a4779"),prop)
			cone_shape(Vector3(0,0.87,0),0.30,0.25,Color("abb2c9"),prop)
			var glass = sphere(Vector3(0,1.40,0),0.27,Color(0.7,0.35,0.9,0.3),prop)
			glass.scale.y = 2.1
			for i in range(4):
				var blob = sphere(Vector3(sin(i*3.0)*0.1,1.07+i*0.20,0),0.10,Color("ff8aad"),prop)
				blob.scale.y = 1.25
				blob.material_override = material(Color("ff79b0"),0.8)
			cone_shape(Vector3(0,1.94,0),0.15,0.20,Color("9ba8c8"),prop)
		"decor_flamingo":
			cylinder(Vector3(0,0.045,0),0.48,0.09,Color("4e3d6b"),prop)
			for side in [-1,1]:
				rod(Vector3(side*0.12,0.08,0),Vector3(side*0.12,0.94,0),0.025,Color("ff9ebf"),prop)
			var bird = sphere(Vector3(0,1.15,0),0.36,Color("ff6caf"),prop)
			bird.scale = Vector3(1.15,0.8,0.6)
			rod(Vector3(0.22,1.3,0),Vector3(0.43,1.76,0),0.065,Color("ff78b5"),prop)
			rod(Vector3(0.43,1.76,0),Vector3(0.26,2.0,0),0.06,Color("ff78b5"),prop)
			sphere(Vector3(0.20,2.0,0),0.14,Color("ff8dc0"),prop)
			box(Vector3(-0.02,1.96,0),Vector3(0.22,0.09,0.11),Color("fff1d4"),0,prop)
			box(Vector3(-0.13,1.91,0),Vector3(0.075,0.15,0.11),Color("353248"),0,prop)
			sphere(Vector3(0.16,2.03,0.127),0.02,Color("292742"),prop)
			bird.material_override = material(Color("ff6caf"),0.4)
		"decor_gumball":
			cylinder(Vector3(0,0.13,0),0.39,0.26,Color("ee637f"),prop)
			cylinder(Vector3(0,0.60,0),0.17,0.80,Color("ca446e"),prop)
			box(Vector3(0,0.9,0),Vector3(0.56,0.4,0.51),Color("ee637f"),0,prop)
			box(Vector3(0,0.95,0.27),Vector3(0.21,0.16,0.04),Color("b7c4dd"),0,prop)
			box(Vector3(0,0.78,0.27),Vector3(0.24,0.11,0.04),Color("24243e"),0,prop)
			for i in range(24):
				var angle: float = i*2.399
				sphere(Vector3(cos(angle)*0.26,1.18+(i%4)*0.105,sin(angle)*0.26),0.085,Color.from_hsv(i/24.0,0.7,1),prop)
			sphere(Vector3(0,1.40,0),0.43,Color(0.6,0.9,1,0.20),prop)
			cylinder(Vector3(0,1.81,0),0.23,0.09,Color("ed668b"),prop)
		"decor_fish":
			box(Vector3(0,0.42,0),Vector3(1.65,0.84,0.80),Color("3f4e78"),0,prop)
			box(Vector3(0,0.94,0),Vector3(1.68,0.1,0.84),Color("7095b0"),0,prop)
			box(Vector3(0,1.55,0),Vector3(1.60,1.10,0.77),Color(0.25,0.78,0.90,0.16),0,prop)
			box(Vector3(0,2.13,0),Vector3(1.72,0.14,0.86),Color("4d667f"),0,prop)
			box(Vector3(0,1.035,0),Vector3(1.55,0.075,0.72),Color("edca93"),0,prop)
			for i in range(4):
				rod(Vector3(-0.6+i*0.39,1.07,-0.2),Vector3(-0.56+i*0.39,1.53+(i%2)*0.2,-0.2),0.045,Color("65d69a"),prop)
			for i in range(3):
				var pos = Vector3(-0.43+i*0.43,1.40+(i%2)*0.32,0.14)
				var fish = sphere(pos,0.15,Color("ffb970") if i%2 else Color("ee89c1"),prop)
				fish.scale = Vector3(1.5,0.8,0.55)
				var tail = cone_shape(pos+Vector3(-0.26,0,0),0.14,0.21,Color("ffc98c"),prop)
				tail.rotation.z = -PI*0.5
				sphere(pos+Vector3(0.14,0.03,0.072),0.023,Color("26364e"),prop)
		"decor_jukebox":
			box(Vector3(0,0.8,0),Vector3(1.14,1.60,0.66),Color("744564"),0,prop)
			var arch = sphere(Vector3(0,1.62,0),0.57,Color("ffc784"),prop)
			arch.scale.z = 0.6
			box(Vector3(0,0.95,0.36),Vector3(0.85,1.55,0.06),Color("242a48"),0,prop)
			for side in [-1,1]:
				box(Vector3(side*0.49,0.97,0.4),Vector3(0.09,1.68,0.08),Color("58e1da"),0.8,prop)
			for i in range(7):
				box(Vector3(0,0.25+i*0.085,0.405),Vector3(0.71,0.025,0.025),Color("b79481"),0,prop)
			for row in range(3):
				for col in range(4):
					box(Vector3(-0.25+col*0.17,1.11+row*0.13,0.405),Vector3(0.13,0.085,0.02),Color("ffc5a2"),0.2,prop)
			label3d("♫",Vector3(0,1.74,0.39),38,Color("ff689d"),prop)
		"decor_bear":
			var brown = Color("c8926d")
			var belly = sphere(Vector3(0,0.85,0),0.57,brown,prop)
			belly.scale.y = 1.15
			sphere(Vector3(0,1.61,0),0.45,brown,prop)
			for side in [-1,1]:
				sphere(Vector3(side*0.35,1.95,0),0.18,brown,prop)
				sphere(Vector3(side*0.35,1.96,0.09),0.10,Color("efbc9d"),prop)
				sphere(Vector3(side*0.54,0.99,0),0.25,brown,prop)
				sphere(Vector3(side*0.32,0.30,0.25),0.29,brown,prop)
				sphere(Vector3(side*0.14,1.68,0.40),0.045,Color("292439"),prop)
			var muzzle = sphere(Vector3(0,1.52,0.40),0.20,Color("f0cdac"),prop)
			muzzle.scale.y = 0.7
			sphere(Vector3(0,1.58,0.56),0.065,Color("443040"),prop)
			box(Vector3(0,1.20,0.49),Vector3(0.54,0.16,0.10),Color("e276a0"),0,prop)
		"decor_disco":
			cylinder(Vector3(0,0.95,0),0.024,1.20,Color("a6b4d0"),prop)
			var ball = sphere(Vector3.ZERO,0.55,Color("bcc6e6"),prop)
			var shiny = material(Color("cddbf2"),0.2)
			shiny.metallic = 0.9
			shiny.roughness = 0.15
			ball.material_override = shiny
			for row in range(5):
				var elevation: float = -0.9+row*0.45
				for i in range(10):
					var angle: float = TAU*i/10.0
					var p = Vector3(cos(elevation)*cos(angle),sin(elevation),cos(elevation)*sin(angle))*0.553
					var tile = box(p,Vector3(0.17,0.15,0.01),Color.from_hsv((i+row*3)/24.0,0.20,1),0.4,prop)
					tile.look_at(prop.global_position+p*2)
		"decor_trophy":
			box(Vector3(0,0.20,0),Vector3(1.65,0.4,0.80),Color("624762"),0,prop)
			for y in [0.43,1.23,2.02]:
				box(Vector3(0,y,0),Vector3(1.7,0.09,0.85),Color("c09c73"),0,prop)
			for side in [-1,1]:
				box(Vector3(side*0.80,1.20,0),Vector3(0.09,1.65,0.80),Color("c09c73"),0,prop)
			box(Vector3(0,1.20,0.39),Vector3(1.50,1.55,0.025),Color(0.6,0.85,1,0.12),0,prop)
			for i in range(4):
				var p = Vector3(-0.4+(i%2)*0.8,0.55+(i/2)*0.8,0)
				cylinder(p,0.20,0.10,Color("59466c"),prop)
				cylinder(p+Vector3(0,0.16,0),0.04,0.26,Color("ffcb68"),prop)
				var cup = cone_shape(p+Vector3(0,0.37,0),0.22,0.29,Color("ffd77e"),prop)
				cup.rotation.z = PI
				for side in [-1,1]:
					var ring = TorusMesh.new()
					ring.inner_radius = 0.09
					ring.outer_radius = 0.12
					var handle = mesh_node(ring,p+Vector3(side*0.21,0.38,0),Color("ffcf72"),prop)
					handle.rotation.x = PI*0.5

func cafe() -> void:
	box(Vector3(-7,0.015,12.5),Vector3(7,0.035,9),Color("413953"))
	box(Vector3(-8.4,0.7,9.5),Vector3(4.6,1.4,1.3),Color("dd9690"))
	solid(Vector3(-8.4,0.7,9.5),Vector3(4.6,1.4,1.3))
	label3d("THE PIXEL CAFE",Vector3(-7.5,3.3,8.5),36,Color("ffb697"))
	label3d("COFFEE  •  SLUSH  •  SOFT SERVE",Vector3(-7.5,2.7,8.5),18,Color("faf0e5"))
	for x in [-9.5,-8.3,-7.1]:
		box(Vector3(x,1.8,9.4),Vector3(0.7,0.8,0.65),Color("92b7c8"))
		cylinder(Vector3(x,2.3,9.4),0.25,0.45,Color("ed9bcc"))
	for z in [12.5,16.2]:
		for x in [-9.1,-5.5]:
			cylinder(Vector3(x,0.95,z),0.85,0.13,Color("e0b498"))
			cylinder(Vector3(x,0.48,z),0.14,0.9,Color("767589"))
			solid(Vector3(x,0.5,z),Vector3(1.7,1,1.7))
			for dz in [-1.1,1.1]:
				cylinder(Vector3(x,0.5,z+dz),0.35,0.15,Color("ce7091"))
				cylinder(Vector3(x,0.25,z+dz),0.08,0.45,Color("9292a9"))
	var barista = figure(Color("97e2dc"))
	barista.position = Vector3(-7.7,0,8.5)

func _physics_process(dt: float) -> void:
	if body == null:
		return
	age += dt
	var dir = Vector3(walk.x,0,walk.y)
	if first_person:
		dir = dir.rotated(Vector3.UP,yaw)
	body.velocity = dir * 4.3
	body.velocity.y = -1
	body.move_and_slide()
	if dir.length() > 0.1:
		avatar.rotation.y = lerp_angle(avatar.rotation.y,atan2(dir.x,dir.z),dt*14)
		avatar.position.y = absf(sin(age*12))*0.045
	var propeller = avatar.get_node_or_null("Propeller")
	if propeller != null:
		propeller.rotation.y += dt*7
	var disco = decor_root.get_node_or_null("decor_disco")
	if disco != null:
		disco.rotation.y += dt*0.3
	avatar.visible = not first_person
	get_node("Ceiling").visible = first_person
	for node in overhead_hidden:
		node.visible = first_person
	update_camera(dt)
	var best = -1
	var distance = 2.1
	for i in spots.size():
		var d = Vector2(body.position.x,body.position.z).distance_to(Vector2(spots[i].x,spots[i].z))
		if d < distance:
			best = spot_games[i]
			distance = d
	if best != nearby:
		nearby = best
		near_changed.emit(best)
	for npc in npcs:
		var node: Node3D = npc.node
		var delta: Vector3 = npc.target-node.position
		if delta.length() < 0.4:
			npc.wait += dt
			if npc.wait > 3:
				npc.target = Vector3(node.position.x,0,-16 if node.position.z>0 else 16)
				npc.wait = 0
		else:
			node.position += delta.normalized()*dt*0.8
			node.rotation.y = atan2(delta.x,delta.z)
			node.position.y = absf(sin(age*7+node.position.z))*0.03

func update_camera(dt: float) -> void:
	if first_person:
		camera.position = camera.position.lerp(body.position+Vector3(0,1.3,0),minf(1,dt*12))
		camera.rotation = Vector3(pitch,yaw,0)
	else:
		var target = body.position + Vector3(0,16,10)
		target.z = minf(target.z,22)
		camera.position = camera.position.lerp(target,minf(1,dt*8))
		camera.look_at(body.position+Vector3(0,0,-3))

func toggle_camera() -> void:
	first_person = not first_person
	SaveStore.data.first_person = first_person
	SaveStore.save()
	walk = Vector2.ZERO
