// engine/gl/GlShaders.kt HDR_GLSL, mirrored by HdrMath: the HDR picture's shared maths.
const float ENC_MAX = 0.96;
const float ACES_INV_MAX = 0.985;
float max3(vec3 v) { return max(v.r, max(v.g, v.b)); }
// The scene image stores c / (1 + max(c)): bounded, reversible, blends like a display colour.
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
// Display value back to linear light: the closed-form inverse of the ACES fit above.
vec3 aces_inv(vec3 y) {
	y = clamp(y, vec3(0.0), vec3(ACES_INV_MAX));
	vec3 a = 2.43 * y - 2.51;
	vec3 b = 0.59 * y - 0.03;
	vec3 c = 0.14 * y;
	return (b + sqrt(max(b * b - 4.0 * a * c, vec3(0.0)))) / (2.0 * (2.51 - 2.43 * y));
}
// Identity up to `start`, then an exponential ease into `cap`.
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
