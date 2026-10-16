"""Renders .github/social-preview.png (GitHub's link preview image, set in the repo settings)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

PAGE = Path(__file__).resolve().parent.parent
REPO = PAGE.parents[1]
FONTS = '/usr/share/fonts/noto/NotoSans-'

W, H = 1280, 640
img = Image.new('RGBA', (W, H), (22, 24, 29, 255))

glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse((90, 120, 530, 560), fill=(255, 153, 32, 60))
img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(90)))

icon = Image.open(PAGE / 'images/icon-24.png').resize((336, 336), Image.NEAREST)
img.alpha_composite(icon, (142, 152))

d = ImageDraw.Draw(img)
title = ImageFont.truetype(FONTS + 'Bold.ttf', 120)
body = ImageFont.truetype(FONTS + 'Regular.ttf', 38)
chip = ImageFont.truetype(FONTS + 'SemiBold.ttf', 26)

x = 560
d.text((x, 150), 'Steamed', font=title, fill=(244, 246, 248))
d.text((x, 315), 'Host your Minecraft world through', font=body, fill=(196, 202, 210))
d.text((x, 362), 'a Steam lobby. No port forwarding.', font=body, fill=(196, 202, 210))

cx = x
for label, col in [('Minecraft 26.2', (98, 180, 122)), ('Fabric', (219, 208, 180)), ('NeoForge', (215, 116, 47))]:
    w = d.textlength(label, font=chip)
    d.rounded_rectangle((cx, 448, cx + w + 36, 496), radius=24, outline=col, width=3)
    d.text((cx + 18, 455), label, font=chip, fill=col)
    cx += w + 36 + 16

img.convert('RGB').save(REPO / '.github/social-preview.png', optimize=True)
