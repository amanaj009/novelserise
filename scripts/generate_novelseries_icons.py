"""Generate Android launcher and splash assets from the approved icon artwork."""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "design" / "novelseries-app-icon-v3-monogram.png"
RES = ROOT / "src" / "main" / "res"

LAUNCHER_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# Android scales adaptive-icon foreground layers beyond the final mask. Keep
# the complete monogram and spark comfortably inside the 66dp safe zone.
ADAPTIVE_MARK_SCALE = 0.78


def foreground_from_composite(source: Image.Image) -> Image.Image:
    """Extract the ivory N and gold spark from the plum/magenta background."""
    rgb = source.convert("RGB")
    pixels = rgb.load()
    alpha = Image.new("L", rgb.size, 0)
    alpha_pixels = alpha.load()

    for y in range(rgb.height):
        for x in range(rgb.width):
            red, green, blue = pixels[x, y]

            # Ivory pixels have a high minimum channel value. Gold pixels are
            # warm, with green comfortably above the magenta background.
            ivory = max(0.0, min(1.0, (min(red, green, blue) - 125) / 70.0))
            gold_green = max(0.0, min(1.0, (green - 65) / 75.0))
            gold_warmth = max(0.0, min(1.0, (red - blue - 30) / 85.0))
            gold = gold_green * gold_warmth
            alpha_pixels[x, y] = round(255 * max(ivory, gold))

    # Smooth the chroma-keyed edge without softening the mark itself.
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.45))
    result = source.convert("RGBA")
    result.putalpha(alpha)
    return result


def resize(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.Resampling.LANCZOS)


def adaptive_foreground(image: Image.Image, size: int) -> Image.Image:
    mark_size = round(size * ADAPTIVE_MARK_SCALE)
    mark = resize(image, mark_size)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    offset = ((size - mark_size) // 2, (size - mark_size) // 2)
    canvas.alpha_composite(mark, offset)
    return canvas


def round_icon(image: Image.Image, size: int) -> Image.Image:
    result = resize(image, size).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    result.putalpha(ImageChops.multiply(result.getchannel("A"), mask))
    return result


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    foreground = foreground_from_composite(source)

    for density, size in LAUNCHER_SIZES.items():
        directory = RES / f"mipmap-{density}"
        directory.mkdir(parents=True, exist_ok=True)
        resize(source, size).save(
            directory / "ic_launcher.webp", "WEBP", quality=96, method=6
        )
        round_icon(source, size).save(
            directory / "ic_launcher_round.webp",
            "WEBP",
            lossless=True,
            method=6,
        )

    for density, size in FOREGROUND_SIZES.items():
        directory = RES / f"mipmap-{density}"
        adaptive_foreground(foreground, size).save(
            directory / "ic_launcher_foreground.webp",
            "WEBP",
            lossless=True,
            method=6,
        )

    drawable_nodpi = RES / "drawable-nodpi"
    drawable_nodpi.mkdir(parents=True, exist_ok=True)
    resize(foreground, 768).save(
        drawable_nodpi / "logo_novelseries_v2.png", "PNG", optimize=True
    )


if __name__ == "__main__":
    main()
