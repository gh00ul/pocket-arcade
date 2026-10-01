class_name ScoreTables
extends RefCounted
## data/ScoreTables.kt: the per-machine top-5 score tables (ranking, initials, encoding). Pure.

## How many entries a machine's table keeps.
const SIZE := 5
const DEFAULT_INITIALS := "AAA"
const INITIALS_LENGTH := 3


## One line of a table: three-character initials and the score they set.
class ScoreEntry:
	extends RefCounted
	var initials: String
	var score: int

	func _init(p_initials: String, p_score: int) -> void:
		initials = p_initials
		score = p_score

	func equals(o: ScoreEntry) -> bool:
		return o != null and o.initials == initials and o.score == score

	func _to_string() -> String:
		return "%s:%d" % [initials, score]


static func _is_initial(ch: String) -> bool:
	return (ch >= "A" and ch <= "Z") or (ch >= "0" and ch <= "9")


## Cleans typed initials to exactly three characters of A-Z and 0-9 (padded with A).
static func sanitize_initials(raw: Variant) -> String:
	var s := "" if raw == null else str(raw).to_upper()
	var out := ""
	for i in s.length():
		var ch := s[i]
		if _is_initial(ch) and out.length() < INITIALS_LENGTH:
			out += ch
	while out.length() < INITIALS_LENGTH:
		out += "A"
	return out


## The place (0 is first) [param score] would take, or -1 if it wouldn't make the table.
static func rank_for(entries: Array, score: int) -> int:
	if score <= 0:
		return -1
	var rank := 0
	for e: ScoreEntry in entries:
		if e.score >= score:
			rank += 1
	return rank if rank < SIZE else -1


## [param entries] with [param entry] placed where [method rank_for] says, best first, cut to SIZE.
static func insert(entries: Array, entry: ScoreEntry) -> Array:
	var sorted := _sorted(entries)
	var rank := rank_for(sorted, entry.score)
	if rank < 0:
		return sorted.slice(0, SIZE)
	sorted.insert(rank, entry)
	return sorted.slice(0, SIZE)


## `AAA:100,BBB:50`
static func encode(entries: Array) -> String:
	var parts := PackedStringArray()
	for e: ScoreEntry in entries:
		parts.append("%s:%d" % [e.initials, e.score])
	return ",".join(parts)


## Reads what [method encode] wrote, skipping bad parts; sorted best first and cut to SIZE.
static func decode(raw: Variant) -> Array:
	if raw == null or str(raw).strip_edges().is_empty():
		return []
	var out: Array = []
	for part in str(raw).split(","):
		var idx := part.rfind(":")
		if idx <= 0:
			continue
		var initials := part.substr(0, idx)
		if initials.length() != INITIALS_LENGTH:
			continue
		var ok := true
		for i in initials.length():
			if not _is_initial(initials[i]):
				ok = false
		if not ok:
			continue
		var parsed: Variant = KParse.to_int_or_null(part.substr(idx + 1))
		if parsed == null or int(parsed) <= 0:
			continue
		var score: int = parsed
		out.append(ScoreEntry.new(initials, score))
	return _sorted(out).slice(0, SIZE)


## Stable sort, best score first (Kotlin sortedByDescending keeps ties in order).
static func _sorted(entries: Array) -> Array:
	var indexed: Array = []
	for i in entries.size():
		indexed.append([entries[i], i])
	indexed.sort_custom(func(a: Array, b: Array) -> bool:
		if a[0].score != b[0].score:
			return a[0].score > b[0].score
		return a[1] < b[1])
	var out: Array = []
	for pair in indexed:
		out.append(pair[0])
	return out
