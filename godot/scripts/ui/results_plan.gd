class_name ResultsPlan
extends RefCounted
## ui/ResultsPlan.kt: the results screen's pure parts: the timeline of the staged reveal, the
## score's count-up, the performance grade and how many tickets each flying sprite stands for.
## The reveal runs title, score count-up, best (or NEW HIGH SCORE), grade stamp, ticket printing
## and finally the buttons. A tap during it jumps to PRINT_AT.

## The letter a round is stamped with, best first (ui/ResultsPlan.kt Grade; S has ordinal 0).
enum Grade { S, A, B, C }

const GRADE_LETTERS := ["S", "A", "B", "C"]
const GRADE_ARGB := [Pal.GOLD, Pal.LIME, Pal.CYAN, Pal.LAVENDER]

## The card slides in over this long.
const CARD_IN := 0.45
## The score starts counting up here and takes SCORE_SECONDS.
const SCORE_AT := 0.35
const SCORE_SECONDS := 0.75
## The best line (or the NEW HIGH SCORE slam) lands here.
const BEST_AT := 1.15
## The grade is stamped here; the stamp takes STAMP_SECONDS to slam down.
const GRADE_AT := 1.5
const STAMP_SECONDS := 0.2
## Tickets start printing here, and the buttons follow once they are done.
const PRINT_AT := 1.85
## Where the ticket printer's slot sits in the game field (the strip feeds out below it).
const SLOT_Y := 372.0
## A soft tick sounds this often while the score counts up.
const COUNT_TICK_SECONDS := 0.055
## However many tickets are paid, they fly into the counter in at most this many sprites.
const MAX_FLIGHTS := 18
## Tickets a good round pays: about the middle of the band every machine is tuned into.
const PAR_TICKETS := 24
## Skill needed for each grade (see skill).
const S_AT := 1.05
const A_AT := 0.8
const B_AT := 0.5


static func grade_letter(g: int) -> String:
	return GRADE_LETTERS[g]


static func grade_argb(g: int) -> int:
	return GRADE_ARGB[g]


## The score shown [param t] seconds into the reveal: 0 before SCORE_AT, the final score once counted, eased out.
static func counted_score(score: int, t: float) -> int:
	var p := MathUtil.clamp01((t - SCORE_AT) / SCORE_SECONDS)
	return score if p >= 1.0 else int(score * MathUtil.ease_out_cubic(p))


## How well a round went, about 0 to 1.4: half how the score stands against the player's [param best]
## before this round (0 to 1.25) and half the ticket haul against PAR_TICKETS (0 to 1.5). With no
## best yet the haul alone decides.
static func skill(score: int, best: int, tickets: int) -> float:
	var pay := clampf(tickets / float(PAR_TICKETS), 0.0, 1.5)
	if best <= 0:
		return pay
	var vs_best := clampf(score / float(best), 0.0, 1.25)
	return 0.5 * vs_best + 0.5 * pay


## The grade for a round: [param score] against the previous [param best], and the [param tickets]
## it paid in all. A zero score is always a C.
static func grade(score: int, best: int, tickets: int) -> int:
	if score <= 0:
		return Grade.C
	var s := skill(score, best, tickets)
	if s >= S_AT:
		return Grade.S
	if s >= A_AT:
		return Grade.A
	if s >= B_AT:
		return Grade.B
	return Grade.C


## How many tickets one flying sprite stands for, so [param total] tickets fly in at most MAX_FLIGHTS sprites.
static func flight_chunk(total: int) -> int:
	return maxi(ceili(maxi(total, 0) / float(MAX_FLIGHTS)), 1)


## The reveal stage at [param t] seconds in: 0 card, 1 score counting, 2 best, 3 grade, 4 printing.
static func stage_at(t: float) -> int:
	if t >= PRINT_AT:
		return 4
	if t >= GRADE_AT:
		return 3
	if t >= BEST_AT:
		return 2
	if t >= SCORE_AT:
		return 1
	return 0
