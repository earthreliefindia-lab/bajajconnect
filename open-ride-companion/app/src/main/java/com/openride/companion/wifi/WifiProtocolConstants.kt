package com.openride.companion.wifi

/**
 * Authoritative cluster Wi-Fi communication constants extracted from
 * Bajaj Ride Connect APK (TcpServerService, UdpServerService) and verified against E2E test specs.
 */
object WifiProtocolConstants {
    const val VEHICLE_GATEWAY_IP = "192.168.4.1"
    const val LOOPBACK_IP = "127.0.0.1"
    const val TCP_CONTROL_PORT = 5558
    const val UDP_STREAMING_PORT = 5556
    const val SYNC_BYTE: Byte = 0x00

    // Frame Groups
    const val GROUP_INPUT_EVENTS: Byte = 0x00
    const val GROUP_DASH_EVENTS: Byte = 0x01
    const val GROUP_DASH_REQUESTS: Byte = 0x02
    const val GROUP_DASH_STATUS: Byte = 0x03

    // Command Codes
    const val CMD_INPUT_TOUCH: Byte = 0x00
    const val CMD_BIKE_INFO: Byte = 0x00
    const val CMD_SCREEN_CONTROL: Byte = 0x02
    const val CMD_NAV_V2: Byte = 0x01
    const val CMD_DOC_IMAGE_REQUEST: Byte = 0x02

    const val MAX_PAYLOAD_LENGTH = 2048
    const val HEADER_SIZE = 5 // Sync (1) + Group (1) + Cmd (1) + Length (2)
    const val CRC_SIZE = 4
    const val MIN_FRAME_SIZE = HEADER_SIZE + CRC_SIZE // 9 bytes

    // Video Streaming Display Dimensions & Timing
    const val TFT_WIDTH = 800
    const val TFT_HEIGHT = 480
    const val TFT_WIDTH_COMPACT = 480
    const val TFT_HEIGHT_COMPACT = 272
    const val HEADER_HEIGHT = 50
    const val TARGET_FPS = 30
    const val FRAME_INTERVAL_MS = 33L // ~30 fps

    // UDP Encryption & Fragmentation Parameters
    const val AES_256_KEY = "Z7in5HGAGFdXXU6ZfDc8lIff4iwLY01o"
    const val FRAME_SIZE_PAYLOAD = 1008
    const val FRAME_SIZE_ENCRYPTED = 1024

    // Dynamic Compression Quality Limits
    const val INITIAL_COMPRESSION_QUALITY = 50
    const val MIN_COMPRESSION_QUALITY = 25
    const val THRESHOLD_IMG_SIZE = 25000 // 25 KB
    const val MAX_IMG_SIZE = 30000 // 30 KB
    const val MAX_REENCODE_ATTEMPTS = 2
}

/**
 * Six directional and selection buttons on motorcycle handlebar switchgear.
 */
enum class HandlebarButton {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACK
}

/**
 * 4D cluster touchscreen touch coordinate model.
 * Pressure is clamped to [0.0f, 1.0f].
 */
data class TouchPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0.0f,
    val pressure: Float = 1.0f
) {
    val clampedPressure: Float
        get() = pressure.coerceIn(0.0f, 1.0f)
}
