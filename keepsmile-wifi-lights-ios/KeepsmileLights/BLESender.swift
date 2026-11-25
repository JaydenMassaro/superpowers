import CoreBluetooth

/// One-shot: connect to a known light, write one command frame, disconnect.
final class BLESender: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    private let id: UUID
    private let payload: Data
    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var continuation: CheckedContinuation<Void, Error>?
    private var finished = false
    private var pendingServices = 0
    private var candidates: [(service: CBUUID, ch: CBCharacteristic)] = []

    private init(id: UUID, payload: Data) {
        self.id = id
        self.payload = payload
    }

    static func send(to id: UUID, payload: Data) async throws {
        let sender = BLESender(id: id, payload: payload)
        try await sender.run()
    }

    private func run() async throws {
        try await withCheckedThrowingContinuation { (c: CheckedContinuation<Void, Error>) in
            self.continuation = c
            DispatchQueue.main.asyncAfter(deadline: .now() + 12) { [self] in finish(.failure(LightError.timeout)) }
            self.central = CBCentralManager(delegate: self, queue: .main)
        }
    }

    private func finish(_ result: Result<Void, Error>) {
        guard !finished else { return }
        finished = true
        if let p = peripheral { central.cancelPeripheralConnection(p) }
        continuation?.resume(with: result)
        continuation = nil
    }

    // MARK: central

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            guard let p = central.retrievePeripherals(withIdentifiers: [id]).first else {
                return finish(.failure(LightError.notFound))
            }
            peripheral = p
            p.delegate = self
            central.connect(p)
        case .poweredOff: finish(.failure(LightError.bluetoothOff))
        case .unauthorized: finish(.failure(LightError.bluetoothDenied))
        case .unsupported: finish(.failure(LightError.failed("Bluetooth LE isn't supported on this device.")))
        default: break // .unknown / .resetting: wait for the next update
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices(nil)
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        finish(.failure(error ?? LightError.failed("Couldn't connect to the light.")))
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        finish(.failure(error ?? LightError.failed("Disconnected before the command was sent.")))
    }

    // MARK: peripheral

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error { return finish(.failure(error)) }
        let services = peripheral.services ?? []
        pendingServices = services.count
        if services.isEmpty { return finish(.failure(LightError.noCharacteristic)) }
        services.forEach { peripheral.discoverCharacteristics(nil, for: $0) }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        for c in service.characteristics ?? [] where c.properties.contains(.write) || c.properties.contains(.writeWithoutResponse) {
            candidates.append((service.uuid, c))
        }
        pendingServices -= 1
        guard pendingServices == 0 else { return }

        let fff3 = CBUUID(string: "FFF3"), ffe1 = CBUUID(string: "FFE1"), fff0 = CBUUID(string: "FFF0")
        let generic = [CBUUID(string: "1800"), CBUUID(string: "1801")]
        let pick = candidates.first { $0.ch.uuid == fff3 }
            ?? candidates.first { $0.ch.uuid == ffe1 }
            ?? candidates.first { $0.service == fff0 }
            ?? candidates.first { !generic.contains($0.service) }
        guard let ch = pick?.ch else { return finish(.failure(LightError.noCharacteristic)) }

        if ch.properties.contains(.writeWithoutResponse) {
            peripheral.writeValue(payload, for: ch, type: .withoutResponse)
            // No ack for this write type; give it a moment to go out before disconnecting.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [self] in finish(.success(())) }
        } else {
            peripheral.writeValue(payload, for: ch, type: .withResponse)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error { finish(.failure(error)) } else { finish(.success(())) }
    }
}
