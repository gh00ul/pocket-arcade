package com.pocketarcade.engine.gl

/**
 * GLSL ES 3.00 sources for the scene, background and post-processing passes.
 *
 * There are two pictures. The LDR one (`SCENE_FS`, `BRIGHT_FS`, `COMPOSITE_FS`, ...) tone-maps each
 * fragment as it is shaded, into an RGBA8 target: unchanged since before HDR existed. The HDR one
 * (the `*_HDR_*` sources, and `SCENE_FS` built with `HDR_OUT` defined) writes linear light,
 * squeezed reversibly into an RGBA16F target (see [HdrLook.ENC_MAX]), and does exposure, bloom,
 * tone mapping and the cinematic finish in the composite.
 */
internal object GlShaders {
    /**
     * Shared GLSL of the HDR pictures, mirrored in Kotlin by [HdrMath] (which the tests pin).
     * Needs `precision highp float` in scope.
     */
    const val HDR_GLSL = """
const float ENC_MAX = ${HdrLook.ENC_MAX};
float max3(vec3 v) { return max(v.r, max(v.g, v.b)); }
// The scene image stores c / (1 + max(c)): bounded, reversible, and it multisamples and blends
// like a display-referred colour would.
vec3 hdrEncode(vec3 c) {
    c = max(c, vec3(0.0));
    return c / (1.0 + max3(c));
}
vec3 hdrDecode(vec3 x) {
    float m = max3(x);
    x *= min(1.0, ENC_MAX / max(m, 1e-5));
    return x / (1.0 - min(m, ENC_MAX));
}
vec3 aces(vec3 c) {
    return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}
float acesS(float c) {
    return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}
// Display value back to linear light (what LDR sources such as gradients and particles are
// authored in): the closed-form inverse of the ACES fit above.
vec3 acesInv(vec3 y) {
    y = clamp(y, vec3(0.0), vec3(${HdrLook.ACES_INV_MAX}));
    vec3 a = 2.43 * y - 2.51;
    vec3 b = 0.59 * y - 0.03;
    vec3 c = 0.14 * y;
    return (b + sqrt(max(b * b - 4.0 * a * c, vec3(0.0)))) / (2.0 * (2.51 - 2.43 * y));
}
// Identity up to `start`, then an exponential ease into `cap`, never above it, no kink.
float softCap(float x, float cap, float start) {
    float r = max(cap - start, 1e-4);
    return x <= start ? x : start + r * (1.0 - exp(-(x - start) / r));
}
vec3 softCapVec(vec3 c, float cap, float start) {
    float m = max3(c);
    return m > start ? c * (softCap(m, cap, start) / m) : c;
}
"""

    const val SCENE_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUV;
layout(location = 3) in vec4 aColor;
layout(location = 4) in vec4 aExtra;
uniform mat4 uModel;
uniform vec3 uEye;
uniform vec3 uRight;
uniform vec3 uUp;
uniform vec3 uFwd;
uniform vec4 uProj;
uniform vec2 uDepth;
uniform vec4 uTint;
uniform float uEmissiveMul;
uniform float uMirror;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUV;
out vec4 vColor;
out vec4 vExtra;
out float vDepth;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    // The floor-reflection pass draws the scene upside down about y = 0 (uMirror = -1).
    w.y *= uMirror;
    vWorld = w.xyz;
    // Normals go through the cofactor (inverse transpose × det) of the model matrix so they
    // stay perpendicular under non-uniform stretch; flipped back when the matrix mirrors.
    mat3 m = mat3(uModel);
    mat3 cof = mat3(cross(m[1], m[2]), cross(m[2], m[0]), cross(m[0], m[1]));
    vNormal = cof * aNormal * (dot(m[0], cof[0]) < 0.0 ? -1.0 : 1.0);
    vNormal.y *= uMirror;
    vec3 d = w.xyz - uEye;
    float x = dot(d, uRight);
    float y = dot(d, uUp);
    float z = dot(d, uFwd);
    vDepth = z;
    gl_Position = vec4(uProj.x * x + uProj.y * z, uProj.z * y + uProj.w * z, uDepth.x * z + uDepth.y * aExtra.y, z);
    vUV = aUV;
    vColor = aColor * uTint;
    vExtra = vec4(aExtra.x * uEmissiveMul, aExtra.y, aExtra.z, aExtra.w);
}
"""

    const val SCENE_FS = """#version 300 es
