package com.openride.companion.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openride.companion.data.preferences.SessionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val sessionPreferences: SessionPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onPageChanged(page: Int) {
        val clamped = page.coerceIn(0, OnboardingCatalog.slides.size - 1)
        _uiState.update { it.copy(currentPage = clamped) }
    }

    fun onNextClicked(onNavigateToAuth: () -> Unit) {
        val currentState = _uiState.value
        if (currentState.isFinalPage) {
            completeOnboarding(onNavigateToAuth)
        } else {
            val nextPage = (currentState.currentPage + 1).coerceAtMost(currentState.totalPages - 1)
            _uiState.update { it.copy(currentPage = nextPage) }
        }
    }

    fun onSkipClicked(onNavigateToAuth: () -> Unit) {
        completeOnboarding(onNavigateToAuth)
    }

    private fun completeOnboarding(onNavigateToAuth: () -> Unit) {
        viewModelScope.launch {
            sessionPreferences.setOnboardingCompleted(true)
            _uiState.update { it.copy(isCompleted = true) }
            onNavigateToAuth()
        }
    }
}
