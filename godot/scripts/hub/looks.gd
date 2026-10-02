class_name Looks
extends RefCounted
## hub/Figures.kt Looks: the cast: the player, the prize clerk, the café barista and a
## deterministic crowd of kids.

const SKINS: Array[int] = [0xFFFFD9B8, 0xFFF1C27D, 0xFFE0A87A, 0xFFC68642, 0xFF8D5524, 0xFF5C3A21]
const HAIRS: Array[int] = [0xFF2B1B10, 0xFF5A3418, 0xFF8B5A2B, 0xFFE8C170, 0xFF141018, 0xFFB5462E, 0xFFFF77C8, 0xFF4DA6FF]
const SHIRTS: Array[int] = [0xFFFF4D4D, 0xFF3DDC84, 0xFF4DA6FF, 0xFFFFE14D, 0xFF8A4FFF, 0xFFFF9A3C, 0xFFFFFFFF, 0xFF3DF5FF, 0xFFFF77C8]
const PANTS: Array[int] = [0xFF1A2A6C, 0xFF3A3A55, 0xFF5A3418, 0xFF2E1F5E, 0xFF2F5BE0, 0xFF22222A]
const SHOES: Array[int] = [0xFFFFFFFF, 0xFF22222A, 0xFFFF4D4D, 0xFF4DA6FF]
## The kids' hats by pick: mostly none.
const KID_HATS: Array[int] = [Catalog.NONE, Catalog.NONE, Catalog.NONE, Catalog.HatStyle.CAP, Catalog.HatStyle.BEANIE, Catalog.HatStyle.HEADPHONES, Catalog.NONE]

## The prize clerk.
static var clerk := CharacterLook.new(0xFFC68642, 0xFF141018, 0, 0xFFE8323C, 0xFF1A2A6C, Pal.DARKGRAY, Catalog.HatStyle.CAP)

## The café barista.
static var barista := CharacterLook.new(0xFFE0A87A, 0xFF5A3418, 2, 0xFFF4EEE2, 0xFF2A2A34, 0xFF22222A, Catalog.HatStyle.CAP, 0xFF1F8A70)


static func _pick(arr: Array[int], seed_value: int, salt: int) -> int:
	var i := MathUtil.i32(MathUtil.i32(seed_value * 7919) + MathUtil.i32(salt * 104729)) & 0x7FFFFFFF
	return arr[i % arr.size()]


## A kid's look, the same for the same [param seed_value] on every run (build-13's picks).
static func random_kid(seed_value: int) -> CharacterLook:
	return CharacterLook.new(
		_pick(SKINS, seed_value, 1), _pick(HAIRS, seed_value, 2), MathUtil.i32(seed_value * 13 + 5) % 3,
		_pick(SHIRTS, seed_value, 3), _pick(PANTS, seed_value, 4), _pick(SHOES, seed_value, 5),
		KID_HATS[MathUtil.i32(seed_value * 31 + 7) % KID_HATS.size()])


## The player's kid in an outfit ([param shirt], [param pants]) and [param hat] (a HatStyle or Catalog.NONE).
static func player(shirt: int, pants: int, hat: int) -> CharacterLook:
	return CharacterLook.new(0xFFF1C27D, 0xFF5A3418, 1, shirt, pants, 0xFFFFFFFF, hat)
