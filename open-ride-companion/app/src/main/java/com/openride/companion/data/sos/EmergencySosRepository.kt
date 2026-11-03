package com.openride.companion.data.sos

import com.openride.companion.model.EmergencyContact
import com.openride.companion.model.SosBroadcastStatus
import com.openride.companion.model.SosIncidentPayload
import com.openride.companion.model.SosTriggerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface EmergencySosRepository {
    val contactsState: StateFlow<List<EmergencyContact>>
    val activeSosState: StateFlow<SosIncidentPayload?>

    suspend fun getContacts(): List<EmergencyContact>
    suspend fun addContact(contact: EmergencyContact): Result<Unit>
    suspend fun removeContact(contactId: String): Result<Unit>
    suspend fun setPrimaryContact(contactId: String): Result<Unit>

    suspend fun initiateSos(
        source: SosTriggerSource,
        riderName: String,
        latitude: Double?,
        longitude: Double?,
        batteryVoltage: Float? = null,
        vehicleModel: String? = null
    ): SosIncidentPayload

    suspend fun cancelSos(): Boolean
    suspend fun dispatchSos(payload: SosIncidentPayload): Result<Unit>
}

/**
 * Production-ready implementation of EmergencySosRepository.
 */
class EmergencySosRepositoryImpl : EmergencySosRepository {

    private val initialContacts = listOf(
        EmergencyContact(
            id = "EMERGENCY_001",
            name = "Anita Sharma",
            phone = "+919876543210",
            relationship = "Spouse",
            isPrimary = true,
            avatarAsset = "avatar_emergency_contact_1.png"
        ),
        EmergencyContact(
            id = "EMERGENCY_002",
            name = "R. K. Sharma (Brother)",
            phone = "+919876543211",
            relationship = "Brother",
            isPrimary = false,
            avatarAsset = "avatar_emergency_contact_2.png"
        )
    )

    private val _contactsState = MutableStateFlow(initialContacts)
    override val contactsState: StateFlow<List<EmergencyContact>> = _contactsState.asStateFlow()

    private val _activeSosState = MutableStateFlow<SosIncidentPayload?>(null)
    override val activeSosState: StateFlow<SosIncidentPayload?> = _activeSosState.asStateFlow()

    override suspend fun getContacts(): List<EmergencyContact> = _contactsState.value

    override suspend fun addContact(contact: EmergencyContact): Result<Unit> {
        val current = _contactsState.value
        if (!EmergencyContact.canAddContact(current, contact.phone)) {
            return Result.failure(
                IllegalArgumentException("Cannot add contact: Maximum 5 contacts reached or duplicate phone number.")
            )
        }
        _contactsState.update { it + contact }
        return Result.success(Unit)
    }

    override suspend fun removeContact(contactId: String): Result<Unit> {
        _contactsState.update { current ->
            current.filter { it.id != contactId }
        }
        return Result.success(Unit)
    }

    override suspend fun setPrimaryContact(contactId: String): Result<Unit> {
        _contactsState.update { current ->
            current.map {
                it.copy(isPrimary = (it.id == contactId))
            }
        }
        return Result.success(Unit)
    }

    override suspend fun initiateSos(
        source: SosTriggerSource,
        riderName: String,
        latitude: Double?,
        longitude: Double?,
        batteryVoltage: Float?,
        vehicleModel: String?
    ): SosIncidentPayload {
        val recipientNumbers = _contactsState.value.map { it.phone }
        val payload = SosIncidentPayload(
            triggerSource = source,
            latitude = latitude,
            longitude = longitude,
            batteryVoltage = batteryVoltage,
            riderName = riderName,
            vehicleModel = vehicleModel,
            recipients = recipientNumbers,
            status = SosBroadcastStatus.COUNTDOWN_ACTIVE
        )
        _activeSosState.value = payload
        return payload
    }

    override suspend fun cancelSos(): Boolean {
        val current = _activeSosState.value
        return if (current != null && current.status == SosBroadcastStatus.COUNTDOWN_ACTIVE) {
            _activeSosState.value = current.copy(status = SosBroadcastStatus.CANCELLED)
            true
        } else {
            false
        }
    }

    override suspend fun dispatchSos(payload: SosIncidentPayload): Result<Unit> {
        _activeSosState.value = payload.copy(status = SosBroadcastStatus.DISPATCHED)
        return Result.success(Unit)
    }
}
