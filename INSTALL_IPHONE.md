# Install REC Remote on your iPhone

## Download the compiled app

1. Open [GitHub Actions](https://github.com/shenenleow10521/RemoteControl/actions/workflows/build-ios.yml) and sign in to GitHub.
2. Open the latest successful **Build REC iPhone App** run.
3. Under **Artifacts**, download **RECRemote-iPhone-unsigned-IPA**.
4. Extract the downloaded ZIP to obtain `RECRemote-unsigned.ipa`.

The IPA contains the compiled, native iPhone app. It is **unsigned** and cannot install by simply tapping a website link. Sign it for your own phone using the method below, or use Xcode directly.

## Option A: AltStore Classic (Mac or Windows computer)

1. Follow the official [AltStore Classic installation guide](https://faq.altstore.io/altstore-classic/how-to-install-altstore) for your computer and install AltStore on your iPhone through AltServer.
2. Follow AltStore's current instructions to sign in and enable the required device settings.
3. Import `RECRemote-unsigned.ipa` in AltStore Classic (My Apps → +) and complete its personal signing/install flow.
4. Open **REC Remote** and allow Bluetooth permission.

Use the official AltStore/AltServer apps and their current installation documentation. Credentials belong in the trusted signing tool on your own device, never in this repository or in chat. App expiry, refresh and app-count limitations depend on your Apple signing account and AltStore version.

Official resources: [AltStore](https://altstore.io/) · [Windows installation](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows) · [macOS installation](https://faq.altstore.io/altstore-classic/how-to-install-altstore-macos).

## Option B: Xcode on a Mac

1. Download or clone this repository.
2. Open `RECRemote.xcodeproj` in Xcode 16 or later.
3. Select the RECRemote target → Signing & Capabilities → your personal Team.
4. Use a unique bundle identifier if requested.
5. Connect and select your iPhone (iOS 16+), enable Developer Mode if prompted, then click Run.

## Connect the robot

This iPhone app uses **Bluetooth LE UART**, not HC-05/HC-06 Bluetooth Classic. Replace that module with a transparent BLE UART bridge using the FFE0/FFE1 profile or Nordic UART Service. Confirm the exact module is supported before buying.

Upload `REC_BLE_Robot/REC_BLE_Robot.ino` to the Nano/Uno; match its UART baud with the module (default 9600). The app will keep motion controls locked until the robot firmware answers the handshake. The PWM-only sketch is not compatible with this app.

Tap Connect, select your BLE UART device and wait for **robot firmware confirmed**. With wheels raised and the mechanism unloaded, test each button, release-to-stop, Bluetooth loss and the physical power switch before use. Read the complete pin mapping and calibration notes in [README.md](README.md).

## What is not automatic

Apple signing is not configured in this repository. A TestFlight or App Store release requires your Apple developer setup and a signed archive/export. The unsigned IPA build uses no signing certificate or Apple account secrets. The app does not run as a browser/PWA and does not make Safari compatible with HC-05.
