# REC Remote — Android APK

Native Android controller and serial terminal for **Bluetooth Classic SPP (HC-05 / HC-06)**, Android 8.0 or newer. This connection is the same transport category used by Serial Bluetooth Terminal. It is not the iPhone BLE app, a website wrapper or a dependency on the Serial Bluetooth Terminal app.

## Download and install

1. Open [Build REC Android APK](https://github.com/shenenleow10521/RemoteControl/actions/workflows/build-android.yml).
2. Open a successful run and download **RECRemote-Android-APK** under Artifacts. Extract `RECRemote-Android.apk` from the ZIP.
3. Open the APK on your Android phone and, if Android prompts, allow installation for the app you use to open this file. This is a debug-signed development build, not a Play Store release. Future development builds may require uninstalling the previous debug build if their signing key changes.
4. Open **REC Remote**. Allow **Nearby devices / Bluetooth connection** permission if requested.

## Connect like a serial Bluetooth terminal

1. Turn on the robot and HC-05. Disconnect any other phone or terminal currently using it; HC-05 typically accepts one serial connection at a time.
2. In Android **Settings → Bluetooth**, pair the HC-05/HC-06 using its configured pairing PIN. This app lists already-paired devices and does not perform BLE scanning or ask for location permission.
3. Return to REC Remote → **连接机器人** → select the paired HC-05.
4. On an RFCOMM connection, the app immediately enables serial passthrough. **No special firmware handshake is required.**
5. Open **串口终端** for a raw receive log, manual text entry, selectable None/LF/CR/CR+LF line endings, clear-log action and F/B/L/R/S shortcut buttons.
6. The app does not set the HC-05's UART baud. The module's physical serial baud must match `Serial.begin(...)` in your Arduino program. The Bluetooth radio connection can succeed even when the UART baud is wrong.

Raw terminal shortcuts send once per tap, like a terminal macro. Your Arduino program decides whether an F command keeps driving until S. The main remote's direction buttons repeat while held and send S on release. Leaving the terminal, stopping, backgrounding or disconnecting sends S! where possible, combining the old sketch's wheel-stop command with the companion firmware's stop-all command. Delivery cannot be guaranteed after a radio loss.

## Complete robot controls

| Button | Command |
|---|---|
| Forward / Reverse | F / B |
| Left / Right | L / R |
| Release driving | S |
| Arm up / down | W / X (swap in settings if needed) |
| Release arm | J |
| Gripper open / close | E / C (swap in settings if needed) |
| Release gripper | K |
| Slower / Faster | - / + |
| Stop all | S! |
| Status | P |

Includes a three-minute match clock, one-minute calibration clock, retry-stop, emergency latch/resume, direction reversal settings and a clearly labelled demo that never writes to a Bluetooth device. The handbook defines tasks rather than a prescribed button list.

The original PWM-only sketch can receive the commands it already implements (F/B/L/R/S/+/-/P). It has no servo command handling or link-loss watchdog. **The presence of arm/gripper buttons does not add those functions to an old Arduino program.**

For every robot control and the firmware timeout, upload [REC_BLE_Robot.ino](../REC_BLE_Robot/REC_BLE_Robot.ino). Despite the older filename/comment referring to the BLE iPhone companion, its Nano/Uno hardware UART protocol also works with HC-05 and this Android app. Its default UART baud is **9600**, which must match your HC-05's actual UART setting. On receiving its REC1 feedback, the app automatically switches to firmware-aware mode with a three-second feedback timeout. Each actuator also has its own approximately 600 ms timeout in that firmware.

The app does not require replacing HC-05 with a BLE module. Positional servo startup, endpoints, offsets, motor directions and power wiring must be calibrated on the physical robot. Firmware reports commanded logical angles and PWM settings, not encoder position or measured speed.

## Wiring retained from your sketch

L298N IN1=D2, IN2=D3, IN3=D4, IN4=D7; ENA=D5, ENB=D6. Arm signal D9, gripper signal D10. HC-05 TX→D0/RX; D1/TX→HC-05 RX through suitable voltage-level conversion; common ground. Disconnect module UART wires before uploading. Use the correct regulated supply for the module/servos and the handbook's required physical power-cutoff switch.

## Build and validation

Open `android/` in Android Studio or use Gradle 8.11.1 with JDK 17 and Android SDK 35:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

The GitHub workflow builds the APK, runs control-engine unit checks and verifies the development signature. Pure Java checks cover raw terminal mode without REC1, strict handshake mode, old/new stop compatibility, independent actuator ownership, emergency latch, interrupted holds, feedback timeouts, bounds and demo isolation. A built APK is not proof of a working physical radio link: phone permission behavior, pairing, socket connection, UART baud and motors/servos still require device testing.

No Internet permission, analytics, cloud account, location permission or background control service is included. The app only lists paired devices; it does not discover BLE devices. Physical BLE modules require a separate BLE client and are outside this Android Classic SPP build.

## Direction correction in 1.0.1

The main remote now sends B for Forward, F for Reverse, C for Grip Open, and E for Grip Close by default, matching the reported robot wiring/orientation. Left/right turns and arm direction retain their defaults. Reverse Drive / Arm / Gripper settings are saved on the phone. These switches affect the main remote buttons, including assistive activation; raw terminal text and F/B/L/R/S terminal macros remain literal serial commands. No Arduino re-upload is required for this button-mapping correction.
