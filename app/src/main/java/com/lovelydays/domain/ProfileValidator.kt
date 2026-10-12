package com.lovelydays.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ProfileField {
    BIRTH,
    WEIGHT,
    HEIGHT,
    LAST_PERIOD,
    CYCLE,
    PERIOD,
}

enum class IssueCode {
    REQUIRED,
    DATE_IN_FUTURE,
    AGE_RANGE,
    NOT_A_NUMBER,
    WEIGHT_RANGE,
    HEIGHT_RANGE,
    PERIOD_BEFORE_BIRTH,
    PERIOD_TOO_OLD,
    CYCLE_RANGE,
    PERIOD_LENGTH_RANGE,
}

object ProfileValidator {
    fun parseDecimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()

    fun validate(
        birth: LocalDate?,
        weightRaw: String,
        heightRaw: String,
        lastPeriod: LocalDate?,
        cycleRaw: String,
        periodRaw: String,
        today: LocalDate,
    ): Map<ProfileField, IssueCode> {
        val errors = linkedMapOf<ProfileField, IssueCode>()

        if (birth == null) {
            errors[ProfileField.BIRTH] = IssueCode.REQUIRED
        } else if (birth.isAfter(today)) {
            errors[ProfileField.BIRTH] = IssueCode.DATE_IN_FUTURE
        } else {
            val age = BodyMetrics.ageYears(birth, today)
            if (age !in 10..60) errors[ProfileField.BIRTH] = IssueCode.AGE_RANGE
        }

        val weight = parseDecimal(weightRaw)
        when {
            weightRaw.isBlank() -> errors[ProfileField.WEIGHT] = IssueCode.REQUIRED
            weight == null -> errors[ProfileField.WEIGHT] = IssueCode.NOT_A_NUMBER
            weight !in 35.0..200.0 -> errors[ProfileField.WEIGHT] = IssueCode.WEIGHT_RANGE
        }

        val height = parseDecimal(heightRaw)
        when {
            heightRaw.isBlank() -> errors[ProfileField.HEIGHT] = IssueCode.REQUIRED
            height == null -> errors[ProfileField.HEIGHT] = IssueCode.NOT_A_NUMBER
            height !in 130.0..220.0 -> errors[ProfileField.HEIGHT] = IssueCode.HEIGHT_RANGE
        }

        when {
            lastPeriod == null -> errors[ProfileField.LAST_PERIOD] = IssueCode.REQUIRED
            lastPeriod.isAfter(today) -> errors[ProfileField.LAST_PERIOD] = IssueCode.DATE_IN_FUTURE
            birth != null && lastPeriod.isBefore(birth) ->
                errors[ProfileField.LAST_PERIOD] = IssueCode.PERIOD_BEFORE_BIRTH
            ChronoUnit.DAYS.between(lastPeriod, today) > 400 ->
                errors[ProfileField.LAST_PERIOD] = IssueCode.PERIOD_TOO_OLD
        }

        if (cycleRaw.isNotBlank()) {
            val cycle = cycleRaw.trim().toIntOrNull()
            when {
                cycle == null -> errors[ProfileField.CYCLE] = IssueCode.NOT_A_NUMBER
                cycle !in 21..45 -> errors[ProfileField.CYCLE] = IssueCode.CYCLE_RANGE
            }
        }

        if (periodRaw.isNotBlank()) {
            val period = periodRaw.trim().toIntOrNull()
            when {
                period == null -> errors[ProfileField.PERIOD] = IssueCode.NOT_A_NUMBER
                period !in 2..10 -> errors[ProfileField.PERIOD] = IssueCode.PERIOD_LENGTH_RANGE
            }
        }

        return errors
    }

    fun message(code: IssueCode): String = when (code) {
        IssueCode.REQUIRED -> "Заполните это поле"
        IssueCode.DATE_IN_FUTURE -> "Дата не может быть в будущем"
        IssueCode.AGE_RANGE -> "Возраст по этой дате должен быть от 10 до 60 лет"
        IssueCode.NOT_A_NUMBER -> "Нужно число"
        IssueCode.WEIGHT_RANGE -> "Укажите вес от 35 до 200 кг"
        IssueCode.HEIGHT_RANGE -> "Укажите рост от 130 до 220 см"
        IssueCode.PERIOD_BEFORE_BIRTH -> "Эта дата раньше дня рождения"
        IssueCode.PERIOD_TOO_OLD -> "Укажите дату не старше 400 дней"
        IssueCode.CYCLE_RANGE -> "Длина цикла — от 21 до 45 дней"
        IssueCode.PERIOD_LENGTH_RANGE -> "Длина менструации — от 2 до 10 дней"
    }
}
