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
    val maneuver: ManeuverType = ManeuverType.DEPART,
    val instructionText: String = "Proceed to designated route",
    val distanceToManeuverMeters: Int = 350,
    val nextStreet: String = "FC Road",
    val remainingDistanceMeters: Float = 4200f,
    val remainingTimeSeconds: Long = 680,
    val currentSpeedKmh: Int = 42,
    val speedLimitKmh: Int = 50,
    val laneGuidance: String = "Keep in Left 2 Lanes"
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
