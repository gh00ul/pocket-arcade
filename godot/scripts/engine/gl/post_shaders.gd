class_name PostShaders
extends RefCounted
## engine/gl/GlShaders.kt: the sources of the background, particle and post-processing passes, as
## Godot shaders assembled from the [HdrLook] constants, so the shaders and the CPU mirrors in
## [HdrMath] (which the tests pin) can never drift apart. The scene shader is [SceneShader].
##
## There are two pictures. The LDR one (BRIGHT, COMPOSITE...) tone-maps each fragment as it is
## shaded: the picture before HDR existed. The HDR one writes linear light squeezed reversibly
## into a float target ([constant HdrLook.ENC_MAX]) and does exposure, bloom, tone mapping and the
## cinematic finish in the composite.
##
## Godot differences: the passes are canvas_item shaders drawn by full-rect quads in SubViewports
## (UV runs 0..1 down the image where GL's vUV ran up it; every kernel here is symmetric, so only
## the floor streaks, in the scene shader, care); there is no #version line.

static var _cache := {}


## A float as a shader literal ("6.0", "0.0022").
static func f(x: float) -> String:
	var s := str(x)
	if not s.contains(".") and not s.contains("e"):
		s += ".0"
	return s


## The HDR pictures' shared maths, mirrored on the CPU by [HdrMath] (GlShaders.HDR_GLSL).
static func hdr_glsl() -> String:
	return """
const float ENC_MAX = %s;
float max3(vec3 v) { return max(v.r, max(v.g, v.b)); }
// The scene image stores c / (1 + max(c)): bounded, reversible, and it multisamples and blends
// like a display-referred colour would.
vec3 hdr_encode(vec3 c) {
	c = max(c, vec3(0.0));
	return c / (1.0 + max3(c));
}
vec3 hdr_decode(vec3 x) {
	float m = max3(x);
	x *= min(1.0, ENC_MAX / max(m, 1e-5));
	return x / (1.0 - min(m, ENC_MAX));
}
vec3 aces(vec3 c) {
	return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}
float aces_s(float c) {
	return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}
// Display value back to linear light (what LDR sources such as gradients and particles are
// authored in): the closed-form inverse of the ACES fit above.
vec3 aces_inv(vec3 y) {
	y = clamp(y, vec3(0.0), vec3(%s));
	vec3 a = 2.43 * y - 2.51;
	vec3 b = 0.59 * y - 0.03;
	vec3 c = 0.14 * y;
	return (b + sqrt(max(b * b - 4.0 * a * c, vec3(0.0)))) / (2.0 * (2.51 - 2.43 * y));
}
// Identity up to `start`, then an exponential ease into `cap`, never above it, no kink.
float soft_cap(float x, float cap, float start) {
	float r = max(cap - start, 1e-4);
	return x <= start ? x : start + r * (1.0 - exp(-(x - start) / r));
}
vec3 soft_cap_vec(vec3 c, float cap, float start) {
	float m = max3(c);
	return m > start ? c * (soft_cap(m, cap, start) / m) : c;
}
float ign(vec2 p) {
	return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}
""" % [f(HdrLook.ENC_MAX), f(HdrLook.ACES_INV_MAX)]


## What a spatial shader must write so that Godot's Compatibility renderer, which treats ALBEDO as
## sRGB and converts it to linear (a cubic approximation) and the result back to sRGB (another
## approximation) on output, stores exactly [param y] (0..1): the inverse of that round trip,
## solved with Newton steps. Godot-only (build-13 wrote its colour straight to the target).
static func out_glsl() -> String:
	return """
vec3 pa_out(vec3 y) {
	vec3 lin = pow((max(y, vec3(0.0)) + 0.055) / 1.055, vec3(2.4));
	vec3 x = max(y, vec3(0.0264));
	for (int i = 0; i < 3; i++) {
		vec3 p = x * (x * (x * 0.305306011 + 0.682171111) + 0.012522878);
		vec3 d = x * (x * 0.915918033 + 1.364342222) + 0.012522878;
		x -= (p - lin) / d;
	}
	return x;
}
"""


## A shader (cached) for one of the sources below.
static func shader(shader_name: String) -> Shader:
	var s: Shader = _cache.get(shader_name)
	if s == null:
		s = Shader.new()
		s.code = code(shader_name)
		_cache[shader_name] = s
	return s


