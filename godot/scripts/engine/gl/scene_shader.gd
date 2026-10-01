class_name SceneShader
extends RefCounted
## engine/gl/GlShaders.kt SCENE_VS + SCENE_FS as Godot spatial shaders: the per-pixel lighting of
## every 3D picture. Compiled in variants (blend layer, culling, filtering, repeat, instanced),
## since Godot fixes those per shader; everything that changes per frame (lights, light grid,
## ambient, fog, exposure, the HDR switch...) comes from the slot's data texture, so a material
## never needs a parameter set per frame.
##
## Data texture (RGBA32F, DATA_W × 3): row 0 light positions (x, y, z, radius), row 1 light
## colours (r, g, b, intensity), row 2 globals (see GfxSlot._write_globals).

const DATA_W := 64

static var _cache := {}


static func key(blend: int, cull: bool, smooth: bool, repeat: bool, instanced: bool) -> String:
	return "%d%d%d%d%d" % [blend, int(cull), int(smooth), int(repeat), int(instanced)]


## The shader for one variant (cached).
static func get_shader(blend: int, cull: bool, smooth: bool, repeat: bool, instanced: bool) -> Shader:
	var k := key(blend, cull, smooth, repeat, instanced)
	var s: Shader = _cache.get(k)
	if s == null:
		s = Shader.new()
		s.code = code(blend, cull, smooth, repeat, instanced)
		_cache[k] = s
	return s


static func code(blend: int, cull: bool, smooth: bool, repeat: bool, instanced: bool) -> String:
	var modes := ["unshaded", "skip_vertex_transform", "cull_back" if cull else "cull_disabled"]
	if blend == Blend.OPAQUE:
		modes.append("blend_mix")
		modes.append("depth_draw_opaque")
	elif blend == Blend.ALPHA:
		modes.append("blend_mix")
		modes.append("depth_draw_never")
	else:
		modes.append("blend_add")
		modes.append("depth_draw_never")
	var filt := "filter_linear_mipmap_anisotropic" if smooth else "filter_nearest"
	var rep := "repeat_enable" if repeat else "repeat_disable"
	var src := TEMPLATE
	src = src.replace("$MODES", ", ".join(PackedStringArray(modes)))
	src = src.replace("$FILTER", filt).replace("$REPEAT", rep)
	src = src.replace("$INSTANCED", "1" if instanced else "0")
	src = src.replace("$BLEND", str(blend))
	return src


