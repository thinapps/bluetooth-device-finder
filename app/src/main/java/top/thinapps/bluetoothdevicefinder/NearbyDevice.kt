package top.thinapps.bluetoothdevicefinder

data class NearbyDevice(
    val address: String,
    val name: String,
    val latestRssi: Int,
    val smoothedRssi: Double,
    val lastSeenElapsedTime: Long,
    val paired: Boolean
)