## Every source by name (the tests read them).
static func code(shader_name: String) -> String:
	match shader_name:
		"bright":
			return bright()
		"bright_hdr":
			return bright_hdr()
		"down":
			return down()
		"up":
			return up()
		"glare":
			return glare()
		"composite":
			return composite()
		"composite_hdr":
			return composite_hdr()
		"background":
			return background()
		"particle":
			return particle()
		"particle_glow":
			return particle_glow()
	push_error("no post shader " + shader_name)
	return ""


const NAMES := ["bright", "bright_hdr", "down", "up", "glare", "composite", "composite_hdr", "background", "particle", "particle_glow"]

const POST_HEAD := """shader_type canvas_item;
render_mode unshaded, blend_disabled;
"""


## First bloom step: the scene at quarter size (4 bilinear taps cover each 4×4 block), keeping
## what is brighter than the threshold with a soft knee so glows fade in rather than pop.
static func bright() -> String:
	return POST_HEAD + """
uniform sampler2D src : filter_linear, repeat_disable;
uniform vec2 texel;
uniform float threshold;
void fragment() {
	vec3 c = texture(src, UV + vec2(-texel.x, -texel.y)).rgb;
	c += texture(src, UV + vec2(texel.x, -texel.y)).rgb;
	c += texture(src, UV + vec2(-texel.x, texel.y)).rgb;
	c += texture(src, UV + vec2(texel.x, texel.y)).rgb;
	c *= 0.25;
	float br = max(c.r, max(c.g, c.b));
	const float knee = 0.1;
	float soft = clamp(br - threshold + knee, 0.0, 2.0 * knee);
	soft = soft * soft / (4.0 * knee);
	float k = max(soft, br - threshold) / max(1.0 - threshold, 0.05);
	COLOR = vec4(c * k, 1.0);
}
"""


## Dual-filter downsample: halves the image with a 5-tap kernel (the next bloom octave).
static func down() -> String:
	return POST_HEAD + """
uniform sampler2D src : filter_linear, repeat_disable;
uniform vec2 texel;
void fragment() {
	vec3 c = texture(src, UV).rgb * 4.0;
	c += texture(src, UV - texel).rgb;
	c += texture(src, UV + texel).rgb;
	c += texture(src, UV + vec2(texel.x, -texel.y)).rgb;
	c += texture(src, UV + vec2(-texel.x, texel.y)).rgb;
	COLOR = vec4(c * 0.125, 1.0);
}
"""


## 9-tap tent upsample of a smaller octave, weighted by `weight` and added on top of this octave
## (build-13 added it with GL blending, ONE + ONE, or ONE + 0.5 × the destination for the
## quarter-size core: `own_k`). With own_k = 0 it is the mirror blur's plain upsample.
static func up() -> String:
	return POST_HEAD + """
uniform sampler2D src : filter_linear, repeat_disable;
uniform sampler2D own : filter_nearest, repeat_disable;
uniform vec2 texel;
uniform float weight;
uniform float own_k;
void fragment() {
	vec2 d = texel;
	vec3 c = texture(src, UV).rgb * 4.0;
	c += (texture(src, UV + vec2(d.x, 0.0)).rgb + texture(src, UV - vec2(d.x, 0.0)).rgb
		+ texture(src, UV + vec2(0.0, d.y)).rgb + texture(src, UV - vec2(0.0, d.y)).rgb) * 2.0;
	c += texture(src, UV + d).rgb + texture(src, UV - d).rgb
		+ texture(src, UV + vec2(d.x, -d.y)).rgb + texture(src, UV + vec2(-d.x, d.y)).rgb;
	vec3 o = own_k > 0.0 ? texture(own, UV).rgb * own_k : vec3(0.0);
	COLOR = vec4(c * (weight / 16.0) + o, 1.0);
}
"""


