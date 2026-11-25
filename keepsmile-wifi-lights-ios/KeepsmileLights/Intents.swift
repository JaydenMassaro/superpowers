import AppIntents

struct TurnOnLightsIntent: AppIntent {
    static var title: LocalizedStringResource = "Turn On Keepsmile Lights"
    static var description = IntentDescription("Turns your Keepsmile light on over Bluetooth.")
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        try await LightStore.send(on: true)
        return .result()
    }
}

struct TurnOffLightsIntent: AppIntent {
    static var title: LocalizedStringResource = "Turn Off Keepsmile Lights"
    static var description = IntentDescription("Turns your Keepsmile light off over Bluetooth.")
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult {
        try await LightStore.send(on: false)
        return .result()
    }
}

struct KeepsmileShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: TurnOnLightsIntent(),
                    phrases: ["Turn on lights with \(.applicationName)"],
                    shortTitle: "Lights On", systemImageName: "lightbulb.fill")
        AppShortcut(intent: TurnOffLightsIntent(),
                    phrases: ["Turn off lights with \(.applicationName)"],
                    shortTitle: "Lights Off", systemImageName: "lightbulb")
    }
}
