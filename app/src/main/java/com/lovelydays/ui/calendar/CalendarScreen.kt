@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.lovelydays.ui.calendar

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovelydays.LovelyApplication
import com.lovelydays.domain.CycleCalculator
import com.lovelydays.domain.DayCopy
import com.lovelydays.domain.Mark
import com.lovelydays.domain.MonthGrid
import com.lovelydays.domain.PeriodStart
import com.lovelydays.domain.Profile
import com.lovelydays.domain.ProfileValidator
import com.lovelydays.domain.Russian
import com.lovelydays.ui.components.Disclaimer
import com.lovelydays.ui.components.EmbeddedScreen
import com.lovelydays.ui.components.InsightCard
import com.lovelydays.ui.components.PrimaryButton
import com.lovelydays.ui.theme.LocalMarkColors
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DayCell(
    val label: String,
    val epochDay: Long?,
    val marks: List<Mark>,
    val today: Boolean,
    val selected: Boolean,
    val period: Boolean,
    val description: String,
)

data class CalendarUiState(
    val loading: Boolean = true,
    val ready: Boolean = false,
    val monthLabel: String = "",
    val cells: List<DayCell> = emptyList(),
    val selectedTitle: String = "",
    val selectedLines: List<String> = emptyList(),
    val markDateLabel: String = "",
    val marking: Boolean = false,
    val markMessage: String? = null,
    val markFailed: Boolean = false,
)

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val container = LovelyApplication.from(app).container
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())
    private val ticks = MutableStateFlow(0)
    private val marking = MutableStateFlow(false)
    private val markMessage = MutableStateFlow<String?>(null)
    private val markFailed = MutableStateFlow(false)

    private val monthSelection = combine(ticks, month, selected) { _, visible, day ->
        visible to day
    }
    private val markStatus = combine(marking, markMessage, markFailed) { busy, message, failed ->
        MarkStatus(busy, message, failed)
    }

    val ui = combine(monthSelection, markStatus) { selection, status ->
        MarkInputs(
            visible = selection.first,
            day = selection.second,
            marking = status.busy,
            message = status.message,
            failed = status.failed,
        )
    }
        .flatMapLatest { input ->
            container.session.userId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(CalendarUiState(loading = false, ready = false))
                } else {
                    container.users.observeProfile(id).map { profile ->
                        mapCalendar(profile, input.visible, input.day).copy(
                            marking = input.marking,
                            markMessage = input.message,
                            markFailed = input.failed,
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun refresh() {
        ticks.update { it + 1 }
    }

    fun shiftMonth(delta: Long) {
        month.update { it.plusMonths(delta) }
    }

    fun select(epochDay: Long) {
        selected.value = LocalDate.ofEpochDay(epochDay)
    }

    fun jumpToday() {
        val today = LocalDate.now()
        selected.value = today
        month.value = YearMonth.from(today)
    }

    fun markPeriodStarted() {
        if (marking.value) return
        viewModelScope.launch {
            marking.value = true
            markMessage.value = null
            markFailed.value = false
            try {
                val id = container.session.currentUserId()
                val profile = id?.let { container.users.getProfile(it) }
                if (id == null || profile == null) {
                    markFailed.value = true
                    markMessage.value = "Сначала заполните профиль."
                    return@launch
                }
                val day = selected.value
                when (val result = PeriodStart.apply(profile, day, LocalDate.now())) {
                    is PeriodStart.Result.Invalid -> {
                        markFailed.value = true
                        markMessage.value = ProfileValidator.message(result.reason)
                    }
                    is PeriodStart.Result.Updated -> {
                        container.users.saveProfile(result.profile)
                        container.scheduler.reschedule(id)
                        markFailed.value = false
                        markMessage.value =
                            "Записали ${Russian.full(day)} как первый день. Прогноз, календарь и напоминания обновились."
                    }
                }
            } catch (_: Exception) {
                markFailed.value = true
                markMessage.value = "Не получилось записать. Попробуйте ещё раз."
            } finally {
                marking.value = false
            }
        }
    }
}

private data class MarkStatus(
    val busy: Boolean,
    val message: String?,
    val failed: Boolean,
)

private data class MarkInputs(
    val visible: YearMonth,
    val day: LocalDate,
    val marking: Boolean,
    val message: String?,
    val failed: Boolean,
)

private fun mapCalendar(profile: Profile?, month: YearMonth, selected: LocalDate): CalendarUiState {
    if (profile == null) return CalendarUiState(loading = false, ready = false)
    val today = LocalDate.now()
    val until = maxOf(month.atEndOfMonth(), selected.plusDays(40))
    val resolved = CycleCalculator.resolve(profile, today)
    val cycles = CycleCalculator.project(resolved.params, until)
    val cells = MonthGrid.cells(month).map { date ->
        if (date == null) {
            DayCell("", null, emptyList(), today = false, selected = false, period = false, description = "")
        } else {
            val marks = CycleCalculator.marksOn(date, cycles)
            DayCell(
                label = date.dayOfMonth.toString(),
                epochDay = date.toEpochDay(),
                marks = marks,
                today = date == today,
                selected = date == selected,
                period = cycles.any { it.period.contains(date) },
                description = dayDescription(date, today, marks),
            )
        }
    }
    val title = buildString {
        append(Russian.full(selected))
        if (selected == today) append(" · сегодня")
    }
    return CalendarUiState(
        loading = false,
        ready = true,
        monthLabel = Russian.month(month),
        cells = cells,
        selectedTitle = title,
        selectedLines = DayCopy.lines(selected, cycles),
        markDateLabel = Russian.full(selected),
    )
}

private fun dayDescription(date: LocalDate, today: LocalDate, marks: List<Mark>): String {
    val parts = mutableListOf(Russian.full(date))
    if (date == today) parts += "сегодня"
    if (marks.isEmpty()) return parts.joinToString(". ")
    return parts.joinToString(". ") + ". " + marks.joinToString(", ") { markLabel(it) }
}

private fun markLabel(mark: Mark): String = when (mark) {
    Mark.PERIOD -> "менструация"
    Mark.OVULATION -> "овуляция"
    Mark.FERTILE -> "фертильные дни"
    Mark.MOOD -> "настроение"
    Mark.SKIN -> "кожа"
}

@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    EmbeddedScreen {
        Text("Календарь", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Отметки — оценка, не медицинский прогноз и не контрацепция.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            !state.ready -> Text("Заполните профиль, чтобы увидеть календарь.")
            else -> {
                PeriodStartAction(state, vm)
                MonthBoard(state, vm)
            }
        }
    }
}

@Composable
private fun PeriodStartAction(state: CalendarUiState, vm: CalendarViewModel) {
    Text(
        "Отметить, что месячные начались ${state.markDateLabel}. По умолчанию это сегодня — выберите другой день, если начало было раньше.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PrimaryButton(
        text = "Месячные начались",
        loading = state.marking,
        onClick = vm::markPeriodStarted,
    )
    state.markMessage?.let { message ->
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.markFailed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun MonthBoard(state: CalendarUiState, vm: CalendarViewModel) {
    val marks = LocalMarkColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { vm.shiftMonth(-1) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
        }
        Text(state.monthLabel, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = { vm.shiftMonth(1) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Следующий месяц")
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = vm::jumpToday) { Text("Сегодня") }
    }
    Row(Modifier.fillMaxWidth()) {
        for (label in listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    for (week in state.cells.chunked(7)) {
        Row(Modifier.fillMaxWidth()) {
            for (cell in week) {
                DayCellView(cell) {
                    cell.epochDay?.let(vm::select)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Обозначения", style = MaterialTheme.typography.titleMedium)
        LegendLine(marks.period, "Менструация")
        LegendLine(marks.ovulation, "Овуляция")
        LegendLine(marks.fertile, "Фертильные дни")
        LegendLine(marks.mood, "Настроение")
        LegendLine(marks.skin, "Кожа")
    }
    InsightCard(
        title = state.selectedTitle,
        body = state.selectedLines.joinToString("\n"),
        accent = MaterialTheme.colorScheme.primary,
    )
    Disclaimer()
}

@Composable
private fun LegendLine(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RowScope.DayCellView(cell: DayCell, onClick: () -> Unit) {
    val palette = LocalMarkColors.current
    val shape = RoundedCornerShape(12.dp)
    val background = when {
        cell.period -> palette.period.copy(alpha = 0.16f)
        cell.selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .padding(2.dp)
            .clip(shape)
            .background(background)
            .then(
                if (cell.today) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = cell.epochDay != null, onClick = onClick)
            .clearAndSetSemantics { contentDescription = cell.description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (cell.label.isNotEmpty()) {
            Text(
                cell.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (cell.today || cell.selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (cell.period) palette.period else MaterialTheme.colorScheme.onSurface,
            )
            if (cell.marks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    cell.marks.forEach { mark ->
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(palette.color(mark)),
                        )
                    }
                }
            }
        }
    }
}
