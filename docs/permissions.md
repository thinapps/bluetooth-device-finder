# Permissions

Bluetooth Device Finder requests only the permissions needed for an active user-initiated scan.

## Android 12 and Newer

The app requests the Nearby devices permission group through:

- `BLUETOOTH_SCAN` to discover nearby Bluetooth Low Energy advertisements
- `BLUETOOTH_CONNECT` to read permitted device details such as the current name and paired status and to check whether Bluetooth is enabled

The scan permission is declared with `neverForLocation`. The app does not derive the phone's physical location from Bluetooth scan results. Android may filter some beacon-style advertisements when this declaration is used.

## Android 11 and Older

Android requires the legacy `BLUETOOTH` and `BLUETOOTH_ADMIN` manifest permissions for scanning. Android 6 through Android 11 also require runtime `ACCESS_FINE_LOCATION` permission and enabled location services before Bluetooth scan results are delivered, even though this app does not calculate or store the user's location.

The legacy permissions are limited to Android 11 and older with `maxSdkVersion="30"`.

## Permission Timing

The app asks for permission only after the user taps **Scan devices**. If access is denied, the app explains that scanning cannot continue and provides access to the app's Android settings.

Bluetooth scanning stops when the app leaves the foreground.

## Permissions Not Used

The app does not request:

- Internet access
- background location
- notifications
- storage or media access
- camera or microphone access
- contacts, phone, SMS, or accessibility access
