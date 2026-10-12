package com.lovelydays.ui.sync

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovelydays.LovelyApplication
import com.lovelydays.data.calendar.DeviceCalendar
import com.lovelydays.data.calendar.SyncResult
import com.lovelydays.domain.Russian
import com.lovelydays.ui.components.EmbeddedScreen
import com.lovelydays.ui.components.NoteCard
import com.lovelydays.ui.components.PrimaryButton
import com.lovelydays.ui.components.QuietTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SyncUi(
    val loading: Boolean = true,
    val permission: Boolean = false,
    val calendars: List<DeviceCalendar> = emptyList(),
    val selectedId: Long? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val container = LovelyApplication.from(app).container
    private val _state = MutableStateFlow(SyncUi())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val granted = container.calendars.hasPermissions()
            val listed = if (granted) {
                withContext(Dispatchers.IO) { runCatching { container.calendars.listCalendars() } }
            } else {
                null
            }
            val id = container.session.currentUserId()
            val link = id?.let { container.users.getLink(it) }
            _state.update { current ->
                current.copy(
                    loading = false,
                    permission = granted,
                    calendars = listed?.getOrNull().orEmpty(),
                    selectedId = link?.calendarId,
                    error = if (listed?.isFailure == true) {
                        "Не получилось прочитать список календарей."
                    } else {
                        current.error
                    },
                )
            }
        }
    }

    fun select(calendar: DeviceCalendar) {
        viewModelScope.launch {
            val id = container.session.currentUserId() ?: return@launch
            container.users.saveLink(
                com.lovelydays.data.repo.CalendarLink(
                    userId = id,
                    calendarId = calendar.id,
                    displayName = calendar.displayName,
                    accountName = calendar.accountName,
                ),
            )
            _state.update { it.copy(selectedId = calendar.id, message = null, error = null) }
        }
    }

    fun sync() {
        val snapshot = _state.value
        val selected = snapshot.calendars.find { it.id == snapshot.selectedId }
        if (selected == null) {
            _state.update { it.copy(error = "Сначала выберите календарь.", message = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            val id = container.session.currentUserId()
            if (id == null) {
                _state.update { it.copy(busy = false, error = "Сначала войдите в аккаунт.") }
                return@launch
            }
            val result = withContext(Dispatchers.IO) { container.calendars.sync(id, selected) }
            _state.update { current ->
                when (result) {
                    is SyncResult.Written -> current.copy(
                        busy = false,
                        message = if (result.count == 0) {
                            "Не нашли будущих окон для записи. Проверьте дату последней менструации в профиле."
                        } else {
                            "Записали ${result.count} ${Russian.events(result.count)} в «${selected.displayName}»."
                        },
                        error = null,
                    )
                    is SyncResult.Failed -> current.copy(busy = false, error = result.message, message = null)
                    is SyncResult.Removed -> current.copy(busy = false)
                }
            }
        }
    }

    fun remove() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            val id = container.session.currentUserId()
            if (id == null) {
                _state.update { it.copy(busy = false, error = "Сначала войдите в аккаунт.") }
                return@launch
            }
            val result = withContext(Dispatchers.IO) { container.calendars.remove(id) }
            _state.update { current ->
                when (result) {
                    is SyncResult.Removed -> current.copy(
                        busy = false,
                        message = "Удалили ${result.count} ${Russian.events(result.count)} «Эти дни». Выбор календаря сохранён.",
                        error = null,
                    )
                    is SyncResult.Failed -> current.copy(busy = false, error = result.message)
                    is SyncResult.Written -> current.copy(busy = false)
                }
            }
        }
    }
}

@Composable
fun SyncScreen(vm: SyncViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    EmbeddedScreen {
        QuietTitle(
            text = "Календарь телефона",
            subtitle = "События записываются в календарь на этом устройстве. Если здесь есть аккаунт Google, обычно доступен и его календарь — отдельный ключ Google Calendar API не нужен.",
        )
        if (state.loading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else if (!state.permission) {
            NoteCard("Без доступа к календарю записать события нельзя. Остальное приложение работает и так.")
            PrimaryButton(text = "Разрешить доступ к календарю") {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                )
            }
            TextButton(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
            ) { Text("Открыть настройки приложения") }
        } else if (state.calendars.isEmpty()) {
            NoteCard(
                "На устройстве нет календаря, куда можно записать события. Добавьте аккаунт Google в настройках телефона — тогда его календарь появится в этом списке. Остальное приложение работает и без этого.",
            )
        } else {
            Text("Куда записать", style = MaterialTheme.typography.titleMedium)
            state.calendars.forEach { calendar ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.select(calendar) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = calendar.id == state.selectedId,
                        onClick = { vm.select(calendar) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(calendar.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            accountLabel(calendar.accountType, calendar.accountName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PrimaryButton(
                text = "Синхронизировать",
                loading = state.busy,
                enabled = state.selectedId != null,
                onClick = vm::sync,
            )
            TextButton(onClick = vm::remove, enabled = !state.busy) {
                Text("Удалить события «Эти дни»")
            }
        }
        state.message?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Свои события помечаем фразой [Эти дни], пакетом приложения и служебным свойством календаря — удаляются только они.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun accountLabel(type: String, name: String): String {
    val kind = when (type) {
        "com.google" -> "Google"
        "com.android.exchange" -> "Exchange"
        "LOCAL" -> "На устройстве"
        else -> if (type.isBlank()) "Календарь" else type.substringAfterLast('.')
    }
    return if (name.isBlank()) kind else "$name · $kind"
}
