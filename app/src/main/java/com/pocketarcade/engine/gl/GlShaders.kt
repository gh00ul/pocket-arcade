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
    vNormal = mat3(uModel) * aNormal;
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
out vec4 outColor;

vec3 tonemap(vec3 c) {
    c *= uExposure;
    return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec4 tex = texture(uTex, vUV);
    float alpha = tex.a * vColor.a;
    if (alpha < uAlphaCut) discard;
    vec3 base = tex.rgb * vColor.rgb;
    vec3 col;
    if (vExtra.x > 0.0) {
        col = base * vExtra.x;
    } else {
        vec3 n = normalize(vNormal);
        vec3 toEye = uEye - vWorld;
        if (dot(n, toEye) < 0.0) n = -n;
        vec3 V = normalize(toEye);
        vec3 L = uAmbient;
        float dd = dot(n, uDirDir);
        float gloss = vExtra.z;
        vec3 S = vec3(0.0);
        if (dd > 0.0) {
            L += uDirCol * dd;
            if (gloss > 0.0) {
                vec3 h = normalize(uDirDir + V);
                S += uDirCol * (pow(max(dot(n, h), 0.0), 40.0) * gloss);
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
                            L += lc.rgb * (fall * lam * lc.a);
                            if (gloss > 0.0) {
                                vec3 h = normalize(ld + V);
                                S += lc.rgb * (pow(max(dot(n, h), 0.0), 48.0) * fall * lc.a * gloss * 1.6);
                            }
                        }
                    }
                }
            }
        }
        col = base * min(L, vec3(4.0)) + S;
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

    /** Downsamples the scene while keeping only its brightest parts. */
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
    float lum = max(c.r, max(c.g, c.b));
    float k = max(lum - uThreshold, 0.0) / max(1.0 - uThreshold, 0.001);
    outColor = vec4(c * k, 1.0);
}
"""

    /** 9-tap Gaussian blur along [uDir], using bilinear taps. */
    const val BLUR_FS = """#version 300 es
precision mediump float;
in vec2 vUV;
uniform sampler2D uTex;
uniform vec2 uDir;
out vec4 outColor;
void main() {
    vec3 c = texture(uTex, vUV).rgb * 0.2270270270;
    c += texture(uTex, vUV + uDir * 1.3846153846).rgb * 0.3162162162;
    c += texture(uTex, vUV - uDir * 1.3846153846).rgb * 0.3162162162;
    c += texture(uTex, vUV + uDir * 3.2307692308).rgb * 0.0702702703;
    c += texture(uTex, vUV - uDir * 3.2307692308).rgb * 0.0702702703;
    outColor = vec4(c, 1.0);
}
"""

    /** Scene plus bloom, with a soft vignette. */
    const val COMPOSITE_FS = """#version 300 es
precision mediump float;
in vec2 vUV;
uniform sampler2D uScene;
uniform sampler2D uBloom;
uniform float uBloomAmount;
uniform float uVignette;
out vec4 outColor;
void main() {
    vec3 c = texture(uScene, vUV).rgb;
    c += texture(uBloom, vUV).rgb * uBloomAmount;
    vec2 d = vUV - 0.5;
    float v = 1.0 - uVignette * smoothstep(0.35, 0.85, length(d * vec2(1.0, 0.8)));
    outColor = vec4(c * v, 1.0);
}
"""
}
