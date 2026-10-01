extends PaTest
## KRandom must draw exactly what kotlin.random.Random(seed) draws. The expected rows were printed
## by kotlin-stdlib 2.2.20 (Random(seed): nextInt(), nextInt(10), nextInt(3, 1000), nextInt(16),
## nextFloat(), nextDouble(), nextBoolean(), nextLong(), nextInt(7)).

const ROWS := [
	[0, -1934310868, 8, 424, 10, 0.659103, 0.1867139191625704, false, 5994621695664957510, 0],
	[1, 600123930, 6, 981, 10, 0.8739363, 0.2662985617095538, true, 1355854690223605162, 4],
	[7, -182312124, 9, 220, 5, 0.36342025, 0.704174948914165, true, 436788053283425495, 6],
	[42, 972016666, 0, 625, 15, 0.27072817, 0.15133155838385304, true, 3386893265803317318, 4],
	[123456789012345, 2024811512, 8, 254, 2, 0.90084887, 0.37798824146663623, true, -3048904022120468845, 6],
	[-5, 1661948647, 4, 748, 14, 0.24090463, 0.16447757638857863, true, 5088738295973626113, 0],
]


func test_sequences_match_kotlin_stdlib() -> void:
	for row: Array in ROWS:
		var r := KRandom.new(row[0])
		var label := "seed %d" % row[0]
		assert_eq(row[1], r.next_int(), label + " nextInt")
		assert_eq(row[2], r.next_int_until(10), label + " nextInt(10)")
		assert_eq(row[3], r.next_int_range(3, 1000), label + " nextInt(3,1000)")
		assert_eq(row[4], r.next_int_until(16), label + " nextInt(16)")
		assert_near(row[5], r.next_float(), 1e-6, label + " nextFloat")
		assert_near(row[6], r.next_double(), 1e-15, label + " nextDouble")
		assert_eq(row[7], r.next_boolean(), label + " nextBoolean")
		assert_eq(row[8], r.next_long(), label + " nextLong")
		assert_eq(row[9], r.next_int_until(7), label + " nextInt(7)")


func test_hash01_is_kotlin_32_bit_hash() -> void:
	# Values from engine/MathUtil.kt hash01's arithmetic run on the JVM.
	assert_near(0.41138324, MathUtil.hash01(12, 34, 5), 1e-6)
	assert_near(0.53196, MathUtil.hash01(-3, 7, 0), 1e-6)
	assert_near(0.33832303, MathUtil.hash01(100000, -99999, 31), 1e-6)
	assert_near(0.0, MathUtil.hash01(0, 0, 0), 1e-9)
