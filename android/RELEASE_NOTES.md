Native Android 8.0+ controller and Bluetooth Classic SPP serial terminal for HC-05 / HC-06.

- Direct connection to already-paired Bluetooth Classic devices, without an Arduino-specific handshake.
- Raw receive log, manual command entry, None/LF/CR/CR+LF endings and F/B/L/R/S shortcuts.
- Full robot remote buttons for drive, speed, arm, gripper, stop and local match/calibration timers.
- Clearly labelled demo mode and support for the repository's extended watchdog firmware.

Download **RECRemote-Android.apk** below. This is a debug-signed development APK, not a Play Store release. Install on your own Android phone and allow Nearby devices/Bluetooth permission. Future debug builds may require uninstalling the old build if their signing key changes.

Physical Bluetooth/UART/motor operation needs testing. Old Arduino programs only support their existing commands and do not gain servo control or disconnect protection by installing this app. See android/README.md for setup and the compatible firmware.
