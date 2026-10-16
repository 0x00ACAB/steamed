"""Renders the mod icon from Pixelarticons' "tea" glyph (MIT, see third_party/pixelarticons/LICENSE) in Steamed's orange.

Writes common/src/main/resources/assets/steamed/icon.png (512x512), images/icon-24.png, and the title screen
button's sprites (20x20, normal and highlighted). Needs rsvg-convert.
"""
import io
import subprocess
from pathlib import Path
from PIL import Image

PAGE = Path(__file__).resolve().parent.parent
REPO = PAGE.parents[1]
ORANGE = '#FF9920'
HIGHLIGHT = (255, 200, 112)

svg = (PAGE / 'third_party/pixelarticons/tea.svg').read_text().replace('currentColor', ORANGE)
png = subprocess.run(['rsvg-convert', '-w', '24', '-h', '24'], input=svg.encode(), capture_output=True, check=True).stdout
glyph = Image.open(io.BytesIO(png)).convert('RGBA')
# rsvg anti-aliases nothing on this grid, but snap alpha anyway so scaling stays crisp.
glyph.putalpha(glyph.getchannel('A').point(lambda a: 255 if a >= 128 else 0))

# The glyph sits off-centre in its 24x24 grid (high and to the right), so centre its bounding box.
box = glyph.getbbox()
centred = Image.new('RGBA', glyph.size, (0, 0, 0, 0))
centred.alpha_composite(glyph.crop(box), ((24 - (box[2] - box[0])) // 2, (24 - (box[3] - box[1])) // 2))
glyph = centred

icon = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
icon.alpha_composite(glyph.resize((480, 480), Image.NEAREST), (16, 16))
icon.save(REPO / 'common/src/main/resources/assets/steamed/icon.png')
(PAGE / 'images').mkdir(exist_ok=True)
glyph.save(PAGE / 'images/icon-24.png')

# Title screen button: the 18x20 glyph at 1:1 on a 20x20 canvas, plus a lighter copy for hover/focus.
sprites = REPO / 'common/src/main/resources/assets/steamed/textures/gui/sprites/menu'
sprites.mkdir(parents=True, exist_ok=True)
trimmed = glyph.crop(glyph.getbbox())
button = Image.new('RGBA', (20, 20), (0, 0, 0, 0))
button.alpha_composite(trimmed, ((20 - trimmed.width) // 2, (20 - trimmed.height) // 2))
button.save(sprites / 'lobby.png')
light = Image.new('RGBA', button.size, HIGHLIGHT + (255,))
light.putalpha(button.getchannel('A'))
light.save(sprites / 'lobby_highlighted.png')
