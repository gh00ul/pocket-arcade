// engine/gl/GlShaders.kt COMPOSITE_FS / COMPOSITE_HDR_FS: the 3D picture onto the screen.
// LDR: scene plus bloom, sharpen, grade, vignette, dither. HDR: decode, exposure, bloom, ACES
// tone map with hue keep, grade with split toning, tinted vignette, grain, dither.
// (Bloom, glare and the finish are wired by the post chain: see scripts/engine/gl/post_chain.gd.)
shader_type canvas_item;
#include "res://shaders/hdr.gdshaderinc"

uniform bool hdr = false;
uniform float exposure = 1.0;
uniform float hue_keep = 0.4;
uniform float hue_keep_from = 0.9;
uniform float hue_keep_to = 3.0;

vec3 tonemap_hdr(vec3 c) {
	vec3 t = aces(c);
	float m = max3(c);
	float keep = hue_keep * smoothstep(hue_keep_from, hue_keep_to, m);
	return mix(t, c * (aces_s(m) / max(m, 1e-4)), keep);
}

void fragment() {
	vec3 x = texture(TEXTURE, UV).rgb;
	vec3 t;
	if (hdr) {
		t = tonemap_hdr(hdr_decode(x) * exposure);
	} else {
		t = x;
	}
	// Triangular dither of one 8-bit step.
	vec2 fc = FRAGCOORD.xy;
	float n1 = ign(fc);
	float n2 = ign(fc + vec2(47.0, 17.0));
	t += vec3((n1 + n2 - 1.0) / 255.0);
	COLOR = vec4(t, 1.0);
}
