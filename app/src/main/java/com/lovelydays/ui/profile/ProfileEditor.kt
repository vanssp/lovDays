package com.lovelydays.ui.profile

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovelydays.LovelyApplication
import com.lovelydays.domain.BodyMetrics
import com.lovelydays.domain.Profile
import com.lovelydays.domain.ProfileField
import com.lovelydays.domain.ProfileValidator
import com.lovelydays.domain.Russian
import com.lovelydays.domain.formatMeasure
import com.lovelydays.ui.components.Disclaimer
import com.lovelydays.ui.components.EmbeddedScreen
import com.lovelydays.ui.components.InsetScreen
import com.lovelydays.ui.components.NoteCard
import com.lovelydays.ui.components.PrimaryButton
import com.lovelydays.ui.components.QuietTitle
import com.lovelydays.ui.components.DateField
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileFormState(
    val birth: LocalDate? = null,
    val weight: String = "",
    val height: String = "",
    val lastPeriod: LocalDate? = null,
    val cycleLength: String = "",
    val periodLength: String = "",
    val errors: Map<ProfileField, String> = emptyMap(),
    val saving: Boolean = false,
    val banner: String? = null,
    val bannerIsError: Boolean = false,
)

class ProfileEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val container = LovelyApplication.from(app).container
    private val _state = MutableStateFlow(ProfileFormState())
    val state = _state.asStateFlow()
    private var dirty = false

    init {
        viewModelScope.launch {
            val id = container.session.currentUserId() ?: return@launch
            val existing = container.users.getProfile(id) ?: return@launch
            if (!dirty) _state.value = existing.toForm()
        }
    }

    fun edit(block: (ProfileFormState) -> ProfileFormState) {
        dirty = true
        _state.update { block(it).copy(banner = null, errors = emptyMap()) }
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.saving) return
        val issues = ProfileValidator.validate(
            birth = snapshot.birth,
            weightRaw = snapshot.weight,
            heightRaw = snapshot.height,
            lastPeriod = snapshot.lastPeriod,
            cycleRaw = snapshot.cycleLength,
            periodRaw = snapshot.periodLength,
            today = LocalDate.now(),
        )
        if (issues.isNotEmpty()) {
            _state.update {
                it.copy(
                    errors = issues.mapValues { (_, code) -> ProfileValidator.message(code) },
                    banner = "Проверьте поля выше.",
                    bannerIsError = true,
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, banner = null) }
            try {
                val id = container.session.currentUserId() ?: error("no session")
                container.users.saveProfile(snapshot.toProfile(id))
                container.scheduler.reschedule(id)
                _state.update {
                    it.copy(
                        saving = false,
                        errors = emptyMap(),
                        banner = "Сохранили. Прогноз обновился. Если календарь уже подключён, откройте «Синхр.» и синхронизируйте ещё раз.",
                        bannerIsError = false,
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        saving = false,
                        banner = "Не получилось сохранить. Попробуйте ещё раз.",
                        bannerIsError = true,
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { container.logout() }
    }
}

private fun Profile.toForm() = ProfileFormState(
    birth = birth,
    weight = formatMeasure(weightKg),
    height = formatMeasure(heightCm),
    lastPeriod = lastPeriod,
    cycleLength = cycleLength?.toString().orEmpty(),
    periodLength = periodLength?.toString().orEmpty(),
)

private fun ProfileFormState.toProfile(userId: Long): Profile {
    val weightKg = ProfileValidator.parseDecimal(weight) ?: error("weight")
    val heightCm = ProfileValidator.parseDecimal(height) ?: error("height")
    return Profile(
        userId = userId,
        birth = birth ?: error("birth"),
        weightKg = weightKg,
        heightCm = heightCm,
        lastPeriod = lastPeriod ?: error("period"),
        cycleLength = cycleLength.trim().takeIf { it.isNotEmpty() }?.toInt(),
        periodLength = periodLength.trim().takeIf { it.isNotEmpty() }?.toInt(),
    )
}

@Composable
fun ProfileEditorScreen(
    onboarding: Boolean,
    vm: ProfileEditorViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        ProfileForm(onboarding = onboarding, state = state, vm = vm)
    }
    if (onboarding) InsetScreen(body) else EmbeddedScreen(body)
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ProfileForm(
    onboarding: Boolean,
    state: ProfileFormState,
    vm: ProfileEditorViewModel,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    QuietTitle(
        text = if (onboarding) "Немного о вас" else "Профиль",
        subtitle = if (onboarding) {
            "Без этих данных прогноз не посчитать. Анкета хранится только на устройстве."
        } else {
            "Эти данные остаются на телефоне и нужны для оценки цикла. Их можно поменять в любой момент."
        },
    )
    DateField(
        label = "Дата рождения",
        value = state.birth,
        placeholder = "Выберите дату",
        error = state.errors[ProfileField.BIRTH],
        initial = today.minusYears(28),
        yearRange = (today.year - 60)..(today.year - 10),
        onChange = { date -> vm.edit { it.copy(birth = date) } },
    )
    OutlinedTextField(
        value = state.weight,
        onValueChange = { value -> vm.edit { it.copy(weight = value.take(6)) } },
        label = { Text("Вес, кг") },
        placeholder = { Text("например, 62.5") },
        supportingText = state.errors[ProfileField.WEIGHT]?.let { message -> { Text(message) } },
        isError = state.errors[ProfileField.WEIGHT] != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.height,
        onValueChange = { value -> vm.edit { it.copy(height = value.take(5)) } },
        label = { Text("Рост, см") },
        placeholder = { Text("например, 168") },
        supportingText = state.errors[ProfileField.HEIGHT]?.let { message -> { Text(message) } },
        isError = state.errors[ProfileField.HEIGHT] != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    DateField(
        label = "Первый день последней менструации",
        value = state.lastPeriod,
        placeholder = "Выберите дату",
        error = state.errors[ProfileField.LAST_PERIOD],
        initial = today.minusDays(14),
        yearRange = (today.year - 2)..today.year,
        onChange = { date -> vm.edit { it.copy(lastPeriod = date) } },
    )
    OutlinedTextField(
        value = state.cycleLength,
        onValueChange = { value -> vm.edit { it.copy(cycleLength = value.filter(Char::isDigit).take(2)) } },
        label = { Text("Длина цикла, дни") },
        placeholder = { Text("пусто — оценка по возрасту и ИМТ") },
        supportingText = {
            Text(state.errors[ProfileField.CYCLE] ?: "Обычно около 28. Можно оставить пустым.")
        },
        isError = state.errors[ProfileField.CYCLE] != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.periodLength,
        onValueChange = { value -> vm.edit { it.copy(periodLength = value.filter(Char::isDigit).take(2)) } },
        label = { Text("Длина менструации, дни") },
        placeholder = { Text("пусто — 5 дней") },
        supportingText = {
            Text(state.errors[ProfileField.PERIOD] ?: "Если не указать, возьмём 5 дней.")
        },
        isError = state.errors[ProfileField.PERIOD] != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    assumptionText(state)?.let { note -> NoteCard(note) }
    state.banner?.let { banner ->
        Text(
            banner,
            color = if (state.bannerIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    PrimaryButton(
        text = if (onboarding) "Показать календарь" else "Сохранить",
        loading = state.saving,
        onClick = vm::save,
    )
    TextButton(onClick = { confirmLogout = true }, enabled = !state.saving) {
        Text("Выйти")
    }
    Text(
        "Версия ${com.lovelydays.BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Disclaimer()
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти?") },
            text = { Text("Календарь и анкета останутся на телефоне. Чтобы вернуться, войдите снова.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        vm.logout()
                    },
                ) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Остаться") }
            },
        )
    }
}

private fun assumptionText(state: ProfileFormState): String? {
    val birth = state.birth ?: return null
    val weight = ProfileValidator.parseDecimal(state.weight) ?: return null
    val height = ProfileValidator.parseDecimal(state.height) ?: return null
    if (weight !in 35.0..200.0 || height !in 130.0..220.0) return null
    val today = LocalDate.now()
    if (!birth.isBefore(today) && birth != today) return null
    val age = BodyMetrics.ageYears(birth, today)
    if (age !in 10..60) return null
    val bmi = BodyMetrics.bmi(weight, height)
    val bmiLabel = Russian.number1(bmi)
    return if (state.cycleLength.isBlank()) {
        val assumed = BodyMetrics.assumedCycleLength(age, bmi)
        "ИМТ $bmiLabel. Если оставить длину цикла пустой, возьмём $assumed ${Russian.days(assumed)} — оценка по возрасту и ИМТ, не измерение."
    } else {
        "ИМТ $bmiLabel. В расчёте будет та длина цикла, которую вы указали."
    }
}
