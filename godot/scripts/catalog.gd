class_name ArcadeCatalog
extends RefCounted

const GAMES = [
	["claw", "CLAW CLUB", "Grab a new plush friend", "ff70c6"],
	["skeeball", "SKEE-BALL", "Flick for the bonus ring", "66e8dd"],
	["whack", "WHACK-A-MOLE", "Quick hands. Big combos.", "b2ee6c"],
	["pusher", "COIN PUSHER", "Make it rain", "ffd675"],
	["hoops", "HOOP SHOT", "Find your hot streak", "ff9b65"],
	["airhockey", "AIR HOCKEY", "You versus the house", "76cfff"],
	["racer", "TURBO RACER", "Three laps. One winner.", "be93ff"],
	["stacker", "STACKER", "Build something tall", "88f2cd"],
	["shooter", "SHOOTOUT", "Aim true. Save civilians.", "ff807e"],
	["pinball", "STAR FLIPPER", "Keep the ball alive", "fa9edc"],
	["fishing", "GONE FISHING", "Cast. Strike. Reel.", "79dce0"]
]
const HATS = [
	["cap","BALL CAP",60], ["beanie","BEANIE",80], ["party","PARTY HAT",100],
	["phones","HEADPHONES",140], ["cowboy","COWBOY HAT",180], ["propeller","PROPELLER",220],
	["wizard","WIZARD HAT",280], ["tophat","TOP HAT",320], ["crown","CROWN",450], ["halo","HALO",600]
]
const OUTFITS = [
	["red","RED TEE",0,"ef5970"], ["blue","BLUE TEE",40,"5f95f5"], ["green","GREEN TEE",40,"69cb92"],
	["purple","GRAPE TEE",60,"9b6bdd"], ["orange","ORANGE TEE",60,"ed9748"], ["pink","BUBBLEGUM",80,"f564b9"],
	["neon","NEON CYAN",120,"54eae3"], ["midnight","MIDNIGHT",150,"34334d"], ["arctic","ARCTIC",150,"e8f5ff"],
	["gold","CHAMPION",400,"f7cd61"]
]
const DECOR = [
	["palm","POTTED PALM",80], ["lava","LAVA LAMP",120], ["flamingo","NEON FLAMINGO",160],
	["gumball","GUMBALL MACHINE",200], ["fish","FISH TANK",260], ["jukebox","JUKEBOX",350],
	["bear","GIANT BEAR",420], ["disco","DISCO BALL",500], ["trophy","TROPHY CASE",600]
]
const PLUSH_NAMES = ["PIXEL BEAR","SLIME PAL","ROBO CAT","SPACE DUCK","MINI GHOST","DINO DAN","OCTO POP","STAR BUN","FROG PRINCE","BLOOP WHALE","GOLDEN CAT"]
const PLUSH_IDS = ["bear","slime","cat","duck","ghost","dino","octo","bunny","frog","whale","golden"]

static func items(kind: String) -> Array:
	var result = []
	var rows = HATS if kind == "hat" else OUTFITS if kind == "outfit" else DECOR
	for row in rows:
		result.append({"id":kind + "_" + row[0], "name":row[1], "price":row[2], "kind":kind})
	return result

static func shirt() -> Color:
	for row in OUTFITS:
		if "outfit_" + row[0] == SaveStore.data.outfit:
			return Color(row[3])
	return Color("ef5970")
