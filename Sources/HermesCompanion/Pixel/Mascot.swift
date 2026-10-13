import Foundation

enum MascotFrame: Sendable, Hashable {
    case idle, blink, talk
}

/// One generated bitmap, as stored in `MascotFrames`.
struct MascotBitmapSource: Sendable {
    let size: Int
    let frame: MascotFrame
    let base64: String
}

/// A decoded 1-bit mascot frame: its ink as horizontal runs, one unit per pixel.
struct MascotBitmap: Sendable {
    let size: Int
    let frame: MascotFrame
    let runs: [CGRect]

    init(_ source: MascotBitmapSource) {
        size = source.size
        frame = source.frame
        let bytes = [UInt8](Data(base64Encoded: source.base64) ?? Data())
        let stride = (size + 7) / 8
        var runs: [CGRect] = []
        if bytes.count >= stride * size {
            for y in 0..<size {
                var x = 0
                while x < size {
                    guard Self.ink(bytes, stride: stride, x: x, y: y) else { x += 1; continue }
                    let start = x
                    while x < size, Self.ink(bytes, stride: stride, x: x, y: y) { x += 1 }
                    runs.append(CGRect(x: start, y: y, width: x - start, height: 1))
                }
            }
        }
        self.runs = runs
    }

    private static func ink(_ bytes: [UInt8], stride: Int, x: Int, y: Int) -> Bool {
        (bytes[y * stride + x / 8] >> (7 - UInt8(x % 8))) & 1 == 1
    }
}

/// The Hermes Agent mascot frames from the firmware (64, 96, 144 and 192 px).
enum Mascot {
    static let bitmaps: [MascotBitmap] = MascotFrames.encoded.map(MascotBitmap.init)

    /// A bitmap and a whole number of device pixels per art pixel for `side`
    /// points: the most detailed bitmap that still fills at least three
    /// quarters of what the best-filling one would. Falls back to the
    /// smallest bitmap, scaled down, when none fits at 1:1.
    static func pick(frame: MascotFrame, side: CGFloat, displayScale: CGFloat) -> (bitmap: MascotBitmap, pixel: CGFloat)? {
        let candidates = bitmaps.filter { $0.frame == frame }
        let devicePixels = side * displayScale
        let fits = candidates.compactMap { bitmap -> (bitmap: MascotBitmap, factor: CGFloat)? in
            let factor = (devicePixels / CGFloat(bitmap.size)).rounded(.down)
            return factor >= 1 ? (bitmap, factor) : nil
        }
        let filled = { (fit: (bitmap: MascotBitmap, factor: CGFloat)) in CGFloat(fit.bitmap.size) * fit.factor }
        if let best = fits.map(filled).max(),
           let choice = fits.filter({ filled($0) >= best * 0.75 }).max(by: { $0.bitmap.size < $1.bitmap.size }) {
            return (choice.bitmap, choice.factor / displayScale)
        }
        guard let smallest = candidates.min(by: { $0.size < $1.size }) else { return nil }
        return (smallest, side / CGFloat(smallest.size))
    }
}
