import Foundation
import CoreBluetooth
import Combine

struct NearbyRobot: Identifiable {
    let id: UUID
    let name: String
    let rssi: Int
    let peripheral: CBPeripheral
}

/// Every movement command is a short lease. The matching firmware expires it
/// after 600 ms unless it receives another command for that actuator.
final class RobotBluetooth: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    enum Group: CaseIterable, Hashable { case drive, arm, grip }
    private struct Held { let owner: String; let command: String }
    @Published private(set) var devices: [NearbyRobot] = []
    @Published private(set) var ready = false
    @Published private(set) var scanning = false
    @Published private(set) var connecting = false
    @Published private(set) var status = "Bluetooth starting…"
    @Published private(set) var robotName = "No robot connected"
    @Published private(set) var telemetry = "Waiting for robot status"
    @Published private(set) var lastSent = "—"
    @Published private(set) var acknowledged = false
    @Published var reverseArm = false
    @Published var reverseGrip = false

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var tx: CBCharacteristic?
    private var held: [Group: Held] = [:]
    private var tick: Timer?
    private var scanTimeout: Timer?
    private var connectTimeout: Timer?
    private var rxBuffer = ""
    private var pending: [String] = []
    private var writeInFlight = false
    private var querying = false
    private var notificationsReady = false
    private var lastFeedback = Date.distantPast

    private let hmService = CBUUID(string: "FFE0")
    private let hmUART = CBUUID(string: "FFE1")
    private let nusService = CBUUID(string: "6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private let nusRX = CBUUID(string: "6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private let nusTX = CBUUID(string: "6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
        let timer = Timer(timeInterval: 0.12, repeats: true) { [weak self] _ in
            guard let self, self.ready else { return }
            if Date().timeIntervalSince(self.lastFeedback) > 3 {
                self.disconnect()
                self.status = "Robot feedback timed out. Check module UART wiring and power."
                return
            }
            for group in Group.allCases {
                if let action = self.held[group] { self.send(action.command) }
            }
        }
        RunLoop.main.add(timer, forMode: .common)
        tick = timer
    }

    func scan() {
        guard central.state == .poweredOn, peripheral == nil else { return }
        devices = []
        scanning = true
        status = "Searching for BLE UART robots…"
        central.scanForPeripherals(withServices: [hmService, nusService], options: nil)
        scanTimeout?.invalidate()
        scanTimeout = Timer.scheduledTimer(withTimeInterval: 12, repeats: false) { [weak self] _ in
            guard let self else { return }
            self.stopScan()
            self.status = self.devices.isEmpty ? "No BLE UART found. HC-05 is not compatible." : "Select your robot"
        }
    }

    func stopScan() {
        central.stopScan()
        scanTimeout?.invalidate()
        scanning = false
    }

    func connect(_ device: NearbyRobot) {
        guard central.state == .poweredOn, peripheral == nil else { return }
        stopScan()
        ready = false
        acknowledged = false
        notificationsReady = false
        querying = false
        tx = nil
        rxBuffer = ""
        pending = []
        writeInFlight = false
        peripheral = device.peripheral
        device.peripheral.delegate = self
        robotName = device.name
        connecting = true
        status = "Connecting to \(device.name)…"
        central.connect(device.peripheral, options: nil)
        connectTimeout?.invalidate()
        connectTimeout = Timer.scheduledTimer(withTimeInterval: 12, repeats: false) { [weak self] _ in
            guard let self, !self.ready else { return }
            self.disconnect()
            self.status = "Connection timed out. Check module, power and UART profile."
        }
    }

    func disconnect() {
        emergencyStop()
        stopScan()
        connectTimeout?.invalidate()
        if let peripheral { central.cancelPeripheralConnection(peripheral) }
        clearConnection()
        status = "Disconnected — robot has a 600 ms motion watchdog"
    }

    private func clearConnection() {
        held = [:]
        ready = false
        connecting = false
        acknowledged = false
        notificationsReady = false
        querying = false
        tx = nil
        pending = []
        writeInFlight = false
        peripheral = nil
        robotName = "No robot connected"
    }

    func start(_ group: Group, owner: String, command: String) {
        guard ready else { return }
        held[group] = Held(owner: owner, command: command)
        send(command)
    }

    func release(_ group: Group, owner: String) {
        guard held[group]?.owner == owner else { return }
        held.removeValue(forKey: group)
        send(group == .drive ? "S" : group == .arm ? "J" : "K", priority: true)
    }

    func emergencyStop() {
        held = [:]
        pending.removeAll()
        send("!", priority: true)
    }

    func changeSpeed(_ faster: Bool) { send(faster ? "+" : "-") }
    func refreshStatus() { send("P") }

    private func send(_ command: String, priority: Bool = false) {
        guard let peripheral, peripheral.state == .connected, tx != nil else { return }
        // Bound queued commands so a slow link cannot replay stale motion later.
        if priority { pending.removeAll() }
        guard pending.count < 6 else { return }
        pending.append(command)
        pump()
    }

    private func pump() {
        guard let peripheral, let tx, peripheral.state == .connected, !pending.isEmpty else { return }
        let noResponse = tx.properties.contains(.writeWithoutResponse)
        if noResponse {
            guard peripheral.canSendWriteWithoutResponse else { return }
            while !pending.isEmpty && peripheral.canSendWriteWithoutResponse {
                let command = pending.removeFirst()
                peripheral.writeValue(Data(command.utf8), for: tx, type: .withoutResponse)
                lastSent = command
            }
        } else {
            guard !writeInFlight else { return }
            let command = pending.removeFirst()
            writeInFlight = true
            peripheral.writeValue(Data(command.utf8), for: tx, type: .withResponse)
            lastSent = command
        }
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn: status = "Ready to find your BLE robot"
        case .poweredOff: clearConnection(); stopScan(); status = "Turn on Bluetooth in Settings"
        case .unauthorized: clearConnection(); status = "Allow Bluetooth in Settings → REC Remote"
        case .unsupported: clearConnection(); status = "Bluetooth LE is unavailable"
        default: clearConnection(); status = "Bluetooth unavailable"
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? peripheral.name ?? "BLE UART robot"
        let item = NearbyRobot(id: peripheral.identifier, name: name, rssi: RSSI.intValue, peripheral: peripheral)
        if let index = devices.firstIndex(where: { $0.id == item.id }) { devices[index] = item }
        else { devices.append(item) }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard peripheral == self.peripheral else { return }
        status = "Checking BLE UART profile…"
        peripheral.discoverServices([hmService, nusService])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        guard peripheral == self.peripheral else { return }
        connectTimeout?.invalidate()
        clearConnection()
        status = "Connection failed: \(error?.localizedDescription ?? "try again")"
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        guard peripheral == self.peripheral else { return }
        connectTimeout?.invalidate()
        clearConnection()
        status = "Connection lost — robot has a 600 ms motion watchdog"
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard peripheral == self.peripheral else { return }
        guard error == nil, let services = peripheral.services, !services.isEmpty else {
            disconnect(); status = "Unsupported device. Use HM-10 UART or Nordic UART Service."; return
        }
        for service in services { peripheral.discoverCharacteristics(nil, for: service) }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard peripheral == self.peripheral, error == nil else { return }
        for characteristic in service.characteristics ?? [] {
            let writable = characteristic.properties.contains(.write) || characteristic.properties.contains(.writeWithoutResponse)
            let isUART = (service.uuid == hmService && characteristic.uuid == hmUART)
                || (service.uuid == nusService && characteristic.uuid == nusRX)
            if writable && isUART { tx = characteristic }
            let isRX = (service.uuid == hmService && characteristic.uuid == hmUART)
                || (service.uuid == nusService && characteristic.uuid == nusTX)
            if isRX && (characteristic.properties.contains(.notify) || characteristic.properties.contains(.indicate)) {
                peripheral.setNotifyValue(true, for: characteristic)
            }
        }
        beginHandshakeIfPossible()
    }

    private func beginHandshakeIfPossible() {
        guard tx != nil, notificationsReady, !querying else { return }
        querying = true
        status = "BLE linked — checking robot firmware…"
        send("!", priority: true)
        send("P")
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard peripheral == self.peripheral else { return }
        guard error == nil, characteristic.isNotifying else {
            disconnect(); status = "UART notifications failed. Check BLE module profile."; return
        }
        notificationsReady = true
        beginHandshakeIfPossible()
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard peripheral == self.peripheral, error == nil, let data = characteristic.value else { return }
        rxBuffer += String(decoding: data, as: UTF8.self)
        if rxBuffer.count > 2048 { rxBuffer = String(rxBuffer.suffix(1024)) }
        while let end = rxBuffer.firstIndex(of: "\n") {
            let line = String(rxBuffer[..<end]).trimmingCharacters(in: .whitespacesAndNewlines)
            rxBuffer = String(rxBuffer[rxBuffer.index(after: end)...])
            if !line.isEmpty { telemetry = line }
            // A BLE connection alone is not proof that the robot understands commands.
            if line.hasPrefix("REC1 ") && tx != nil {
                lastFeedback = Date()
                acknowledged = true
                ready = true
                connecting = false
                connectTimeout?.invalidate()
                status = "Connected — robot firmware confirmed"
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        guard peripheral == self.peripheral else { return }
        writeInFlight = false
        if let error { disconnect(); status = "Bluetooth write failed: \(error.localizedDescription)" }
        else { pump() }
    }

    func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) {
        guard peripheral == self.peripheral else { return }
        pump()
    }
}