precision highp float;
precision highp int;
in vec3 vWorld;
in vec3 vNormal;
in vec2 vUV;
in vec4 vColor;
in vec4 vExtra;
in float vDepth;
uniform sampler2D uTex;
uniform highp sampler2D uGrid;
uniform vec4 uGridInfo;
uniform ivec2 uGridSize;
uniform vec4 uLightPos[64];
uniform vec4 uLightCol[64];
uniform vec3 uAmbient;
uniform vec3 uDirDir;
uniform vec3 uDirCol;
uniform vec3 uEye;
uniform vec3 uFog;
uniform float uAlphaCut;
uniform float uExposure;
uniform float uRim;
uniform float uFloorGlow;
uniform float uMirror;
uniform lowp samplerCube uEnv;
uniform float uEnvAmount;
uniform float uGlass;
uniform sampler2D uRefl;
uniform vec4 uReflInfo;
uniform float uReflStreak;
out vec4 outColor;

#ifdef HDR_OUT
${HDR_GLSL}
const float EMISSIVE_GAIN = ${HdrLook.EMISSIVE_GAIN};
// Lit paint (not glints, not reflections) may not climb past a ceiling in exposed linear light,
// so a pale surface under many lamps can never cross the bloom threshold and glow white.
vec3 limitLit(vec3 c) {
    float m = max3(c) * uExposure;
    return m > ${HdrLook.LIT_START} ? c * (softCap(m, ${HdrLook.LIT_CEILING}, ${HdrLook.LIT_START}) / m) : c;
}
#endif

