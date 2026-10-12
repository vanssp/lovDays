package com.lovelydays.domain

import java.time.LocalDate
import java.time.Period

/**
 * Age and BMI only soften confidence and, when the user leaves cycle length empty,
 * pick a plain default. They do not invent a more precise date.
 */
object BodyMetrics {
    const val DEFAULT_PERIOD_LENGTH = 5

    fun ageYears(birth: LocalDate, today: LocalDate): Int =
        Period.between(birth, today).years.coerceAtLeast(0)

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val meters = heightCm / 100.0
        if (meters <= 0.0) return 0.0
        return weightKg / (meters * meters)
    }

    fun assumedCycleLength(ageYears: Int, bmi: Double): Int {
        val base = when {
            ageYears < 18 -> 32
            ageYears <= 22 -> 30
            else -> 28
        }
        val adjusted = when {
            bmi < 18.5 -> base + 1
            bmi >= 30.0 -> base + 2
            else -> base
        }
        return adjusted.coerceIn(26, 35)
    }

    enum class Level { TYPICAL, LOWER }

    data class Confidence(val level: Level, val note: String)

    fun confidence(ageYears: Int, bmi: Double): Confidence {
        val bits = mutableListOf<String>()
        if (ageYears < 18) {
            bits += "До 18 лет цикл часто ещё неровный, поэтому даты легко сдвигаются."
        }
        if (ageYears >= 45) {
            bits += "После 45 лет цикл может становиться менее регулярным — это только ориентир."
        }
        if (bmi < 18.5 || bmi >= 30.0) {
            bits += "При ИМТ заметно ниже 18,5 или от 30 цикл иногда идёт менее ровно, чем у большинства."
        }
        return if (bits.isEmpty()) {
            Confidence(
                level = Level.TYPICAL,
                note = "По возрасту и ИМТ нет отдельного повода ждать особенно неровный цикл. Это всё равно ориентир, не точный календарь.",
            )
        } else {
            Confidence(level = Level.LOWER, note = bits.joinToString(" "))
        }
    }
}
