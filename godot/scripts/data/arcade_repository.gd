class_name ArcadeRepository
extends RefCounted
## data/ArcadeRepository.kt: the single source of truth for progress. Every mutation is one atomic
## edit of the save ([PrefsStore]); the keys and string encodings are build-13's DataStore ones,
## so an imported save is read by exactly the code that reads a native one.
##
## A write that fails on disk never crashes the game: the mutators report it as "couldn't do it"
## (false, or nothing done). Kotlin's suspend functions are plain calls here: the write lands
## before the call returns, so nothing can drop it.

signal changed(state: SaveState)

const STARTING_TOKENS := 20
const DAILY_TOKENS := 10
## Tickets needed to buy one token at the token machine.
const TICKETS_PER_TOKEN := 40
## While broke, a spare token can be claimed this often.
const SPARE_TOKEN_COOLDOWN_MS := 3 * 60 * 1000

## The name over the door until the player picks one.
const DEFAULT_ARCADE_NAME := "POCKET ARCADE"
const MAX_ARCADE_NAME_LENGTH := 14

# These names are the saved file's format (build-13's DataStore keys): never rename one.
const TOKENS := "tokens"
const TICKETS := "tickets"
const LAST_REFILL_DAY := "last_refill_day"
const OWNED := "owned"
const HAT := "hat"
const OUTFIT := "outfit"
const COLLECTION := "collection"
const MUTED := "muted"
const SPARE_AT := "spare_token_at"
const TOTAL_PLAYS := "total_plays"
const FIRST_PERSON := "first_person"
const STATS := "stats"
const UNLOCKED := "unlocked"
const COLLECTIBLES := "collectibles"
const ARCADE_NAME := "arcade_name"
const HIGH_SCORE_PREFIX := "hs_"
const SCORES_PREFIX := "scores_"

## Every key ArcadeRepository.kt defines, by DataStore type (used by the importer and its tests).
const KEY_TYPES := {
	TOKENS: "int", TICKETS: "int", LAST_REFILL_DAY: "long", OWNED: "string_set", HAT: "string",
	OUTFIT: "string", COLLECTION: "string", MUTED: "bool", SPARE_AT: "long", TOTAL_PLAYS: "int",
	FIRST_PERSON: "bool", STATS: "string", UNLOCKED: "string", COLLECTIBLES: "string", ARCADE_NAME: "string",
}

const INT_MAX := 0x7FFFFFFF
const LONG_MAX := 0x7FFFFFFFFFFFFFFF

var store: PrefsStore
var _state: SaveState = null


func _init(p_store: PrefsStore) -> void:
	store = p_store


static func high_score_key(game_id: String) -> String:
	return HIGH_SCORE_PREFIX + game_id


static func scores_key(game_id: String) -> String:
	return SCORES_PREFIX + game_id


# ---------------------------------------------------------------- codecs (pure)

static func encode_collection(map: Dictionary) -> String:
	var parts := PackedStringArray()
	for k in map:
		if int(map[k]) > 0:
			parts.append("%s:%d" % [k, int(map[k])])
	return ";".join(parts)


static func decode_collection(raw: Variant) -> Dictionary:
	var out: Dictionary = {}
	if raw == null or str(raw).strip_edges().is_empty():
		return out
	for part in str(raw).split(";"):
		var idx := part.rfind(":")
		if idx <= 0:
			continue
		var count: Variant = KParse.to_int_or_null(part.substr(idx + 1))
		if count == null:
			continue
		out[part.substr(0, idx)] = count
	return out


## `plays:racer:12;tickets:earned:340`: each key (which may hold colons) then its count.
static func encode_stats(map: Dictionary) -> String:
	var parts := PackedStringArray()
	for k in map:
		if int(map[k]) > 0:
			parts.append("%s:%d" % [k, int(map[k])])
	return ";".join(parts)


## Reads what [method encode_stats] wrote, skipping parts with no key or a count not above zero.
static func decode_stats(raw: Variant) -> Dictionary:
	var out: Dictionary = {}
	if raw == null or str(raw).strip_edges().is_empty():
		return out
	for part in str(raw).split(";"):
		var idx := part.rfind(":")
		if idx <= 0:
			continue
		var key := part.substr(0, idx).strip_edges()
		var count: Variant = KParse.to_long_or_null(part.substr(idx + 1).strip_edges())
		if key.is_empty() or count == null or int(count) <= 0:
			continue
		out[key] = count
	return out