const TEMPLATE := """shader_type spatial;
render_mode $MODES;

// The polygon's texture, stored premultiplied (clean filtering at cut-out edges); undone below.
uniform sampler2D tex : $FILTER, $REPEAT;
// Lights and per-frame globals (RGBA32F), the light grid (RGBA8, two texels per cell), the room
// glossy things reflect, and the floor-reflection source.
uniform sampler2D data_tex : filter_nearest, repeat_disable;
uniform sampler2D grid_tex : filter_nearest, repeat_disable;
uniform samplerCube env_tex : filter_linear_mipmap;
uniform sampler2D refl_tex : filter_linear, repeat_disable;

varying vec3 v_world;
varying vec3 v_normal;
varying vec4 v_extra;
varying float v_depth;

const float ENC_MAX = 0.96;
const float EMISSIVE_GAIN = 1.2;
const float LIT_CEILING = 1.05;
const float LIT_START = 0.6;

float max3(vec3 v) { return max(v.r, max(v.g, v.b)); }
vec3 hdr_encode(vec3 c) {
	c = max(c, vec3(0.0));
	return c / (1.0 + max3(c));
}
float soft_cap(float x, float cap, float start) {
	float r = max(cap - start, 1e-4);
	return x <= start ? x : start + r * (1.0 - exp(-(x - start) / r));
}
float aces_s(float c) {
	return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}
vec3 tonemap(vec3 c, float exposure) {
	c *= exposure;
	return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}

void vertex() {
	// The per-instance tint arrives already multiplied into COLOR (the MultiMesh's instance colour);
	// the custom data's alpha is the emissive boost.
	float boost = 1.0;
#if $INSTANCED
	boost = INSTANCE_CUSTOM.a;
#endif
	vec4 viewp = MODELVIEW_MATRIX * vec4(VERTEX, 1.0);
	v_world = (INV_VIEW_MATRIX * viewp).xyz;
	vec3 vn = normalize(MODELVIEW_NORMAL_MATRIX * NORMAL);
	v_normal = (INV_VIEW_MATRIX * vec4(vn, 0.0)).xyz;
	v_depth = -viewp.z;
	// CUSTOM0: emissive, depth bias, gloss, fog.
	v_extra = vec4(CUSTOM0.x * boost, CUSTOM0.y, CUSTOM0.z, CUSTOM0.w);
	VERTEX = viewp.xyz;
	NORMAL = vn;
	// A depth bias above 1 pulls the polygon toward the camera for depth tests only: the point is
	// projected as if that much nearer along its own ray (same place on screen).
	float bias = max(CUSTOM0.y, 0.01);
	POSITION = PROJECTION_MATRIX * vec4(viewp.xyz / bias, 1.0);
}

void fragment() {
	vec4 t = texture(tex, UV);
	float alpha = t.a * COLOR.a;
	float cut = $BLEND == 0 ? 0.5 : 0.003;
	if (alpha < cut) discard;
	vec3 base = t.rgb / max(t.a, 0.004) * COLOR.rgb;
	vec4 g_amb = texelFetch(data_tex, ivec2(0, 2), 0);   // ambient rgb, rim
	vec4 g_dir = texelFetch(data_tex, ivec2(1, 2), 0);   // direction, floor glow
	vec4 g_dcol = texelFetch(data_tex, ivec2(2, 2), 0);  // directional colour, exposure
	vec4 g_eye = texelFetch(data_tex, ivec2(3, 2), 0);   // eye, env amount
	vec4 g_fog = texelFetch(data_tex, ivec2(4, 2), 0);   // fog near, far, floor, hdr
	vec4 g_grid = texelFetch(data_tex, ivec2(5, 2), 0);  // grid x0, z0, 1/cell, -
	vec4 g_size = texelFetch(data_tex, ivec2(6, 2), 0);  // grid w, h, refl streak, -
	vec4 g_refl = texelFetch(data_tex, ivec2(7, 2), 0);  // refl 1/w, 1/h, gloss amount, matte amount
	bool hdr = g_fog.w > 0.5;
	float exposure = g_dcol.w;
	float glass = $BLEND == 1 ? 1.0 : 0.0;
	vec3 col;
	vec3 refl = vec3(0.0);
	if (v_extra.x > 0.0) {
		col = base * (hdr ? v_extra.x * EMISSIVE_GAIN : v_extra.x);
	} else {
		vec3 n = normalize(v_normal);
		vec3 to_eye = g_eye.xyz - v_world;
		if (dot(n, to_eye) < 0.0) n = -n;
		vec3 V = normalize(to_eye);
		// Hemisphere ambient: faces turned up catch more of the room's bounce light.
		vec3 L = g_amb.rgb * (0.8 + 0.4 * n.y);
		vec3 P = vec3(0.0);
		float dd = dot(n, g_dir.xyz);
		// Gloss sets both the highlight's strength and its tightness.
		float gloss = v_extra.z;
		float spec_pow = 16.0 + 64.0 * gloss * gloss;
		float spec_k = gloss * sqrt((spec_pow + 8.0) / 56.0);
		vec3 S = vec3(0.0);
		if (dd > 0.0) {
			L += g_dcol.rgb * dd;
			if (gloss > 0.0) {
				vec3 h = normalize(g_dir.xyz + V);
				S += g_dcol.rgb * (pow(max(dot(n, h), 0.0), spec_pow) * spec_k);
			}
		}
		ivec2 gsize = ivec2(int(g_size.x + 0.5), int(g_size.y + 0.5));
		ivec2 cell = ivec2(floor((v_world.xz - g_grid.xy) * g_grid.z));
		if (cell.x >= 0 && cell.y >= 0 && cell.x < gsize.x && cell.y < gsize.y) {
			for (int k2 = 0; k2 < 2; k2++) {
				vec4 ids = texelFetch(grid_tex, ivec2(cell.x * 2 + k2, cell.y), 0) * 255.0;
				for (int k = 0; k < 4; k++) {
					int id = int(ids[k] + 0.5) - 1;
					if (id >= 0) {
						vec4 lp = texelFetch(data_tex, ivec2(id, 0), 0);
						vec3 dv = lp.xyz - v_world;
						float d2 = dot(dv, dv);
						if (d2 < lp.w * lp.w) {
							float dist = sqrt(d2);
							vec3 ld = dv / max(dist, 0.001);
							float fall = 1.0 - dist / lp.w;
							fall *= fall;
							vec4 lc = texelFetch(data_tex, ivec2(id, 1), 0);
							float lam = max(dot(n, ld) * 0.6 + 0.4, 0.0);
							vec3 reach = lc.rgb * (fall * lc.a);
							L += reach * lam;
							P += reach;
							if (gloss > 0.0) {
								vec3 h = normalize(ld + V);
								S += reach * (pow(max(dot(n, h), 0.0), spec_pow) * spec_k * 1.6);
							}
						}
					}
				}
			}
		}
		vec3 glint = vec3(0.0);
		if (hdr) {
			col = base * min(L, vec3(4.0));
			glint = S;
		} else {
			col = base * min(L, vec3(4.0)) + S;
		}
		float nv = max(dot(n, V), 0.0);
		float fr = 1.0 - nv;
		float env_amount = g_eye.w;
		if (gloss > 0.0 && env_amount > 0.0) {
			// The room reflected, sharper for glossier surfaces; chrome reflects strongly, tinted by its colour.
			vec3 R = reflect(-V, n);
			if (R.y < 0.0) R.y *= 0.4;
			vec4 es = textureLod(env_tex, R, (1.0 - gloss) * 5.0);
			vec3 e = es.rgb * es.rgb * 4.0;
			float metal = smoothstep(0.72, 0.9, gloss) * (1.0 - glass);
			float f0 = mix(0.04, 0.35, metal);
			float fp = fr * fr * fr * mix(1.0, fr, glass);
			float F = (f0 + (1.0 - f0) * fp) * gloss * gloss * env_amount * (1.0 - 0.4 * glass);
			float mx = max(base.r, max(base.g, base.b));
			float sat = (mx - min(base.r, min(base.g, base.b))) / max(mx, 0.001);
			vec3 hue = mix(vec3(1.0), base / max(mx, 0.001), metal * sat);
			refl += e * hue * F;
		}
		if (g_refl.z + g_refl.w > 0.0 && n.y > 0.85 && v_world.y < 3.0 && v_world.y > -4.0) {
			// Glowing things reflected in the floor (streaks from last frame's glow up the screen).
			vec2 ruv = SCREEN_UV;
			ruv.y = 1.0 - ruv.y;
			ruv.y += (dot(base, vec3(0.333)) - 0.3) * 0.012 * gloss;
			float streak = g_size.z;
			vec3 rc;
			if (streak > 0.0) {
				rc = texture(refl_tex, vec2(ruv.x, 1.0 - (ruv.y + 0.2 * streak))).rgb * 0.5
					+ texture(refl_tex, vec2(ruv.x, 1.0 - (ruv.y + 0.55 * streak))).rgb * 0.35
					+ texture(refl_tex, vec2(ruv.x, 1.0 - (ruv.y + streak))).rgb * 0.25;
			} else {
				rc = texture(refl_tex, vec2(ruv.x, 1.0 - ruv.y)).rgb;
			}
			float lum = dot(base, vec3(0.3, 0.59, 0.11));
			float dark = 1.0 - clamp((lum - 0.25) * 2.0, 0.0, 1.0);
			refl += min(rc, vec3(1.2)) * (dark * (gloss * (0.3 + 0.7 * fr * fr * fr) * g_refl.z + g_refl.w));
		}
		// Rim light: a Fresnel sheen tinted by the lights nearby.
		float fres = 1.0 - max(dot(n, V), 0.0);
		fres *= fres;
		fres *= fres;
		col += (g_amb.rgb + min(P, vec3(1.5)) * 0.4) * (fres * g_amb.w) * (base * 0.7 + 0.3);
		// Blacklight: saturated paint on the floor fluoresces.
		float floor_glow = g_dir.w;
		if (floor_glow > 0.0 && n.y > 0.9 && v_world.y < 1.5) {
			float sat2 = max(base.r, max(base.g, base.b)) - min(base.r, min(base.g, base.b));
			col += base * (sat2 * floor_glow);
		}
		if (hdr) {
			// Lit paint may not climb past a ceiling, so a pale surface under many lamps never blooms.
			float m = max3(col) * exposure;
			col = (m > LIT_START ? col * (soft_cap(m, LIT_CEILING, LIT_START) / m) : col) + glint;
		}
	}
	if (v_extra.w > 0.5 && v_depth > g_fog.x) {
		float f = max(1.0 - (v_depth - g_fog.x) / max(g_fog.y - g_fog.x, 1.0), g_fog.z);
		col *= f;
		refl *= f;
	}
	vec3 out_rgb;
	float out_a = alpha;
	if (glass > 0.5) {
		// See-through glass: the reflection is its own layer on top.
		if (hdr) {
			float k = aces_s(max3(refl) * exposure);
			float a = clamp(alpha + (1.0 - alpha) * k, 0.0, 1.0);
			out_rgb = hdr_encode((col * alpha + refl) / max(a, 0.001));
			out_a = a;
		} else {
			vec3 tt = tonemap(col, exposure);
			vec3 r = tonemap(refl, exposure);
			float k = max(r.r, max(r.g, r.b));
			float a = clamp(alpha + (1.0 - alpha) * k, 0.0, 1.0);
			out_rgb = (tt * alpha + r) / max(a, 0.001);
			out_a = a;
		}
	} else {
		out_rgb = hdr ? hdr_encode(col + refl) : tonemap(col + refl, exposure);
	}
	ALBEDO = out_rgb;
#if $BLEND != 0
	ALPHA = out_a;
#endif
}
"""
