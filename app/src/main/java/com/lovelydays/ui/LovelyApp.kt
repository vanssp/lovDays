@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.lovelydays.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovelydays.LovelyApplication
import com.lovelydays.ui.auth.AuthScreen
import com.lovelydays.ui.components.LoadingPane
import com.lovelydays.ui.profile.ProfileEditorScreen
import com.lovelydays.ui.shell.MainShell
import com.lovelydays.ui.theme.LovelyTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface Gate {
    data object Loading : Gate
    data object LoggedOut : Gate
    data object NeedsProfile : Gate
    data class Ready(val userId: Long) : Gate

    fun storeKey(): String = when (this) {
        Loading -> "loading"
        LoggedOut -> "out"
        NeedsProfile -> "needs-profile"
        is Ready -> "ready-$userId"
    }
}

class GateViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val container = LovelyApplication.from(app).container

    val gate: StateFlow<Gate> = container.session.userId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(Gate.LoggedOut)
            } else {
                container.users.observeProfile(id).map { profile ->
                    if (profile == null) Gate.NeedsProfile else Gate.Ready(id)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Gate.Loading)
}

@Composable
fun LovelyApp() {
    val gateVm: GateViewModel = viewModel()
    val gate by gateVm.gate.collectAsStateWithLifecycle()
    LovelyTheme {
        FreshStore(gate.storeKey()) {
            when (gate) {
                Gate.Loading -> LoadingPane()
                Gate.LoggedOut -> AuthScreen()
                Gate.NeedsProfile -> ProfileEditorScreen(onboarding = true)
                is Gate.Ready -> MainShell()
            }
        }
    }
}

@Composable
private fun FreshStore(key: Any, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
    val store = remember(key) { ViewModelStore() }
    DisposableEffect(store) {
        onDispose { store.clear() }
    }
    // A bare ViewModelStoreOwner drops the activity factory, so AndroidViewModel
    // (auth, profile, and the tabs) cannot be created and the process dies on launch.
    val owner = remember(store, parent) {
        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
            override val viewModelStore: ViewModelStore = store
            override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
                (parent as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory
                    ?: ViewModelProvider.NewInstanceFactory()
            override val defaultViewModelCreationExtras: CreationExtras =
                (parent as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
                    ?: CreationExtras.Empty
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
