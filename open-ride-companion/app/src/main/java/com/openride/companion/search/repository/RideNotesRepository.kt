package com.openride.companion.search.repository

import com.openride.companion.search.model.RideNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object RideNotesRepository {
    private val _notes = MutableStateFlow<List<RideNote>>(
        listOf(
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
    )
    val notes: StateFlow<List<RideNote>> = _notes.asStateFlow()

    fun addNote(note: RideNote) {
        _notes.value = listOf(note) + _notes.value
    }

    fun deleteNote(id: String) {
        _notes.value = _notes.value.filter { it.id != id }
    }

    fun togglePin(id: String) {
        _notes.value = _notes.value.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
    }

    fun clearAllNotes() {
        _notes.value = emptyList()
    }

    fun resetToFresh() {
        _notes.value = emptyList()
    }
}
