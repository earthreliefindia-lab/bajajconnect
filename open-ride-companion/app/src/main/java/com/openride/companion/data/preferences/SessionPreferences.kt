package com.openride.companion.data.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interface for persisting onboarding, session, and local preference flags.
 */
interface SessionPreferences {
    val isOnboardingCompletedFlow: StateFlow<Boolean>
    fun isOnboardingCompleted(): Boolean
    fun setOnboardingCompleted(completed: Boolean)
}

/**
 * In-memory and test-ready implementation of SessionPreferences.
 */
class InMemorySessionPreferences : SessionPreferences {
    private val _onboardingCompleted = MutableStateFlow(false)
    override val isOnboardingCompletedFlow: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    override fun isOnboardingCompleted(): Boolean = _onboardingCompleted.value

    override fun setOnboardingCompleted(completed: Boolean) {
        _onboardingCompleted.value = completed
    }
}
