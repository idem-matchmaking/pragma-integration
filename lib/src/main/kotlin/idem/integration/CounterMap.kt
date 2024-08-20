package idem.integration


@JvmInline
value class CounterMap(private val map: MutableMap<String, Int> = mutableMapOf()) {
    fun increment(key: String): Int {
        return map.compute(key) { _, value -> (value ?: 0) + 1 }!!
    }

    fun decrementOrSkip(key: String): Int? {
        return map.computeIfPresent(key) { _, value -> if (value > 1) value - 1 else null }
    }
}