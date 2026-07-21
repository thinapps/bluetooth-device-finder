# Scope

Bluetooth Device Finder helps users locate a nearby detectable Bluetooth device by comparing its changing signal strength while moving around.

## Supported

Version 0.1.0 supports:

- foreground Bluetooth Low Energy scanning
- a nearby-device list sorted by smoothed signal strength
- advertised device names when available
- Bluetooth addresses and paired status
- current RSSI values with simple signal labels
- Finder Mode for following one selected device
- automatic removal of devices that have stopped advertising during the current scan
- permission denial and settings recovery
- light and dark system themes

Scan results remain in memory only and are not stored as scan history. Android may temporarily restore the selected device name and address when recreating a visible Finder Mode screen, such as after rotation; this is limited to visible interface state.

## Detection Limits

A device can appear only when it is powered on, within Bluetooth range, and actively advertising in a form Android exposes to the app. A device may disappear when it connects, enters a charging case, sleeps, changes its private address, or stops advertising.

Walls, furniture, people, interference, transmission power, antenna placement, and device orientation can change RSSI substantially. The app therefore presents relative signal guidance and does not convert RSSI into meters or feet.

An unknown Bluetooth device is not evidence of a hidden camera. Cameras using Wi-Fi, cellular service, local recording, cables, or disabled Bluetooth cannot be found through this scan.

## Deferred

These ideas may be reconsidered after the initial BLE finder is tested on real devices:

- optional Classic Bluetooth discovery
- clearer device-type hints derived from standard advertised services
- a bounded recent-device list
- optional sound or haptic guidance in Finder Mode
- additional accessibility refinements based on device testing

## Out of Scope

The initial product does not support:

- exact distance estimates
- guaranteed hidden-camera or surveillance detection
- Wi-Fi network scanning
- background or continuous monitoring
- location history, maps, or remote tracking
- connecting to, controlling, ringing, or pairing devices
- vendor account networks such as Find My Device or crowdsourced tracker networks
- accounts, analytics, advertising, or cloud synchronization
