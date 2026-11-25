import SwiftUI

struct ContentView: View {
    @AppStorage(LightStore.idKey) private var lightID = ""
    @AppStorage(LightStore.nameKey) private var lightName = ""
    @AppStorage(LightStore.onKey) private var onHex = LightStore.defaultOn
    @AppStorage(LightStore.offKey) private var offHex = LightStore.defaultOff
    @StateObject private var scanner = BLEScanner()
    @State private var status = ""

    var body: some View {
        NavigationView {
            Form {
                Section("1. Your light") {
                    Text(lightID.isEmpty ? "No light selected" : "\(lightName) selected")
                    Button(scanner.scanning ? "Scanning…" : "Find light (scan Bluetooth)") { scanner.start() }
                        .disabled(scanner.scanning)
                    if !scanner.message.isEmpty { Text(scanner.message).font(.footnote) }
                    ForEach(scanner.found) { d in
                        Button("\(d.name)   \(d.rssi) dBm") {
                            lightID = d.id.uuidString
                            lightName = d.name
                            scanner.found = []
                            scanner.message = ""
                        }
                    }
                }

                Section("2. Commands (hex). Defaults suit many BLE LED strips.") {
                    TextField("ON command", text: $onHex).autocorrectionDisabled().textInputAutocapitalization(.characters)
                    TextField("OFF command", text: $offHex).autocorrectionDisabled().textInputAutocapitalization(.characters)
                    HStack {
                        Button("Test ON") { test(true) }.buttonStyle(.bordered)
                        Button("Test OFF") { test(false) }.buttonStyle(.bordered)
                    }
                    if !status.isEmpty { Text(status).font(.footnote) }
                }

                Section("3. Make it automatic") {
                    Text("""
                    Open the Shortcuts app → Automation → + → Wi-Fi.
                    Choose your network, turn on "Is Connected", tap Next.
                    Add action: "Turn On Keepsmile Lights" (search Keepsmile).
                    Set "Run Immediately" and turn off "Notify When Run", then Done.
                    """).font(.footnote)
                }
            }
            .navigationTitle("Keepsmile Lights")
        }
    }

    private func test(_ on: Bool) {
        status = "Sending…"
        Task {
            do {
                try await LightStore.send(on: on)
                status = "Sent. Did the light react?"
            } catch {
                status = "Failed: \(error.localizedDescription)"
            }
        }
    }
}
