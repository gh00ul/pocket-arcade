class_name Blend
extends RefCounted
## engine/r3d/Renderer3D.kt Blend: how a polygon is drawn.

## Depth-tested and written; texels with alpha under one half are cut out.
const OPAQUE := 0
## Depth-tested, not written; blended by texel alpha × polygon alpha (the "glass" layer).
const ALPHA := 1
## Depth-tested, not written; added on top (glows, light pools, sparks).
const ADD := 2