## `a;b;c`: ids in the order they were added.
static func encode_ids(ids: Array) -> String:
	return ";".join(PackedStringArray(ids))


static func decode_ids(raw: Variant) -> Array[String]:
	var out: Array[String] = []
	if raw == null or str(raw).strip_edges().is_empty():
		return out
	for part in str(raw).split(";"):
		var id := part.strip_edges()
		if not id.is_empty() and not out.has(id):
			out.append(id)
	return out


## A free-form id or key, trimmed; "" if it is blank or holds `;`.
static func clean_id(id: String) -> String:
	var t := id.strip_edges()
	if t.is_empty() or t.contains(";"):
		return ""
	return t


## Upper-cased, only A-Z, 0-9 and spaces, trimmed, cut to 14; blank becomes the default.
static func sanitize_arcade_name(raw: Variant) -> String:
	var s := "" if raw == null else str(raw).to_upper()
	var kept := ""
	for i in s.length():
		var ch := s[i]
		if (ch >= "A" and ch <= "Z") or (ch >= "0" and ch <= "9") or ch == " ":
			kept += ch
	kept = kept.strip_edges().substr(0, MAX_ARCADE_NAME_LENGTH).strip_edges(false, true)
	return DEFAULT_ARCADE_NAME if kept.is_empty() else kept


## Stores the hall's camera choice: first person (true) or overhead.
static func write_first_person(p: Dictionary, on: bool) -> void:
	p[FIRST_PERSON] = on


## Everything saved, as the game sees it (ArcadeRepository.read).
static func read(p: Dictionary) -> SaveState:
	var s := SaveState.new()
	s.loaded = true
	s.tokens = int(p.get(TOKENS, STARTING_TOKENS))
	s.tickets = int(p.get(TICKETS, 0))
	s.last_refill_day = int(p.get(LAST_REFILL_DAY, 0))
	var owned: Array[String] = []
	for id in p.get(OWNED, []):
		if not owned.has(str(id)):
			owned.append(str(id))
	if not owned.has(Catalog.DEFAULT_OUTFIT):
		owned.append(Catalog.DEFAULT_OUTFIT)
	s.owned = owned
	s.hat = str(p.get(HAT, ""))
	s.outfit = str(p.get(OUTFIT, Catalog.DEFAULT_OUTFIT))
	s.collection = decode_collection(p.get(COLLECTION))
	s.muted = bool(p.get(MUTED, false))
	s.spare_token_at = int(p.get(SPARE_AT, 0))
	s.total_plays = int(p.get(TOTAL_PLAYS, 0))
	s.first_person = bool(p.get(FIRST_PERSON, false))
	s.stats = decode_stats(p.get(STATS))
	s.unlocked = decode_ids(p.get(UNLOCKED))
	s.collectibles = decode_collection(p.get(COLLECTIBLES))
	s.arcade_name = sanitize_arcade_name(p.get(ARCADE_NAME))
	var hs: Dictionary = {}
	var tables: Dictionary = {}
	for key: String in p:
		if key.begins_with(HIGH_SCORE_PREFIX):
			var v: Variant = p[key]
			hs[key.trim_prefix(HIGH_SCORE_PREFIX)] = int(v) if (v is int) else 0
		elif key.begins_with(SCORES_PREFIX):
			var entries := ScoreTables.decode(p[key] if p[key] is String else null)
			if not entries.is_empty():
				tables[key.trim_prefix(SCORES_PREFIX)] = entries
	s.high_scores = hs
	s.score_tables = tables
	return s


# ---------------------------------------------------------------- state

## The current save (a snapshot).
func state() -> SaveState:
	if _state == null:
		_state = read(store.read())
	return _state


func _edit(transform: Callable) -> bool:
	var ok := store.edit(transform)
	if ok:
		_state = null
		changed.emit(state())
	else:
		push_warning("ArcadeRepository: couldn't save progress")
	return ok


static func _tokens(p: Dictionary) -> int:
	return int(p.get(TOKENS, STARTING_TOKENS))


static func _tickets(p: Dictionary) -> int:
	return int(p.get(TICKETS, 0))


## Kotlin Int arithmetic for counters that are Ints on disk.
static func _int_add(a: int, b: int) -> int:
	return MathUtil.i32(a + b)


