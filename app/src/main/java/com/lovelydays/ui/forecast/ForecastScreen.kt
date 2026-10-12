@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.lovelydays.ui.forecast

import android.app.Application
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovelydays.LovelyApplication
import com.lovelydays.domain.Forecast
import com.lovelydays.domain.ForecastBuilder
import com.lovelydays.ui.components.Disclaimer
import com.lovelydays.ui.components.EmbeddedScreen
import com.lovelydays.ui.components.InsightCard
import com.lovelydays.ui.components.NoteCard
import com.lovelydays.ui.components.QuietTitle
import com.lovelydays.ui.theme.LocalMarkColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface ForecastUi {
    data object Loading : ForecastUi
    data object Missing : ForecastUi
    data class Ready(val forecast: Forecast) : ForecastUi
}

class ForecastViewModel(app: Application) : AndroidViewModel(app) {
    private val container = LovelyApplication.from(app).container
    private val ticks = MutableStateFlow(0)

    val ui = ticks.flatMapLatest {
        container.session.userId.flatMapLatest { id ->
            if (id == null) {
                flowOf(ForecastUi.Missing)
            } else {
                container.users.observeProfile(id).map { profile ->
                    if (profile == null) ForecastUi.Missing else ForecastUi.Ready(ForecastBuilder.build(profile))
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForecastUi.Loading)

    fun refresh() {
        ticks.update { it + 1 }
    }
}

@Composable
fun ForecastScreen(vm: ForecastViewModel = viewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val marks = LocalMarkColors.current
    EmbeddedScreen {
        QuietTitle(
            text = "Прогноз",
            subtitle = "Ближайшие окна по вашим данным. Это ориентир, не расписание организма.",
        )
        when (val ui = state) {
            ForecastUi.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            ForecastUi.Missing -> Text("Сначала заполните профиль — без него считать нечего.")
            is ForecastUi.Ready -> {
                val forecast = ui.forecast
                NoteCard(forecast.cycleNote)
                NoteCard(forecast.periodLengthNote)
                forecast.gapNote?.let { NoteCard(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) }
                NoteCard(forecast.metricsNote)
                NoteCard(
                    "${forecast.confidenceTitle}. ${forecast.confidenceBody}",
                    container = if (forecast.confidenceTitle.startsWith("Уверенность ниже")) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (forecast.confidenceTitle.startsWith("Уверенность ниже")) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                forecast.overlapNote?.let { NoteCard(it) }
                InsightCard(forecast.periodTitle, forecast.periodBody, marks.period)
                InsightCard(forecast.ovulationTitle, forecast.ovulationBody, marks.ovulation)
                InsightCard("Настроение", forecast.moodBody, marks.mood)
                InsightCard("Кожа", forecast.skinBody, marks.skin)
                Disclaimer()
            }
        }
    }
}
