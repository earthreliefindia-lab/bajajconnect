package com.openride.companion.search.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID

enum class SearchCategory(val displayName: String) {
    ALL("All"),
    NOTES("Ride Notes"),
    TRIPS("Trips"),
    PLACES("Places"),
    SERVICE("Service"),
    DIAGNOSTICS("Diagnostics"),
    SETTINGS("Settings")
}

data class SearchResultItem(
    val id: String,
    val category: SearchCategory,
    val title: String,
    val subtitle: String? = null,
    val snippet: String? = null,
    val matchedRanges: List<IntRange> = emptyList(),
    val icon: ImageVector = Icons.Default.Info,
    val score: Int = 0,
    val rawPayload: Any? = null
)

data class RecentSearchRecord(
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class RideNote(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAtFormatted: String,
    val category: String = "General",
    val isPinned: Boolean = false
)