# ---------------------------------------------------------------- mutations

## Grants the daily tokens if the calendar day changed since the last refill. The very first
## launch just starts the clock. Returns the tokens granted.
func apply_daily_refill(today: int = -1) -> int:
	if today < 0:
		today = today_epoch_day()
	var granted := [0]
	var saved := _edit(func(p: Dictionary) -> void:
		if not p.has(LAST_REFILL_DAY):
			p[LAST_REFILL_DAY] = today
			p[TOKENS] = _tokens(p)
		else:
			var last := int(p[LAST_REFILL_DAY])
			if today > last:
				granted[0] = DAILY_TOKENS
				p[TOKENS] = _int_add(_tokens(p), DAILY_TOKENS)
				p[LAST_REFILL_DAY] = today
			elif today < last:
				# Clock moved backwards: resync without granting.
				p[LAST_REFILL_DAY] = today)
	return granted[0] if saved else 0


## Spends one token; false if the player has none (or the save failed).
func spend_token() -> bool:
	var ok := [false]
	var saved := _edit(func(p: Dictionary) -> void:
		var t := _tokens(p)
		if t > 0:
			p[TOKENS] = t - 1
			p[TOTAL_PLAYS] = _int_add(int(p.get(TOTAL_PLAYS, 0)), 1)
			ok[0] = true)
	return saved and ok[0]


func refund_token() -> void:
	_edit(func(p: Dictionary) -> void:
		p[TOKENS] = _int_add(_tokens(p), 1)
		p[TOTAL_PLAYS] = maxi(int(p.get(TOTAL_PLAYS, 1)) - 1, 0))


func add_tickets(n: int) -> void:
	if n <= 0:
		return
	_edit(func(p: Dictionary) -> void:
		p[TICKETS] = _int_add(_tickets(p), n))


## Spends [param n] tickets atomically; false, changing nothing, if the player has fewer.
func spend_tickets(n: int) -> bool:
	if n < 0:
		return false
	if n == 0:
		return true
	var ok := [false]
	var saved := _edit(func(p: Dictionary) -> void:
		var t := _tickets(p)
		if t >= n:
			p[TICKETS] = t - n
			ok[0] = true)
	return saved and ok[0]


## Stores [param score] if it beats the machine's record. True on a new high score.
func record_score(game_id: String, score: int) -> bool:
	var beaten := [false]
	var saved := _edit(func(p: Dictionary) -> void:
		var key := high_score_key(game_id)
		var old := int(p.get(key, 0))
		if score > old:
			p[key] = score
			beaten[0] = true)
	return saved and beaten[0]


## Enters [param score] with [param initials] in the machine's top-5 table if it makes it.
## Returns its place (0 is first) or -1.
func record_score_entry(game_id: String, score: int, initials: String) -> int:
	if game_id.strip_edges().is_empty() or score <= 0:
		return -1
	var rank := [-1]
	var saved := _edit(func(p: Dictionary) -> void:
		var key := scores_key(game_id)
		var entries := ScoreTables.decode(p.get(key))
		var place := ScoreTables.rank_for(entries, score)
		if place >= 0:
			var entry := ScoreTables.ScoreEntry.new(ScoreTables.sanitize_initials(initials), score)
			p[key] = ScoreTables.encode(ScoreTables.insert(entries, entry))
			rank[0] = place)
	return rank[0] if saved else -1


func add_prize(plush_id: String) -> void:
	_edit(func(p: Dictionary) -> void:
		var map := decode_collection(p.get(COLLECTION))
		map[plush_id] = int(map.get(plush_id, 0)) + 1
		p[COLLECTION] = encode_collection(map))


## Adds [param delta] to the lifetime counter [param key]; never below zero, saturating at the top.
func add_stat(key: String, delta: int = 1) -> void:
	var clean := clean_id(key)
	if clean.is_empty() or delta == 0:
		return
	_edit(func(p: Dictionary) -> void:
		var map := decode_stats(p.get(STATS))
		var old := int(map.get(clean, 0))
		var sum: int
		if delta > 0 and old > LONG_MAX - delta:
			sum = LONG_MAX
		else:
			sum = old + delta
		map[clean] = maxi(sum, 0)
		p[STATS] = encode_stats(map))


