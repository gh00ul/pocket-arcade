class_name SaveState
extends RefCounted
## data/SaveState.kt: everything the player has earned, as last read from the save. A snapshot:
## the repository hands out a fresh one after every change, nothing mutates one in place.

const STARTING_TOKENS := 20
const DEFAULT_ARCADE_NAME := "POCKET ARCADE"

var loaded := false
var tokens: int = STARTING_TOKENS
var tickets: int = 0
var last_refill_day: int = 0
var owned: Array[String] = [Catalog.DEFAULT_OUTFIT]
var hat: String = ""
var outfit: String = Catalog.DEFAULT_OUTFIT
## plush id -> count
var collection: Dictionary = {}
## game id -> best score
var high_scores: Dictionary = {}
var muted := false
## Wall-clock millis after which a free spare token can be claimed while broke.
var spare_token_at: int = 0
var total_plays: int = 0
## Whether the hall is walked in first person rather than seen from above.
var first_person := false
## Free-form lifetime counters such as "plays:racer" or "tickets:earned".
var stats: Dictionary = {}
## Ids of things the player has unlocked (achievements and the like), in the order earned.
var unlocked: Array[String] = []
## Namespaced collectibles and how many of each were found, such as "fish:trout".
var collectibles: Dictionary = {}
## The name over the arcade's door.
var arcade_name: String = DEFAULT_ARCADE_NAME
## game id -> Array[ScoreTables.ScoreEntry], best first. Machines nobody scored on are absent.
var score_tables: Dictionary = {}


func high_score(game_id: String) -> int:
	return int(high_scores.get(game_id, 0))


func owns(item_id: String) -> bool:
	return owned.has(item_id)


func stat(key: String) -> int:
	return int(stats.get(key, 0))


func is_unlocked(id: String) -> bool:
	return unlocked.has(id)


func collectible_count(id: String) -> int:
	return int(collectibles.get(id, 0))


func score_table(game_id: String) -> Array:
	return score_tables.get(game_id, [])


## The decor styles (Catalog.DecorStyle) the player owns.
func owned_decor() -> Array[int]:
	var out: Array[int] = []
	for item in Catalog.decor():
		if owned.has(item.id) and item.decor != Catalog.NONE and not out.has(item.decor):
			out.append(item.decor)
	return out


func equals(o: SaveState) -> bool:
	if o == null:
		return false
	if loaded != o.loaded or tokens != o.tokens or tickets != o.tickets or last_refill_day != o.last_refill_day:
		return false
	if hat != o.hat or outfit != o.outfit or muted != o.muted or spare_token_at != o.spare_token_at:
		return false
	if total_plays != o.total_plays or first_person != o.first_person or arcade_name != o.arcade_name:
		return false
	if not PaTestEq.same_set(owned, o.owned) or not PaTestEq.same_set(unlocked, o.unlocked):
		return false
	if collection != o.collection or high_scores != o.high_scores or stats != o.stats or collectibles != o.collectibles:
		return false
	if score_tables.size() != o.score_tables.size():
		return false
	for k in score_tables:
		if not o.score_tables.has(k):
			return false
		if ScoreTables.encode(score_tables[k]) != ScoreTables.encode(o.score_tables[k]):
			return false
	return true


func _to_string() -> String:
	return "SaveState(tokens=%d, tickets=%d, plays=%d, owned=%s, hat=%s, outfit=%s, hs=%s)" % [tokens, tickets, total_plays, str(owned), hat, outfit, str(high_scores)]
