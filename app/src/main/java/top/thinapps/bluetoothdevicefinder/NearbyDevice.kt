package top.thinapps.bluetoothdevicefinder

data class NearbyDevice(
    val address: String,
    var name: String,
    var latestRssi: Int,
    var smoothedRssi: Double,
    var lastSeenElapsedTime: Long,
    var paired: Boolean
)
