package com.tozes.hermescompanion.core

import java.util.Base64
import kotlin.math.floor

enum class MascotFrame { IDLE, BLINK, TALK }

/** One generated bitmap, as stored in [MascotFrames]. */
internal class MascotBitmapSource(val size: Int, val frame: MascotFrame, val base64: String)

/** A horizontal run of ink, one unit per art pixel. */
data class MascotRun(val x: Int, val y: Int, val width: Int)

/** A decoded 1-bit mascot frame: its ink as horizontal runs. */
class MascotBitmap internal constructor(source: MascotBitmapSource) {
    val size: Int = source.size
    val frame: MascotFrame = source.frame
    val runs: List<MascotRun>

    init {
        val bytes = runCatching { Base64.getDecoder().decode(source.base64) }.getOrDefault(ByteArray(0))
        val stride = (size + 7) / 8
        val runs = mutableListOf<MascotRun>()
        if (bytes.size >= stride * size) {
            for (y in 0 until size) {
                var x = 0
                while (x < size) {
                    if (!ink(bytes, stride, x, y)) {
                        x += 1
                        continue
                    }
                    val start = x
                    while (x < size && ink(bytes, stride, x, y)) x += 1
                    runs += MascotRun(start, y, x - start)
                }
            }
        }
        this.runs = runs
    }

    private fun ink(bytes: ByteArray, stride: Int, x: Int, y: Int): Boolean =
        (bytes[y * stride + x / 8].toInt() shr (7 - x % 8)) and 1 == 1
}

/** A bitmap and how big each of its pixels is drawn. */
data class MascotPick(val bitmap: MascotBitmap, val pixel: Float)

/** The Hermes Agent mascot frames from the firmware (64, 96, 144 and 192 px). */
object Mascot {
    val bitmaps: List<MascotBitmap> by lazy { MascotFrames.encoded.map(::MascotBitmap) }

    /**
     * A bitmap and a whole number of device pixels per art pixel for [side]
     * (in units of [displayScale] device pixels): the most detailed bitmap that
     * still fills at least three quarters of what the best-filling one would.
     * Falls back to the smallest bitmap, scaled down, when none fits at 1:1.
     */
    fun pick(frame: MascotFrame, side: Float, displayScale: Float): MascotPick? {
        val candidates = bitmaps.filter { it.frame == frame }
        val devicePixels = side * displayScale
        val fits = candidates.mapNotNull { bitmap ->
            val factor = floor(devicePixels / bitmap.size)
            if (factor >= 1) bitmap to factor else null
        }
        val best = fits.maxOfOrNull { (bitmap, factor) -> bitmap.size * factor }
        if (best != null) {
            val choice = fits.filter { (bitmap, factor) -> bitmap.size * factor >= best * 0.75f }.maxBy { it.first.size }
            return MascotPick(choice.first, choice.second / displayScale)
        }
        val smallest = candidates.minByOrNull { it.size } ?: return null
        return MascotPick(smallest, side / smallest.size)
    }
}
