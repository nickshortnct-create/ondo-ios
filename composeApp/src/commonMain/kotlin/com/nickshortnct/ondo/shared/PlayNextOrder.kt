package com.nickshortnct.ondo.shared

/** Shift old indices and place the inserted block immediately after the playing entry. */
internal fun playNextShuffleOrder(order: List<Int>, current: Int, insertAt: Int, count: Int): List<Int> {
	val shifted = order.map { if (it >= insertAt) it + count else it }.toMutableList()
	val currentShifted = if (current >= insertAt) current + count else current
	val position = (shifted.indexOf(currentShifted) + 1).coerceIn(0, shifted.size)
	shifted.addAll(position, (insertAt until insertAt + count).toList())
	return shifted
}
