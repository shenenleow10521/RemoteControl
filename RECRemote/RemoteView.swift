import SwiftUI
import UIKit

private let accent = Color(red: 0.25, green: 0.91, blue: 0.77)
private let surface = Color(red: 0.08, green: 0.12, blue: 0.17)

struct RemoteView: View {
    @EnvironmentObject private var robot: RobotBluetooth
    @Environment(\.scenePhase) private var scenePhase
    @State private var showDevices = false
    @State private var showSetup = false
    @State private var remaining = 180
    @State private var clockRunning = false
    @State private var clockEnd: Date?
    @State private var note = "Start only after the referee’s signal."
    private let clock = Timer.publish(every: 0.2, on: .main, in: .common).autoconnect()

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    header
                    connection
                    HStack(spacing: 12) {
                        Label(robot.ready ? "ROBOT READY" : "CONTROLS LOCKED", systemImage: robot.ready ? "checkmark.shield.fill" : "lock.fill")
                            .font(.caption.bold()).foregroundStyle(robot.ready ? accent : .orange)
                        Spacer()
                        Button { showSetup = true } label: { Image(systemName: "slider.horizontal.3").padding(10) }
                            .accessibilityLabel("Controller setup and calibration")
                    }
                    if geometry.size.width > 650 {
                        HStack(alignment: .top, spacing: 18) {
                            drivePanel.frame(maxWidth: .infinity)
                            VStack(spacing: 18) { mechanismPanel; speedPanel }.frame(maxWidth: .infinity)
                        }
                    } else {
                        drivePanel
                        mechanismPanel
                        speedPanel
                    }
                    Button {
                        robot.emergencyStop()
                        note = "All motion stopped. Physical power switch remains available on the robot."
                    } label: {
                        Label("STOP ALL MOTION", systemImage: "stop.circle.fill")
                            .font(.headline).frame(maxWidth: .infinity).padding(.vertical, 20)
                            .background(Color.red, in: RoundedRectangle(cornerRadius: 18))
                    }.buttonStyle(.plain).disabled(!robot.ready)
                    matchPanel
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("ROBOT FEEDBACK").font(.caption.bold()).foregroundStyle(.secondary)
                            Spacer()
                            Button { robot.refreshStatus() } label: { Image(systemName: "arrow.clockwise") }
                                .disabled(!robot.ready).accessibilityLabel("Refresh robot status")
                        }
                        Text(robot.telemetry).font(.system(.caption, design: .monospaced)).textSelection(.enabled)
                        Text("Last sent: \(robot.lastSent)").font(.caption2).foregroundStyle(.secondary)
                    }.panel()
                    Text("Hold to move. Release to stop. Keep the app open while operating.")
                        .font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity)
                }.padding(20).frame(maxWidth: 1000).frame(maxWidth: .infinity)
            }.background(Color(red: 0.035, green: 0.06, blue: 0.09))
        }
        .tint(accent)
        .sheet(isPresented: $showDevices) { deviceSheet }
        .sheet(isPresented: $showSetup) { setupSheet }
        .onChange(of: scenePhase) { phase in
            if phase != .active {
                robot.emergencyStop()
                robot.disconnect()
            }
        }
        .onChange(of: robot.ready) { ready in
            UIApplication.shared.isIdleTimerDisabled = ready
        }
        .onReceive(clock) { now in
            guard clockRunning, let clockEnd else { return }
            remaining = max(0, Int(ceil(clockEnd.timeIntervalSince(now))))
            if remaining == 0 {
                clockRunning = false
                robot.emergencyStop()
                note = "Timer ended. Follow the referee’s stop signal."
            }
        }
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 5) {
                Text("REC / PICK & PLACE").font(.caption.weight(.bold)).tracking(2).foregroundStyle(accent)
                Text("Robot remote").font(.largeTitle.bold())
            }
            Spacer()
            Image(systemName: "antenna.radiowaves.left.and.right").font(.title2).foregroundStyle(accent).padding(12).background(surface, in: Circle())
        }
    }

    private var connection: some View {
        HStack(spacing: 12) {
            Circle().fill(robot.ready ? accent : .orange).frame(width: 10, height: 10)
            VStack(alignment: .leading, spacing: 3) {
                Text(robot.robotName).font(.subheadline.bold())
                Text(robot.status).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button(robot.ready || robot.connecting ? "Disconnect" : "Connect") {
                if robot.ready || robot.connecting { robot.disconnect() }
                else { showDevices = true; robot.scan() }
            }.font(.subheadline.bold()).buttonStyle(.bordered)
        }.panel()
    }

    private var drivePanel: some View {
        VStack(alignment: .leading, spacing: 14) {
            panelTitle("DRIVE", detail: "Hold a direction")
            VStack(spacing: 10) {
                HStack(spacing: 10) {
                    Color.clear.frame(maxWidth: .infinity, minHeight: 76)
                    move("Forward", icon: "arrow.up", group: .drive, command: "F")
                    Color.clear.frame(maxWidth: .infinity, minHeight: 76)
                }
                HStack(spacing: 10) {
                    move("Left", icon: "arrow.turn.up.left", group: .drive, command: "L")
                    Button { robot.emergencyStop() } label: {
                        VStack(spacing: 6) { Image(systemName: "stop.fill").font(.title2); Text("Stop").font(.caption.bold()) }
                            .frame(maxWidth: .infinity, minHeight: 76).background(.red.opacity(0.18), in: RoundedRectangle(cornerRadius: 15)).foregroundStyle(.red)
                    }.buttonStyle(.plain).disabled(!robot.ready)
                    move("Right", icon: "arrow.turn.up.right", group: .drive, command: "R")
                }
                HStack(spacing: 10) {
                    Color.clear.frame(maxWidth: .infinity, minHeight: 76)
                    move("Reverse", icon: "arrow.down", group: .drive, command: "B")
                    Color.clear.frame(maxWidth: .infinity, minHeight: 76)
                }
            }
        }.panel()
    }

    private var mechanismPanel: some View {
        VStack(alignment: .leading, spacing: 16) {
            panelTitle("PICK & PLACE", detail: "Hold for fine adjustment")
            HStack(spacing: 10) {
                move("Arm up", icon: "arrow.up.to.line", group: .arm, command: robot.reverseArm ? "X" : "W")
                move("Arm down", icon: "arrow.down.to.line", group: .arm, command: robot.reverseArm ? "W" : "X")
            }
            HStack(spacing: 10) {
                move("Grip open", icon: "arrow.left.and.right", group: .grip, command: robot.reverseGrip ? "C" : "E")
                move("Grip close", icon: "arrow.down.right.and.arrow.up.left", group: .grip, command: robot.reverseGrip ? "E" : "C")
            }
        }.panel()
    }

    private var speedPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            panelTitle("DRIVE SPEED", detail: "PWM 80–255 · steps of 20")
            HStack(spacing: 10) {
                Button { robot.changeSpeed(false) } label: { Label("Slower", systemImage: "minus").frame(maxWidth: .infinity).padding(14) }
                Button { robot.changeSpeed(true) } label: { Label("Faster", systemImage: "plus").frame(maxWidth: .infinity).padding(14) }
            }.buttonStyle(.bordered).disabled(!robot.ready)
            Text("The confirmed speed appears in robot feedback.").font(.caption).foregroundStyle(.secondary)
        }.panel()
    }

    private var matchPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            panelTitle("MATCH CLOCK", detail: "Local aid · referee controls the match")
            HStack {
                Text(String(format: "%d:%02d", remaining / 60, remaining % 60)).font(.system(size: 40, weight: .semibold, design: .monospaced))
                Spacer()
                Button(clockRunning ? "Pause & stop" : "Start clock") {
                    if clockRunning { clockRunning = false; robot.emergencyStop() }
                    else if remaining > 0 { clockEnd = Date().addingTimeInterval(Double(remaining)); clockRunning = true }
                }.buttonStyle(.borderedProminent).foregroundStyle(.black)
            }
            HStack {
                Button("Reset 3:00") { resetClock(seconds: 180) }
                Spacer()
                Button("Calibration 1:00") { resetClock(seconds: 60) }
            }.font(.caption).buttonStyle(.bordered)
            Button {
                robot.emergencyStop()
                note = "Retry: ask the referee. Pit crew returns the robot and eligible cubes. The match clock keeps running."
            } label: { Label("Stop for retry", systemImage: "arrow.uturn.backward").frame(maxWidth: .infinity) }
                .buttonStyle(.bordered)
            Text(note).font(.caption).foregroundStyle(.secondary)
        }.panel()
    }

    private func resetClock(seconds: Int) {
        robot.emergencyStop()
        clockRunning = false
        clockEnd = nil
        remaining = seconds
        note = seconds == 60 ? "Calibration only in the referee’s allowed period. No code upload." : "Start only after the referee’s signal."
    }

    private func panelTitle(_ title: String, detail: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption.bold()).tracking(1).foregroundStyle(accent)
            Text(detail).font(.caption).foregroundStyle(.secondary)
        }
    }

    private func move(_ title: String, icon: String, group: RobotBluetooth.Group, command: String) -> some View {
        HoldControl(title: title, icon: icon, enabled: robot.ready,
                    begin: { robot.start(group, owner: title, command: command) },
                    end: { robot.release(group, owner: title) })
    }

    private var deviceSheet: some View {
        NavigationStack {
            List {
                Section {
                    Text("Power the robot’s BLE UART module. Connect here in the app. HC-05/HC-06 Bluetooth Classic modules will not appear.")
                    Text(robot.status).foregroundStyle(.secondary)
                }
                Section("Nearby BLE UART devices") {
                    if robot.devices.isEmpty { Text(robot.scanning ? "Searching…" : "No compatible module found").foregroundStyle(.secondary) }
                    ForEach(robot.devices) { device in
                        Button { robot.connect(device); showDevices = false } label: {
                            HStack {
                                VStack(alignment: .leading) { Text(device.name); Text(device.id.uuidString.suffix(12)).font(.caption).foregroundStyle(.secondary) }
                                Spacer()
                                Text("\(device.rssi) dBm").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                Button(robot.scanning ? "Searching…" : "Scan again") { robot.scan() }.disabled(robot.scanning || robot.connecting)
            }
            .navigationTitle("Connect robot")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showDevices = false; robot.stopScan() } } }
        }.onDisappear { robot.stopScan() }
    }

    private var setupSheet: some View {
        NavigationStack {
            Form {
                Section("Direction calibration") {
                    Toggle("Reverse arm direction", isOn: $robot.reverseArm)
                    Toggle("Reverse gripper direction", isOn: $robot.reverseGrip)
                    Text("Use short presses with the mechanism unloaded. Reverse the setting if the labels move the wrong way. Servo limits are set in the Arduino sketch.")
                        .font(.footnote)
                }
                Section("Required robot setup") {
                    Text("Nano + L298N; PWM D5/D6, arm D9, positional gripper D10. Install the included REC_BLE_Robot sketch.")
                    Text("Use BLE UART with FFE0/FFE1 or Nordic UART Service. Module UART baud must match the sketch (9600 by default).")
                    Text("A successful firmware handshake unlocks controls. A BLE link by itself does not confirm the robot is ready.")
                }
                Section("Handbook reminders") {
                    Text("Maximum robot size: 20 × 20 × 20 cm throughout the game. Battery nominal voltage: at most 9 V. A physical power-cutoff switch is required.")
                    Text("Actively carry at most two cubes. Do not push or pull cubes across the field. Place a cube on your team’s placing target before entering Center Zone.")
                    Text("The app cannot verify cube placement, robot dimensions or field position. Follow the referee’s instructions.")
                }
            }.navigationTitle("Setup")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showSetup = false } } }
        }.onAppear { robot.emergencyStop() }
    }
}

