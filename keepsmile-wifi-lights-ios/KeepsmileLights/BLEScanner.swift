import CoreBluetooth

final class BLEScanner: NSObject, ObservableObject, CBCentralManagerDelegate {
    struct Found: Identifiable {
        let id: UUID
        let name: String
        var rssi: Int
    }

    @Published var found: [Found] = []
    @Published var scanning = false
    @Published var message = ""
    private var central: CBCentralManager!

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    func start() {
        guard central.state == .poweredOn else {
            message = "Turn Bluetooth on and allow access for this app."
            return
        }
        found = []
        scanning = true
        message = "Scanning 8 seconds. Close the Keepsmile app first (it can hold the light's only connection)."
        central.scanForPeripherals(withServices: nil)
        DispatchQueue.main.asyncAfter(deadline: .now() + 8) { [weak self] in self?.stop() }
    }

    func stop() {
        guard scanning else { return }
        central.stopScan()
        scanning = false
        message = found.isEmpty ? "No named Bluetooth devices found." : "Pick your light (strongest signal first)."
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {}

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard let name = peripheral.name ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String else { return }
        if let i = found.firstIndex(where: { $0.id == peripheral.identifier }) {
            found[i].rssi = RSSI.intValue
        } else {
            found.append(Found(id: peripheral.identifier, name: name, rssi: RSSI.intValue))
        }
        found.sort { $0.rssi > $1.rssi }
    }
}
