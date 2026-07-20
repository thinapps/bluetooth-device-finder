package top.thinapps.bluetoothdevicefinder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import top.thinapps.bluetoothdevicefinder.databinding.ItemDeviceBinding
import kotlin.math.roundToInt

class DeviceAdapter(
    private val onDeviceSelected: (NearbyDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private var devices: List<NearbyDevice> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submitDevices(updatedDevices: List<NearbyDevice>) {
        devices = updatedDevices.map { device -> device.copy() }
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return devices[position].address.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: NearbyDevice) {
            val context = binding.root.context
            val displayedRssi = device.smoothedRssi.roundToInt()
            val signalLabel = signalLabel(context, displayedRssi)

            binding.deviceName.text = device.name
            binding.deviceAddress.text = device.address
            binding.deviceType.text = context.getString(
                if (device.paired) R.string.device_paired else R.string.device_nearby
            )
            binding.deviceSignal.text = context.getString(
                R.string.signal_format,
                displayedRssi,
                signalLabel
            )
            binding.root.contentDescription = listOf(
                device.name,
                device.address,
                binding.deviceType.text,
                binding.deviceSignal.text
            ).joinToString(", ")
            binding.root.setOnClickListener {
                onDeviceSelected(device)
            }
        }
    }

    private fun signalLabel(context: android.content.Context, rssi: Int): String {
        val resourceId = when {
            rssi >= -55 -> R.string.signal_very_strong
            rssi >= -67 -> R.string.signal_strong
            rssi >= -78 -> R.string.signal_fair
            else -> R.string.signal_weak
        }
        return context.getString(resourceId)
    }
}
