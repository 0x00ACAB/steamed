"""Builds the page images from raw/ screenshots into images/. Run from anywhere: python3 tools/build.py"""
from pathlib import Path
from PIL import Image
from headline import headline

HERE = Path(__file__).resolve().parent
PAGE = HERE.parent
RAW = PAGE / 'raw'
OUT = PAGE / 'images'
FONT = PAGE / 'fonts' / 'PixelifySans[wght].ttf'
ICON = PAGE / 'images' / 'icon-24.png'  # from tools/icon.py

GREEN = (108, 192, 74)
BLUE = (77, 142, 240)
ORANGE = (255, 153, 32)
GOLD = (232, 184, 60)
SHADOW = (0, 0, 0, 120)


def headlines():
    for name, lines, color in [
        ('h-host', ["HOST IN", "TWO CLICKS."], GREEN),
        ('h-invites', ["INVITES", "ASK FIRST."], BLUE),
        ('h-diamond', ["SPOT THE", "STEAM PLAYERS."], ORANGE),
        ('h-extras', ["VOICE AND", "MAPS TOO."], GOLD),
    ]:
        headline(lines, color, FONT, 96, OUT / f'{name}.png', shadow=SHADOW)

    icon = Image.open(ICON).convert('RGBA').resize((240, 240), Image.NEAREST)  # exactly x10
    top = headline(["YOUR WORLD."], GREEN, FONT, 104, shadow=SHADOW)
    bottom = headline(["YOUR STEAM FRIENDS."], ORANGE, FONT, 104, shadow=SHADOW)
    w = 240 + 36 + max(top.width, bottom.width)
    h = max(240, top.height + bottom.height)
    hero = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    hero.alpha_composite(icon, (0, (h - 240) // 2))
    ty = (h - top.height - bottom.height) // 2
    hero.alpha_composite(top, (276, ty))
    hero.alpha_composite(bottom, (276, ty + top.height))
    hero.save(OUT / 'hero.png')


def shot(src, box, out, width=1200):
    im = Image.open(RAW / src).convert('RGB')
    if box:
        im = im.crop(box)
    im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    im.save(OUT / out, quality=90, optimize=True, progressive=True)


def screenshots():
    # Crops assume 1920x1080 screenshots from tools/shoot-hook.kt.
    shot('raw-lobby.png', None, 's-lobby.jpg')
    shot('raw-invited.png', None, 's-invited.jpg')
    shot('raw-nametags.png', (260, 180, 1860, 1080), 's-nametags.jpg')
    shot('raw-maplink.png', (0, 200, 1440, 1010), 's-maplink.jpg')


if __name__ == '__main__':
    OUT.mkdir(exist_ok=True)
    headlines()
    if RAW.exists():
        screenshots()
    else:
        print(f'{RAW} not found, only rebuilt the headlines')
