extends PaTest
## Godot-only: the booth's studio pictures as ui/PhotoBoothScreen.kt's boothShot records them over
## ui/Thumbs.kt's Studio: the camera at chest height, the two lights, Studio's rig, the stool only
## for the seated shot, the soft shadow, one picture per look and pose, and Thumbs' memory budget.

const PX := PhotoStrip.FRAME


func _recorded(pose_index: int) -> Array:
	var r := Renderer3D.new(PX, PX)
	r.start_frame()
	r.resize(PX, PX)
	var look := PhotoBoothStudio.player_look(SaveState.new())
	PhotoBoothStudio.record(r, PX, PX, look, PhotoBoothPlan.poses[pose_index])
	return [r, r.finish_frame(0.0, 0.0, PX, PX)]


func test_one_picture_per_look_and_pose() -> void:
	var a := Looks.player(Pal.RED, Pal.NAVY, Catalog.NONE)
	var same := Looks.player(Pal.RED, Pal.NAVY, Catalog.NONE)
	var b := Looks.player(Pal.RED, Pal.NAVY, Catalog.HatStyle.CROWN)
	assert_eq(PhotoBoothStudio.shot_key(a, 2), PhotoBoothStudio.shot_key(same, 2))
	assert_ne(PhotoBoothStudio.shot_key(a, 2), PhotoBoothStudio.shot_key(a, 3))
	assert_ne(PhotoBoothStudio.shot_key(a, 2), PhotoBoothStudio.shot_key(b, 2))
	assert_true(PhotoBoothStudio.shot_key(a, 0).begins_with("booth:"))
	assert_true(PhotoBoothStudio.figure(a) == PhotoBoothStudio.figure(same), "one kid per look")


func test_the_kid_wears_the_saves_outfit_and_hat() -> void:
	var s := SaveState.new()
	var outfit := Catalog.outfit(s.outfit)
	var look := PhotoBoothStudio.player_look(s)
	assert_true(look.equals(Looks.player(outfit.shirt, outfit.pants, Catalog.NONE)))
	var hat: Catalog.ShopItem = Catalog.hats()[2]
	s.hat = hat.id
	assert_eq(hat.hat, PhotoBoothStudio.player_look(s).hat)


func test_the_camera_and_lights_are_build_13s() -> void:
	var rec := _recorded(0)
	var r: Renderer3D = rec[0]
	var p: RenderPass = rec[1]
	# A ball of 34 round y = 28 fills the picture, seen from 7 degrees up.
	var fov := deg_to_rad(30.0)
	var dist := 34.0 / tan(fov / 2.0) * 1.08
	var cam := r.camera
	assert_near(0.0, cam.ex, 1e-4)
	assert_near(28.0 + sin(deg_to_rad(7.0)) * dist, cam.ey, 1e-3)
	assert_near(cos(deg_to_rad(7.0)) * dist, cam.ez, 1e-3)
	assert_near((PX / 2.0) / tan(fov / 2.0), cam.focal, 1e-3)
	# Studio's rig: ambient, the directional light, no fog, exposure and bloom.
	var l := r.lighting
	assert_near(0.42, l.amb_r, 1e-6)
	assert_near(0.4, l.amb_g, 1e-6)
	assert_near(0.5, l.amb_b, 1e-6)
	var d := Vector3(-0.45, 0.8, 0.7).normalized()
	assert_near(d.x, l.dir_x, 1e-5)
	assert_near(d.y, l.dir_y, 1e-5)
	assert_near(d.z, l.dir_z, 1e-5)
	assert_near(0.75, l.dir_r, 1e-6)
	assert_near(0.72, l.dir_g, 1e-6)
	assert_near(0.68, l.dir_b, 1e-6)
	assert_near(5000.0, r.fog_near, 0.0)
	assert_near(9000.0, r.fog_far, 0.0)
	assert_near(1.15, p.exposure, 1e-6)
	assert_near(0.42, p.bloom, 1e-6)
	# The booth's warm key light and the violet one from behind, and nothing else.
	assert_eq(2, l.points.size())
	if l.points.size() == 2:
		var key := l.points[0]
		assert_true(Vector3(-51.0, 89.2, 68.0).is_equal_approx(Vector3(key.x, key.y, key.z)))
		assert_true(Vector3(1.0, 0.9, 0.8).is_equal_approx(Vector3(key.r, key.g, key.b)))
		assert_near(204.0, key.radius, 1e-4)
		assert_near(0.75, key.intensity, 1e-6)
		var back := l.points[1]
		assert_true(Vector3(40.8, 62.0, -61.2).is_equal_approx(Vector3(back.x, back.y, back.z)))
		assert_true(Vector3(0.7, 0.45, 1.0).is_equal_approx(Vector3(back.r, back.g, back.b)))
		assert_near(170.0, back.radius, 1e-4)
		assert_near(0.9, back.intensity, 1e-6)
	# Studio's clear colour and backdrop, then the curtain's gradient over it.
	assert_eq(PhotoBoothStudio.STUDIO_CLEAR, p.clear_color)
	assert_eq(2, p.gradients.size())


func test_only_the_seated_shot_has_the_stool() -> void:
	for i in PhotoBoothPlan.poses.size():
		var p: RenderPass = _recorded(i)[1]
		var stool := false
		for run: RenderPass.ModelRun in p.opaque_runs:
			if run.model == PhotoBoothStudio.stool():
				stool = true
		assert_eq(PhotoBoothPlan.poses[i].pose == Pose.SIT, stool, "shot %d" % i)


func test_the_kid_stands_on_a_soft_shadow() -> void:
	var p: RenderPass = _recorded(0)[1]
	var found := 0
	for e in p.ordered:
		if e is RenderPass.Batch and (e as RenderPass.Batch).tex == HallArt.shadow():
			var b := e as RenderPass.Batch
			assert_eq(Blend.ALPHA, b.blend)
			assert_near(0.5, b.col[0].a, 1e-3)
			found += 1
	assert_eq(1, found)


func test_kept_pictures_stay_within_thumbs_budget() -> void:
	PhotoBoothStudio.clear_cache()
	var img := Image.create_empty(1024, 1024, false, Image.FORMAT_RGBA8)
	var tex := ImageTexture.create_from_image(img)
	for k in 12:
		PhotoBoothStudio._keep("k%d" % k, tex)
		if k == 6:
			# Seeing a picture makes it the most recently used.
			assert_not_null(PhotoBoothStudio.cached("k0"))
	# 4 MB each, 40 MB kept: the least recently used went first, k0 was saved by being seen.
	assert_le(PhotoBoothStudio._bytes, PhotoBoothStudio.BUDGET)
	assert_eq(10, PhotoBoothStudio._cache.size())
	assert_not_null(PhotoBoothStudio.cached("k0"))
	assert_null(PhotoBoothStudio.cached("k1"))
	assert_null(PhotoBoothStudio.cached("k2"))
	assert_not_null(PhotoBoothStudio.cached("k11"))
	PhotoBoothStudio.clear_cache()
