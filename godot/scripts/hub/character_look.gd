class_name CharacterLook
extends RefCounted
## hub/Figures.kt CharacterLook: how a kid looks: skin, hair (colour and style 0 short, 1 spiky,
## 2 long), clothes and hat, and an [member apron] colour for staff (0 for none). Colours are ARGB
## ints; [member hat] is a [enum Catalog.HatStyle] or [constant Catalog.NONE].
##
## Kotlin's data class compares by value: use [method equals], and [method key] wherever a look
## keys a Dictionary (Kotlin's HashMap<CharacterLook, ...>).

var skin: int
var hair: int
var hair_style: int
var shirt: int
var pants: int
var shoes: int
var hat: int
var apron: int


func _init(p_skin: int, p_hair: int, p_hair_style: int, p_shirt: int, p_pants: int,
		p_shoes: int = Pal.DARKGRAY, p_hat: int = Catalog.NONE, p_apron: int = 0) -> void:
	skin = p_skin & 0xFFFFFFFF
	hair = p_hair & 0xFFFFFFFF
	hair_style = p_hair_style
	shirt = p_shirt & 0xFFFFFFFF
	pants = p_pants & 0xFFFFFFFF
	shoes = p_shoes & 0xFFFFFFFF
	hat = p_hat
	apron = p_apron & 0xFFFFFFFF


func equals(o: CharacterLook) -> bool:
	return o != null and skin == o.skin and hair == o.hair and hair_style == o.hair_style and shirt == o.shirt \
		and pants == o.pants and shoes == o.shoes and hat == o.hat and apron == o.apron


## A string that is the same for equal looks (a Dictionary key).
func key() -> String:
	return "%x|%x|%d|%x|%x|%x|%d|%x" % [skin, hair, hair_style, shirt, pants, shoes, hat, apron]


## Kotlin's data-class hashCode over the same fields, in 32-bit Int arithmetic (Kotlin colours are
## signed Ints). Build-13 hashed the hat with its enum's identity hash, which changes from launch to
## launch; here a hat hashes as its style's name would (Java's String.hashCode), the same every run.
func hash_code() -> int:
	var r := MathUtil.i32(skin)
	r = MathUtil.i32(r * 31 + MathUtil.i32(hair))
	r = MathUtil.i32(r * 31 + hair_style)
	r = MathUtil.i32(r * 31 + MathUtil.i32(shirt))
	r = MathUtil.i32(r * 31 + MathUtil.i32(pants))
	r = MathUtil.i32(r * 31 + MathUtil.i32(shoes))
	r = MathUtil.i32(r * 31 + hat_hash(hat))
	r = MathUtil.i32(r * 31 + MathUtil.i32(apron))
	return r


## What the hat contributes to [method hash_code]: 0 for none, else its style name's String.hashCode.
static func hat_hash(style: int) -> int:
	if style == Catalog.NONE:
		return 0
	var name: String = Catalog.HatStyle.keys()[style]
	var h := 0
	for i in name.length():
		h = MathUtil.i32(h * 31 + name.unicode_at(i))
	return h


func _to_string() -> String:
	return "CharacterLook(skin=%x, hair=%x, hairStyle=%d, shirt=%x, pants=%x, shoes=%x, hat=%d, apron=%x)" % [
		skin, hair, hair_style, shirt, pants, shoes, hat, apron]
