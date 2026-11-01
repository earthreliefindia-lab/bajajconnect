package com.openride.companion.telephony

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveIncomingCall(
    val callerNumber: String,
    val callerName: String = "Incoming Call",
    val isRinging: Boolean = false
)

class CallSmsManager(private val context: Context) {

    companion object {
        private const val TAG = "CallSmsManager"
        const val DEFAULT_AUTO_REPLY_SMS = "I am currently riding my motorcycle. I will call you back once I arrive safely."
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _incomingCall = MutableStateFlow<ActiveIncomingCall?>(null)
    val incomingCall: StateFlow<ActiveIncomingCall?> = _incomingCall.asStateFlow()

    var isAutoSmsEnabled: Boolean = true
    var customAutoSmsText: String = DEFAULT_AUTO_REPLY_SMS

    fun registerCallListener(onCallStatusChanged: (callerNumber: String, isRinging: Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager?.registerTelephonyCallback(
                context.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state, onCallStatusChanged)
                    }
                }
            )
        }
    }

    private fun handleCallState(state: Int, callback: (String, Boolean) -> Unit) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "Incoming call ringing...")
                _incomingCall.value = ActiveIncomingCall("Incoming Caller", isRinging = true)
                callback("Incoming Caller", true)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val previous = _incomingCall.value
                if (previous?.isRinging == true && isAutoSmsEnabled) {
                    // Call was rejected or missed while riding: send auto-reply SMS
                    sendAutoReplySms(previous.callerNumber)
                }
                _incomingCall.value = null
                callback("", false)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                _incomingCall.value = null
                callback("", false)
            }
        }
    }

    fun sendAutoReplySms(targetPhoneNumber: String) {
        if (targetPhoneNumber.isBlank() || targetPhoneNumber == "Incoming Caller") return
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager?.sendTextMessage(targetPhoneNumber, null, customAutoSmsText, null, null)
            Log.d(TAG, "Sent auto-reply SMS to $targetPhoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto-reply SMS", e)
        }
    }
}
