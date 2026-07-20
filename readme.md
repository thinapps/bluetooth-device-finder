# Bluetooth Device Finder

Find nearby detectable Bluetooth devices and follow live signal strength to help locate one.

| Document | Description |
| --- | --- |
| [Build](docs/build.md) | Lists the Android toolchain, build commands, signing requirements, and generated artifacts. |
| [Permissions](docs/permissions.md) | Explains the Bluetooth and legacy location permissions used on different Android versions. |
| [Privacy](docs/privacy.md) | Describes the app's local processing, temporary scan data, and lack of network access or tracking. |
| [Scope](docs/scope.md) | Defines the supported finder workflow, technical limitations, deferred ideas, and misleading claims that remain out of scope. |

## Changelog

### 0.1.0

- scan for nearby Bluetooth Low Energy devices while the app is open
- sort detectable devices by smoothed live signal strength
- show advertised names, Bluetooth addresses, paired status, and RSSI readings
- provide Finder Mode with stronger and weaker signal guidance instead of an exact-distance estimate
- handle Nearby devices permission on Android 12 and newer and legacy location requirements on older Android versions
- include local-only Privacy Policy and About dialogs
