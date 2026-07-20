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

The current version does not save scan history, Bluetooth addresses, signal readings, or location data to persistent storage.

Stopping a scan leaves the current result list visible in the active screen so the user can review it. Starting a new scan clears that list before collecting fresh results. Returning from Finder Mode clears the selected device, and closing the activity removes the in-memory result list.

Android may temporarily restore the selected device name and address when recreating a visible Finder Mode screen, such as after rotation. This restoration is limited to visible interface state and is not a saved scan history.

## Network and Third Parties

The app does not request Internet permission and does not include accounts, analytics, advertising, behavioral tracking, remote crash reporting, or cloud synchronization.

## Android Permissions

Android 12 and newer use the Nearby devices permission. Android 11 and older require location permission and enabled location services for Bluetooth scanning because of Android's platform rules. The app does not read GPS coordinates or retain location information.

## Deletion

Starting a new scan replaces the current in-memory result list. Returning to the device list clears the current Finder selection. Closing the activity or uninstalling the app removes all remaining application state that this version creates.

## Changes

This policy must be reviewed before adding persistent history, background scanning, Internet access, analytics, advertising, accounts, or any new data flow.

Questions may be sent to support@thinapps.top.
