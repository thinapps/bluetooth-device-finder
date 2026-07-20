package top.thinapps.bluetoothdevicefinder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.ViewGroup
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import top.thinapps.bluetoothdevicefinder.databinding.ActivityMainBinding
import top.thinapps.bluetoothdevicefinder.databinding.DialogInformationBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceAdapter: DeviceAdapter

    private val devices = linkedMapOf<String, NearbyDevice>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var scanRequested = false
    private var permissionRejected = false
    private var legacyLocationUnavailable = false
    private var selectedAddress: String? = null
    private var selectedName: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { value -> value }
        if (granted) {
            permissionRejected = false
            beginBluetoothCheck()
        } else {
            scanRequested = false
            permissionRejected = true
            showPermissionRequired()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isBluetoothEnabled()) {
            startBleScan(clearExisting = true)
        } else {
            scanRequested = false
            binding.statusText.setText(R.string.status_bluetooth_off)
            binding.scanButton.setText(R.string.scan_devices)
            binding.finderScanButton.setText(R.string.scan_devices)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            runOnUiThread {
                processScanResult(result)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            runOnUiThread {
                results.forEach { result -> processScanResult(result) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                isScanning = false
                scanRequested = false
                mainHandler.removeCallbacks(staleDeviceRunnable)
                binding.statusText.setText(R.string.status_scan_failed)
                binding.scanButton.setText(R.string.scan_devices)
                binding.finderScanButton.setText(R.string.scan_devices)
            }
        }
    }

    private val staleDeviceRunnable = object : Runnable {
        override fun run() {
            if (!isScanning) {
                return
            }

            val now = SystemClock.elapsedRealtime()
            val iterator = devices.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value.lastSeenElapsedTime > DEVICE_STALE_AFTER_MS) {
                    iterator.remove()
                }
            }

            renderDevices()
            renderFinder()
            mainHandler.postDelayed(this, DEVICE_REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

        deviceAdapter = DeviceAdapter { device ->
            openFinder(device)
        }
        binding.deviceList.adapter = deviceAdapter

        binding.privacyLink.paintFlags =
            binding.privacyLink.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.aboutLink.paintFlags =
            binding.aboutLink.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        binding.scanButton.setOnClickListener {
            handleScanButton()
        }
        binding.finderScanButton.setOnClickListener {
            handleScanButton()
        }
        binding.backToDevicesButton.setOnClickListener {
            showDeviceList()
        }
        binding.privacyLink.setOnClickListener {
            showInformationDialog(R.string.privacy_policy, R.string.privacy_body)
        }
        binding.aboutLink.setOnClickListener {
            showInformationDialog(R.string.about, R.string.about_body)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.finderPanel.isVisible) {
                        showDeviceList()
                    } else {
                        finish()
                    }
                }
            }
        )

        selectedAddress = savedInstanceState?.getString(STATE_SELECTED_ADDRESS)
        selectedName = savedInstanceState?.getString(STATE_SELECTED_NAME)
        scanRequested = savedInstanceState?.getBoolean(STATE_SCAN_REQUESTED, false) ?: false
        val finderVisible = savedInstanceState?.getBoolean(STATE_FINDER_VISIBLE, false) ?: false

        if (finderVisible && selectedAddress != null) {
            binding.homePanel.isVisible = false
            binding.finderPanel.isVisible = true
            renderFinder()
        }

        if (bluetoothAdapter == null) {
            binding.statusText.setText(R.string.status_unsupported)
            binding.scanButton.isEnabled = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (scanRequested && !isScanning && hasRequiredPermissions()) {
            beginBluetoothCheck(clearExisting = false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionRejected && hasRequiredPermissions()) {
            permissionRejected = false
            binding.statusText.setText(R.string.status_ready)
            binding.scanButton.setText(R.string.scan_devices)
            binding.finderScanButton.setText(R.string.scan_devices)
        }
        if (legacyLocationUnavailable && isLegacyLocationEnabled()) {
            legacyLocationUnavailable = false
            binding.statusText.setText(R.string.status_ready)
            binding.scanButton.setText(R.string.scan_devices)
            binding.finderScanButton.setText(R.string.scan_devices)
        }
    }

    override fun onStop() {
        stopBleScan(clearRequest = false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_ADDRESS, selectedAddress)
        outState.putString(STATE_SELECTED_NAME, selectedName)
        outState.putBoolean(STATE_SCAN_REQUESTED, scanRequested)
        outState.putBoolean(STATE_FINDER_VISIBLE, binding.finderPanel.isVisible)
        super.onSaveInstanceState(outState)
    }

    private fun handleScanButton() {
        if (isScanning || scanRequested) {
            stopBleScan(clearRequest = true)
            return
        }

        if (permissionRejected && !hasRequiredPermissions()) {
            openAppSettings()
            return
        }

        if (legacyLocationUnavailable && !isLegacyLocationEnabled()) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        if (!hasRequiredPermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }

        beginBluetoothCheck()
    }

    private fun beginBluetoothCheck(clearExisting: Boolean = true) {
        if (bluetoothAdapter == null) {
            binding.statusText.setText(R.string.status_unsupported)
            return
        }

        if (requiresLegacyLocationServices() && !isLegacyLocationEnabled()) {
            scanRequested = false
            legacyLocationUnavailable = true
            binding.statusText.setText(R.string.status_location_services_off)
            binding.scanButton.setText(R.string.open_settings)
            binding.finderScanButton.setText(R.string.open_settings)
            return
        }

        if (!isBluetoothEnabled()) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        legacyLocationUnavailable = false
        startBleScan(clearExisting)
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan(clearExisting: Boolean) {
        if (!hasRequiredPermissions()) {
            showPermissionRequired()
            return
        }

        val adapter = bluetoothAdapter ?: return
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            binding.statusText.setText(R.string.status_scan_failed)
            return
        }

        if (clearExisting) {
            devices.clear()
        }

        bluetoothLeScanner = scanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            isScanning = true
            scanRequested = true
            permissionRejected = false
            binding.statusText.setText(R.string.status_scanning)
            binding.scanButton.setText(R.string.stop_scan)
            binding.finderScanButton.setText(R.string.stop_scan)
            renderDevices()
            renderFinder()
            mainHandler.removeCallbacks(staleDeviceRunnable)
            mainHandler.postDelayed(staleDeviceRunnable, DEVICE_REFRESH_INTERVAL_MS)
        } catch (exception: SecurityException) {
            scanRequested = false
            showPermissionRequired()
        } catch (exception: IllegalStateException) {
            scanRequested = false
            binding.statusText.setText(R.string.status_scan_failed)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan(clearRequest: Boolean) {
        if (isScanning) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
            } catch (exception: SecurityException) {
                // Permission may have been removed while the app was scanning.
            }
        }

        isScanning = false
        if (clearRequest) {
            scanRequested = false
        }
        mainHandler.removeCallbacks(staleDeviceRunnable)

        if (clearRequest) {
            binding.statusText.setText(R.string.status_stopped)
            binding.scanButton.setText(R.string.scan_devices)
            binding.finderScanButton.setText(R.string.scan_devices)
        }
    }

    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult) {
        if (!isScanning) {
            return
        }

        val address = result.device.address ?: return
        val existing = devices[address]
        val advertisedName = result.scanRecord?.deviceName?.trim()
        val deviceName = try {
            result.device.name?.trim()
        } catch (exception: SecurityException) {
            null
        }
        val resolvedName = when {
            !advertisedName.isNullOrEmpty() -> advertisedName
            !deviceName.isNullOrEmpty() -> deviceName
            existing != null -> existing.name
            else -> getString(R.string.device_unknown)
        }
        val paired = try {
            result.device.bondState == BluetoothDevice.BOND_BONDED
        } catch (exception: SecurityException) {
            false
        }
        val smoothedRssi = if (existing == null) {
            result.rssi.toDouble()
        } else {
            (RSSI_SMOOTHING_ALPHA * result.rssi) +
                ((1.0 - RSSI_SMOOTHING_ALPHA) * existing.smoothedRssi)
        }

        devices[address] = NearbyDevice(
            address = address,
            name = resolvedName,
            latestRssi = result.rssi,
            smoothedRssi = smoothedRssi,
            lastSeenElapsedTime = SystemClock.elapsedRealtime(),
            paired = paired
        )

        if (selectedAddress == address) {
            selectedName = resolvedName
        }

        renderDevices()
        renderFinder()
    }

    private fun renderDevices() {
        val sortedDevices = devices.values.sortedWith(
            compareByDescending<NearbyDevice> { device -> device.smoothedRssi }
                .thenBy { device -> device.name.lowercase() }
        )
        deviceAdapter.submitDevices(sortedDevices)

        binding.statusDetailText.text = when (sortedDevices.size) {
            0 -> getString(R.string.device_count_zero)
            1 -> getString(R.string.device_count_one)
            else -> getString(R.string.device_count_many, sortedDevices.size)
        }
    }

    private fun openFinder(device: NearbyDevice) {
        selectedAddress = device.address
        selectedName = device.name
        binding.homePanel.isVisible = false
        binding.finderPanel.isVisible = true
        renderFinder()
    }

    private fun showDeviceList() {
        binding.finderPanel.isVisible = false
        binding.homePanel.isVisible = true
    }

    private fun renderFinder() {
        val address = selectedAddress ?: return
        val device = devices[address]
        val displayedName = device?.name ?: selectedName ?: getString(R.string.device_unknown)

        binding.finderDeviceName.text = displayedName
        binding.finderDeviceAddress.text = address

        if (device == null) {
            binding.finderSignalLabel.setText(R.string.signal_unavailable)
            binding.finderSignalValue.setText(R.string.signal_dash)
            binding.finderSignalProgress.setProgressCompat(0, true)
            binding.finderWaitingText.isVisible = true
            return
        }

        val displayedRssi = device.smoothedRssi.roundToInt()
        binding.finderSignalLabel.text = signalLabel(displayedRssi)
        binding.finderSignalValue.text = getString(R.string.rssi_value, displayedRssi)
        binding.finderSignalProgress.setProgressCompat(signalProgress(displayedRssi), true)
        binding.finderWaitingText.isVisible = false
    }

    private fun signalLabel(rssi: Int): String {
        val resourceId = when {
            rssi >= -55 -> R.string.signal_very_strong
            rssi >= -67 -> R.string.signal_strong
            rssi >= -78 -> R.string.signal_fair
            else -> R.string.signal_weak
        }
        return getString(resourceId)
    }

    private fun signalProgress(rssi: Int): Int {
        val bounded = rssi.coerceIn(RSSI_MIN, RSSI_MAX)
        return ((bounded - RSSI_MIN) * 100) / (RSSI_MAX - RSSI_MIN)
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showPermissionRequired() {
        binding.statusText.setText(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                R.string.status_permission_required
            } else {
                R.string.status_location_required
            }
        )
        binding.scanButton.setText(R.string.open_settings)
        binding.finderScanButton.setText(R.string.open_settings)
    }

    @SuppressLint("MissingPermission")
    private fun isBluetoothEnabled(): Boolean {
        if (!hasRequiredPermissions()) {
            return false
        }
        return bluetoothAdapter?.isEnabled == true
    }

    private fun requiresLegacyLocationServices(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.R
    }

    private fun isLegacyLocationEnabled(): Boolean {
        val locationManager = getSystemService(LocationManager::class.java)
        return locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager)
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    private fun showInformationDialog(titleResource: Int, bodyResource: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogBinding = DialogInformationBinding.inflate(layoutInflater)
        dialogBinding.dialogTitle.setText(titleResource)
        dialogBinding.dialogBody.setText(bodyResource)
        dialogBinding.closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * DIALOG_WIDTH_RATIO).roundToInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        private const val STATE_SELECTED_ADDRESS = "selected_address"
        private const val STATE_SELECTED_NAME = "selected_name"
        private const val STATE_SCAN_REQUESTED = "scan_requested"
        private const val STATE_FINDER_VISIBLE = "finder_visible"

        private const val DEVICE_STALE_AFTER_MS = 15_000L
        private const val DEVICE_REFRESH_INTERVAL_MS = 1_000L
        private const val RSSI_SMOOTHING_ALPHA = 0.25
        private const val RSSI_MIN = -100
        private const val RSSI_MAX = -35
        private const val DIALOG_WIDTH_RATIO = 0.90
    }
}
