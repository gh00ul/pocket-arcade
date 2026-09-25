package com.pocketarcade.data

import com.pocketarcade.engine.Pal

enum class ItemKind { HAT, OUTFIT, DECOR }

/** Hat shapes the character generator knows how to draw. */
enum class HatStyle { CAP, BEANIE, PARTY, HEADPHONES, COWBOY, PROPELLER, WIZARD, TOPHAT, CROWN, HALO }

/** Decorations that can appear in the arcade hall once bought. Each has its own spot in the hall. */
enum class DecorStyle { PALM, LAVA_LAMP, GUMBALL, FLAMINGO, FISH_TANK, JUKEBOX, PLUSH_BEAR, DISCO_BALL, TROPHY_CASE }

data class ShopItem(
    val id: String,
    val kind: ItemKind,
    val name: String,
    val price: Int,
    val blurb: String,
    val hat: HatStyle? = null,
    val shirt: Int = 0,
    val pants: Int = 0,
    val decor: DecorStyle? = null,
)

/** Plush shapes for the claw machine. */
enum class PlushShape { BEAR, SLIME, CAT, DUCK, GHOST, DINO, OCTO, BUNNY, FROG, WHALE }

data class Plush(
    val id: String,
    val name: String,
    val shape: PlushShape,
    val main: Int,
    val accent: Int,
    /** Radius in claw-game units; bigger plushies are heavier and harder to hold. */
    val radius: Float,
    val points: Int,
    /** Relative chance of appearing in the pile. */
    val weight: Int,
    val rare: Boolean = false,
)

object Catalog {
    const val DEFAULT_OUTFIT = "outfit_red"

    val hats = listOf(
        ShopItem("hat_cap", ItemKind.HAT, "BALL CAP", 60, "A CLASSIC.", hat = HatStyle.CAP),
        ShopItem("hat_beanie", ItemKind.HAT, "BEANIE", 80, "COZY AND COOL.", hat = HatStyle.BEANIE),
        ShopItem("hat_party", ItemKind.HAT, "PARTY HAT", 100, "EVERY DAY IS A PARTY.", hat = HatStyle.PARTY),
        ShopItem("hat_phones", ItemKind.HAT, "HEADPHONES", 140, "8-BIT BEATS.", hat = HatStyle.HEADPHONES),
        ShopItem("hat_cowboy", ItemKind.HAT, "COWBOY HAT", 180, "YEEHAW!", hat = HatStyle.COWBOY),
        ShopItem("hat_propeller", ItemKind.HAT, "PROPELLER", 220, "IT SPINS!", hat = HatStyle.PROPELLER),
        ShopItem("hat_wizard", ItemKind.HAT, "WIZARD HAT", 280, "HIGH SCORE MAGIC.", hat = HatStyle.WIZARD),
        ShopItem("hat_tophat", ItemKind.HAT, "TOP HAT", 320, "VERY DAPPER.", hat = HatStyle.TOPHAT),
        ShopItem("hat_crown", ItemKind.HAT, "CROWN", 450, "ARCADE ROYALTY.", hat = HatStyle.CROWN),
        ShopItem("hat_halo", ItemKind.HAT, "HALO", 600, "A TRUE LEGEND.", hat = HatStyle.HALO),
    )

    val outfits = listOf(
        ShopItem(DEFAULT_OUTFIT, ItemKind.OUTFIT, "RED TEE", 0, "YOUR TRUSTY TEE.", shirt = Pal.RED, pants = Pal.NAVY),
        ShopItem("outfit_blue", ItemKind.OUTFIT, "BLUE TEE", 40, "COOL AS ICE.", shirt = Pal.BLUE, pants = Pal.DARKGRAY),
        ShopItem("outfit_green", ItemKind.OUTFIT, "GREEN TEE", 40, "FRESH.", shirt = Pal.GREEN, pants = Pal.DARKGREEN),
        ShopItem("outfit_purple", ItemKind.OUTFIT, "GRAPE TEE", 60, "PURPLE POWER.", shirt = Pal.PURPLE, pants = Pal.PLUM),
        ShopItem("outfit_orange", ItemKind.OUTFIT, "ORANGE TEE", 60, "ZESTY.", shirt = Pal.ORANGE, pants = Pal.DARKBROWN),
        ShopItem("outfit_pink", ItemKind.OUTFIT, "BUBBLEGUM", 80, "POP!", shirt = Pal.HOTPINK, pants = Pal.PLUM),
        ShopItem("outfit_neon", ItemKind.OUTFIT, "NEON CYAN", 120, "GLOWS UNDER BLACKLIGHT.", shirt = Pal.CYAN, pants = Pal.NAVY),
        ShopItem("outfit_midnight", ItemKind.OUTFIT, "MIDNIGHT", 150, "STEALTH MODE.", shirt = Pal.DARKGRAY, pants = Pal.BLACK),
        ShopItem("outfit_arctic", ItemKind.OUTFIT, "ARCTIC", 150, "CRISP WHITE.", shirt = Pal.WHITE, pants = Pal.LIGHTGRAY),
        ShopItem("outfit_gold", ItemKind.OUTFIT, "CHAMPION", 400, "SOLID GOLD.", shirt = Pal.GOLD, pants = Pal.DARKBROWN),
    )

