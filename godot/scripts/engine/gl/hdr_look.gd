class_name HdrLook
extends RefCounted
## engine/gl/HdrMath.kt HdrLook: the HDR picture's constants (encoding, shading, bloom, tone map,
## grade, vignette, film finish). The shaders in godot/shaders use the same values.

## The scene image holds c / (1 + max(c)); the largest encoded value ever decoded caps light at 24.
const ENC_MAX := 0.96
## The largest display value the inverse tone map is asked about.
const ACES_INV_MAX := 0.985
## Glowing surfaces are lifted this much in HDR.
const EMISSIVE_GAIN := 1.2
## Lit paint is eased into this ceiling (exposed linear light) from LIT_START.
const LIT_CEILING := 1.05
const LIT_START := 0.6
## Width of the soft knee around the bloom threshold.
const BLOOM_KNEE := 0.4
## Karis weighting of the first downsample.
const KARIS_STRENGTH := 0.25
## The energy one texel may feed into the bloom chain.
const BLOOM_INPUT_CAP := 6.0
const BLOOM_INPUT_START := 3.0
## Bloom's overall strength against the LDR picture's.
const BLOOM_GAIN := 0.6
## The most light the finished bloom may add to a pixel.
const BLOOM_ADD_CAP := 2.5
const BLOOM_ADD_START := 1.2
## Energy conservation: bloom / (1 + scene × this).
const BLOOM_SELF_SHADOW := 0.5
## Hue-preserving tone map in the highlights.
const HUE_KEEP := 0.4
const HUE_KEEP_FROM := 0.9
const HUE_KEEP_TO := 3.0
## Colour grade (after the tone map).
const GRADE_SATURATION := 1.08
const GRADE_CONTRAST := 0.12
const SPLIT_STRENGTH := 0.55
const SHADOW_TINT_R := 0.93
const SHADOW_TINT_G := 1.0
const SHADOW_TINT_B := 1.03
const HIGHLIGHT_TINT_R := 1.03
const HIGHLIGHT_TINT_G := 1.0
const HIGHLIGHT_TINT_B := 0.95
const BLACK_LIFT_R := 0.010
const BLACK_LIFT_G := 0.013
const BLACK_LIFT_B := 0.018
## Vignette.
const VIGNETTE_FROM := 0.28
const VIGNETTE_TO := 0.98
const VIGNETTE_TINT_R := 0.96
const VIGNETTE_TINT_G := 0.97
const VIGNETTE_TINT_B := 1.0
## Film finish.
const GRAIN := 0.028
const ABERRATION := 0.0022
const ABERRATION_MOTION := 1.5
## Anamorphic glare.
const GLARE_AMOUNT := 0.55
const GLARE_THRESHOLD := 0.9
const GLARE_TAPS := 8
const GLARE_SPACING := 2.0
const GLARE_FALLOFF := 0.28
const GLARE_GAIN := 2.2
const GLARE_TINT_R := 0.85
const GLARE_TINT_G := 0.95
const GLARE_TINT_B := 1.1
## Camera-motion signal for the aberration.
const MOTION_FULL := 0.06
const MOTION_TRAVEL := 60.0
const MOTION_SMOOTH := 0.18
