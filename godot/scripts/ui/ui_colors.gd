class_name UiColors
extends RefCounted
## ui/UiTheme.kt UiColors: the menus' colour tokens. Every screen picks from here instead of inventing
## a hex, so the whole product shares one night-arcade look: violet-black surfaces, frosted glass, one
## accent per screen and warm gold for currency. (Kotlin's properties, snake_case; exact /255 channels.)

# ---- Surfaces. The first five predate the design system and are used by the game host's cards.
const card_top := Color(34 / 255.0, 26 / 255.0, 60 / 255.0, 255 / 255.0)  # 0xFF221A3C
const card_bottom := Color(18 / 255.0, 12 / 255.0, 34 / 255.0, 255 / 255.0)  # 0xFF120C22
const glass := Color(255 / 255.0, 255 / 255.0, 255 / 255.0, 22 / 255.0)  # 0x16FFFFFF
const glass_edge := Color(255 / 255.0, 255 / 255.0, 255 / 255.0, 36 / 255.0)  # 0x24FFFFFF
const scrim := Color(7 / 255.0, 5 / 255.0, 14 / 255.0, 204 / 255.0)  # 0xCC07050E

# ---- A modal panel's body, top to bottom: a touch lighter than a card, sinking into near-black.
const panel_top := Color(40 / 255.0, 31 / 255.0, 74 / 255.0, 255 / 255.0)  # 0xFF281F4A
const panel_bottom := Color(15 / 255.0, 10 / 255.0, 30 / 255.0, 255 / 255.0)  # 0xFF0F0A1E

# ---- Frosted glass inside a panel, brightest at its top edge.
const glass_hi := Color(255 / 255.0, 255 / 255.0, 255 / 255.0, 34 / 255.0)  # 0x22FFFFFF
const glass_lo := Color(255 / 255.0, 255 / 255.0, 255 / 255.0, 8 / 255.0)  # 0x08FFFFFF

# ---- The bright hairline that runs just inside a surface's top edge, like light catching a bevel.
const bevel_light := Color(255 / 255.0, 255 / 255.0, 255 / 255.0, 77 / 255.0)  # 0x4DFFFFFF

# ---- A sunken well (progress tracks, the turntable stage, empty slots).
const well := Color(0 / 255.0, 0 / 255.0, 0 / 255.0, 89 / 255.0)  # 0x59000000
const well_edge := Color(255 / 255.0, 255 / 255.0, 255 / 255.0, 31 / 255.0)  # 0x1FFFFFFF

# ---- A deep backdrop for stages that show a 3D picture.
const stage := Color(18 / 255.0, 12 / 255.0, 34 / 255.0, 255 / 255.0)  # 0xFF120C22

# ---- Text, brightest to dimmest.
const text_hi := Color(255 / 255.0, 255 / 255.0, 255 / 255.0, 255 / 255.0)  # Color.White
const text_mid := Color(195 / 255.0, 166 / 255.0, 255 / 255.0, 255 / 255.0)  # Pal.LAVENDER
const text_low := Color(151 / 255.0, 144 / 255.0, 182 / 255.0, 255 / 255.0)  # 0xFF9790B6
const text_off := Color(111 / 255.0, 105 / 255.0, 138 / 255.0, 255 / 255.0)  # 0xFF6F698A

# ---- Currency and status.
const token := Color(255 / 255.0, 211 / 255.0, 90 / 255.0, 255 / 255.0)  # 0xFFFFD35A
const ticket := Color(255 / 255.0, 162 / 255.0, 74 / 255.0, 255 / 255.0)  # 0xFFFFA24A
const good := Color(166 / 255.0, 240 / 255.0, 74 / 255.0, 255 / 255.0)  # Pal.LIME
const info := Color(61 / 255.0, 245 / 255.0, 255 / 255.0, 255 / 255.0)  # Pal.CYAN
const warn := Color(255 / 255.0, 154 / 255.0, 60 / 255.0, 255 / 255.0)  # Pal.ORANGE
const bad := Color(255 / 255.0, 122 / 255.0, 102 / 255.0, 255 / 255.0)  # 0xFFFF7A66
const gold := Color(255 / 255.0, 200 / 255.0, 61 / 255.0, 255 / 255.0)  # Pal.GOLD

# ---- Buttons that cannot be pressed: a dull slate cap on a darker skirt.
const off_cap_top := Color(76 / 255.0, 69 / 255.0, 104 / 255.0, 255 / 255.0)  # 0xFF4C4568
const off_cap_bottom := Color(48 / 255.0, 42 / 255.0, 71 / 255.0, 255 / 255.0)  # 0xFF302A47
const off_skirt := Color(33 / 255.0, 28 / 255.0, 52 / 255.0, 255 / 255.0)  # 0xFF211C34
