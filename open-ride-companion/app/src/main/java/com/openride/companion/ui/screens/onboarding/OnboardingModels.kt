package com.openride.companion.ui.screens.onboarding

import androidx.annotation.DrawableRes
import com.openride.companion.R

data class OnboardingSlide(
    val index: Int,
    val imageAsset: String,
    @DrawableRes val drawableRes: Int,
    val headline: String,
    val description: String
)

object OnboardingCatalog {
    val slides = listOf(
        OnboardingSlide(
            index = 0,
            imageAsset = "navigatetrack.png",
            drawableRes = R.drawable.img_onboarding_navigate_track,
            headline = "Turn-by-Turn Navigation",
            description = "Get turn by turn navigation on your bike cluster"
        ),
        OnboardingSlide(
            index = 1,
            imageAsset = "notificationreminder.png",
            drawableRes = R.drawable.img_onboarding_notification_reminder,
            headline = "Call & SMS Alerts",
            description = "Never miss important calls or messages while riding"
        ),
        OnboardingSlide(
            index = 2,
            imageAsset = "servicebooking.png",
            drawableRes = R.drawable.img_onboarding_service_booking,
            headline = "Service & Maintenance",
            description = "Book bike service and track maintenance effortlessly"
        )
    )
}

data class OnboardingUiState(
    val currentPage: Int = 0,
    val isCompleted: Boolean = false
) {
    val totalPages: Int = OnboardingCatalog.slides.size
    val isFinalPage: Boolean = currentPage == totalPages - 1
    val actionButtonText: String = if (isFinalPage) "GET STARTED" else "NEXT"
}