vec3 tonemap(vec3 c) {
    c *= uExposure;
    return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    // Floor-reflection pass: only glowing things, and nothing lying on the floor itself.
    if (uMirror < 0.0 && (vExtra.x <= 0.0 || vWorld.y > -2.0)) discard;
    // Textures are stored premultiplied (clean filtering at cut-out edges); undo it here.
    vec4 tex = texture(uTex, vUV);
    float alpha = tex.a * vColor.a;
    if (alpha < uAlphaCut) discard;
    vec3 base = tex.rgb / max(tex.a, 0.004) * vColor.rgb;
    vec3 col;
    vec3 refl = vec3(0.0);
    if (vExtra.x > 0.0) {
#ifdef HDR_OUT
        col = base * (vExtra.x * EMISSIVE_GAIN);
#else
        col = base * vExtra.x;
#endif
    } else {
        vec3 n = normalize(vNormal);
        vec3 toEye = uEye - vWorld;
        if (dot(n, toEye) < 0.0) n = -n;
        vec3 V = normalize(toEye);
        // Hemisphere ambient: faces turned up catch more of the room's bounce light.
        vec3 L = uAmbient * (0.8 + 0.4 * n.y);
        vec3 P = vec3(0.0);
        float dd = dot(n, uDirDir);
        // Gloss sets both the highlight's strength and its tightness (glossier = sharper).
        float gloss = vExtra.z;
        float specPow = 16.0 + 64.0 * gloss * gloss;
        float specK = gloss * sqrt((specPow + 8.0) / 56.0);
        vec3 S = vec3(0.0);
        if (dd > 0.0) {
            L += uDirCol * dd;
            if (gloss > 0.0) {
                vec3 h = normalize(uDirDir + V);
                S += uDirCol * (pow(max(dot(n, h), 0.0), specPow) * specK);
            }
        }
        ivec2 cell = ivec2(floor((vWorld.xz - uGridInfo.xy) * uGridInfo.z));
        if (cell.x >= 0 && cell.y >= 0 && cell.x < uGridSize.x && cell.y < uGridSize.y) {
            for (int t = 0; t < 2; t++) {
                vec4 ids = texelFetch(uGrid, ivec2(cell.x * 2 + t, cell.y), 0) * 255.0;
                for (int k = 0; k < 4; k++) {
                    int id = int(ids[k] + 0.5) - 1;
                    if (id >= 0) {
                        vec4 lp = uLightPos[id];
                        vec3 dv = lp.xyz - vWorld;
                        float d2 = dot(dv, dv);
                        if (d2 < lp.w * lp.w) {
                            float dist = sqrt(d2);
                            vec3 ld = dv / max(dist, 0.001);
                            float fall = 1.0 - dist / lp.w;
                            fall *= fall;
                            vec4 lc = uLightCol[id];
                            float lam = max(dot(n, ld) * 0.6 + 0.4, 0.0);
                            vec3 reach = lc.rgb * (fall * lc.a);
                            L += reach * lam;
                            P += reach;
                            if (gloss > 0.0) {
                                vec3 h = normalize(ld + V);
                                S += reach * (pow(max(dot(n, h), 0.0), specPow) * specK * 1.6);
                            }
                        }
                    }
                }
            }
        }
#ifdef HDR_OUT
        col = base * min(L, vec3(4.0));
        vec3 glint = S;
#else
        col = base * min(L, vec3(4.0)) + S;
#endif
        float nv = max(dot(n, V), 0.0);
        float fr = 1.0 - nv;
        if (gloss > 0.0 && uEnvAmount > 0.0) {
            // The room reflected (added on top): sharper for glossier surfaces; very glossy
            // opaque things (chrome, balls) reflect strongly, tinted by their colour. Downward
            // reflections are bent toward the horizon, where the room's glow is, so upright
            // chrome and glass seen from above still pick something up.
            vec3 R = reflect(-V, n);
            if (R.y < 0.0) R.y *= 0.4;
            vec4 es = textureLod(uEnv, R, (1.0 - gloss) * 5.0);
            vec3 e = es.rgb * es.rgb * 4.0;
            float metal = smoothstep(0.72, 0.9, gloss) * (1.0 - uGlass);
            // Glass keeps a physically faint face-on sheen that climbs toward grazing angles.
            float f0 = mix(0.04, 0.35, metal);
            float fp = fr * fr * fr * mix(1.0, fr, uGlass);
            float F = (f0 + (1.0 - f0) * fp) * gloss * gloss * uEnvAmount * (1.0 - 0.4 * uGlass);
            float mx = max(base.r, max(base.g, base.b));
            float sat = (mx - min(base.r, min(base.g, base.b))) / max(mx, 0.001);
            vec3 hue = mix(vec3(1.0), base / max(mx, 0.001), metal * sat);
            refl += e * hue * F;
        }
        if (uReflInfo.z + uReflInfo.w > 0.0 && n.y > 0.85 && vWorld.y < 3.0 && vWorld.y > -4.0) {
            // Glowing things reflected in the floor; the floor's own texture ripples them a
            // little. Either the mirror pass at this pixel, or (uReflStreak > 0) last frame's
            // glow gathered from up the screen, where whatever stands behind this spot is.
            vec2 ruv = gl_FragCoord.xy * uReflInfo.xy;
            ruv.y += (dot(base, vec3(0.333)) - 0.3) * 0.012 * gloss;
            vec3 rc;
            if (uReflStreak > 0.0) {
                rc = texture(uRefl, ruv + vec2(0.0, 0.2 * uReflStreak)).rgb * 0.5
                    + texture(uRefl, ruv + vec2(0.0, 0.55 * uReflStreak)).rgb * 0.35
                    + texture(uRefl, ruv + vec2(0.0, uReflStreak)).rgb * 0.25;
            } else {
                rc = texture(uRefl, ruv).rgb;
            }
#ifdef HDR_OUT
            // The streaks are read from the bloom chain, which holds linear light here; the
            // mirror image is already a display-referred RGBA8 picture.
            if (uReflStreak > 0.0) rc = aces(rc);
#endif
            // A light floor's own glow is in that buffer too; left alone it would feed on itself
            // frame after frame until pale tiles burn white. A bright floor washes out a
            // reflection anyway, so fade the reflection out on light paint and cap what it adds.
            float lum = dot(base, vec3(0.3, 0.59, 0.11));
            float dark = 1.0 - clamp((lum - 0.25) * 2.0, 0.0, 1.0);
            refl += min(rc, vec3(1.2)) * (dark * (gloss * (0.3 + 0.7 * fr * fr * fr) * uReflInfo.z + uReflInfo.w));
        }
        // Rim light: a Fresnel sheen tinted by the lights nearby, so figures and cabinets
        // stand out from the dark room.
        float fres = 1.0 - max(dot(n, V), 0.0);
        fres *= fres;
        fres *= fres;
        col += (uAmbient + min(P, vec3(1.5)) * 0.4) * (fres * uRim) * (base * 0.7 + 0.3);
        // Blacklight: saturated paint on the floor fluoresces.
        if (uFloorGlow > 0.0 && n.y > 0.9 && vWorld.y < 1.5) {
            float sat = max(base.r, max(base.g, base.b)) - min(base.r, min(base.g, base.b));
            col += base * (sat * uFloorGlow);
        }
#ifdef HDR_OUT
        col = limitLit(col) + glint;
#endif
    }
    if (vExtra.w > 0.5 && vDepth > uFog.x) {
        float f = max(1.0 - (vDepth - uFog.x) / max(uFog.y - uFog.x, 1.0), uFog.z);
        col *= f;
        refl *= f;
    }
    if (uGlass > 0.5) {
        // See-through glass: the reflection is its own layer on top, so it shows even where
        // the pane is nearly clear (it covers a little more of what's behind as it brightens).
#ifdef HDR_OUT
        float k = acesS(max3(refl) * uExposure);
        float a = clamp(alpha + (1.0 - alpha) * k, 0.0, 1.0);
        outColor = vec4(hdrEncode((col * alpha + refl) / max(a, 0.001)), a);
#else
        vec3 t = tonemap(col);
        vec3 r = tonemap(refl);
        float k = max(r.r, max(r.g, r.b));
        float a = clamp(alpha + (1.0 - alpha) * k, 0.0, 1.0);
        outColor = vec4((t * alpha + r) / max(a, 0.001), a);
#endif
    } else {
#ifdef HDR_OUT
        outColor = vec4(hdrEncode(col + refl), alpha);
#else
        outColor = vec4(tonemap(col + refl), alpha);
#endif
    }
}
"""

    /** The scene shader for the HDR picture: the same source with the linear-light paths switched on. */
    val SCENE_FS_HDR: String = SCENE_FS.replaceFirst("#version 300 es\n", "#version 300 es\n#define HDR_OUT\n")

    /** Screen-space particle quads: positions already in clip space, local coordinates for the glow. */
    const val PARTICLE_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aLocal;
layout(location = 2) in vec4 aColor;
out vec2 vLocal;
out vec4 vColor;
void main() {
    vLocal = aLocal;
    vColor = aColor;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    /** A solid particle, or (uSoft = 1) a round glow that fades to nothing at the quad's edge. */
    const val PARTICLE_FS = """#version 300 es
