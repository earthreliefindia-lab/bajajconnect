package com.openride.companion.model

enum class ManeuverType(val clusterCode: Int, val iconName: String) {
    DEPART(0, "Depart"),
    STRAIGHT(1, "Straight"),
    TURN_LEFT(2, "TurnLeft"),
    TURN_RIGHT(3, "TurnRight"),
    SLIGHT_LEFT(4, "SlightLeft"),
    SLIGHT_RIGHT(5, "SlightRight"),
    U_TURN(6, "UTurn"),
    ROUNDABOUT(7, "Roundabout"),
    ARRIVE(8, "Arrive")
}

data class LiveNavInstruction(
    val maneuver: ManeuverType = ManeuverType.STRAIGHT,
    val instructionText: String = "Proceed along route",
    val distanceToManeuverMeters: Int = 0,
    val nextStreet: String = "",
    val remainingDistanceMeters: Float = 0f,
    val remainingTimeSeconds: Long = 0,
    val currentSpeedKmh: Int = 0,
    val speedLimitKmh: Int = 50,
    val laneGuidance: String = ""
) {
    val formattedManeuverDistance: String
        get() = if (distanceToManeuverMeters >= 1000) {
            String.format("%.1f km", distanceToManeuverMeters / 1000f)
        } else {
            "$distanceToManeuverMeters m"
        }

    val formattedRemainingDistance: String
        get() = String.format("%.1f km", remainingDistanceMeters / 1000f)

    val formattedRemainingTime: String
        get() {
            val mins = (remainingTimeSeconds / 60) % 60
            val hours = remainingTimeSeconds / 3600
            return if (hours > 0) "${hours}h ${mins}m" else "${mins} min"
        }
}
