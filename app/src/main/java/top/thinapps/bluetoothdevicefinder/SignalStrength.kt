package top.thinapps.bluetoothdevicefinder

import androidx.annotation.StringRes

object SignalStrength {

    @StringRes
    fun labelResource(rssi: Int): Int {
        return when {
            rssi >= VERY_STRONG_MINIMUM -> R.string.signal_very_strong
            rssi >= STRONG_MINIMUM -> R.string.signal_strong
            rssi >= FAIR_MINIMUM -> R.string.signal_fair
            else -> R.string.signal_weak
        }
    }

    fun progress(rssi: Int): Int {
        val bounded = rssi.coerceIn(MINIMUM_RSSI, MAXIMUM_RSSI)
        return ((bounded - MINIMUM_RSSI) * 100) / (MAXIMUM_RSSI - MINIMUM_RSSI)
    }

    private const val VERY_STRONG_MINIMUM = -55
    private const val STRONG_MINIMUM = -67
    private const val FAIR_MINIMUM = -78
    private const val MINIMUM_RSSI = -100
    private const val MAXIMUM_RSSI = -35
}