## Scene plus bloom, then a light sharpen (the scene is upscaled from the render scale), a gentle
## colour grade, a soft vignette and a dither that hides banding in dark gradients.
static func composite() -> String:
	return """shader_type canvas_item;
render_mode unshaded, blend_disabled;
uniform sampler2D bloom_tex : filter_linear, repeat_disable;
uniform vec2 texel;
uniform float bloom_amount;
uniform float vignette;
uniform float sharpen;
uniform float grade;
float ign(vec2 p) {
	return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}
void fragment() {
	vec3 c = texture(TEXTURE, UV).rgb;
	if (sharpen > 0.0) {
		vec3 nb = texture(TEXTURE, UV + vec2(texel.x, 0.0)).rgb + texture(TEXTURE, UV - vec2(texel.x, 0.0)).rgb
			+ texture(TEXTURE, UV + vec2(0.0, texel.y)).rgb + texture(TEXTURE, UV - vec2(0.0, texel.y)).rgb;
		c = max(c + (c - nb * 0.25) * sharpen, 0.0);
	}
	c += texture(bloom_tex, UV).rgb * bloom_amount;
	if (grade > 0.0) {
		// A touch more saturation and contrast, cool shadows and warm highlights.
		float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
		vec3 g = max(mix(vec3(l), c, 1.08), 0.0);
		g = mix(g, g * g * (3.0 - 2.0 * g), 0.12);
		g *= mix(vec3(0.97, 0.98, 1.05), vec3(1.02, 1.0, 0.97), clamp(l * 1.4, 0.0, 1.0));
		c = mix(c, g, grade);
	}
	vec2 d = UV - 0.5;
	c *= 1.0 - vignette * smoothstep(0.35, 0.85, length(d * vec2(1.0, 0.8)));
	// Triangular dither of one 8-bit step, from interleaved gradient noise.
	highp vec2 fc = FRAGCOORD.xy;
	highp float n1 = ign(fc);
	highp float n2 = ign(fc + vec2(47.0, 17.0));
	c += vec3((n1 + n2 - 1.0) / 255.0);
	COLOR = vec4(c, 1.0);
}
"""


## First bloom step for the HDR picture: the scene at quarter size from four bilinear taps,
## decoded to linear light and put through the exposure. The taps are averaged with a mild Karis
## weight (a hot texel can't sparkle), the brightest channel is compared with the threshold through
## a soft knee, and what passes is eased into a cap. The result keeps the pixel's hue, scaled so
## its brightest channel equals the energy that passed.
static func bright_hdr() -> String:
	return POST_HEAD + hdr_glsl() + """
uniform sampler2D src : filter_linear, repeat_disable;
uniform vec2 texel;
uniform float exposure;
uniform float threshold;
vec3 tap(vec2 uv) { return hdr_decode(texture(src, uv).rgb) * exposure; }
float karis(vec3 c) { return 1.0 / (1.0 + max3(c) * %s); }
void fragment() {
	vec3 a = tap(UV + vec2(-texel.x, -texel.y));
	vec3 b = tap(UV + vec2(texel.x, -texel.y));
	vec3 c = tap(UV + vec2(-texel.x, texel.y));
	vec3 d = tap(UV + vec2(texel.x, texel.y));
	float wa = karis(a);
	float wb = karis(b);
	float wc = karis(c);
	float wd = karis(d);
	vec3 avg = (a * wa + b * wb + c * wc + d * wd) / (wa + wb + wc + wd);
	float br = max3(avg);
	float knee = %s;
	float soft = clamp(br - threshold + knee, 0.0, 2.0 * knee);
	soft = soft * soft / (4.0 * knee);
	float e = soft_cap(max(soft, br - threshold), %s, %s);
	COLOR = vec4(avg * (e / max(br, 1e-4)), 1.0);
}
""" % [f(HdrLook.KARIS_STRENGTH), f(HdrLook.BLOOM_KNEE), f(HdrLook.BLOOM_INPUT_CAP), f(HdrLook.BLOOM_INPUT_START)]


## Anamorphic glare: a wide horizontal blur of only the very brightest of the bloom's second
## octave, an exponential falloff over 2 × taps + 1 taps spaced `stride` apart.
static func glare() -> String:
	return POST_HEAD + """
uniform sampler2D src : filter_linear, repeat_disable;
uniform vec2 stride;
void fragment() {
	vec3 sum = vec3(0.0);
	float wsum = 0.0;
	for (int i = -%d; i <= %d; i++) {
		float fi = float(i);
		float w = exp(-abs(fi) * %s);
		sum += max(texture(src, UV + stride * fi).rgb - vec3(%s), vec3(0.0)) * w;
		wsum += w;
	}
	COLOR = vec4(sum * (%s / wsum), 1.0);
}
""" % [HdrLook.GLARE_TAPS, HdrLook.GLARE_TAPS, f(HdrLook.GLARE_FALLOFF), f(HdrLook.GLARE_THRESHOLD), f(HdrLook.GLARE_GAIN)]


