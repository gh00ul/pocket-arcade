class_name UiGlow
extends RefCounted
## ui/UiTheme.kt UiGlow: one glow language for every screen: soft rings of the accent colour round a
## surface (drawn by [method UiTheme.glow_round_rect] and [method UiTheme.glow_circle]).

## How far a glow reaches beyond its surface, in dp.
const REACH := 10.0
## How bright a resting glow is (0..1 alpha at the surface's edge), and an active or chosen one.
const IDLE := 0.3
const ACTIVE := 0.55
