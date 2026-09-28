package com.pocketarcade.engine.gl

/** GLSL ES 3.00 sources for the scene, background and post-processing passes. */
internal object GlShaders {
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
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUV;
out vec4 vColor;
out vec4 vExtra;
out float vDepth;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    // Normals go through the cofactor (inverse transpose × det) of the model matrix so they
    // stay perpendicular under non-uniform stretch; flipped back when the matrix mirrors.
    mat3 m = mat3(uModel);
    mat3 cof = mat3(cross(m[1], m[2]), cross(m[2], m[0]), cross(m[0], m[1]));
    vNormal = cof * aNormal * (dot(m[0], cof[0]) < 0.0 ? -1.0 : 1.0);
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
out vec4 outColor;

vec3 tonemap(vec3 c) {
    c *= uExposure;
    return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    // Textures are stored premultiplied (clean filtering at cut-out edges); undo it here.
    vec4 tex = texture(uTex, vUV);
    float alpha = tex.a * vColor.a;
    if (alpha < uAlphaCut) discard;
    vec3 base = tex.rgb / max(tex.a, 0.004) * vColor.rgb;
    vec3 col;
    if (vExtra.x > 0.0) {
        col = base * vExtra.x;
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
        col = base * min(L, vec3(4.0)) + S;
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
    }
    if (vExtra.w > 0.5 && vDepth > uFog.x) {
        float f = max(1.0 - (vDepth - uFog.x) / max(uFog.y - uFog.x, 1.0), uFog.z);
        col *= f;
    }
    outColor = vec4(tonemap(col), alpha);
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
}
