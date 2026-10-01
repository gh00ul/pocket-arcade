// The soft additive glow under bright particles (PARTICLE_FS with uSoft = 1, blended SRC_ALPHA, ONE).
// Their colours are authored as display values; in the HDR picture they are brought back to light
// (and encoded) so the tone map returns them, with headroom so bright sparks bloom.
shader_type canvas_item;
render_mode blend_add;
#include "res://shaders/hdr.gdshaderinc"

uniform bool hdr = false;
uniform float exposure = 1.0;

void fragment() {
	vec4 c = COLOR * texture(TEXTURE, UV);
	if (hdr) {
		c.rgb = hdr_encode(aces_inv(c.rgb) / max(exposure, 0.05));
	}
	COLOR = c;
}
