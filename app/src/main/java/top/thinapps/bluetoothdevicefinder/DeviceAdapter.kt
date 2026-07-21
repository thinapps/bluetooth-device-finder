package top.thinapps.bluetoothdevicefinder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import top.thinapps.bluetoothdevicefinder.databinding.ItemDeviceBinding
import kotlin.math.roundToInt

class DeviceAdapter(
    private val onDeviceSelected: (NearbyDevice) -> Unit
) : ListAdapter<NearbyDevice, DeviceAdapter.DeviceViewHolder>(DIFF_CALLBACK) {

    init {
        setHasStableIds(true)
    }

    fun submitDevices(updatedDevices: List<NearbyDevice>) {
        submitList(updatedDevices.toList())
    }

    override fun getItemId(position: Int): Long {
        val compactAddress = getItem(position).address.replace(":", "")
        return compactAddress.toLongOrNull(16)
            ?: getItem(position).address.hashCode().toLong()
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
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: NearbyDevice) {
            val context = binding.root.context
            val displayedRssi = device.smoothedRssi.roundToInt()
            val signalLabel = context.getString(SignalStrength.labelResource(displayedRssi))

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

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NearbyDevice>() {
            override fun areItemsTheSame(
                oldItem: NearbyDevice,
                newItem: NearbyDevice
            ): Boolean {
                return oldItem.address == newItem.address
            }

            override fun areContentsTheSame(
                oldItem: NearbyDevice,
                newItem: NearbyDevice
            ): Boolean {
                return oldItem.name == newItem.name &&
                    oldItem.paired == newItem.paired &&
                    oldItem.smoothedRssi.roundToInt() == newItem.smoothedRssi.roundToInt()
            }
        }
    }
}
