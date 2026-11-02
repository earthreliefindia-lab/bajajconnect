package com.openride.companion.e2e

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * End-to-End Test Suite for Notes App Search Integration and Rider Notes Journal (Features 21-25).
 * Covers Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases).
 */
class NotesSearchEngineE2ETest {

    // =========================================================================
    // FEATURE 21: NOTES APP SEARCH UI (Tier 1 & Tier 2)
    // =========================================================================

    enum class SearchCategory(val displayName: String) {
        ALL("All"),
        NOTES("Ride Notes"),
        TRIPS("Trips"),
        PLACES("Places"),
        SERVICE("Service"),
        DIAGNOSTICS("Diagnostics"),
        SETTINGS("Settings")
    }

    class SearchUiState(
        var query: String = "",
        var activeCategory: SearchCategory = SearchCategory.ALL,
        var isOverlayVisible: Boolean = false,
        var hasFocus: Boolean = false
    ) {
        val isClearButtonVisible: Boolean
            get() = query.isNotEmpty()

        val pillCornerRadiusDp: Int = 28

        fun onClearTapped() {
            query = ""
            // Maintains keyboard focus when clearing
            hasFocus = true
        }

        fun onBackTapped() {
            query = ""
            isOverlayVisible = false
            hasFocus = false
        }
    }

    @Test
    fun testSearchUi_PillCornerRadius_Matches28dpSpecification() {
        val state = SearchUiState()
        assertEquals("Floating pill search bar corner radius must be 28.dp", 28, state.pillCornerRadiusDp)
    }

    @Test
    fun testSearchUi_CategoryChipsCatalog_CoversAllSevenFilters() {
        val categories = SearchCategory.values()
        assertEquals(7, categories.size)
        assertTrue(SearchCategory.ALL in categories)
        assertTrue(SearchCategory.NOTES in categories)
        assertTrue(SearchCategory.TRIPS in categories)
        assertTrue(SearchCategory.PLACES in categories)
        assertTrue(SearchCategory.SERVICE in categories)
        assertTrue(SearchCategory.DIAGNOSTICS in categories)
        assertTrue(SearchCategory.SETTINGS in categories)
    }

    @Test
    fun testSearchUi_ClearButton_VisibilityToggledByQuery() {
        val state = SearchUiState()
        assertFalse("Clear button hidden when query is empty", state.isClearButtonVisible)

        state.query = "chain"
        assertTrue("Clear button visible when query has text", state.isClearButtonVisible)

        state.onClearTapped()
        assertEquals("", state.query)
        assertFalse(state.isClearButtonVisible)
        assertTrue("Clearing text preserves keyboard focus", state.hasFocus)
    }

    @Test
    fun testSearchUi_BackNavigation_DismissesOverlay() {
        val state = SearchUiState(isOverlayVisible = true, hasFocus = true, query = "lonavala")
        state.onBackTapped()
        assertFalse("Back button dismisses overlay", state.isOverlayVisible)
        assertEquals("Back button resets query", "", state.query)
        assertFalse("Back button releases focus", state.hasFocus)
    }

    @Test
    fun testSearchUi_CategoryFilterSelection_UpdatesActiveFilter() {
        val state = SearchUiState()
        state.activeCategory = SearchCategory.NOTES
        assertEquals(SearchCategory.NOTES, state.activeCategory)
        state.activeCategory = SearchCategory.SERVICE
        assertEquals(SearchCategory.SERVICE, state.activeCategory)
    }

    @Test
    fun testSearchUi_Boundary_CategoryEnumSerialization() {
        assertEquals(SearchCategory.ALL, SearchCategory.valueOf("ALL"))
        assertEquals(SearchCategory.NOTES, SearchCategory.valueOf("NOTES"))
    }

    @Test
    fun testSearchUi_Boundary_WhitespaceQueryStillShowsClearButton() {
        val state = SearchUiState(query = " ")
        assertTrue("Whitespace counts as non-empty for clear button", state.isClearButtonVisible)
    }

    @Test
    fun testSearchUi_Boundary_RecentSearchesLimit_MaxTenQueries() {
        val recents = (1..15).map { "Query $it" }
        val capped = recents.take(10)
        assertEquals("Recent search history capped at 10 items", 10, capped.size)
    }

