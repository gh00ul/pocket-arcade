class_name Catalog
extends RefCounted
## data/Catalog.kt: the prize counter's hats, outfits and decor, and the claw machine's plushies.

enum ItemKind { HAT, OUTFIT, DECOR }

## Hat shapes the character generator knows how to draw.
enum HatStyle { CAP, BEANIE, PARTY, HEADPHONES, COWBOY, PROPELLER, WIZARD, TOPHAT, CROWN, HALO }

## Decorations that can appear in the arcade hall once bought. Each has its own spot in the hall.
enum DecorStyle { PALM, LAVA_LAMP, GUMBALL, FLAMINGO, FISH_TANK, JUKEBOX, PLUSH_BEAR, DISCO_BALL, TROPHY_CASE }

## Plush shapes for the claw machine.
enum PlushShape { BEAR, SLIME, CAT, DUCK, GHOST, DINO, OCTO, BUNNY, FROG, WHALE }

const DEFAULT_OUTFIT := "outfit_red"
const NONE := -1


class ShopItem:
	extends RefCounted
	var id: String
	var kind: int
	var name: String
	var price: int
	var blurb: String
	## A HatStyle, or NONE.
	var hat: int = NONE
	var shirt: int = 0
	var pants: int = 0
	## A DecorStyle, or NONE.
	var decor: int = NONE

	func _init(p_id: String, p_kind: int, p_name: String, p_price: int, p_blurb: String) -> void:
		id = p_id
		kind = p_kind
		name = p_name
		price = p_price
		blurb = p_blurb


class Plush:
	extends RefCounted
	var id: String
	var name: String
	var shape: int
	var main: int
	var accent: int
	## Radius in claw-game units; bigger plushies are heavier and harder to hold.
	var radius: float
	var points: int
	## Relative chance of appearing in the pile.
	var weight: int
	var rare: bool

	func _init(p_id: String, p_name: String, p_shape: int, p_main: int, p_accent: int, p_radius: float, p_points: int, p_weight: int, p_rare: bool = false) -> void:
		id = p_id
		name = p_name
		shape = p_shape
		main = p_main
		accent = p_accent
		radius = p_radius
		points = p_points
		weight = p_weight
		rare = p_rare


static var _hats: Array[ShopItem] = []
static var _outfits: Array[ShopItem] = []
static var _decor: Array[ShopItem] = []
static var _all: Array[ShopItem] = []
static var _plushies: Array[Plush] = []


static func _hat(id: String, name: String, price: int, blurb: String, style: int) -> ShopItem:
	var s := ShopItem.new(id, ItemKind.HAT, name, price, blurb)
	s.hat = style
	return s


static func _outfit(id: String, name: String, price: int, blurb: String, shirt: int, pants: int) -> ShopItem:
	var s := ShopItem.new(id, ItemKind.OUTFIT, name, price, blurb)
	s.shirt = shirt
	s.pants = pants
	return s


static func _decor_item(id: String, name: String, price: int, blurb: String, style: int) -> ShopItem:
	var s := ShopItem.new(id, ItemKind.DECOR, name, price, blurb)
	s.decor = style
	return s


