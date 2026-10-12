package com.lovelydays.ui.auth

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovelydays.LovelyApplication
import com.lovelydays.R
import com.lovelydays.data.auth.AuthOutcome
import com.lovelydays.data.auth.AuthRepository
import com.lovelydays.ui.components.BrandMark
import com.lovelydays.ui.components.Disclaimer
import com.lovelydays.ui.components.InsetScreen
import com.lovelydays.ui.components.PrimaryButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val login: String = "",
    val password: String = "",
    val confirm: String = "",
    val registering: Boolean = false,
    val showPassword: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = LovelyApplication.from(app).container.auth
    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()

    fun onLogin(value: String) = _state.update { it.copy(login = value.take(32), error = null) }

    fun onPassword(value: String) = _state.update { it.copy(password = value.take(128), error = null) }

    fun onConfirm(value: String) = _state.update { it.copy(confirm = value.take(128), error = null) }

    fun togglePassword() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun toggleMode() = _state.update {
        it.copy(registering = !it.registering, error = null, password = "", confirm = "", showPassword = false)
    }

    fun submit() {
        val snapshot = _state.value
        if (snapshot.submitting) return
        val failure = AuthRepository.validate(
            loginRaw = snapshot.login,
            password = snapshot.password,
            confirm = if (snapshot.registering) snapshot.confirm else null,
        )
        if (failure != null) {
            _state.update { it.copy(error = AuthRepository.message(failure)) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = if (snapshot.registering) {
                auth.register(snapshot.login, snapshot.password, snapshot.confirm)
            } else {
                auth.signIn(snapshot.login, snapshot.password)
            }
            when (result) {
                is AuthOutcome.Success -> _state.value = AuthUiState()
                is AuthOutcome.Failed -> _state.update {
                    it.copy(submitting = false, error = AuthRepository.message(result.reason))
                }
            }
        }
    }
}

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    InsetScreen {
        BrandMark()
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(
            "Календарь цикла. Спокойно, на вашем телефоне, без облака.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.login,
            onValueChange = vm::onLogin,
            label = { Text("Логин") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Text,
            ),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::onPassword,
            label = { Text("Пароль") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = vm::togglePassword) {
                    Icon(
                        imageVector = if (state.showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (state.showPassword) "Скрыть пароль" else "Показать пароль",
                    )
                }
            },
        )
        if (state.registering) {
            OutlinedTextField(
                value = state.confirm,
                onValueChange = vm::onConfirm,
                label = { Text("Ещё раз пароль") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        Text(
            "Логин от 3 символов: буквы, цифры, точка или _. Пароль — от 6 символов, в памяти телефона только его хеш. Вход сохранится, пока вы сами не выйдете.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryButton(
            text = if (state.registering) "Создать аккаунт" else "Войти",
            loading = state.submitting,
            onClick = vm::submit,
        )
        TextButton(onClick = vm::toggleMode, enabled = !state.submitting) {
            Text(
                if (state.registering) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться",
            )
        }
        Disclaimer()
    }
}
