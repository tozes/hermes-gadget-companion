#!/usr/bin/env python3
"""Draw the Android launcher icon from the gadget's own mascot bitmap.

    python3 scripts/make-android-icon.py ~/dev/hermes-gadget-sdk-ble

The same art as ios/AppIcon.png (scripts/make-icon.py): the 1-bit idle mascot
from firmware/core/src/mascot_data.cpp, pixel for pixel at a whole-number
scale. Writes an adaptive icon's layers into android/app/src/main/res, for
every density: ic_launcher_foreground.png (the mascot in the gadget's white,
on transparent) and ic_launcher_monochrome.png (the same shape, for themed
icons). The black background layer is a colour resource. Every ink pixel
stays inside the 66 dp circle Android keeps unmasked. Needs Pillow.
"""

import math
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"
INK = (232, 238, 242, 255)  # ui.cpp kText
MONOCHROME = (255, 255, 255, 255)
LAYER_DP = 108
SAFE_DIAMETER_DP = 66
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
SIZES = (192, 144, 96, 64)


def bitmap(src: str, size: int) -> list[list[bool]]:
    match = re.search(rf"const uint8_t kIdle{size}\[(\d+)\] = \{{([^}}]*)\}};", src)
    if not match:
        sys.exit(f"kIdle{size} not found")
    raw = bytes(int(b, 16) for b in re.findall(r"0x([0-9a-fA-F]{2})", match.group(2)))
    stride = (size + 7) // 8
    return [[bool(raw[y * stride + x // 8] >> (7 - x % 8) & 1) for x in range(size)] for y in range(size)]


def cropped(art: list[list[bool]]) -> list[list[bool]]:
    """The art cut to its ink, as make-icon.py centres it."""
    rows = [y for y, row in enumerate(art) if any(row)]
    cols = [x for x in range(len(art)) if any(row[x] for row in art)]
    return [row[cols[0] : cols[-1] + 1] for row in art[rows[0] : rows[-1] + 1]]


def fits(art: list[list[bool]], scale: int, radius: float) -> bool:
    """Whether every ink pixel, drawn at `scale` and centred, lies inside `radius`."""
    height, width = len(art), len(art[0])
    cx, cy = width * scale / 2, height * scale / 2
    for y, row in enumerate(art):
        for x, ink in enumerate(row):
            if not ink:
                continue
            # The pixel's farthest corner from the centre.
            dx = max(abs(x * scale - cx), abs((x + 1) * scale - cx))
            dy = max(abs(y * scale - cy), abs((y + 1) * scale - cy))
            if math.hypot(dx, dy) > radius:
                return False
    return True


def draw(art: list[list[bool]], scale: int, colour: tuple[int, int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (len(art[0]) * scale, len(art) * scale), (0, 0, 0, 0))
    pixels = image.load()
    for y, row in enumerate(art):
        for x, ink in enumerate(row):
            if ink:
                for dy in range(scale):
                    for dx in range(scale):
                        pixels[x * scale + dx, y * scale + dy] = colour
    return image


def layer(src: str, side: int, radius: float, colour: tuple[int, int, int, int]) -> Image.Image:
    """The mascot centred on a transparent `side` square, as large as fits at a whole-number scale."""
    art = None
    for size in SIZES:
        candidate = cropped(bitmap(src, size))
        scale = int(radius * 2 / len(candidate)) + 1
        while scale >= 1 and not fits(candidate, scale, radius):
            scale -= 1
        if scale >= 1:
            art = draw(candidate, scale, colour)
            break
    if art is None:
        # The smallest densities: even the 64 px art is too big at 1:1, so
        # the 192 px art is scaled down smoothly instead.
        candidate = cropped(bitmap(src, SIZES[0]))
        ratio = 1.0
        while not fits(candidate, 1, radius / ratio):
            ratio *= 0.98
        full = draw(candidate, 1, colour)
        art = full.resize((max(1, round(full.width * ratio)), max(1, round(full.height * ratio))), Image.LANCZOS)
    image = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    image.alpha_composite(art, ((side - art.width) // 2, (side - art.height) // 2))
    return image


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = (Path(sys.argv[1]).expanduser() / "firmware/core/src/mascot_data.cpp").read_text()
    for density, factor in DENSITIES.items():
        side = round(LAYER_DP * factor)
        radius = SAFE_DIAMETER_DP * factor / 2
        out = RES / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        for name, colour in (("ic_launcher_foreground", INK), ("ic_launcher_monochrome", MONOCHROME)):
            path = out / f"{name}.png"
            layer(src, side, radius, colour).save(path, optimize=True)
            print(f"wrote {path}")


if __name__ == "__main__":
    main()
