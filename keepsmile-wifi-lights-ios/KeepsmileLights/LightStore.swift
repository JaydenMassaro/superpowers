import Foundation

enum LightError: LocalizedError {
    case notConfigured, badCommand, bluetoothOff, bluetoothDenied, notFound, noCharacteristic, timeout
    case failed(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "No light selected. Open the Keepsmile Lights app and pick your light."
        case .badCommand: return "The command isn't valid hex, e.g. 7E 00 04 F0 00 01 FF 00 EF."
        case .bluetoothOff: return "Bluetooth is off."
        case .bluetoothDenied: return "Bluetooth access is denied for this app (Settings > Keepsmile Lights)."
        case .notFound: return "Couldn't find the saved light. Re-select it in the app."
        case .noCharacteristic: return "No writable Bluetooth characteristic found on the light."
        case .timeout: return "Timed out talking to the light. Is it powered on and in range?"
        case .failed(let m): return m
        }
    }
}

/// Parses "7E 00 04 ..." / "0x7e,0x00" / "7e0004" into bytes; nil if invalid.
func parseHex(_ s: String) -> Data? {
    let cleaned = s.replacingOccurrences(of: "0x", with: "", options: .caseInsensitive)
        .filter { $0.isHexDigit }
    guard !cleaned.isEmpty, cleaned.count % 2 == 0 else { return nil }
    var out = Data()
    var idx = cleaned.startIndex
    while idx < cleaned.endIndex {
        let next = cleaned.index(idx, offsetBy: 2)
        guard let b = UInt8(cleaned[idx..<next], radix: 16) else { return nil }
        out.append(b)
        idx = next
    }
    return out
}

enum LightStore {
    static let idKey = "lightID"
    static let nameKey = "lightName"
    static let onKey = "onHex"
    static let offKey = "offHex"
    // ELK-BLEDOM style frames: common on cheap BLE LED strips. Editable in the app.
    static let defaultOn = "7E 00 04 F0 00 01 FF 00 EF"
    static let defaultOff = "7E 00 04 00 00 00 FF 00 EF"

    /// Used by the app's test buttons and by the Shortcuts actions.
    static func send(on: Bool) async throws {
        let d = UserDefaults.standard
        guard let s = d.string(forKey: idKey), let id = UUID(uuidString: s) else { throw LightError.notConfigured }
        let hex = d.string(forKey: on ? onKey : offKey) ?? (on ? defaultOn : defaultOff)
        guard let data = parseHex(hex) else { throw LightError.badCommand }

        var last: Error = LightError.timeout
        for attempt in 1...3 {
            do { try await BLESender.send(to: id, payload: data); return }
            catch {
                last = error
                if attempt < 3 { try? await Task.sleep(nanoseconds: 1_000_000_000) }
            }
        }
        throw last
    }
}
