from PIL import Image, ImageDraw, ImageFont


def headline(lines, color, font_path, size, out=None, shadow=(0, 0, 0, 120), pad=8):
    """Renders pixel-font headline lines onto a transparent PNG with a drop shadow."""
    font = ImageFont.truetype(str(font_path), size)
    if 'Pixelify' in str(font_path):
        font.set_variation_by_axes([700])
    measure = ImageDraw.Draw(Image.new('RGBA', (1, 1)))
    gap = int(size * 0.12)
    boxes = [measure.textbbox((0, 0), line, font=font) for line in lines]
    w = max(b[2] for b in boxes) + pad * 2 + size // 10
    h = sum(b[3] - min(0, b[1]) for b in boxes) + gap * (len(lines) - 1) + pad * 2 + size // 10
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    y = pad
    off = max(2, size // 14)
    for line, b in zip(lines, boxes):
        draw.text((pad + off, y + off), line, font=font, fill=shadow)
        draw.text((pad, y), line, font=font, fill=color)
        y += b[3] + gap
    if out:
        im.save(out)
    return im
