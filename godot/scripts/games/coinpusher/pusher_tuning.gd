class_name PusherTuning
extends RefCounted
## games/coinpusher/CoinPusherGame.kt PusherTuning: difficulty and payout knobs for the coin pusher.

const ROUND_SECONDS := 50.0
const COINS_PER_ROUND := 25
const DROP_COOLDOWN := 0.18
## Fraction of the hex-packed starting deck left empty (looser deck = fewer spills).
const PACK_GAP_CHANCE := 0.06
## How far (in coin radii) the front row sits back from the lip.
const FRONT_ROW_OVERHANG := 0.6
const START_ITEMS := 4
## Chance that a dropped coin brings a bonus item along with it.
const ITEM_DROP_CHANCE := 0.14
const PUSHER_PERIOD := 3.2
const PUSHER_BACK := 150.0
const PUSHER_FORWARD := 232.0
## Fraction of velocity lost per second as coins slide on the deck.
const SLIDE_DAMPING := 7.0
const COIN_POINTS := 10
const GEM_POINTS := 50
const BIG_COIN_POINTS := 50
const TICKET_BUNDLE := 10
const SHOWER_COINS := 6
## Coins falling off within AVALANCHE_WINDOW seconds that trigger the avalanche bonus.
const AVALANCHE_COUNT := 5
const AVALANCHE_WINDOW := 0.9
const AVALANCHE_BONUS := 30
const POINTS_PER_TICKET := 15
const BASE_TICKETS := 1
## Seconds after the last coin before an out-of-coins round ends early.
const SETTLE_AFTER_LAST_COIN := 3.5