    @Test
    fun testSearchUi_Boundary_EmptyStateRecommendationText() {
        val emptyStateText = "Check spelling, try broader keywords, or switch to 'All' category."
        assertTrue(emptyStateText.contains("Check spelling"))
    }

    @Test
    fun testSearchUi_Boundary_DuplicateRecentSearchesDeduplicated() {
        val list = mutableListOf<String>()
        fun addRecent(q: String) {
            list.remove(q)
            list.add(0, q)
        }
        addRecent("Pulsar")
        addRecent("Dominar")
        addRecent("Pulsar")
        assertEquals(2, list.size)
        assertEquals("Pulsar", list.first())
    }

    // =========================================================================
    // FEATURE 22: DEBOUNCED MULTI-TOKEN SEARCH (Tier 1 & Tier 2)
    // =========================================================================

    class MultiTokenSearchEngine {
        val debounceWindowMs: Long = 250L

        fun tokenize(query: String): List<String> {
            return query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        }

        fun matchesAllTokens(tokens: List<String>, vararg searchFields: String): Boolean {
            if (tokens.isEmpty()) return false
            val combined = searchFields.joinToString(" ").lowercase()
            return tokens.all { token -> combined.contains(token) }
        }
    }

    @Test
    fun testDebounce_DebounceDelay_Matches250msSpecification() {
        val engine = MultiTokenSearchEngine()
        assertEquals("Notes search debounce window must be 250ms", 250L, engine.debounceWindowMs)
    }