## The HDR composite. Scene (with an optional chromatic split and the sharpen, both done on the
## encoded image) decoded to light and exposed, bloom and glare added energy-consciously, ACES tone
## map, then the finish in display space: grade with split toning and lifted blacks, vignette, film
## grain, dither. Unset finish strengths are zero, which switches each effect off.
static func composite_hdr() -> String:
	return """shader_type canvas_item;
render_mode unshaded, blend_disabled;
""" + hdr_glsl() + """
uniform sampler2D bloom_tex : filter_linear, repeat_disable;
uniform sampler2D glare_tex : filter_linear, repeat_disable;
uniform vec2 texel;
uniform float bloom_amount;
uniform float exposure;
uniform float vignette;
uniform float sharpen;
uniform float grade;
uniform float grain;
uniform float grain_time;
uniform float aberration;
uniform float glare_amount;
// ACES per channel, drifting toward a hue-preserving curve in the very bright, so neon stays coloured.
vec3 tonemap(vec3 c) {
	vec3 t = aces(c);
	float m = max3(c);
	float keep = %s * smoothstep(%s, %s, m);
	return mix(t, c * (aces_s(m) / max(m, 1e-4)), keep);
}
void fragment() {
	vec2 d = UV - 0.5;
	vec3 x;
	if (aberration > 0.0) {
		// Red and blue pulled apart along the radius: nothing mid-screen, a pixel or so at the corners.
		vec2 ca = d * (dot(d, d) * aberration);
		x = vec3(texture(TEXTURE, UV + ca).r, texture(TEXTURE, UV).g, texture(TEXTURE, UV - ca).b);
	} else {
		x = texture(TEXTURE, UV).rgb;
	}
	if (sharpen > 0.0) {
		vec3 nb = texture(TEXTURE, UV + vec2(texel.x, 0.0)).rgb + texture(TEXTURE, UV - vec2(texel.x, 0.0)).rgb
			+ texture(TEXTURE, UV + vec2(0.0, texel.y)).rgb + texture(TEXTURE, UV - vec2(0.0, texel.y)).rgb;
		x = max(x + (x - nb * 0.25) * sharpen, 0.0);
	}
	vec3 c = hdr_decode(x) * exposure;
	vec3 glow = texture(bloom_tex, UV).rgb * bloom_amount;
	if (glare_amount > 0.0) {
		glow += texture(glare_tex, UV).rgb * (vec3(%s, %s, %s) * glare_amount);
	}
	// Energy-conserving: capped, and held back where the scene is already bright.
	c += soft_cap_vec(glow, %s, %s) / (1.0 + max3(c) * %s);
	vec3 t = tonemap(c);
	if (grade > 0.0) {
		float l = dot(t, vec3(0.2126, 0.7152, 0.0722));
		vec3 g = max(mix(vec3(l), t, %s), 0.0);
		g = mix(g, g * g * (3.0 - 2.0 * g), %s);
		// Split toning: teal in the shadows, warm in the highlights.
		float shadow = 1.0 - smoothstep(0.0, 0.55, l);
		float light = smoothstep(0.45, 1.0, l);
		g *= mix(vec3(1.0), vec3(%s, %s, %s), shadow * %s);
		g *= mix(vec3(1.0), vec3(%s, %s, %s), light * %s);
		// Lifted blacks: black becomes a faint teal, white stays white.
		g += (1.0 - g) * vec3(%s, %s, %s);
		t = mix(t, g, grade);
	}
	// Vignette: a long, soft falloff with a faint cool cast in the corners.
	float v = smoothstep(%s, %s, length(d * vec2(1.0, 0.8)));
	t *= (1.0 - vignette * v) * mix(vec3(1.0), vec3(%s, %s, %s), v);
	highp vec2 fc = FRAGCOORD.xy;
	if (grain > 0.0) {
		// Film grain: luma grain that is strongest in the mid-tones and fades in highlights and the deepest black.
		float gl = dot(t, vec3(0.2126, 0.7152, 0.0722));
		float w = grain * (1.0 - smoothstep(0.55, 1.0, gl)) * (0.5 + 0.5 * smoothstep(0.0, 0.15, gl));
		t += (ign(fc + vec2(grain_time * 17.0, grain_time * 29.0)) - 0.5) * w;
	}
	// Triangular dither of one 8-bit step.
	highp float n1 = ign(fc);
	highp float n2 = ign(fc + vec2(47.0, 17.0));
	t += vec3((n1 + n2 - 1.0) / 255.0);
	COLOR = vec4(t, 1.0);
}
""" % [
		f(HdrLook.HUE_KEEP), f(HdrLook.HUE_KEEP_FROM), f(HdrLook.HUE_KEEP_TO),
		f(HdrLook.GLARE_TINT_R), f(HdrLook.GLARE_TINT_G), f(HdrLook.GLARE_TINT_B),
		f(HdrLook.BLOOM_ADD_CAP), f(HdrLook.BLOOM_ADD_START), f(HdrLook.BLOOM_SELF_SHADOW),
		f(HdrLook.GRADE_SATURATION), f(HdrLook.GRADE_CONTRAST),
		f(HdrLook.SHADOW_TINT_R), f(HdrLook.SHADOW_TINT_G), f(HdrLook.SHADOW_TINT_B), f(HdrLook.SPLIT_STRENGTH),
		f(HdrLook.HIGHLIGHT_TINT_R), f(HdrLook.HIGHLIGHT_TINT_G), f(HdrLook.HIGHLIGHT_TINT_B), f(HdrLook.SPLIT_STRENGTH),
		f(HdrLook.BLACK_LIFT_R), f(HdrLook.BLACK_LIFT_G), f(HdrLook.BLACK_LIFT_B),
		f(HdrLook.VIGNETTE_FROM), f(HdrLook.VIGNETTE_TO),
		f(HdrLook.VIGNETTE_TINT_R), f(HdrLook.VIGNETTE_TINT_G), f(HdrLook.VIGNETTE_TINT_B),
	]


