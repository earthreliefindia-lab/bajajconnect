package com.openride.companion.search.engine

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import com.openride.companion.search.model.RecentSearchRecord
import com.openride.companion.search.model.RideNote
import com.openride.companion.search.model.SearchCategory
import com.openride.companion.search.model.SearchResultItem
import com.openride.companion.search.repository.RideNotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object GlobalSearchManager {

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _selectedCategory = MutableStateFlow(SearchCategory.ALL)
    val selectedCategory: StateFlow<SearchCategory> = _selectedCategory.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<RecentSearchRecord>>(
        listOf(
            RecentSearchRecord("Chain lube"),
            RecentSearchRecord("Lonavala trip"),
            RecentSearchRecord("Tire pressure")
        )
    )
    val recentSearches: StateFlow<List<RecentSearchRecord>> = _recentSearches.asStateFlow()

    fun setCategory(category: SearchCategory) {
        _selectedCategory.value = category
    }

    fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val filtered = _recentSearches.value.filterNot { it.query.equals(trimmed, ignoreCase = true) }
        _recentSearches.value = (listOf(RecentSearchRecord(trimmed)) + filtered).take(10)
    }

    fun removeRecentSearch(query: String) {
        _recentSearches.value = _recentSearches.value.filterNot { it.query.equals(query, ignoreCase = true) }
    }

    fun clearAllRecentSearches() {
        _recentSearches.value = emptyList()
    }

    suspend fun executeSearch(query: String, filterCategory: SearchCategory = _selectedCategory.value) = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isBlank()) {
            _searchResults.value = emptyList()
            return@withContext
        }

        val tokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        val results = mutableListOf<SearchResultItem>()

        // 1. Search Notes (Ride Logs)
        if (filterCategory == SearchCategory.ALL || filterCategory == SearchCategory.NOTES) {
            RideNotesRepository.notes.value.forEach { note ->
                matchNote(note, tokens)?.let { results.add(it) }
            }
        }

        // 2. Search Static & Active Trips
        if (filterCategory == SearchCategory.ALL || filterCategory == SearchCategory.TRIPS) {
            getStaticTrips().forEach { (title, subtitle, summary) ->
                if (tokens.all { title.lowercase().contains(it) || summary.lowercase().contains(it) }) {
                    results.add(
                        SearchResultItem(
                            id = "trip_${title.hashCode()}",
                            category = SearchCategory.TRIPS,
                            title = title,
                            subtitle = subtitle,
                            snippet = createSnippet(summary, tokens),
                            icon = Icons.Default.LocationOn,
                            score = 50
                        )
                    )
                }
            }
        }

        // 3. Search Service Centers
        if (filterCategory == SearchCategory.ALL || filterCategory == SearchCategory.SERVICE) {
            getStaticServiceCenters().forEach { (name, address, rating) ->
                if (tokens.all { name.lowercase().contains(it) || address.lowercase().contains(it) }) {
                    results.add(
                        SearchResultItem(
                            id = "service_${name.hashCode()}",
                            category = SearchCategory.SERVICE,
                            title = name,
                            subtitle = "$address · $rating",
                            icon = Icons.Default.Build,
                            score = 45
                        )
                    )
                }
            }
        }

        // 4. Search Vehicle Diagnostics & Specs
        if (filterCategory == SearchCategory.ALL || filterCategory == SearchCategory.DIAGNOSTICS) {
            getStaticDiagnosticsAndSpecs().forEach { (spec, detail) ->
                if (tokens.all { spec.lowercase().contains(it) || detail.lowercase().contains(it) }) {
                    results.add(
                        SearchResultItem(
                            id = "diag_${spec.hashCode()}",
                            category = SearchCategory.DIAGNOSTICS,
                            title = spec,
                            subtitle = "Vehicle Specification",
                            snippet = createSnippet(detail, tokens),
                            icon = Icons.Default.Info,
                            score = 40
                        )
                    )
                }
            }
        }

        // 5. Search Settings & Controls
        if (filterCategory == SearchCategory.ALL || filterCategory == SearchCategory.SETTINGS) {
            getStaticSettings().forEach { (setting, detail) ->
                if (tokens.all { setting.lowercase().contains(it) || detail.lowercase().contains(it) }) {
                    results.add(
                        SearchResultItem(
                            id = "setting_${setting.hashCode()}",
                            category = SearchCategory.SETTINGS,
                            title = setting,
                            subtitle = "System Control",
                            snippet = createSnippet(detail, tokens),
                            icon = Icons.Default.Settings,
                            score = 35
                        )
                    )
                }
            }
        }

        // 6. Search Places & POIs
        if (filterCategory == SearchCategory.ALL || filterCategory == SearchCategory.PLACES) {
            getStaticPois().forEach { (name, type) ->
                if (tokens.all { name.lowercase().contains(it) || type.lowercase().contains(it) }) {
                    results.add(
                        SearchResultItem(
                            id = "poi_${name.hashCode()}",
                            category = SearchCategory.PLACES,
                            title = name,
                            subtitle = "Quick POI Category · $type",
                            icon = Icons.Default.Place,
                            score = 30
                        )
                    )
                }
            }
        }

        _searchResults.value = results.sortedByDescending { it.score }
    }

    private fun matchNote(note: RideNote, tokens: List<String>): SearchResultItem? {
        val titleLower = note.title.lowercase()
        val contentLower = note.content.lowercase()
        val tagsLower = note.tags.map { it.lowercase() }

        val allTokensMatch = tokens.all { token ->
            titleLower.contains(token) ||
            contentLower.contains(token) ||
            tagsLower.any { it.contains(token) }
        }

        if (!allTokensMatch) return null

        val isTitleMatch = tokens.all { titleLower.contains(it) }
        val score = if (isTitleMatch) 100 else 60

        return SearchResultItem(
            id = note.id,
            category = SearchCategory.NOTES,
            title = note.title,
            subtitle = "${note.category} · ${note.createdAtFormatted}",
            snippet = createSnippet(note.content, tokens),
            icon = Icons.Default.Edit,
            score = score,
            rawPayload = note
        )
    }

    private fun createSnippet(text: String, tokens: List<String>): String {
        val lower = text.lowercase()
        val firstIndex = tokens.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (firstIndex - 25).coerceAtLeast(0)
        val end = (firstIndex + 45).coerceAtMost(text.length)
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        return prefix + text.substring(start, end).trim() + suffix
    }

    private fun getStaticTrips() = listOf(
        Triple("Pune to Lonavala Ghat Ride", "94.2 km · 2h 15m", "Morning expressway ride via Dehu Road and Kamshet with scenic Tiger Point breakfast."),
        Triple("Lavasa Hill Climb Tour", "62.8 km · 1h 45m", "Twisty mountain switchbacks, lean angle peak 44 degrees left and 42 degrees right."),
        Triple("Sinhagad Fort Circuit", "38.5 km · 1h 10m", "Steep gravel climb with off-road ABS test. Odometer reading 3,950 km.")
    )

    private fun getStaticServiceCenters() = listOf(
        Triple("Sai Service Bajaj (Shivajinagar)", "FC Road, Shivajinagar, Pune", "4.8 ★ (1,240 reviews)"),
        Triple("Kothari Bajaj (Kothrud)", "Paud Road, Kothrud, Pune", "4.6 ★ (890 reviews)"),
        Triple("Theur Bajaj Care Center", "Hadapsar Bypass, Pune", "4.5 ★ (450 reviews)")
    )

    private fun getStaticDiagnosticsAndSpecs() = listOf(
        "Engine Oil Grade" to "20W50 Semi-Synthetic JASO MA2. Sump capacity: 1.5 Litres.",
        "Tire Pressure Specs" to "Front: 28 PSI (Cold) | Rear: 32 PSI (Solo), 36 PSI (Pillion).",
        "Battery Health" to "12.8V Float Voltage (94% Charged). Maintenance-free VRLA battery.",
        "Chain Slack Specification" to "25 mm to 30 mm free play at midpoint of lower chain run.",
        "ABS Ride Modes" to "Road (Balanced), Rain (Max Traction), Off-Road (Rear Off), Sport (Dynamic Throttle)."
    )

    private fun getStaticSettings() = listOf(
        "Mappls Free Mode" to "Zero API key required mode with permanent motorcycle routing and high-contrast vector tiles.",
        "Dynamic Wallpaper Theming" to "Monet palette extracted from device wallpaper on Android 12 through Android 16.",
        "Bluetooth Cluster Relay" to "Stream turn-by-turn maneuvers (Opcode 0x01) to LCD 2763 and TFT 0265 consoles.",
        "Roadside Assistance (RSA)" to "Toll-free 24x7 emergency helpline: 1800 233 2453."
    )

    private fun getStaticPois() = listOf(
        "Fuel Stations (Petrol & Diesel)" to "TRNPMP",
        "EV Charging Stations" to "TRNECS",
        "Motorcycle Repair & Mechanics" to "AUTREP",
        "Hospitals & First Aid" to "HLTHSP",
        "ATM & Cash Machines" to "FINATM",
        "Parking Lots" to "TRNPRK"
    )
}
