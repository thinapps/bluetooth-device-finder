# Privacy

Bluetooth Device Finder scans for nearby detectable Bluetooth Low Energy devices while the user actively runs a scan.

## Local Processing

Android supplies temporary scan results that may include:

- an advertised device name
- a Bluetooth address
- whether the device is already paired
- received signal strength readings
- the time the advertisement was last observed during the current scan

The app processes this information locally to sort the device list and update Finder Mode. It does not transmit Bluetooth information or use it to derive the phone's physical location.

## Storage and Retention

The current version does not save scan history, selected devices, Bluetooth addresses, signal readings, or location data to persistent storage. Visible state may be retained temporarily while Android recreates the activity, and all scan data disappears when the app process ends.

## Network and Third Parties

The app does not request Internet permission and does not include accounts, analytics, advertising, behavioral tracking, remote crash reporting, or cloud synchronization.

## Android Permissions

Android 12 and newer use the Nearby devices permission. Android 11 and older require location permission and enabled location services for Bluetooth scanning because of Android's platform rules. The app does not read GPS coordinates or retain location information.

## Deletion

Because scan results are temporary, stopping the scan or closing the app removes them from the active interface. Uninstalling the app removes the application and any normal Android-managed app state.

## Changes

This policy must be reviewed before adding persistent history, background scanning, Internet access, analytics, advertising, accounts, or any new data flow.

Questions may be sent to support@thinapps.top.
