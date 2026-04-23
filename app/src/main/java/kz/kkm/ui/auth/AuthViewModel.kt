package kz.kkm.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kz.kkm.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val pinInput: String = "",
    val error: String = "",
    val isAuthenticated: Boolean = false,
    val isPinSetup: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val pinSet = settingsRepo.isPinSet()
            _state.update { it.copy(isPinSetup = !pinSet) }
            // If no PIN set, auto-authenticate for first run
            if (!pinSet) _state.update { it.copy(isAuthenticated = true) }
        }
    }

    fun onDigit(digit: String) {
        val current = _state.value.pinInput
        if (current.length >= 4) return
        val updated = current + digit
        _state.update { it.copy(pinInput = updated, error = "") }
        if (updated.length == 4) verifyPin(updated)
    }

    fun onDelete() {
        _state.update { it.copy(pinInput = it.pinInput.dropLast(1), error = "") }
    }

    fun tryBiometric(launchPrompt: () -> Unit) {
        viewModelScope.launch {
            if (settingsRepo.isPinSet()) launchPrompt()
        }
    }

    private fun verifyPin(pin: String) {
        viewModelScope.launch {
            val ok = settingsRepo.verifyPin(pin)
            if (ok) {
                _state.update { it.copy(isAuthenticated = true) }
            } else {
                _state.update { it.copy(pinInput = "", error = "Неверный PIN-код") }
            }
        }
    }
}
