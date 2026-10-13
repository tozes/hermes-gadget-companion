import Foundation

extension GadgetStatus.Power {
    /// "84%, charging, USB power", or nil when the board doesn't report a battery.
    public var summary: String? {
        guard available != false, batteryPresent != false, let percent = batteryPercent else {
            return externalPower == true ? "USB power, no battery" : nil
        }
        var parts = ["\(percent)%"]
        if charging == true { parts.append("charging") }
        if externalPower == true { parts.append("USB power") } else { parts.append("on battery") }
        return parts.joined(separator: ", ")
    }

    /// The device turns its battery red at this level or below, without USB power.
    static let lowPercent = 10

    /// Low as the device shows it: 10% or less, without USB power.
    public var isLow: Bool {
        (batteryPercent ?? 100) <= Self.lowPercent && externalPower != true
    }

    /// The level and charging state for a battery icon, when there is a battery.
    public var gauge: (percent: Int, charging: Bool, low: Bool)? {
        guard available != false, batteryPresent != false, let percent = batteryPercent else { return nil }
        return (percent, charging == true, isLow)
    }

    public var voltage: String? {
        batteryMv.map { String(format: "%.2f V", Double($0) / 1000) }
    }
}
