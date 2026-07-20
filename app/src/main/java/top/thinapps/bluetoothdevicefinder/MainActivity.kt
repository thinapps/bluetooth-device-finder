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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import top.thinapps.bluetoothdevicefinder.databinding.ActivityMainBinding
import top.thinapps.bluetoothdevicefinder.databinding.DialogInformationBinding
import java.util.Locale
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
    private var permissionPermanentlyDenied = false
    private var legacyLocationUnavailable = false
    private var renderScheduled = false
    private var selectedAddress: String? = null
    private var selectedName: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { value -> value }
        if (granted) {
            permissionPermanentlyDenied = false
            beginBluetoothCheck()
        } else {
            resetScanState(clearRequest = true)
            permissionPermanentlyDenied = hasPermanentlyDeniedPermission()
            showPermissionRequired()
            renderFinder()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!hasRequiredPermissions()) {
            handlePermissionLoss()
        } else if (isBluetoothEnabled()) {
            startBleScan(clearExisting = true)
        } else {
            resetScanState(clearRequest = true)
            binding.statusText.setText(R.string.status_bluetooth_off)
            setScanButtonText(R.string.scan_devices)
            renderFinder()
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
                showScanFailure()
            }
        }
    }

    private val renderRunnable = Runnable {
        renderScheduled = false
        if (binding.finderPanel.isVisible) {
            renderFinder()
        } else {
            renderDevices()
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

            scheduleRender()
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
            binding.finderScanButton.isEnabled = false
        }
    }

    override fun onStart() {
        super.onStart()

        if (!scanRequested || isScanning) {
            return
        }

        if (!hasRequiredPermissions()) {
            scanRequested = false
            permissionPermanentlyDenied = hasPermanentlyDeniedPermission()
            showPermissionRequired()
            renderFinder()
            return
        }

        devices.clear()
        renderDevices()
        renderFinder()
        beginBluetoothCheck(clearExisting = false)
    }

    override fun onResume() {
        super.onResume()

        if (permissionPermanentlyDenied && hasRequiredPermissions()) {
            permissionPermanentlyDenied = false
            binding.statusText.setText(R.string.status_ready)
            setScanButtonText(R.string.scan_devices)
        }

        if (legacyLocationUnavailable && isLegacyLocationEnabled()) {
            legacyLocationUnavailable = false
            binding.statusText.setText(R.string.status_ready)
            setScanButtonText(R.string.scan_devices)
        }
    }

    override fun onStop() {
        stopBleScan(clearRequest = false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val finderVisible = binding.finderPanel.isVisible
        outState.putBoolean(STATE_SCAN_REQUESTED, scanRequested)
        outState.putBoolean(STATE_FINDER_VISIBLE, finderVisible)

        if (finderVisible) {
            outState.putString(STATE_SELECTED_ADDRESS, selectedAddress)
            outState.putString(STATE_SELECTED_NAME, selectedName)
        }

        super.onSaveInstanceState(outState)
    }

    private fun handleScanButton() {
        if (isScanning || scanRequested) {
            stopBleScan(clearRequest = true)
            return
        }

        if (permissionPermanentlyDenied && !hasRequiredPermissions()) {
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
        if (!hasRequiredPermissions()) {
            handlePermissionLoss()
            return
        }

        if (bluetoothAdapter == null) {
            scanRequested = false
            binding.statusText.setText(R.string.status_unsupported)
            setScanButtonText(R.string.scan_devices)
            renderFinder()
            return
        }

        if (requiresLegacyLocationServices() && !isLegacyLocationEnabled()) {
            scanRequested = false
            legacyLocationUnavailable = true
            binding.statusText.setText(R.string.status_location_services_off)
            setScanButtonText(R.string.open_settings)
            renderFinder()
            return
        }

        if (!isBluetoothEnabled()) {
            scanRequested = false
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        legacyLocationUnavailable = false
        startBleScan(clearExisting)
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan(clearExisting: Boolean) {
        if (!hasRequiredPermissions()) {
            handlePermissionLoss()
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            showScanFailure()
            return
        }

        val scanner = try {
            adapter.bluetoothLeScanner
        } catch (exception: SecurityException) {
            handlePermissionLoss()
            return
        }
        if (scanner == null) {
            showScanFailure()
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
            permissionPermanentlyDenied = false
            binding.statusText.setText(R.string.status_scanning)
            setScanButtonText(R.string.stop_scan)
            renderDevices()
            renderFinder()
            mainHandler.removeCallbacks(staleDeviceRunnable)
            mainHandler.postDelayed(staleDeviceRunnable, DEVICE_REFRESH_INTERVAL_MS)
        } catch (exception: SecurityException) {
            handlePermissionLoss()
        } catch (exception: IllegalStateException) {
            showScanFailure()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan(clearRequest: Boolean) {
        resetScanState(clearRequest)
        renderDevices()
        renderFinder()

        if (clearRequest) {
            binding.statusText.setText(R.string.status_stopped)
            setScanButtonText(R.string.scan_devices)
        }
    }

    @SuppressLint("MissingPermission")
    private fun resetScanState(clearRequest: Boolean) {
        if (isScanning) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
            } catch (exception: SecurityException) {
                // Permission may have been removed while the app was scanning.
            }
        }

        isScanning = false
        bluetoothLeScanner = null

        if (clearRequest) {
            scanRequested = false
        }

        mainHandler.removeCallbacks(staleDeviceRunnable)
        mainHandler.removeCallbacks(renderRunnable)
        renderScheduled = false
    }

    private fun handlePermissionLoss() {
        resetScanState(clearRequest = true)
        permissionPermanentlyDenied = hasPermanentlyDeniedPermission()
        showPermissionRequired()
        renderDevices()
        renderFinder()
    }

    private fun showScanFailure() {
        resetScanState(clearRequest = true)
        binding.statusText.setText(R.string.status_scan_failed)
        setScanButtonText(R.string.scan_devices)
        renderDevices()
        renderFinder()
    }

    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult) {
        if (!isScanning) {
            return
        }

        val address = try {
            result.device.address
        } catch (exception: SecurityException) {
            handlePermissionLoss()
            return
        } ?: return
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

        scheduleRender()
    }

    private fun scheduleRender() {
        if (renderScheduled) {
            return
        }

        renderScheduled = true
        mainHandler.postDelayed(renderRunnable, DEVICE_RENDER_INTERVAL_MS)
    }

    private fun renderDevices() {
        val sortedDevices = devices.values.sortedWith(
            compareByDescending<NearbyDevice> { device -> device.smoothedRssi }
                .thenBy { device -> device.name.lowercase(Locale.ROOT) }
        )
        deviceAdapter.submitDevices(sortedDevices)

        val countText = when (sortedDevices.size) {
            0 -> getString(R.string.device_count_zero)
            1 -> getString(R.string.device_count_one)
            else -> getString(R.string.device_count_many, sortedDevices.size)
        }
        if (binding.statusDetailText.text.toString() != countText) {
            binding.statusDetailText.text = countText
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
        selectedAddress = null
        selectedName = null
        binding.finderPanel.isVisible = false
        binding.homePanel.isVisible = true
        renderDevices()
    }

    private fun renderFinder() {
        val address = selectedAddress ?: return
        val device = devices[address]
        val displayedName = device?.name ?: selectedName ?: getString(R.string.device_unknown)

        binding.finderDeviceName.text = displayedName
        binding.finderDeviceAddress.text = address

        if (!isScanning) {
            showUnavailableFinderSignal(R.string.finder_scan_stopped)
            return
        }

        if (device == null) {
            showUnavailableFinderSignal(R.string.finder_waiting)
            return
        }

        val displayedRssi = device.smoothedRssi.roundToInt()
        val signalLabel = getString(SignalStrength.labelResource(displayedRssi))
        if (binding.finderSignalLabel.text.toString() != signalLabel) {
            binding.finderSignalLabel.text = signalLabel
        }
        binding.finderSignalValue.text = getString(R.string.rssi_value, displayedRssi)
        binding.finderSignalProgress.setProgressCompat(
            SignalStrength.progress(displayedRssi),
            true
        )
        binding.finderWaitingText.isVisible = false
    }

    private fun showUnavailableFinderSignal(messageResource: Int) {
        val unavailableLabel = getString(R.string.signal_unavailable)
        if (binding.finderSignalLabel.text.toString() != unavailableLabel) {
            binding.finderSignalLabel.text = unavailableLabel
        }
        binding.finderSignalValue.setText(R.string.signal_dash)
        binding.finderSignalProgress.setProgressCompat(0, true)
        binding.finderWaitingText.setText(messageResource)
        binding.finderWaitingText.isVisible = true
    }

    private fun setScanButtonText(textResource: Int) {
        binding.scanButton.setText(textResource)
        binding.finderScanButton.setText(textResource)
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

    private fun hasPermanentlyDeniedPermission(): Boolean {
        return requiredPermissions().any { permission ->
            ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED &&
                !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
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
        val buttonText = if (permissionPermanentlyDenied) {
            R.string.open_settings
        } else {
            R.string.allow_access
        }
        setScanButtonText(buttonText)
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
        private const val DEVICE_RENDER_INTERVAL_MS = 250L
        private const val RSSI_SMOOTHING_ALPHA = 0.25
        private const val DIALOG_WIDTH_RATIO = 0.90
    }
}