private struct HoldControl: View {
    let title: String
    let icon: String
    let enabled: Bool
    let begin: () -> Void
    let end: () -> Void
    @State private var pressed = false
    @GestureState private var contact = false

    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 7) {
                Image(systemName: icon).font(.title2.weight(.semibold))
                Text(title).font(.caption.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.75)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .foregroundStyle(enabled ? (pressed ? Color.black : accent) : .gray)
            .background(pressed && enabled ? accent : Color.white.opacity(enabled ? 0.06 : 0.025), in: RoundedRectangle(cornerRadius: 15))
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($contact) { _, active, _ in active = true }
                .onChanged { value in
                    let inside = CGRect(origin: .zero, size: geometry.size).contains(value.location)
                    if enabled && inside && !pressed { pressed = true; begin() }
                    else if !inside && pressed { pressed = false; end() }
                }
                .onEnded { _ in if pressed { pressed = false; end() } })
        }.frame(minHeight: 76, maxHeight: 76)
        // GestureState also resets when a parent scroll view cancels the touch.
        .onChange(of: contact) { if !$0 && pressed { pressed = false; end() } }
        .onChange(of: enabled) { if !$0 && pressed { pressed = false; end() } }
        .onDisappear { if pressed { pressed = false; end() } }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityHint("Press and hold to move; release to stop. With VoiceOver, activate for a short movement pulse.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            guard enabled else { return }
            begin()
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { end() }
        }
    }
}

private extension View {
    func panel() -> some View { padding(16).background(surface, in: RoundedRectangle(cornerRadius: 20)) }
}
