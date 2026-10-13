#!/usr/bin/env python3
"""Draw the app icon from the gadget's own mascot bitmap.

    python3 scripts/make-icon.py ~/dev/hermes-gadget-sdk-ble

Writes AppIcon.png (1024x1024, opaque): the 1-bit idle mascot from
firmware/core/src/mascot_data.cpp in white, pixel for pixel at a whole-number
scale, centred on black. Needs Pillow.
"""

import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SIZE = 1024
BACKGROUND = (0, 0, 0)
INK = (232, 238, 242)  # ui.cpp kText


def bitmap(sdk: Path, size: int) -> list[list[bool]]:
    src = (sdk / "firmware/core/src/mascot_data.cpp").read_text()
    match = re.search(rf"const uint8_t kIdle{size}\[(\d+)\] = \{{([^}}]*)\}};", src)
    if not match:
        sys.exit(f"kIdle{size} not found")
    raw = bytes(int(b, 16) for b in re.findall(r"0x([0-9a-fA-F]{2})", match.group(2)))
    stride = (size + 7) // 8
    return [[bool(raw[y * stride + x // 8] >> (7 - x % 8) & 1) for x in range(size)] for y in range(size)]


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    art = bitmap(Path(sys.argv[1]).expanduser(), 192)
    rows = [y for y, row in enumerate(art) if any(row)]
    cols = [x for x in range(192) if any(row[x] for row in art)]
    top, bottom, left, right = rows[0], rows[-1], cols[0], cols[-1]
    width, height = right - left + 1, bottom - top + 1

    scale = 4
    icon = Image.new("RGB", (SIZE, SIZE), BACKGROUND)
    draw = ImageDraw.Draw(icon)
    x0 = (SIZE - width * scale) // 2
    y0 = (SIZE - height * scale) // 2
    for y in range(height):
        for x in range(width):
            if art[top + y][left + x]:
                px, py = x0 + x * scale, y0 + y * scale
                draw.rectangle((px, py, px + scale - 1, py + scale - 1), fill=INK)

    out = ROOT / "AppIcon.png"
    icon.save(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