    val decor = listOf(
        ShopItem("decor_palm", ItemKind.DECOR, "POTTED PALM", 80, "A LITTLE GREENERY.", decor = DecorStyle.PALM),
        ShopItem("decor_lava", ItemKind.DECOR, "LAVA LAMP", 120, "GROOVY BLOBS.", decor = DecorStyle.LAVA_LAMP),
        ShopItem("decor_flamingo", ItemKind.DECOR, "NEON FLAMINGO", 160, "PINK AND PROUD.", decor = DecorStyle.FLAMINGO),
        ShopItem("decor_gumball", ItemKind.DECOR, "GUMBALL MACHINE", 200, "SWEET!", decor = DecorStyle.GUMBALL),
        ShopItem("decor_fish", ItemKind.DECOR, "FISH TANK", 260, "BLUB BLUB.", decor = DecorStyle.FISH_TANK),
        ShopItem("decor_jukebox", ItemKind.DECOR, "JUKEBOX", 350, "PLAYS THE HITS.", decor = DecorStyle.JUKEBOX),
        ShopItem("decor_bear", ItemKind.DECOR, "GIANT BEAR", 420, "THE BIGGEST PRIZE.", decor = DecorStyle.PLUSH_BEAR),
        ShopItem("decor_disco", ItemKind.DECOR, "DISCO BALL", 500, "SPARKLES ON THE FLOOR.", decor = DecorStyle.DISCO_BALL),
        ShopItem("decor_trophy", ItemKind.DECOR, "TROPHY CASE", 600, "SHOW OFF YOUR WINS.", decor = DecorStyle.TROPHY_CASE),
    )

    val all: List<ShopItem> = hats + outfits + decor

    fun byId(id: String): ShopItem? = all.firstOrNull { it.id == id }

    fun outfit(id: String): ShopItem = outfits.firstOrNull { it.id == id } ?: outfits.first()

    fun hat(id: String): ShopItem? = hats.firstOrNull { it.id == id }

    val plushies = listOf(
        Plush("plush_bear", "PIXEL BEAR", PlushShape.BEAR, Pal.BROWN, Pal.TAN, 26f, 100, 10),
        Plush("plush_slime", "SLIME PAL", PlushShape.SLIME, Pal.LIME, Pal.GREEN, 22f, 60, 14),
        Plush("plush_cat", "ROBO CAT", PlushShape.CAT, Pal.LIGHTGRAY, Pal.CYAN, 24f, 80, 12),
        Plush("plush_duck", "SPACE DUCK", PlushShape.DUCK, Pal.YELLOW, Pal.ORANGE, 22f, 70, 12),
        Plush("plush_ghost", "MINI GHOST", PlushShape.GHOST, Pal.WHITE, Pal.LAVENDER, 21f, 60, 14),
        Plush("plush_dino", "DINO DAN", PlushShape.DINO, Pal.GREEN, Pal.YELLOW, 28f, 120, 8),
        Plush("plush_octo", "OCTO POP", PlushShape.OCTO, Pal.PINK, Pal.HOTPINK, 24f, 90, 10),
        Plush("plush_bunny", "STAR BUN", PlushShape.BUNNY, Pal.LAVENDER, Pal.HOTPINK, 23f, 90, 10),
        Plush("plush_frog", "FROG PRINCE", PlushShape.FROG, Pal.GREEN, Pal.GOLD, 25f, 110, 7),
        Plush("plush_whale", "BLOOP WHALE", PlushShape.WHALE, Pal.SKY, Pal.WHITE, 30f, 150, 5),
        Plush("plush_golden", "GOLDEN CAT", PlushShape.CAT, Pal.GOLD, Pal.YELLOW, 24f, 400, 1, rare = true),
    )

    fun plush(id: String): Plush? = plushies.firstOrNull { it.id == id }
}
