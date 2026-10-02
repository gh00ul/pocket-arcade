extends PaTest
## ui/RollingNumberTest.kt: the odometer maths behind rolling counters, and the flight path of
## coins and tickets.

const Odo := RollingNumber.Odometer
const Fly := CurrencyFx.FlyPath


func test_whole_numbers_rest_every_wheel_on_its_digit() -> void:
	for v: int in [0, 7, 10, 19, 20, 99, 100, 4321, 100000]:
		var f := float(v)
		for p in range(0, 6):
			assert_near(0.0, Odo.roll(f, p), 0.0, "roll of %d at %d" % [v, p])
			assert_eq(v / maxi(int(pow(10.0, p)), 1), Odo.turns(f, p))


func test_the_units_wheel_turns_with_the_value_and_eases_at_both_ends() -> void:
	assert_near(0.0, Odo.roll(4.0, 0), 0.0)
	assert_near(0.5, Odo.roll(4.5, 0), 1e-4)
	# Smoothstep: slow start and end, so a digit lingers on its number while counting fast.
	assert_true(Odo.roll(4.1, 0) < 0.1)
	assert_true(Odo.roll(4.9, 0) > 0.9)
	assert_eq(4, Odo.turns(4.99, 0))


func test_a_higher_wheel_only_rolls_while_the_one_below_carries() -> void:
	# 19 -> 20: the tens wheel stays put until the units wheel is in its last tenth.
	assert_near(0.0, Odo.roll(11.3, 1), 0.0)
	assert_near(0.0, Odo.roll(15.8, 1), 0.0)
	assert_near(0.0, Odo.roll(18.9, 1), 1e-6)
	var r := Odo.roll(19.5, 1)
	assert_true(r >= 0.4 and r <= 0.6)
	assert_true(Odo.roll(19.99, 1) > 0.99)
	# And the units wheel is mid-turn (9 -> 0) at the same moment.
	var u := Odo.roll(19.5, 0)
	assert_true(u >= 0.4 and u <= 0.6)
	assert_eq(1, Odo.turns(19.5, 1))


func test_a_carry_through_several_digits_rolls_them_all_together() -> void:
	# 99.5 -> 100: units, tens and hundreds all halfway through their turn at once.
	for p in range(0, 3):
		var r := Odo.roll(99.5, p)
		assert_true(r >= 0.4 and r <= 0.6, "wheel %d" % p)
	# But 90.5 has only the units wheel moving.
	var u := Odo.roll(90.5, 0)
	assert_true(u >= 0.4 and u <= 0.6)
	assert_near(0.0, Odo.roll(90.5, 1), 0.0)
	assert_near(0.0, Odo.roll(90.5, 2), 0.0)


func test_a_leading_blank_wheel_rolls_in_the_new_digit() -> void:
	# 9.95: no tens digit yet (turns 0), but the wheel is about to bring in a 1.
	assert_eq(0, Odo.turns(9.95, 1))
	assert_true(Odo.roll(9.95, 1) > 0.7)
	assert_eq(1, Odo.turns(10.0, 1))
	assert_near(0.0, Odo.roll(10.0, 1), 0.0)


func test_negative_and_huge_values_stay_in_range() -> void:
	assert_eq(0, Odo.turns(-5.0, 0))
	assert_near(0.0, Odo.roll(-5.0, 0), 0.0)
	assert_eq(0, Odo.turns(5.0, 12))
	var r := Odo.roll(2000000000.0, 9)
	assert_true(r >= 0.0 and r <= 1.0)


func test_digit_counts() -> void:
	assert_eq(1, Odo.digit_count(0))
	assert_eq(1, Odo.digit_count(9))
	assert_eq(2, Odo.digit_count(10))
	assert_eq(3, Odo.digit_count(999))
	assert_eq(4, Odo.digit_count(1000))
	assert_eq(1, Odo.digit_count(-40))


func test_big_jumps_roll_longer_but_never_forever() -> void:
	var one := RollingNumber.roll_millis(0.0, 1.0)
	assert_true(one >= 300 and one <= 400)
	assert_true(RollingNumber.roll_millis(0.0, 30.0) > RollingNumber.roll_millis(0.0, 1.0))
	assert_eq(1100, RollingNumber.roll_millis(0.0, 1000000.0))
	assert_eq(RollingNumber.roll_millis(5.0, 0.0), RollingNumber.roll_millis(0.0, 5.0))


# ---- the arc a coin flies

func test_a_flight_starts_and_ends_exactly_where_it_should() -> void:
	assert_near(0.0, Fly.ease(0.0), 0.0)
	assert_near(1.0, Fly.ease(1.0), 1e-6)
	assert_near(0.5, Fly.ease(0.5), 1e-6)
	assert_near(10.0, Fly.bezier(10.0, 50.0, 90.0, 0.0), 0.0)
	assert_near(90.0, Fly.bezier(10.0, 50.0, 90.0, 1.0), 1e-4)


func test_easing_never_goes_backwards_or_out_of_range() -> void:
	var prev := 0.0
	for i in range(0, 101):
		var e := Fly.ease(i / 100.0)
		assert_true(e >= prev - 1e-6 and e >= 0.0 and e <= 1.0)
		prev = e
	assert_near(0.0, Fly.ease(-3.0), 0.0)
	assert_near(1.0, Fly.ease(9.0), 1e-6)


func test_the_arc_rises_above_the_straight_line_between_the_ends() -> void:
	# From (0, 300) to (300, 300): a level flight must lift (smaller y) in the middle.
	var lift := Fly.lift(300.0, 0.0)
	assert_true(lift >= 56.0)
	var mid_y := Fly.bezier(300.0, 300.0 - lift, 300.0, 0.5)
	assert_true(mid_y < 300.0 - lift / 3.0, "mid y %f" % mid_y)
	# A short hop still gets a visible arc.
	assert_near(56.0, Fly.lift(10.0, 10.0), 0.0)
