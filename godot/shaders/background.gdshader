// engine/gl/GlShaders.kt BG_FS / BG_HDR_FS: vertical gradient bands behind a 3D picture.
// A quad placed just inside the far plane; bands are given as fractions of the image height.
shader_type spatial;
render_mode unshaded, cull_disabled, depth_draw_never, skip_vertex_transform;
#include "res://shaders/hdr.gdshaderinc"

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
	ALBEDO = hdr ? hdr_encode(aces_inv(c) / max(exposure, 0.05)) : c;
}
