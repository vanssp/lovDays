package com.lovelydays.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.round

data class Forecast(
    val periodTitle: String,
    val periodBody: String,
    val ovulationTitle: String,
    val ovulationBody: String,
    val moodBody: String,
    val skinBody: String,
    val cycleNote: String,
    val periodLengthNote: String,
    val confidenceTitle: String,
    val confidenceBody: String,
    val gapNote: String?,
    val overlapNote: String?,
    val metricsNote: String,
    val disclaimer: String,
)

object ForecastBuilder {
    const val DISCLAIMER =
        "«Эти дни» не медицинское изделие и не средство контрацепции. Все даты — приблизительная оценка по вашим данным."

    fun build(profile: Profile, today: LocalDate = LocalDate.now()): Forecast {
        val resolved = CycleCalculator.resolve(profile, today)
        val cycles = CycleCalculator.project(resolved.params, today.plusDays(220))
        val periodCycle = cycles.firstOrNull { !it.period.endInclusive.isBefore(today) }
        val ovulationCycle = cycles.firstOrNull { !it.ovulation.isBefore(today) }
        val gapDays = ChronoUnit.DAYS.between(profile.lastPeriod, today)

        val periodTitle: String
        val periodBody: String
        if (periodCycle == null) {
            periodTitle = "Менструация"
            periodBody = "Не хватает данных, чтобы назвать дату."
        } else {
            val ongoing = periodCycle.period.contains(today)
            periodTitle = if (ongoing) "Сейчас менструация" else "Следующая менструация"
            periodBody = "Окно: ${Russian.range(periodCycle.period.start, periodCycle.period.endInclusive)}. " +
                "Начало — ${Russian.day(periodCycle.period.start)}. Это оценка, не диагноз."
        }

        val ovulationTitle: String
        val ovulationBody: String
        if (ovulationCycle == null) {
            ovulationTitle = "Овуляция"
            ovulationBody = "Не хватает данных, чтобы назвать день."
        } else {
            val day = ovulationCycle.ovulation
            ovulationTitle = if (day == today) "Сегодня ориентировочная овуляция" else "Овуляция"
            ovulationBody = "Около ${Russian.day(day)} — за ${CycleCalculator.LUTEAL_DAYS} дней до следующей менструации. " +
                "Фертильное окно: ${Russian.range(ovulationCycle.fertile.start, ovulationCycle.fertile.endInclusive)}. " +
                "Считаем 5 дней до овуляции и сам этот день. Это не контрацепция."
        }

        val moodBody = if (periodCycle != null && ovulationCycle != null) {
            "Перед менструацией настроение может меняться примерно " +
                "${Russian.range(periodCycle.pmsMood.start, periodCycle.pmsMood.endInclusive)}. " +
                "Около овуляции — ещё короткое окно " +
                "${Russian.range(ovulationCycle.midCycleMood.start, ovulationCycle.midCycleMood.endInclusive)}."
        } else {
            "Не хватает данных для окна настроения."
        }

        val skinBody = if (periodCycle != null) {
            "Высыпания чуть вероятнее ${Russian.range(periodCycle.skin.start, periodCycle.skin.endInclusive)}: " +
                "за несколько дней до менструации и в её начале."
        } else {
            "Не хватает данных для окна кожи."
        }

        val cycleLen = resolved.params.cycleLength
        val cycleNote = if (resolved.cycleLengthAssumed) {
            "Длина цикла не задана. Для расчёта берём $cycleLen ${Russian.days(cycleLen)} — " +
                "это оценка по возрасту (${resolved.ageYears} ${Russian.years(resolved.ageYears)}) " +
                "и ИМТ ${Russian.number1(resolved.bmi)}, не измерение."
        } else {
            "Длина цикла: $cycleLen ${Russian.days(cycleLen)}, как вы указали."
        }

        val periodLen = resolved.params.periodLength
        val periodLengthNote = if (resolved.periodLengthAssumed) {
            "Длина менструации не задана. Показываем $periodLen ${Russian.days(periodLen)} — обычное значение по умолчанию."
        } else {
            "Длина менструации: $periodLen ${Russian.days(periodLen)}, как вы указали."
        }

        val confidenceTitle = if (resolved.confidence.level == BodyMetrics.Level.LOWER) {
            "Уверенность ниже"
        } else {
            "Уверенность обычная для такой оценки"
        }

        val overlapNote = if (
            periodCycle != null &&
            ovulationCycle != null &&
            periodCycle.period.overlaps(ovulationCycle.fertile)
        ) {
            "При такой длине цикла фертильное окно пересекается с менструацией. Так выходит из простой формулы, это не точный расчёт."
        } else {
            null
        }

        val gapNote = if (gapDays > 90) {
            "С указанной менструации прошло больше трёх месяцев. Пока нет новой даты, прогноз совсем грубый."
        } else {
            null
        }

        val metricsNote = "Возраст ${resolved.ageYears} ${Russian.years(resolved.ageYears)}, " +
            "ИМТ ${Russian.number1(resolved.bmi)} " +
            "(вес ${formatMeasure(profile.weightKg)} кг, рост ${formatMeasure(profile.heightCm)} см)."

        return Forecast(
            periodTitle = periodTitle,
            periodBody = periodBody,
            ovulationTitle = ovulationTitle,
            ovulationBody = ovulationBody,
            moodBody = moodBody,
            skinBody = skinBody,
            cycleNote = cycleNote,
            periodLengthNote = periodLengthNote,
            confidenceTitle = confidenceTitle,
            confidenceBody = resolved.confidence.note,
            gapNote = gapNote,
            overlapNote = overlapNote,
            metricsNote = metricsNote,
            disclaimer = DISCLAIMER,
        )
    }
}

object DayCopy {
    fun lines(date: LocalDate, cycles: List<PredictedCycle>): List<String> {
        var period = false
        var ovulation = false
        var fertile = false
        var pms = false
        var mid = false
        var skin = false
        for (cycle in cycles) {
            if (cycle.period.contains(date)) period = true
            if (cycle.ovulation == date) ovulation = true
            if (cycle.fertile.contains(date)) fertile = true
            if (cycle.pmsMood.contains(date)) pms = true
            if (cycle.midCycleMood.contains(date)) mid = true
            if (cycle.skin.contains(date)) skin = true
        }
        val lines = mutableListOf<String>()
        if (period) lines += "Менструация"
        if (ovulation) lines += "Овуляция (оценка)"
        if (fertile) lines += "Фертильное окно (оценка, не контрацепция)"
        if (pms) lines += "Настроение: возможное ПМС"
        if (mid) lines += "Настроение: середина цикла, около овуляции"
        if (skin) lines += "Кожа: выше шанс высыпаний"
        if (lines.isEmpty()) lines += "Нет отметок. Обычный день по этому прогнозу."
        return lines
    }
}

fun formatMeasure(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    val whole = rounded.toInt()
    return if (abs(rounded - whole) < 0.001) {
        whole.toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", rounded)
    }
}
