# REC iPhone Robot Remote

An iPhone/iPad Bluetooth LE controller and matching Arduino Nano/Uno firmware for your REC pick-and-place robot.

**Native iPhone app with a GitHub Actions IPA build.** Download the compiled unsigned IPA from a successful [Actions run](https://github.com/shenenleow10521/RemoteControl/actions/workflows/build-ios.yml), then sign it for your phone. Follow [INSTALL_IPHONE.md](INSTALL_IPHONE.md) for AltStore Classic (Windows/Mac) or Xcode installation. The existing HC-05 must be replaced with a compatible BLE UART module. Physical robot operation still needs testing.

## What you need

- iPhone/iPad running iOS 16 or later.
- Apple signing via Xcode on a Mac, or an IPA sideload/signing tool such as AltStore Classic with its required computer setup. A TestFlight release requires your Apple Developer setup.
- Existing Nano/Uno + L298N, two DC drive motors, two **positional** servos and appropriate regulated power.
- A Bluetooth **LE UART bridge**, replacing HC-05. The app supports:

| BLE profile | Service | Phone writes to | Robot notifications |
|---|---|---|---|
| HM-10 style UART | FFE0 | FFE1 | FFE1 |
| Nordic UART Service | 6E400001-B5A3-F393-E0A9-E50E24DCCA9E | 6E400002-B5A3-F393-E0A9-E50E24DCCA9E | 6E400003-B5A3-F393-E0A9-E50E24DCCA9E |

The bridge must advertise the matching service, accept writes and provide notifications, with a transparent physical UART to the Nano. Not every device labelled “Bluetooth” or “HM-10” provides these exact properties. Confirm the module's datasheet and UART support before buying. A BLE development board needs its own UART-bridge firmware; this package does not program the BLE module.

## Buttons

The handbook defines tasks and restrictions, not a mandatory button layout. These controls map those tasks to your existing two-motor/two-servo robot. There are no autonomous route or pick/place macros.

| Control | Command | Result |
|---|---|---|
| Forward / Reverse | F / B | Both wheels forward/backward |
| Left / Right | L / R | Pivot by reversing one wheel |
| Release direction | S | Stop wheels |
| Arm up / down | W / X | Increase/decrease logical arm target by 5° per repeat |
| Release arm | J | Hold current arm position |
| Grip open / close | E / C | Increase/decrease logical gripper target by 5° per repeat |
| Release gripper | K | Hold current gripper position |
| Slower / Faster | - / + | PWM changes by 20, clamped to 80–255 |
| Stop / Stop all motion | ! | Stop wheels and freeze both servo targets |
| Refresh status | P | Request firmware settings and logical positions |
| Connect / Disconnect | BLE | Find and connect/disconnect the BLE UART module |
| Start / Pause clock | Local | Start a timer; pause also sends stop |
| Reset 3:00 / Calibration 1:00 | Local | Stop motion and select a timer duration |
| Stop for retry | ! + local note | Stop motion; the operator must still request referee permission |

Hold a motion button for continuous movement; release or slide off it to stop. Multiple actuator groups can be held together. Reverse-arm and reverse-gripper toggles in Setup swap their command directions if the mechanism is mounted the other way. Start with very short presses and calibrate with the mechanism unloaded.

The app enables motion only after receiving a `REC1` status from the included firmware. The previous PWM-only `REC_test.ino` does not support this handshake or the servo controls and must not be used with this remote.

## Wiring — preserve your uploaded sketch's pins

| Component | Nano/Uno pin |
|---|---|
| L298N IN1 / IN2 | D2 / D3 |
| L298N IN3 / IN4 | D4 / D7 |
| L298N ENA / ENB | D5 / D6 |
| Arm servo signal | D9 |
| Gripper servo signal | D10 |
| BLE UART TX | D0 / RX |
| BLE UART RX | D1 / TX **through a suitable 5 V → 3.3 V level shifter/divider** |
| Grounds | Common ground for board, driver, servos and BLE module |

Remove the L298N ENA/ENB enable jumpers when those pins are driven by D5/D6 PWM. Supply the BLE board according to its exact board datasheet; a bare 3.3 V module must not be powered with 5 V. Power servos from a suitable regulated supply rather than relying on the Nano regulator. Keep the robot's required physical power-cutoff switch.

The sketch defaults to **9600 baud**. Set `UART_BAUD` to the BLE module's actual UART baud, or configure the module to match. The iPhone has no UART baud setting; the physical Nano-to-module serial link determines it. The baud does not have to match your former HC-05 setup.

## Upload robot firmware

1. Open `REC_BLE_Robot/REC_BLE_Robot.ino` in Arduino IDE and install the official Servo library if prompted.
2. Select the actual Nano/Uno board, processor/bootloader and USB port.
3. Disconnect the module's TX/RX wires from D0/D1 before uploading. Upload the sketch.
4. For USB-only checking, keep the module UART wires disconnected. Open Serial Monitor at the sketch's baud (default 9600); `P` should return `REC1 PWM=200 ...`.
5. Disconnect USB and reconnect the BLE UART wiring. If using USB only as power, do not transmit from a USB serial terminal while the BLE module is attached to D0/D1.
6. With wheels lifted and the mechanism unloaded, test each direction and the physical cutoff before field use. Swap a motor's polarity if its physical direction is wrong.
7. Check servo endpoints in `ARM_MIN`, `ARM_MAX`, `GRIP_MIN`, `GRIP_MAX` and offsets. These are inherited from your original sketch; they are not measured limits for your mechanism. The startup positions are 90° logical (arm writes 100° due to its +10° offset). Power-up can reposition the servos.

## Install the iPhone app

1. Unzip on a Mac and open `RECRemote.xcodeproj`.
2. In the RECRemote target, open **Signing & Capabilities**, choose your Team and change the bundle identifier if Xcode requests a unique value.
3. Connect your iPhone, trust the Mac, and enable Developer Mode if requested by iOS/Xcode.
4. Select your iPhone as the run destination and click Run. A simulator can inspect the layout but cannot validate this physical robot connection.
5. Allow Bluetooth permission, tap **Connect**, power the BLE module and select it.
6. Wait for **Connected — robot firmware confirmed**. If it times out, check the supported BLE profile, module UART baud, cross-connected TX/RX and common ground.
7. Test movement, release-to-stop, gripper direction and loss of connection before operating on the field.

The app runs in portrait or landscape. It keeps the screen awake while connected. Bringing another app to the foreground, locking the phone or leaving the active scene sends stop and disconnects. Reconnect when ready to operate again.

## Stop behavior

- Each motion command is repeated every 120 ms while held.
- Each actuator has a separate 600 ms command timeout in the firmware. Arm commands cannot keep a forgotten drive command alive.
- Repeated direction commands refresh the timeout without repeatedly restarting the motors.
- Reversals use a non-blocking 50 ms pause; no full-power kick-start is applied.
- Loss of feedback for more than 3 seconds causes the app to stop and disconnect.
- Release, gesture cancellation, disconnect and app inactivity clear active controls. Bluetooth delivery is not guaranteed; the firmware timeout is the fallback.
- Stop freezes servo targets but keeps positional servos holding torque. **It does not cut electrical power.** Use the required physical switch to cut power.
- Firmware status reports PWM settings and commanded logical servo positions, not measured motor speed, encoder position, cube possession or battery level.

## Handbook coverage

Source reviewed: *REC Team Recruitment 2 Handbook v1.3.pdf*, especially sections 4.3, 4.4 and Safety.

| Handbook requirement/task | Controller support |
|---|---|
| Wireless manual or semi-autonomous control | Manual Bluetooth LE control |
| Pick up and actively carry cubes | Manual gripper + arm controls |
| Place on team target, then access Center Zone | Manual placement; operator judges placement and zone access |
| Three-minute game | Optional local 3:00 countdown, stop on expiry |
| One-minute final checking/calibration | Optional local 1:00 countdown; no automatic calibration or code changes |
| Retry by permission | Stop-for-retry action; no automatic repositioning or timer reset |
| Physical power cutoff | Required on robot; a touchscreen stop does not satisfy it |
| Maximum 20 cm cube, nominal battery ≤9 V, max two cubes, no pushing | Operator/build constraints; not electronically enforced by this app |

The game also ends when a team completes the Center placement, on the referee's signal. The timer cannot detect this. The handbook contains conflicting placing-target dimensions (50 mm elsewhere, 5 mm in one scoring paragraph); this controller does not assume a target dimension.

## Validation and limits

The actual firmware was compiled as C++ against Arduino/Servo host stubs and exercised for startup stop, all directions, reversal delay, repeated commands, independent 600 ms timeouts, servo endpoints, speed limits, stop-all and bounded UART parsing. Those checks passed. This is not an AVR build or electrical test.

Project structure, source references, privacy plist and shared Xcode scheme were checked locally. The included GitHub workflow compiles the actual iOS app on a macOS runner, produces an unsigned IPA, and separately compiles the firmware for Nano and Uno. Check the current [Actions results](https://github.com/shenenleow10521/RemoteControl/actions/workflows/build-ios.yml) for build verification. **Signing, device installation, Bluetooth radio behavior and physical robot operation still require testing.** Test the complete app → BLE bridge → Nano → motors/servos path before competition use.

Official references:
- Apple BLE central: https://developer.apple.com/documentation/corebluetooth/cbcentralmanager
- Apple accessory support: https://developer.apple.com/accessories/
- Arduino Servo library: https://github.com/arduino-libraries/Servo
- Nordic UART Service: https://nrfconnectdocs.nordicsemi.com/ncs/latest/nrf/libraries/bluetooth/services/nus.html