## Background gradients (BG_FS / BG_HDR_FS): vertical bands behind a 3D picture, on a quad just
## inside the far plane; bands are given as fractions of the image height from the top. For the
## HDR picture the authored (display) colour is inverse tone-mapped so it comes out unchanged.
static func background() -> String:
	return """shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never, skip_vertex_transform;
""" + hdr_glsl() + out_glsl() + """
uniform int bands = 0;
uniform vec4 tops[8];
uniform vec4 bottoms[8];
uniform vec2 spans[8];
uniform bool hdr = false;
uniform float exposure = 1.0;
uniform float far_depth = 8000.0;

void vertex() {
	// The quad's corners (-1..1) through the inverse projection to the far end of the view.
	vec4 v = INV_PROJECTION_MATRIX * vec4(VERTEX.xy * 2.0, 0.0, 1.0);
	vec3 dir = v.xyz / v.w;
	dir /= -dir.z;
	VERTEX = dir * far_depth;
	POSITION = PROJECTION_MATRIX * vec4(VERTEX, 1.0);
}

void fragment() {
	float y = SCREEN_UV.y;
	vec3 c = vec3(0.0);
	bool hit = false;
	for (int i = 0; i < 8; i++) {
		if (i >= bands) break;
		if (y >= spans[i].x && y < spans[i].y) {
			float t = (y - spans[i].x) / max(spans[i].y - spans[i].x, 1e-5);
			c = mix(tops[i].rgb, bottoms[i].rgb, t);
			hit = true;
		}
	}
	if (!hit) discard;
	ALBEDO = pa_out(hdr ? hdr_encode(aces_inv(c) / max(exposure, 0.05)) : c);
}
"""


## Screen-space particles (PARTICLE_FS / PARTICLE_HDR_FS), solid: their colours are authored as
## display values; in the HDR picture they are brought back to light so the tone map returns them,
## with headroom above one so bright sparks bloom. Drawn alpha-blended.
static func particle() -> String:
	return """shader_type canvas_item;
render_mode unshaded, blend_mix;
""" + hdr_glsl() + """
uniform bool hdr = false;
uniform float exposure = 1.0;
void fragment() {
	vec4 c = COLOR;
	if (hdr) {
		c.rgb = hdr_encode(aces_inv(c.rgb) / max(exposure, 0.05));
	}
	COLOR = c;
}
"""


## The soft round glow under bright particles (PARTICLE_FS with uSoft = 1): it fades to nothing at
## the quad's edge as (1 - r)², added on top (SRC_ALPHA, ONE).
static func particle_glow() -> String:
	return """shader_type canvas_item;
render_mode unshaded, blend_add;
""" + hdr_glsl() + """
uniform bool hdr = false;
uniform float exposure = 1.0;
void fragment() {
	float d = clamp(1.0 - length(UV * 2.0 - 1.0), 0.0, 1.0);
	float soft = d * d;
	vec3 rgb = COLOR.rgb;
	if (hdr) {
		rgb = hdr_encode(aces_inv(rgb) / max(exposure, 0.05));
	}
	COLOR = vec4(rgb, COLOR.a * soft);
}
"""
