package com.openride.companion.ble

/**
 * 16-Bit Telltale Indicator Bitmask model representing dashboard cluster status lights.
 * Mapped from Bytes 25-26 of the inbound telemetry frame.
 */
data class TelltaleIndicators(
    val turnSignalLeft: Boolean = false,
    val turnSignalRight: Boolean = false,
    val highBeam: Boolean = false,
    val neutralLamp: Boolean = false,
    val absAlert: Boolean = false,
    val checkEngineMil: Boolean = false,
    val lowFuelAlert: Boolean = false,
    val batteryAlert: Boolean = false,
    val sideStandDown: Boolean = false,
    val serviceDue: Boolean = false,
    val tractionControlAlert: Boolean = false,
    val engineOverheatWarning: Boolean = false
) {
    /**
     * Serializes indicators into a 16-bit bitmask integer.
     */
    fun toBitmask(): Int {
        var mask = 0
        if (turnSignalLeft) mask = mask or 0x0001
        if (turnSignalRight) mask = mask or 0x0002
        if (highBeam) mask = mask or 0x0004
        if (neutralLamp) mask = mask or 0x0008
        if (absAlert) mask = mask or 0x0010
        if (checkEngineMil) mask = mask or 0x0020
        if (lowFuelAlert) mask = mask or 0x0040
        if (batteryAlert) mask = mask or 0x0080
        if (sideStandDown) mask = mask or 0x0100
        if (serviceDue) mask = mask or 0x0200
        if (tractionControlAlert) mask = mask or 0x0400
        if (engineOverheatWarning) mask = mask or 0x0800
        return mask
    }

    companion object {
        /**
         * Decodes a 16-bit bitmask integer into structured TelltaleIndicators.
         */
        fun fromBitmask(mask: Int): TelltaleIndicators {
            return TelltaleIndicators(
                turnSignalLeft = (mask and 0x0001) != 0,
                turnSignalRight = (mask and 0x0002) != 0,
                highBeam = (mask and 0x0004) != 0,
                neutralLamp = (mask and 0x0008) != 0,
                absAlert = (mask and 0x0010) != 0,
                checkEngineMil = (mask and 0x0020) != 0,
                lowFuelAlert = (mask and 0x0040) != 0,
                batteryAlert = (mask and 0x0080) != 0,
                sideStandDown = (mask and 0x0100) != 0,
                serviceDue = (mask and 0x0200) != 0,
                tractionControlAlert = (mask and 0x0400) != 0,
                engineOverheatWarning = (mask and 0x0800) != 0
            )
        }
    }
}
