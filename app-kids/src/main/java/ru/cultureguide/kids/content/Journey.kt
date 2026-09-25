package ru.cultureguide.kids.content

import kotlin.math.roundToInt

/**
 * Прогресс прогулки. Точки проходятся строго по порядку, поэтому достаточно
 * числа найденных вещей: следующая цель — точка с индексом [found].
 */
data class Journey(val stopCount: Int, val found: Int = 0, val started: Boolean = false) {
    init {
        require(found in 0..stopCount) { "found=$found вне 0..$stopCount" }
    }

    val activeIndex: Int get() = found
    val finished: Boolean get() = found == stopCount

    fun isFound(index: Int): Boolean = index < found

    fun start(): Journey = copy(started = true)

    /** Вещь на точке [index] найдена; повторное подтверждение ничего не меняет. */
    fun collect(index: Int): Journey =
        if (index == found && !finished) copy(found = found + 1, started = true) else this

    fun reset(): Journey = Journey(stopCount)
}

/** Средний шаг ребёнка 4–7 лет, м. */
private const val KID_STEP_M = 0.45

/** Расстояние в детских шагах, округлённое так, чтобы число легко было прочитать вслух. */
fun kidSteps(meters: Double): Int {
    val steps = meters / KID_STEP_M
    return when {
        steps < 20 -> steps.roundToInt()
        steps < 200 -> (steps / 10).roundToInt() * 10
        else -> (steps / 50).roundToInt() * 50
    }
}

/** «шаг», «шага», «шагов» — по правилам русского языка. */
fun stepsWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "шагов"
        mod10 == 1 -> "шаг"
        mod10 in 2..4 -> "шага"
        else -> "шагов"
    }
}
