#!/usr/bin/env python3
"""Render docs/modrinth/icon.png: the vanilla emerald on the cubealgos navy badge Create add-ons
share.

This mod adds no block or item of its own (00-context.md: "no new screen, no new block, no new
item"), so the icon's subject is the game's own emerald item sprite, read straight out of the
Minecraft client jar in the Gradle cache -- never vendored into this repo. The jar is found by
globbing `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/*.jar`
first, then any `~/.gradle/caches/fabric-loom/26.2/**/*.jar` that contains the sprite; pass
`--jar PATH` to use a specific jar instead. The sprite is cropped to its alpha bounding box, then
scaled without smoothing to a 320 px fit box on the navy badge (a white rim, a pale band, a navy
disc `#0d1226` with its edge darkened to `#090c1b`, and a blueprint grid lifted to `#344c80`), with
a one-pixel white outline and a soft shadow scaled to match. Requires Pillow.
"""
import argparse
import io
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

SIZE = 512
CENTRE = SIZE // 2
SUPERSAMPLE = 4
RIM = (255, 255, 255, 255)
BAND = (232, 236, 244, 255)
RING = (9, 12, 27, 255)
BLUEPRINT = (13, 18, 38, 255)
GRID = (52, 76, 128, 255)
OUTLINE = (255, 255, 255, 235)
SHADOW = (20, 50, 90, 130)
SPRITE_ENTRY = "assets/minecraft/textures/item/emerald.png"
OUT = Path("docs/modrinth/icon.png")
BOX = 320

GRADLE_CACHE = Path.home() / ".gradle" / "caches" / "fabric-loom"
PRIMARY_GLOB = GRADLE_CACHE / "minecraftMaven" / "net" / "minecraft" / "minecraft-merged-deobf" / "26.2"
FALLBACK_ROOT = GRADLE_CACHE / "26.2"


def jar_has_sprite(jar: Path) -> bool:
    try:
        with zipfile.ZipFile(jar) as zf:
            return SPRITE_ENTRY in zf.namelist()
    except (OSError, zipfile.BadZipFile):
        return False


def find_jar() -> Path:
    for candidate in sorted(PRIMARY_GLOB.glob("*.jar")):
        if jar_has_sprite(candidate):
            return candidate
    for candidate in sorted(FALLBACK_ROOT.glob("**/*.jar")):
        if jar_has_sprite(candidate):
            return candidate
    raise SystemExit(
        "no Minecraft jar with "
        f"{SPRITE_ENTRY} found under {PRIMARY_GLOB} or {FALLBACK_ROOT}; "
        "run a Gradle build to populate the cache, or pass --jar PATH"
    )


def load_sprite(jar: Path) -> Image.Image:
    with zipfile.ZipFile(jar) as zf:
        data = zf.read(SPRITE_ENTRY)
    raw = Image.open(io.BytesIO(data)).convert("RGBA")
    return raw.crop(raw.getbbox())


def badge() -> Image.Image:
    big = SIZE * SUPERSAMPLE
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for radius, colour in ((256, RIM), (250, BAND), (238, RING), (200, BLUEPRINT)):
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
    ImageDraw.Draw(glow).ellipse((CENTRE - 120, CENTRE - 120, CENTRE + 120, CENTRE + 120), fill=(34, 48, 92, 150))
    glow = glow.filter(ImageFilter.GaussianBlur(50))
    inner = Image.alpha_composite(glow, grid)
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).ellipse((CENTRE - 238, CENTRE - 238, CENTRE + 238, CENTRE + 238), fill=255)
    clipped = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    clipped.paste(inner, (0, 0), mask)
    return Image.alpha_composite(img, clipped)


def subject(img: Image.Image, raw: Image.Image) -> Image.Image:
    w, h = raw.size
    factor = max(1, BOX // max(w, h))
    size = (w * factor, h * factor)
    sprite = raw.resize(size, Image.NEAREST)
    alpha = sprite.getchannel("A")
    x = CENTRE - size[0] // 2
    y = CENTRE - size[1] // 2
    step = factor
    grown = Image.new("L", (SIZE, SIZE), 0)
    for dx in (-step, 0, step):
        for dy in (-step, 0, step):
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, help="Minecraft jar to read the emerald sprite from")
    args = parser.parse_args()

    jar = args.jar if args.jar else find_jar()
    raw = load_sprite(jar)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    subject(badge(), raw).save(OUT, optimize=True)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes) from {jar}")


if __name__ == "__main__":
    main()
