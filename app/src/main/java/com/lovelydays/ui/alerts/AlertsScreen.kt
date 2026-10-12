package com.lovelydays.ui.alerts

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovelydays.LovelyApplication
import com.lovelydays.notify.Notifier
import com.lovelydays.ui.components.EmbeddedScreen
import com.lovelydays.ui.components.NoteCard
import com.lovelydays.ui.components.QuietTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertsUi(
    val loading: Boolean = true,
    val period: Boolean = true,
    val ovulation: Boolean = true,
    val mood: Boolean = true,
    val skin: Boolean = true,
    val notificationsOn: Boolean = true,
)

class AlertsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = LovelyApplication.from(app).container
    private val _state = MutableStateFlow(AlertsUi())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val id = container.session.currentUserId() ?: return@launch
            val prefs = container.users.getPrefs(id)
            _state.update {
                it.copy(
                    loading = false,
                    period = prefs.period,
                    ovulation = prefs.ovulation,
                    mood = prefs.mood,
                    skin = prefs.skin,
                    notificationsOn = Notifier.canPost(getApplication()),
                )
            }
        }
    }

    fun onPermissionResult() {
        refresh()
        viewModelScope.launch {
            val id = container.session.currentUserId() ?: return@launch
            container.scheduler.reschedule(id)
        }
    }

    fun setEnabled(type: String, enabled: Boolean) {
        viewModelScope.launch {
            val id = container.session.currentUserId() ?: return@launch
            val current = container.users.getPrefs(id)
            val next = when (type) {
                "period" -> current.copy(period = enabled)
                "ovulation" -> current.copy(ovulation = enabled)
                "mood" -> current.copy(mood = enabled)
                "skin" -> current.copy(skin = enabled)
                else -> current
            }
            container.users.savePrefs(next)
            container.scheduler.reschedule(id)
            _state.update {
                it.copy(
                    period = next.period,
                    ovulation = next.ovulation,
                    mood = next.mood,
                    skin = next.skin,
                )
            }
        }
    }
}

@Composable
fun AlertsScreen(vm: AlertsViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        vm.onPermissionResult()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    EmbeddedScreen {
        QuietTitle(
            text = "Напоминания",
            subtitle = "За день и в день события, около 9:00 по местному времени. Система иногда немного сдвигает доставку.",
        )
        if (state.loading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            if (!state.notificationsOn) {
                NoteCard(
                    if (Build.VERSION.SDK_INT >= 33) {
                        "Чтобы присылать напоминания, Android просит разрешение на уведомления."
                    } else {
                        "Уведомления для «Эти дни» выключены в системе."
                    },
                )
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openSettings(context)
                        }
                    },
                ) {
                    Text(if (Build.VERSION.SDK_INT >= 33) "Разрешить уведомления" else "Открыть настройки")
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    TextButton(onClick = { openSettings(context) }) {
                        Text("Открыть настройки приложения")
                    }
                }
            }
            ToggleRow("Менструация", "Возможное начало", state.period) { vm.setEnabled("period", it) }
            ToggleRow("Овуляция", "Ориентировочный день", state.ovulation) { vm.setEnabled("ovulation", it) }
            ToggleRow("Настроение", "Окно перед менструацией", state.mood) { vm.setEnabled("mood", it) }
            ToggleRow("Кожа", "Дни, когда выше шанс высыпаний", state.skin) { vm.setEnabled("skin", it) }
            Text(
                "Выключенный тип не ставится в очередь. После смены анкеты напоминания пересчитываются сами.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun openSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
