"""Renders a rough preview of the page: python3 tools/preview.py dark|light -> preview-<theme>.png"""
import sys
import textwrap
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

PAGE = Path(__file__).resolve().parent.parent
theme = sys.argv[1] if len(sys.argv) > 1 else 'dark'
bg = (39, 41, 46) if theme == 'dark' else (255, 255, 255)
fg = (220, 222, 226) if theme == 'dark' else (40, 40, 44)
W, pad = 860, 24
try:
    body = ImageFont.truetype('NotoSans-Regular.ttf', 17)
except OSError:
    body = ImageFont.load_default()

# Images and paragraphs in page order, taken from description.md.
blocks = []
for line in (PAGE / 'description.md').read_text().splitlines():
    if line.startswith('!['):
        name = line.split('({')[1].split('})')[0]
        ext = '.png' if name.startswith(('h-', 'hero')) else '.jpg'
        blocks.append(('img', PAGE / 'images' / (name + ext)))
    elif line.startswith('## '):
        break
    elif line.strip() and not line.startswith('<br>'):
        blocks.append(('txt', textwrap.wrap(line, 88)))
    elif line.startswith('<br>'):
        blocks.append(('gap', 30))

parts = []
for kind, val in blocks:
    if kind == 'img':
        im = Image.open(val).convert('RGBA')
        maxw = W - 2 * pad
        if im.width > maxw:
            im = im.resize((maxw, round(im.height * maxw / im.width)), Image.LANCZOS)
        elif val.name.startswith('h-'):
            im = im.resize((im.width * 2 // 3, im.height * 2 // 3), Image.LANCZOS)
        parts.append(('img', im))
    else:
        parts.append((kind, val))

height = pad + sum(v.height + 14 if k == 'img' else len(v) * 24 + 12 if k == 'txt' else v for k, v in parts)
page = Image.new('RGBA', (W, height + pad), bg)
draw = ImageDraw.Draw(page)
y = pad
for k, v in parts:
    if k == 'img':
        page.alpha_composite(v, (pad, y))
        y += v.height + 14
    elif k == 'txt':
        for line in v:
            draw.text((pad, y), line, font=body, fill=fg)
            y += 24
        y += 12
    else:
        y += v
page.convert('RGB').save(PAGE / f'preview-{theme}.png')
