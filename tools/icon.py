#!/usr/bin/env python3
"""Render docs/modrinth/icon.png: a villager on the round blueprint badge Create's add-ons share.

The palette is sampled from Create's own Modrinth icon: outer band, pale ring, blueprint blue, white
grid at half alpha. The sprite defaults to docs/modrinth/placeholder-customer.png, a 16x16 villager
standing at a table-cloth shop's edge. Unlike the sibling add-ons, this mod adds no block or item of
its own (00-context.md: "no new screen, no new block, no new item"), so the placeholder is the
icon's final subject, not a stand-in to be swapped later; pass a path as the first argument to
render from a different sprite regardless. Scaled without smoothing, with a one-pixel white outline
and a soft shadow. Requires Pillow.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

SIZE = 512
CENTRE = SIZE // 2
SUPERSAMPLE = 4
RIM = (181, 197, 217, 255)
BAND = (60, 118, 168, 255)
RING = (190, 214, 235, 255)
BLUEPRINT = (82, 150, 209, 255)
GRID = (255, 255, 255, 120)
OUTLINE = (255, 255, 255, 235)
SHADOW = (20, 50, 90, 130)
SPRITE = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("docs/modrinth/placeholder-customer.png")
OUT = Path("docs/modrinth/icon.png")


def badge() -> Image.Image:
    big = SIZE * SUPERSAMPLE
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for radius, colour in ((256, RIM), (253, BAND), (224, RING), (198, BLUEPRINT)):
        r = radius * SUPERSAMPLE
        c = CENTRE * SUPERSAMPLE
        draw.ellipse((c - r, c - r, c + r, c + r), fill=colour)
    img = img.resize((SIZE, SIZE), Image.LANCZOS)

    grid = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    g = ImageDraw.Draw(grid)
    for k in range(-4, 5):
        p = CENTRE + k * 48
        g.line((p, 0, p, SIZE), fill=GRID, width=3)
        g.line((0, p, SIZE, p), fill=GRID, width=3)
    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((CENTRE - 120, CENTRE - 120, CENTRE + 120, CENTRE + 120), fill=(140, 190, 235, 110))
    glow = glow.filter(ImageFilter.GaussianBlur(50))
    inner = Image.alpha_composite(glow, grid)
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).ellipse((CENTRE - 196, CENTRE - 196, CENTRE + 196, CENTRE + 196), fill=255)
    clipped = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    clipped.paste(inner, (0, 0), mask)
    return Image.alpha_composite(img, clipped)


def subject(img: Image.Image) -> Image.Image:
    scale = 17
    size = 16 * scale
    sprite = Image.open(SPRITE).convert("RGBA").resize((size, size), Image.NEAREST)
    alpha = sprite.getchannel("A")
    x = CENTRE - size // 2
    y = CENTRE - size // 2
    grown = Image.new("L", (SIZE, SIZE), 0)
    for dx in (-scale, 0, scale):
        for dy in (-scale, 0, scale):
            grown.paste(alpha, (x + dx, y + dy), alpha)
    shadow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    shadow.paste(SHADOW, (0, 0), grown.transform(grown.size, Image.AFFINE, (1, 0, -14, 0, 1, -14)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(10))
    outline = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    outline.paste(OUTLINE, (0, 0), grown)
    img = Image.alpha_composite(img, shadow)
    img = Image.alpha_composite(img, outline)
    img.alpha_composite(sprite, (x, y))
    return img


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    subject(badge()).save(OUT, optimize=True)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