## Unlocks [param id]; true only when newly unlocked (and saved).
func unlock(id: String) -> bool:
	var clean := clean_id(id)
	if clean.is_empty():
		return false
	var fresh := [false]
	var saved := _edit(func(p: Dictionary) -> void:
		var ids := decode_ids(p.get(UNLOCKED))
		if not ids.has(clean):
			ids.append(clean)
			p[UNLOCKED] = encode_ids(ids)
			fresh[0] = true)
	return saved and fresh[0]


## Counts one more of the collectible [param id] (namespaced like "fish:trout").
func add_collectible(id: String) -> void:
	var clean := clean_id(id)
	if clean.is_empty():
		return
	_edit(func(p: Dictionary) -> void:
		var map := decode_collection(p.get(COLLECTIBLES))
		var old := int(map.get(clean, 0))
		map[clean] = old + 1 if old < INT_MAX else old
		p[COLLECTIBLES] = encode_collection(map))


func set_arcade_name(raw: String) -> void:
	_edit(func(p: Dictionary) -> void:
		p[ARCADE_NAME] = sanitize_arcade_name(raw))


## Buys [param item] with tickets and equips it (hats/outfits). False if unaffordable or owned.
func buy(item: Catalog.ShopItem) -> bool:
	var ok := [false]
	var saved := _edit(func(p: Dictionary) -> void:
		var owned: Array = p.get(OWNED, [])
		var tickets := _tickets(p)
		if not owned.has(item.id) and tickets >= item.price:
			p[TICKETS] = tickets - item.price
			var next: Array[String] = []
			for o in owned:
				next.append(str(o))
			next.append(item.id)
			p[OWNED] = next
			if item.kind == Catalog.ItemKind.HAT:
				p[HAT] = item.id
			elif item.kind == Catalog.ItemKind.OUTFIT:
				p[OUTFIT] = item.id
			ok[0] = true)
	return saved and ok[0]


## Equips an owned hat or outfit; equipping the worn hat again takes it off.
func equip(item: Catalog.ShopItem) -> void:
	_edit(func(p: Dictionary) -> void:
		var owned: Array = (p.get(OWNED, []) as Array).duplicate()
		if not owned.has(Catalog.DEFAULT_OUTFIT):
			owned.append(Catalog.DEFAULT_OUTFIT)
		if not owned.has(item.id):
			return
		if item.kind == Catalog.ItemKind.HAT:
			p[HAT] = "" if str(p.get(HAT, "")) == item.id else item.id
		elif item.kind == Catalog.ItemKind.OUTFIT:
			p[OUTFIT] = item.id)


func exchange_tickets_for_token() -> bool:
	var ok := [false]
	var saved := _edit(func(p: Dictionary) -> void:
		var tickets := _tickets(p)
		if tickets >= TICKETS_PER_TOKEN:
			p[TICKETS] = tickets - TICKETS_PER_TOKEN
			p[TOKENS] = _int_add(_tokens(p), 1)
			ok[0] = true)
	return saved and ok[0]


## Gives a free token when the player is completely out and the cooldown has passed.
func claim_spare_token(now_ms: int = -1) -> bool:
	if now_ms < 0:
		now_ms = now_millis()
	var ok := [false]
	var saved := _edit(func(p: Dictionary) -> void:
		var at := int(p.get(SPARE_AT, 0))
		if _tokens(p) == 0 and now_ms >= at:
			p[TOKENS] = 1
			p[SPARE_AT] = now_ms + SPARE_TOKEN_COOLDOWN_MS
			ok[0] = true)
	return saved and ok[0]


func set_muted(muted: bool) -> void:
	_edit(func(p: Dictionary) -> void:
		p[MUTED] = muted)


## Remembers whether the hall is walked in first person.
func set_first_person(on: bool) -> void:
	_edit(func(p: Dictionary) -> void:
		write_first_person(p, on))


# ---------------------------------------------------------------- clocks

## LocalDate.now().toEpochDay(): days since 1970-01-01 of the local calendar date.
static func today_epoch_day() -> int:
	var d := Time.get_date_dict_from_system()
	return int(Time.get_unix_time_from_datetime_dict({"year": d.year, "month": d.month, "day": d.day, "hour": 0, "minute": 0, "second": 0}) / 86400)


## System.currentTimeMillis().
static func now_millis() -> int:
	return int(Time.get_unix_time_from_system() * 1000.0)
