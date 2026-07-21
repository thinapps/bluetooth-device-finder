package top.thinapps.bluetoothdevicefinder

data class NearbyDevice(
    val address: String,
    val name: String,
    val smoothedRssi: Double,
    val lastSeenElapsedTime: Long,
    val paired: Boolean
)
