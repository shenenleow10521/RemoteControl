# REC Remote web controller

A buildless, installable web app for the REC robot. Open the deployed page directly and use **试用演示** to try the interface without robot hardware. Demo state never writes to a transport and is labelled throughout.

## Real connections

- BLE UART: Web Bluetooth, HM-10 FFE0/FFE1 or Nordic UART Service.
- HC-05/HC-06 UART: Web Serial on a compatible browser after system Bluetooth pairing. This is distinct from Web Bluetooth.
- USB serial: bench testing only; competition control must be wireless.
- iPhone Safari can open the interface and demo but does not natively supply these connection APIs. Adding to Home Screen does not grant those APIs. The repository's native iPhone app requires signing and BLE UART hardware.
- Use the supplied `REC_BLE_Robot.ino` with matching UART baud, default 9600. The older PWM-only sketch is not compatible with the complete controls and watchdog handshake.

All movement controls repeat their commands while held. Release, pointer cancellation, focus loss, page hiding and emergency stop clear held actions. Firmware independently expires each actuator command after about 600 ms. Real controls stay disabled until valid REC1 feedback is received. Missing feedback disconnects the session. Bluetooth and physical hardware behavior still need end-to-end testing.

## Verification

Run `node --test controller.test.mjs`. Checks cover firmware handshake validation, independent actuator release, ownership of simultaneous holds, emergency latch, event-loop suspension, feedback timeouts, demo isolation, bounds and stale queue suppression.

Browser QA verified demo activation, speed and arm commands, emergency lock/resume, calibration timer and desktop layout. No physical device is available for radio or motor verification.

## Assets and hosting

Everything in `dist/` is static. The PWA manifest and service worker support installation/offline interface access; offline caching does not add Bluetooth support. Icons are rasterizations of the included vector asset. There are no analytics, server-side user records or external frontend dependencies.
