package com.tozes.hermescompanion.core

/**
 * The gadget's 5x7 bitmap font, ported from the firmware (`font5x7.cpp`).
 *
 * Metrics are in font pixels; multiply by the integer scale to get pixels on
 * screen.
 */
object PixelFont {
    const val GLYPH_WIDTH = 5
    const val GLYPH_HEIGHT = 7

    /** Advance and line pitch: the glyph plus spacing. */
    const val CELL_WIDTH = 6
    const val CELL_HEIGHT = 9

    /** Seven row masks per glyph; bit 4 is the leftmost pixel. */
    private val glyphs: List<IntArray> by lazy {
        PixelFontGlyphs.table.map { entry ->
            entry.split(' ').map { row -> row.fold(0) { mask, cell -> (mask shl 1) or (if (cell == '#') 1 else 0) } }.toIntArray()
        }
    }

    /** Replaces typographic characters with the ASCII the font has. */
    fun normalize(text: String): String = buildString {
        for (ch in text) {
            when (ch) {
                '‘', '’' -> append('\'')
                '“', '”' -> append('"')
                '–', '—', '−' -> append('-')
                '…' -> append("...")
                '→' -> append('>')
                '·', '•' -> append('.')
                ' ' -> append(' ')
                else -> append(ch)
            }
        }
    }

    /** Whether every character has a glyph (printable ASCII). */
    fun canRender(text: String): Boolean = text.all { it in ' '..'~' }

    /** Width in font pixels of [count] characters on one line. */
    fun width(columns: Int): Int = if (columns > 0) columns * CELL_WIDTH - 1 else 0

    /** Height in font pixels of [lines] lines. */
    fun height(lines: Int): Int = if (lines > 0) lines * CELL_HEIGHT - (CELL_HEIGHT - GLYPH_HEIGHT) else 0

    /** How many characters fit in [pixels] at [scale]. */
    fun columns(pixels: Float, scale: Float): Int = maxOf(1, ((pixels + scale) / (CELL_WIDTH * scale)).toInt())

    /** Shortens a line to [columns], ending in ".." like the firmware's `fit()`. */
    fun fit(text: String, columns: Int): String = when {
        text.length <= columns -> text
        columns <= 2 -> text.take(columns)
        else -> text.take(columns - 2) + ".."
    }

    /** Greedy word wrap to [columns]; words longer than a line are split. */
    fun wrap(text: String, columns: Int): List<String> {
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            var line = ""
            for (piece in paragraph.split(' ')) {
                if (piece.isEmpty()) continue
                var word = piece
                while (word.length > columns) {
                    if (line.isNotEmpty()) {
                        lines += line
                        line = ""
                    }
                    lines += word.take(columns)
                    word = word.drop(columns)
                }
                if (line.isEmpty()) {
                    line = word
                } else if (line.length + 1 + word.length <= columns) {
                    line += " $word"
                } else {
                    lines += line
                    line = word
                }
            }
            lines += line
        }
        return lines
    }

    /** The lines to draw in [widthPixels] at [scale]: wrapped, or each fitted to the width. */
    fun lines(text: String, widthPixels: Float?, scale: Float, wraps: Boolean): List<String> {
        if (widthPixels == null || !widthPixels.isFinite()) return text.split('\n')
        val columns = columns(widthPixels, scale)
        return if (wraps) wrap(text, columns) else text.split('\n').map { fit(it, columns) }
    }

    /**
     * Calls [rect] for each horizontal run of ink in [lines], in font-pixel
     * units, each line aligned within [width] pixels.
     */
    inline fun forEachRun(lines: List<String>, width: Int, centered: Boolean, rect: (x: Int, y: Int, w: Int) -> Unit) {
        for ((row, line) in lines.withIndex()) {
            var x = if (centered) (width - PixelFont.width(line.length)) / 2 else 0
            val y = row * CELL_HEIGHT
            for (ch in line) {
                val rows = glyph(ch)
                for (dy in rows.indices) {
                    val mask = rows[dy]
                    if (mask == 0) continue
                    var col = 0
                    while (col < GLYPH_WIDTH) {
                        if (mask and (0x10 shr col) == 0) {
                            col += 1
                            continue
                        }
                        val start = col
                        while (col < GLYPH_WIDTH && mask and (0x10 shr col) != 0) col += 1
                        rect(x + start, y + dy, col - start)
                    }
                }
                x += CELL_WIDTH
            }
        }
    }

    /** The row masks of [ch], or of `?` when the font has no glyph for it. */
    fun glyph(ch: Char): IntArray = glyphs[if (ch in ' '..'~') ch - ' ' else '?' - ' ']
}