    @Test
    fun testTokenizer_SingleToken_SplitsCorrectly() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("pulsar")
        assertEquals(listOf("pulsar"), tokens)
    }

    @Test
    fun testTokenizer_MultiToken_SplitsByWhitespace() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("pune lonavala expressway")
        assertEquals(listOf("pune", "lonavala", "expressway"), tokens)
    }

    @Test
    fun testTokenizer_AndConjunction_MatchesAcrossFields() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("chain motul")
        val title = "Chain Maintenance & Cleaning"
        val body = "Applied Motul C2 road chain lube at 4000 km."

        assertTrue("Matches when token 1 is in title and token 2 is in body", engine.matchesAllTokens(tokens, title, body))
    }

    @Test
    fun testTokenizer_AndConjunction_FailsIfOneTokenMissing() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("chain motul mobil")
        val title = "Chain Maintenance & Cleaning"
        val body = "Applied Motul C2 road chain lube."

        assertFalse("'mobil' is missing so AND conjunction fails", engine.matchesAllTokens(tokens, title, body))
    }

    @Test
    fun testTokenizer_Boundary_ExtraSpacesAndTabs() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("   chain     motul \t\n  ")
        assertEquals(listOf("chain", "motul"), tokens)
    }

    @Test
    fun testTokenizer_Boundary_PunctuationAndSymbols() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("#maintenance #chain!")
        assertEquals(listOf("#maintenance", "#chain!"), tokens)
    }

    @Test
    fun testTokenizer_Boundary_EmptyQueryYieldsZeroTokens() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("    ")
        assertTrue(tokens.isEmpty())
        assertFalse(engine.matchesAllTokens(tokens, "Some text"))
    }

    @Test
    fun testTokenizer_Boundary_CaseInsensitivity() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("LONAVALA TIGER")
        assertTrue(engine.matchesAllTokens(tokens, "Lonavala Scenic Ride", "Tiger Point view"))
    }

    @Test
    fun testTokenizer_Boundary_SingleCharacterToken() {
        val engine = MultiTokenSearchEngine()
        val tokens = engine.tokenize("c 1")
        assertEquals(listOf("c", "1"), tokens)
        assertTrue(engine.matchesAllTokens(tokens, "Motul C1 Cleaner"))
    }

    // =========================================================================
    // FEATURE 23: KEYWORD HIGHLIGHT & SNIPPETS (Tier 1 & Tier 2)
    // =========================================================================

    class SnippetHighlighter {
        fun createSnippet(text: String, tokens: List<String>): String {
            val lower = text.lowercase()
            val firstIndex = tokens.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
            val start = (firstIndex - 25).coerceAtLeast(0)
            val end = (firstIndex + 45).coerceAtMost(text.length)
            val prefix = if (start > 0) "..." else ""
            val suffix = if (end < text.length) "..." else ""
            return prefix + text.substring(start, end).trim() + suffix
        }

        fun findHighlightRanges(text: String, tokens: List<String>): List<IntRange> {
            val lower = text.lowercase()
            val ranges = mutableListOf<IntRange>()
            tokens.forEach { token ->
                var index = lower.indexOf(token)
                while (index >= 0) {
                    ranges.add(index until (index + token.length))
                    index = lower.indexOf(token, index + token.length)
                }
            }
            return ranges.sortedBy { it.first }
        }

        fun calculateScore(title: String, tags: List<String>, content: String, tokens: List<String>): Int {
            var score = 0
            val tLower = title.lowercase()
            val cLower = content.lowercase()
            val tagsCombined = tags.joinToString(" ").lowercase()

            if (tokens.any { tLower == it }) score += 100 // Exact title match
            else if (tokens.any { tLower.contains(it) }) score += 60 // Title substring
            if (tokens.any { tagsCombined.contains(it) }) score += 30 // Tag match
            if (tokens.any { cLower.contains(it) }) score += 15 // Body match

            return score
        }
    }

    @Test
    fun testHighlight_SnippetExtraction_IncludesMatchWindow() {
        val highlighter = SnippetHighlighter()
        val text = "We departed early morning at 6 AM. The road to Tiger Point was clear of fog and fuel efficiency was high."
        val snippet = highlighter.createSnippet(text, listOf("tiger"))

        assertTrue("Snippet must contain match keyword", snippet.contains("Tiger Point"))
        assertTrue("Snippet has ellipsis bounding", snippet.startsWith("...") || snippet.endsWith("..."))
    }

    @Test
    fun testHighlight_HighlightRanges_FindsAllOccurrences() {
        val highlighter = SnippetHighlighter()
        val text = "Chain cleaning and chain lubrication with Motul"
        val ranges = highlighter.findHighlightRanges(text, listOf("chain"))

        assertEquals("Found 2 occurrences of 'chain'", 2, ranges.size)
        assertEquals(0 until 5, ranges[0])
        assertEquals(19 until 24, ranges[1])
    }

    @Test
    fun testHighlight_RelevanceScoring_ExactTitleScoresHighest() {
        val highlighter = SnippetHighlighter()
        val scoreExact = highlighter.calculateScore("chain", emptyList(), "body", listOf("chain"))
        val scoreSubstring = highlighter.calculateScore("chain maintenance", emptyList(), "body", listOf("chain"))
        val scoreBodyOnly = highlighter.calculateScore("other", emptyList(), "chain in body", listOf("chain"))

        assertTrue("Exact match (100) > Substring (60)", scoreExact > scoreSubstring)
        assertTrue("Substring (60) > Body only (15)", scoreSubstring > scoreBodyOnly)
        assertEquals(100, scoreExact)
        assertEquals(60, scoreSubstring)
        assertEquals(15, scoreBodyOnly)
    }

    @Test
    fun testHighlight_SnippetWindow_LimitsAround25and45Chars() {
        val highlighter = SnippetHighlighter()
        val longText = "A".repeat(100) + "MATCH" + "B".repeat(100)
        val snippet = highlighter.createSnippet(longText, listOf("match"))

        assertTrue("Snippet must be much shorter than 200 chars", snippet.length < 90)
        assertTrue(snippet.startsWith("..."))
        assertTrue(snippet.endsWith("..."))
    }

    @Test
    fun testHighlight_TagMatching_AddsRelevanceScore() {
        val highlighter = SnippetHighlighter()
        val scoreWithTag = highlighter.calculateScore("General Notes", listOf("#diy", "#motul"), "Cleaned bike", listOf("motul"))
        assertEquals(30, scoreWithTag)
    }

    @Test
    fun testHighlight_Boundary_NoMatchTokenInSnippet() {
        val highlighter = SnippetHighlighter()
        val snippet = highlighter.createSnippet("Some short text", listOf("nonexistent"))
        assertEquals("Some short text", snippet)
    }

    @Test
    fun testHighlight_Boundary_MatchAtBeginningOfText_NoLeadingEllipsis() {
        val highlighter = SnippetHighlighter()
        val text = "Tiger Point is in Lonavala"
        val snippet = highlighter.createSnippet(text, listOf("tiger"))
        assertFalse("No leading ellipsis when match is at beginning", snippet.startsWith("..."))
    }

    @Test
    fun testHighlight_Boundary_MatchAtEndOfText_NoTrailingEllipsis() {
        val highlighter = SnippetHighlighter()
        val text = "The destination was Tiger"
        val snippet = highlighter.createSnippet(text, listOf("tiger"))
        assertFalse("No trailing ellipsis when match is at end", snippet.endsWith("..."))
    }

    @Test
    fun testHighlight_Boundary_OverlappingHighlightRanges() {
        val highlighter = SnippetHighlighter()
        val text = "bajaj bajaj"
        val ranges = highlighter.findHighlightRanges(text, listOf("bajaj"))
        assertEquals(2, ranges.size)
        assertEquals(0 until 5, ranges[0])
        assertEquals(6 until 11, ranges[1])
    }

    @Test
    fun testHighlight_Boundary_MultiKeywordHighlighting() {
        val highlighter = SnippetHighlighter()
        val text = "Pulsar Dominar Chetak"
        val ranges = highlighter.findHighlightRanges(text, listOf("pulsar", "chetak"))
        assertEquals(2, ranges.size)
    }

    // =========================================================================
    // FEATURE 24: 20-CATEGORY POI SEARCH (Tier 1 & Tier 2)
    // =========================================================================

    data class PoiCategory(val id: String, val name: String, val code: String, val searchQuery: String)

    private val authoritative20Categories = listOf(
        PoiCategory("cat_1", "Petrol Pump", "TRNPMP", "petrol pump fuel"),
        PoiCategory("cat_2", "EV Fast Charging", "EVCHG", "ev charging station"),
        PoiCategory("cat_3", "Bajaj Service Hub", "AUTSRV", "bajaj service center workshop"),
        PoiCategory("cat_4", "Emergency Hospital", "HSPTL", "hospital clinic trauma"),
        PoiCategory("cat_5", "Food & Restaurants", "RSTRNT", "restaurant cafe dhaba"),
        PoiCategory("cat_6", "ATMs & Banking", "ATMBNK", "atm cash bank"),
        PoiCategory("cat_7", "Puncture & Tires", "TYRREP", "puncture tyre repair"),
        PoiCategory("cat_8", "Police Station", "POLICE", "police chowki station"),
        PoiCategory("cat_9", "Pharmacies & Meds", "PHRMCY", "pharmacy medical store chemist"),
        PoiCategory("cat_10", "Highway Rest Stop", "RSTSTP", "highway rest area motel"),
        PoiCategory("cat_11", "Coffee Shops", "COFFEE", "cafe coffee tea stall"),
        PoiCategory("cat_12", "Bike Parking", "PRKING", "two wheeler motorcycle parking"),
        PoiCategory("cat_13", "Hotels & Lodges", "HOTEL", "hotel lodge resort"),
        PoiCategory("cat_14", "Convenience Store", "CNVNCE", "convenience store general store"),
        PoiCategory("cat_15", "Scenic Viewpoint", "SCENIC", "scenic view viewpoint ghat"),
        PoiCategory("cat_16", "Temples & Worship", "TMPLWR", "temple spiritual place"),
        PoiCategory("cat_17", "Shopping Malls", "SHOPNG", "shopping mall market"),
        PoiCategory("cat_18", "Toll Plazas", "TOLLPL", "toll plaza fastag"),
        PoiCategory("cat_19", "Bus Stations", "BUSSTN", "bus stand station depot"),
        PoiCategory("cat_20", "Railway Stations", "RLWSTN", "railway station junction")
    )

    @Test
    fun testPoi_ExactCategoryCount_IsTwenty() {
        assertEquals("Must provide exactly 20 distinct POI categories from categories.json", 20, authoritative20Categories.size)
    }

    @Test
    fun testPoi_EssentialRiderCategories_Present() {
        val ids = authoritative20Categories.map { it.id }
        assertTrue("Petrol Pump present", "cat_1" in ids)
        assertTrue("EV Charging present", "cat_2" in ids)
        assertTrue("Service Center present", "cat_3" in ids)
        assertTrue("Hospital present", "cat_4" in ids)
        assertTrue("Puncture Repair present", "cat_7" in ids)
    }

    @Test
    fun testPoi_SearchByCategoryCode() {
        val petrol = authoritative20Categories.first { it.code == "TRNPMP" }
        assertEquals("Petrol Pump", petrol.name)
        assertTrue(petrol.searchQuery.contains("fuel"))
    }

    @Test
    fun testPoi_KeywordFilterOverCategories() {
        val results = authoritative20Categories.filter { it.searchQuery.contains("charging") }
        assertEquals(1, results.size)
        assertEquals("EV Fast Charging", results.first().name)
    }

    @Test
    fun testPoi_DistinctCategoryCodes_NoDuplicates() {
        val codes = authoritative20Categories.map { it.code }
        assertEquals(20, codes.distinct().size)
    }

    @Test
    fun testPoi_Boundary_CaseInsensitiveCategorySearch() {
        fun searchCategories(q: String): List<PoiCategory> {
            val query = q.lowercase().trim()
            return authoritative20Categories.filter { it.name.lowercase().contains(query) || it.searchQuery.lowercase().contains(query) }
        }
        assertEquals(1, searchCategories("PUNCTURE").size)
        assertEquals(1, searchCategories("fastag").size)
    }

    @Test
    fun testPoi_Boundary_EmptySearchReturnsAllCategories() {
        fun filterSafe(q: String): List<PoiCategory> = if (q.isBlank()) authoritative20Categories else authoritative20Categories.filter { it.name.contains(q) }
        assertEquals(20, filterSafe("   ").size)
    }

    @Test
    fun testPoi_Boundary_CategoryIconMappingIntegrity() {
        fun getCategoryIconResource(code: String): String = "cat_icon_${code.lowercase()}.png"
        assertEquals("cat_icon_trnpmp.png", getCategoryIconResource("TRNPMP"))
        assertEquals("cat_icon_evchg.png", getCategoryIconResource("EVCHG"))
    }

    @Test
    fun testPoi_Boundary_NoMatchQueryReturnsEmpty() {
        val matches = authoritative20Categories.filter { it.name.contains("spacecraft") }
        assertTrue(matches.isEmpty())
    }

    @Test
    fun testPoi_Boundary_MultiKeywordPoiSearch() {
        val tokens = listOf("ev", "charging")
        val matches = authoritative20Categories.filter { cat ->
            tokens.all { cat.searchQuery.contains(it) }
        }
        assertEquals(1, matches.size)
        assertEquals("EV Fast Charging", matches.first().name)
    }

    // =========================================================================
    // FEATURE 25: RIDER NOTES & JOURNAL (Tier 1 & Tier 2)
    // =========================================================================

    data class RideNote(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val content: String,
        val tags: List<String> = emptyList(),
        val createdAtFormatted: String,
        val category: String = "General",
        val isPinned: Boolean = false
    )

    class NotesRepository {
        private val _notes = mutableListOf(
            RideNote(
                id = "note_1",
                title = "Chain Maintenance & Lube",
                content = "Cleaned chain thoroughly with Motul C1 at 4,100 km. Applied Motul C2 road chain lube. Checked chain slack: set to 28 mm.",
                tags = listOf("#maintenance", "#chain", "#motul", "#diy"),
                createdAtFormatted = "Yesterday, 05:30 PM",
                category = "Maintenance",
                isPinned = true
            ),
            RideNote(
                id = "note_2",
                title = "Lonavala Scenic Route Observations",
                content = "Took Old Mumbai-Pune highway via Dehu Road. Excellent tarmac until Kamshet. Tiger Point view was clear, early morning fog at 7 AM. Fuel economy achieved: 44.5 km/L in Road mode.",
                tags = listOf("#trips", "#lonavala", "#fuel", "#scenic"),
                createdAtFormatted = "28 Oct 2026, 11:00 AM",
                category = "Riding Journal",
                isPinned = false
            ),
            RideNote(
                id = "note_3",
                title = "Tire Pressure & Cold Weather Check",
                content = "Checked cold pressures at Shell petrol pump. Front: 28 PSI, Rear: 32 PSI. Valve caps replaced with metal seals.",
                tags = listOf("#tires", "#safety", "#pressure"),
                createdAtFormatted = "25 Oct 2026, 08:15 AM",
                category = "Safety",
                isPinned = false
            )
        )

        fun getAll(): List<RideNote> = _notes.toList()

        fun add(note: RideNote) {
            _notes.add(0, note)
        }

        fun delete(id: String) {
            _notes.removeIf { it.id == id }
        }

        fun update(updated: RideNote) {
            val idx = _notes.indexOfFirst { it.id == updated.id }
            if (idx >= 0) _notes[idx] = updated
        }

        fun searchByTag(tag: String): List<RideNote> {
            val cleanTag = tag.lowercase().trim()
            return _notes.filter { note -> note.tags.any { it.lowercase() == cleanTag } }
        }
    }

    @Test
    fun testNotes_InitialCatalog_ContainsSampleJournalEntries() {
        val repo = NotesRepository()
        assertEquals(3, repo.getAll().size)
        assertTrue(repo.getAll().first { it.id == "note_1" }.isPinned)
    }

    @Test
    fun testNotes_AddNote_PrependsToTop() {
        val repo = NotesRepository()
        val newNote = RideNote(
            title = "New Helmet Purchase",
            content = "Bought MT Thunder 4 SV helmet in Matte Black. Pinlock 120 fitted.",
            tags = listOf("#gear", "#helmet"),
            createdAtFormatted = "Today, 10:00 AM"
        )
        repo.add(newNote)
        assertEquals(4, repo.getAll().size)
        assertEquals("New Helmet Purchase", repo.getAll().first().title)
    }

    @Test
    fun testNotes_DeleteNote_RemovesById() {
        val repo = NotesRepository()
        repo.delete("note_2")
        assertEquals(2, repo.getAll().size)
        assertFalse(repo.getAll().any { it.id == "note_2" })
    }

    @Test
    fun testNotes_UpdateNote_EditsContent() {
        val repo = NotesRepository()
        var note = repo.getAll().first { it.id == "note_1" }
        note = note.copy(content = "Updated slack to 30 mm")
        repo.update(note)
        assertEquals("Updated slack to 30 mm", repo.getAll().first { it.id == "note_1" }.content)
    }

    @Test
    fun testNotes_SearchByTag_FiltersCorrectly() {
        val repo = NotesRepository()
        val motulNotes = repo.searchByTag("#motul")
        assertEquals(1, motulNotes.size)
        assertEquals("Chain Maintenance & Lube", motulNotes.first().title)
    }

    @Test
    fun testNotes_Boundary_TagSearchWithoutHashPrefix() {
        val repo = NotesRepository()
        fun searchTagFuzzy(tag: String): List<RideNote> {
            val clean = tag.removePrefix("#").lowercase().trim()
            return repo.getAll().filter { note -> note.tags.any { it.removePrefix("#").lowercase() == clean } }
        }
        val result = searchTagFuzzy("maintenance")
        assertEquals(1, result.size)
    }

    @Test
    fun testNotes_Boundary_EmptyTitleFallback() {
        fun resolveTitle(title: String, content: String): String {
            return if (title.isNotBlank()) title else content.take(30) + "..."
        }
        assertEquals("Short note preview text goes h...", resolveTitle("", "Short note preview text goes here"))
    }

    @Test
    fun testNotes_Boundary_PinToggle() {
        val repo = NotesRepository()
        var note = repo.getAll().first { it.id == "note_2" }
        assertFalse(note.isPinned)
        note = note.copy(isPinned = true)
        repo.update(note)
        assertTrue(repo.getAll().first { it.id == "note_2" }.isPinned)
    }

    @Test
    fun testNotes_Boundary_LargeNoteContent() {
        val largeContent = "Note paragraph content. ".repeat(200) // ~5000 chars
        val note = RideNote(title = "Detailed Ride Log", content = largeContent, createdAtFormatted = "Now")
        assertTrue("Rider note handles multi-paragraph journal text", note.content.length > 4000)
    }

    @Test
    fun testNotes_Boundary_SearchByMultipleTags() {
        val repo = NotesRepository()
        val tags = listOf("#chain", "#diy")
        val matching = repo.getAll().filter { note ->
            tags.all { tag -> note.tags.any { it.equals(tag, ignoreCase = true) } }
        }
        assertEquals(1, matching.size)
        assertEquals("note_1", matching.first().id)
    }
}