static func _build() -> void:
	if not _all.is_empty():
		return
	_hats = [
		_hat("hat_cap", "BALL CAP", 60, "A CLASSIC.", HatStyle.CAP),
		_hat("hat_beanie", "BEANIE", 80, "COZY AND COOL.", HatStyle.BEANIE),
		_hat("hat_party", "PARTY HAT", 100, "EVERY DAY IS A PARTY.", HatStyle.PARTY),
		_hat("hat_phones", "HEADPHONES", 140, "8-BIT BEATS.", HatStyle.HEADPHONES),
		_hat("hat_cowboy", "COWBOY HAT", 180, "YEEHAW!", HatStyle.COWBOY),
		_hat("hat_propeller", "PROPELLER", 220, "IT SPINS!", HatStyle.PROPELLER),
		_hat("hat_wizard", "WIZARD HAT", 280, "HIGH SCORE MAGIC.", HatStyle.WIZARD),
		_hat("hat_tophat", "TOP HAT", 320, "VERY DAPPER.", HatStyle.TOPHAT),
		_hat("hat_crown", "CROWN", 450, "ARCADE ROYALTY.", HatStyle.CROWN),
		_hat("hat_halo", "HALO", 600, "A TRUE LEGEND.", HatStyle.HALO),
	]
	_outfits = [
		_outfit(DEFAULT_OUTFIT, "RED TEE", 0, "YOUR TRUSTY TEE.", Pal.RED, Pal.NAVY),
		_outfit("outfit_blue", "BLUE TEE", 40, "COOL AS ICE.", Pal.BLUE, Pal.DARKGRAY),
		_outfit("outfit_green", "GREEN TEE", 40, "FRESH.", Pal.GREEN, Pal.DARKGREEN),
		_outfit("outfit_purple", "GRAPE TEE", 60, "PURPLE POWER.", Pal.PURPLE, Pal.PLUM),
		_outfit("outfit_orange", "ORANGE TEE", 60, "ZESTY.", Pal.ORANGE, Pal.DARKBROWN),
		_outfit("outfit_pink", "BUBBLEGUM", 80, "POP!", Pal.HOTPINK, Pal.PLUM),
		_outfit("outfit_neon", "NEON CYAN", 120, "GLOWS UNDER BLACKLIGHT.", Pal.CYAN, Pal.NAVY),
		_outfit("outfit_midnight", "MIDNIGHT", 150, "STEALTH MODE.", Pal.DARKGRAY, Pal.BLACK),
		_outfit("outfit_arctic", "ARCTIC", 150, "CRISP WHITE.", Pal.WHITE, Pal.LIGHTGRAY),
		_outfit("outfit_gold", "CHAMPION", 400, "SOLID GOLD.", Pal.GOLD, Pal.DARKBROWN),
	]
	_decor = [
		_decor_item("decor_palm", "POTTED PALM", 80, "A LITTLE GREENERY.", DecorStyle.PALM),
		_decor_item("decor_lava", "LAVA LAMP", 120, "GROOVY BLOBS.", DecorStyle.LAVA_LAMP),
		_decor_item("decor_flamingo", "NEON FLAMINGO", 160, "PINK AND PROUD.", DecorStyle.FLAMINGO),
		_decor_item("decor_gumball", "GUMBALL MACHINE", 200, "SWEET!", DecorStyle.GUMBALL),
		_decor_item("decor_fish", "FISH TANK", 260, "BLUB BLUB.", DecorStyle.FISH_TANK),
		_decor_item("decor_jukebox", "JUKEBOX", 350, "PLAYS THE HITS.", DecorStyle.JUKEBOX),
		_decor_item("decor_bear", "GIANT BEAR", 420, "THE BIGGEST PRIZE.", DecorStyle.PLUSH_BEAR),
		_decor_item("decor_disco", "DISCO BALL", 500, "SPARKLES ON THE FLOOR.", DecorStyle.DISCO_BALL),
		_decor_item("decor_trophy", "TROPHY CASE", 600, "SHOW OFF YOUR WINS.", DecorStyle.TROPHY_CASE),
	]
	_all = []
	_all.append_array(_hats)
	_all.append_array(_outfits)
	_all.append_array(_decor)
	_plushies = [
		Plush.new("plush_bear", "PIXEL BEAR", PlushShape.BEAR, Pal.BROWN, Pal.TAN, 26.0, 100, 10),
		Plush.new("plush_slime", "SLIME PAL", PlushShape.SLIME, Pal.LIME, Pal.GREEN, 22.0, 60, 14),
		Plush.new("plush_cat", "ROBO CAT", PlushShape.CAT, Pal.LIGHTGRAY, Pal.CYAN, 24.0, 80, 12),
		Plush.new("plush_duck", "SPACE DUCK", PlushShape.DUCK, Pal.YELLOW, Pal.ORANGE, 22.0, 70, 12),
		Plush.new("plush_ghost", "MINI GHOST", PlushShape.GHOST, Pal.WHITE, Pal.LAVENDER, 21.0, 60, 14),
		Plush.new("plush_dino", "DINO DAN", PlushShape.DINO, Pal.GREEN, Pal.YELLOW, 28.0, 120, 8),
		Plush.new("plush_octo", "OCTO POP", PlushShape.OCTO, Pal.PINK, Pal.HOTPINK, 24.0, 90, 10),
		Plush.new("plush_bunny", "STAR BUN", PlushShape.BUNNY, Pal.LAVENDER, Pal.HOTPINK, 23.0, 90, 10),
		Plush.new("plush_frog", "FROG PRINCE", PlushShape.FROG, Pal.GREEN, Pal.GOLD, 25.0, 110, 7),
		Plush.new("plush_whale", "BLOOP WHALE", PlushShape.WHALE, Pal.SKY, Pal.WHITE, 30.0, 150, 5),
		Plush.new("plush_golden", "GOLDEN CAT", PlushShape.CAT, Pal.GOLD, Pal.YELLOW, 24.0, 400, 1, true),
	]


static func hats() -> Array[ShopItem]:
	_build()
	return _hats


static func outfits() -> Array[ShopItem]:
	_build()
	return _outfits


static func decor() -> Array[ShopItem]:
	_build()
	return _decor


static func all() -> Array[ShopItem]:
	_build()
	return _all


static func plushies() -> Array[Plush]:
	_build()
	return _plushies


static func by_id(id: String) -> ShopItem:
	for s in all():
		if s.id == id:
			return s
	return null


static func outfit(id: String) -> ShopItem:
	for s in outfits():
		if s.id == id:
			return s
	return outfits()[0]


static func hat(id: String) -> ShopItem:
	for s in hats():
		if s.id == id:
			return s
	return null


static func plush(id: String) -> Plush:
	for p in plushies():
		if p.id == id:
			return p
	return null
