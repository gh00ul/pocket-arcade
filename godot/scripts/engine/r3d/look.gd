class_name Look
extends RefCounted
## engine/r3d/Renderer3D.kt Look: default post-processing and shading settings shared by
## [Renderer3D] and [RenderPass].

const BLOOM_THRESHOLD := 0.62
## The HDR picture's bloom threshold: exposed linear light, above the lit-paint ceiling's knee.
const BLOOM_THRESHOLD_HDR := 1.0
const BLOOM_RADIUS := 0.65
const GRADE := 1.0
const SHARPEN := 0.25
const VIGNETTE := 0.22
const RIM := 0.3
const ENV_REFLECT := 1.0