precision mediump float;
in vec2 vLocal;
in vec4 vColor;
uniform float uSoft;
out vec4 outColor;
void main() {
    float f = 1.0;
    if (uSoft > 0.5) {
        float d = clamp(1.0 - length(vLocal), 0.0, 1.0);
        f = d * d;
    }
    outColor = vec4(vColor.rgb, vColor.a * f);
}
"""

    /** Background gradients: positions already in clip space, colours per vertex. */
    const val BG_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec3 aColor;
out vec3 vColor;
void main() {
    vColor = aColor;
    gl_Position = vec4(aPos, 1.0, 1.0);
}
"""

    const val BG_FS = """#version 300 es
precision mediump float;
in vec3 vColor;
out vec4 outColor;
void main() {
    outColor = vec4(vColor, 1.0);
}
"""

    /** Full-screen triangle strip for post-processing; uv from the quad corners. */
    const val POST_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vUV;
void main() {
    vUV = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    /**
     * First bloom step: the scene at quarter size (4 bilinear taps cover each 4×4 block), keeping
     * what is brighter than [uThreshold] with a soft knee so glows fade in rather than pop.
     */
    const val BRIGHT_FS = """#version 300 es
precision mediump float;
in vec2 vUV;
uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uThreshold;
out vec4 outColor;
void main() {
    vec3 c = texture(uTex, vUV + vec2(-uTexel.x, -uTexel.y)).rgb;
    c += texture(uTex, vUV + vec2(uTexel.x, -uTexel.y)).rgb;
    c += texture(uTex, vUV + vec2(-uTexel.x, uTexel.y)).rgb;
    c += texture(uTex, vUV + vec2(uTexel.x, uTexel.y)).rgb;
    c *= 0.25;
    float br = max(c.r, max(c.g, c.b));
    const float knee = 0.1;
    float soft = clamp(br - uThreshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float k = max(soft, br - uThreshold) / max(1.0 - uThreshold, 0.05);
    outColor = vec4(c * k, 1.0);
}
"""

    /** Dual-filter downsample: halves the image with a 5-tap kernel (the next bloom octave). */
    const val DOWN_FS = """#version 300 es
precision mediump float;
in vec2 vUV;
uniform sampler2D uTex;
uniform vec2 uTexel;
out vec4 outColor;
void main() {
    vec3 c = texture(uTex, vUV).rgb * 4.0;
    c += texture(uTex, vUV - uTexel).rgb;
    c += texture(uTex, vUV + uTexel).rgb;
    c += texture(uTex, vUV + vec2(uTexel.x, -uTexel.y)).rgb;
    c += texture(uTex, vUV + vec2(-uTexel.x, uTexel.y)).rgb;
    outColor = vec4(c * 0.125, 1.0);
}
"""

    /** 9-tap tent upsample of a smaller octave, weighted by [uWeight] and added on top. */
    const val UP_FS = """#version 300 es
precision mediump float;
in vec2 vUV;
uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uWeight;
out vec4 outColor;
void main() {
    vec2 d = uTexel;
    vec3 c = texture(uTex, vUV).rgb * 4.0;
    c += (texture(uTex, vUV + vec2(d.x, 0.0)).rgb + texture(uTex, vUV - vec2(d.x, 0.0)).rgb
        + texture(uTex, vUV + vec2(0.0, d.y)).rgb + texture(uTex, vUV - vec2(0.0, d.y)).rgb) * 2.0;
    c += texture(uTex, vUV + d).rgb + texture(uTex, vUV - d).rgb
        + texture(uTex, vUV + vec2(d.x, -d.y)).rgb + texture(uTex, vUV + vec2(-d.x, d.y)).rgb;
    outColor = vec4(c * (uWeight / 16.0), 1.0);
}
"""

    /**
     * Scene plus bloom, then a light sharpen (the scene is upscaled from the render scale), a
     * gentle colour grade, a soft vignette and a dither that hides banding in dark gradients.
     */
    const val COMPOSITE_FS = """#version 300 es
precision mediump float;
in vec2 vUV;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform vec2 uTexel;
uniform float uBloomAmount;
uniform float uVignette;
uniform float uSharpen;
uniform float uGrade;
out vec4 outColor;
void main() {
    vec3 c = texture(uScene, vUV).rgb;
    if (uSharpen > 0.0) {
        vec3 nb = texture(uScene, vUV + vec2(uTexel.x, 0.0)).rgb + texture(uScene, vUV - vec2(uTexel.x, 0.0)).rgb
            + texture(uScene, vUV + vec2(0.0, uTexel.y)).rgb + texture(uScene, vUV - vec2(0.0, uTexel.y)).rgb;
        c = max(c + (c - nb * 0.25) * uSharpen, 0.0);
    }
    c += texture(uBloom, vUV).rgb * uBloomAmount;
    if (uGrade > 0.0) {
        // A touch more saturation and contrast, cool shadows and warm highlights.
        float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
        vec3 g = max(mix(vec3(l), c, 1.08), 0.0);
        g = mix(g, g * g * (3.0 - 2.0 * g), 0.12);
        g *= mix(vec3(0.97, 0.98, 1.05), vec3(1.02, 1.0, 0.97), clamp(l * 1.4, 0.0, 1.0));
        c = mix(c, g, uGrade);
    }
    vec2 d = vUV - 0.5;
    c *= 1.0 - uVignette * smoothstep(0.35, 0.85, length(d * vec2(1.0, 0.8)));
    // Triangular dither of one 8-bit step, from interleaved gradient noise.
    highp vec2 fc = gl_FragCoord.xy;
    highp float n1 = fract(52.9829189 * fract(dot(fc, vec2(0.06711056, 0.00583715))));
    highp float n2 = fract(52.9829189 * fract(dot(fc + vec2(47.0, 17.0), vec2(0.06711056, 0.00583715))));
    c += vec3((n1 + n2 - 1.0) / 255.0);
    outColor = vec4(c, 1.0);
}
"""

    // ------------------------------------------------------------------ the HDR picture

    /** Background gradients for the HDR picture: the authored (display) colour, inverse tone-mapped so it comes out unchanged. */
    val BG_HDR_FS: String = """#version 300 es
precision highp float;
in vec3 vColor;
uniform float uExposure;
out vec4 outColor;
$HDR_GLSL
void main() {
    outColor = vec4(hdrEncode(acesInv(vColor) / max(uExposure, 0.05)), 1.0);
}
"""

    /**
     * Particles for the HDR picture. Their colours are authored as display values: bring them
     * back to light so the tone map returns them, with headroom above one so bright sparks bloom.
     */
    val PARTICLE_HDR_FS: String = """#version 300 es
precision highp float;
in vec2 vLocal;
in vec4 vColor;
uniform float uSoft;
uniform float uExposure;
out vec4 outColor;
$HDR_GLSL
void main() {
    float f = 1.0;
    if (uSoft > 0.5) {
        float d = clamp(1.0 - length(vLocal), 0.0, 1.0);
        f = d * d;
    }
    outColor = vec4(hdrEncode(acesInv(vColor.rgb) / max(uExposure, 0.05)), vColor.a * f);
}
"""

    /**
     * First bloom step for the HDR picture: the scene at quarter size from four bilinear taps,
     * decoded to linear light and put through the exposure. The taps are averaged with a mild
     * Karis weight (a hot texel can't sparkle), the brightest channel is compared with the
     * threshold through a soft knee, and what passes is eased into a cap. The result keeps the
     * pixel's hue, scaled so its brightest channel equals the energy that passed.
     */
    val BRIGHT_HDR_FS: String = """#version 300 es
precision highp float;
in vec2 vUV;
uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uExposure;
uniform float uThreshold;
out vec4 outColor;
$HDR_GLSL
vec3 tap(vec2 o) { return hdrDecode(texture(uTex, vUV + o).rgb) * uExposure; }
float karis(vec3 c) { return 1.0 / (1.0 + max3(c) * ${HdrLook.KARIS_STRENGTH}); }
void main() {
    vec3 a = tap(vec2(-uTexel.x, -uTexel.y));
    vec3 b = tap(vec2(uTexel.x, -uTexel.y));
    vec3 c = tap(vec2(-uTexel.x, uTexel.y));
    vec3 d = tap(vec2(uTexel.x, uTexel.y));
    float wa = karis(a);
    float wb = karis(b);
    float wc = karis(c);
    float wd = karis(d);
    vec3 avg = (a * wa + b * wb + c * wc + d * wd) / (wa + wb + wc + wd);
    float br = max3(avg);
    float knee = ${HdrLook.BLOOM_KNEE};
    float soft = clamp(br - uThreshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float e = softCap(max(soft, br - uThreshold), ${HdrLook.BLOOM_INPUT_CAP}, ${HdrLook.BLOOM_INPUT_START});
    outColor = vec4(avg * (e / max(br, 1e-4)), 1.0);
}
"""

    /**
     * Anamorphic glare: a wide horizontal blur of only the very brightest of the bloom's first
     * octave, an exponential falloff over 2 * taps + 1 taps spaced [uStep] apart.
     */
    val GLARE_FS: String = """#version 300 es
precision highp float;
in vec2 vUV;
uniform sampler2D uTex;
uniform vec2 uStep;
out vec4 outColor;
void main() {
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = -${HdrLook.GLARE_TAPS}; i <= ${HdrLook.GLARE_TAPS}; i++) {
        float fi = float(i);
        float w = exp(-abs(fi) * ${HdrLook.GLARE_FALLOFF});
        sum += max(texture(uTex, vUV + uStep * fi).rgb - vec3(${HdrLook.GLARE_THRESHOLD}), vec3(0.0)) * w;
        wsum += w;
    }
    outColor = vec4(sum * (${HdrLook.GLARE_GAIN} / wsum), 1.0);
}
"""

    /**
     * The HDR composite. Scene (with an optional chromatic split and the sharpen, both done on the
     * encoded image) decoded to light and exposed, bloom and glare added energy-consciously,
     * ACES tone map, then the finish in display space: grade with split toning and lifted
     * blacks, vignette, film grain, dither. Unset finish strengths are zero, which switches
     * each effect off.
     */
    val COMPOSITE_HDR_FS: String = """#version 300 es
precision highp float;
in vec2 vUV;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform sampler2D uGlare;
uniform vec2 uTexel;
uniform float uBloomAmount;
uniform float uExposure;
uniform float uVignette;
uniform float uSharpen;
uniform float uGrade;
uniform float uGrain;
uniform float uTime;
uniform float uAberration;
uniform float uGlareAmount;
out vec4 outColor;
$HDR_GLSL
// ACES per channel, drifting toward a hue-preserving curve in the very bright, so neon stays coloured.
vec3 tonemap(vec3 c) {
    vec3 t = aces(c);
    float m = max3(c);
    float keep = ${HdrLook.HUE_KEEP} * smoothstep(${HdrLook.HUE_KEEP_FROM}, ${HdrLook.HUE_KEEP_TO}, m);
    return mix(t, c * (acesS(m) / max(m, 1e-4)), keep);
}
highp float ign(highp vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}
void main() {
    vec2 d = vUV - 0.5;
    vec3 x;
    if (uAberration > 0.0) {
        // Red and blue pulled apart along the radius: nothing mid-screen, a pixel or so at the corners.
        vec2 ca = d * (dot(d, d) * uAberration);
        x = vec3(texture(uScene, vUV + ca).r, texture(uScene, vUV).g, texture(uScene, vUV - ca).b);
    } else {
        x = texture(uScene, vUV).rgb;
    }
    if (uSharpen > 0.0) {
        vec3 nb = texture(uScene, vUV + vec2(uTexel.x, 0.0)).rgb + texture(uScene, vUV - vec2(uTexel.x, 0.0)).rgb
            + texture(uScene, vUV + vec2(0.0, uTexel.y)).rgb + texture(uScene, vUV - vec2(0.0, uTexel.y)).rgb;
        x = max(x + (x - nb * 0.25) * uSharpen, 0.0);
    }
    vec3 c = hdrDecode(x) * uExposure;
    vec3 glow = texture(uBloom, vUV).rgb * uBloomAmount;
    if (uGlareAmount > 0.0) {
        glow += texture(uGlare, vUV).rgb * (vec3(${HdrLook.GLARE_TINT_R}, ${HdrLook.GLARE_TINT_G}, ${HdrLook.GLARE_TINT_B}) * uGlareAmount);
    }
    // Energy-conserving: capped, and held back where the scene is already bright.
    c += softCapVec(glow, ${HdrLook.BLOOM_ADD_CAP}, ${HdrLook.BLOOM_ADD_START}) / (1.0 + max3(c) * ${HdrLook.BLOOM_SELF_SHADOW});
    vec3 t = tonemap(c);
    if (uGrade > 0.0) {
        float l = dot(t, vec3(0.2126, 0.7152, 0.0722));
        vec3 g = max(mix(vec3(l), t, ${HdrLook.GRADE_SATURATION}), 0.0);
        g = mix(g, g * g * (3.0 - 2.0 * g), ${HdrLook.GRADE_CONTRAST});
        // Split toning: teal in the shadows, warm in the highlights.
        float shadow = 1.0 - smoothstep(0.0, 0.55, l);
        float light = smoothstep(0.45, 1.0, l);
        g *= mix(vec3(1.0), vec3(${HdrLook.SHADOW_TINT_R}, ${HdrLook.SHADOW_TINT_G}, ${HdrLook.SHADOW_TINT_B}), shadow * ${HdrLook.SPLIT_STRENGTH});
        g *= mix(vec3(1.0), vec3(${HdrLook.HIGHLIGHT_TINT_R}, ${HdrLook.HIGHLIGHT_TINT_G}, ${HdrLook.HIGHLIGHT_TINT_B}), light * ${HdrLook.SPLIT_STRENGTH});
        // Lifted blacks: black becomes a faint teal, white stays white.
        g += (1.0 - g) * vec3(${HdrLook.BLACK_LIFT_R}, ${HdrLook.BLACK_LIFT_G}, ${HdrLook.BLACK_LIFT_B});
        t = mix(t, g, uGrade);
    }
    // Vignette: a long, soft falloff with a faint cool cast in the corners.
    float v = smoothstep(${HdrLook.VIGNETTE_FROM}, ${HdrLook.VIGNETTE_TO}, length(d * vec2(1.0, 0.8)));
    t *= (1.0 - uVignette * v) * mix(vec3(1.0), vec3(${HdrLook.VIGNETTE_TINT_R}, ${HdrLook.VIGNETTE_TINT_G}, ${HdrLook.VIGNETTE_TINT_B}), v);
    highp vec2 fc = gl_FragCoord.xy;
    if (uGrain > 0.0) {
        // Film grain: luma grain that is strongest in the mid-tones and fades in highlights and the deepest black.
        float gl = dot(t, vec3(0.2126, 0.7152, 0.0722));
        float w = uGrain * (1.0 - smoothstep(0.55, 1.0, gl)) * (0.5 + 0.5 * smoothstep(0.0, 0.15, gl));
        t += (ign(fc + vec2(uTime * 17.0, uTime * 29.0)) - 0.5) * w;
    }
    // Triangular dither of one 8-bit step.
    highp float n1 = ign(fc);
    highp float n2 = ign(fc + vec2(47.0, 17.0));
    t += vec3((n1 + n2 - 1.0) / 255.0);
    outColor = vec4(t, 1.0);
}
"""
}
