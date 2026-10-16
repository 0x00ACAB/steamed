"""Renders the mod icon from Pixelarticons' "tea" glyph (MIT, see third_party/pixelarticons/LICENSE) in Steamed's orange.

Writes common/src/main/resources/assets/steamed/icon.png (512x512) and icons/icon-24.png. Needs rsvg-convert.
"""
import io
import subprocess
from pathlib import Path
from PIL import Image

PAGE = Path(__file__).resolve().parent.parent
REPO = PAGE.parents[1]
ORANGE = '#FF9920'

svg = (PAGE / 'third_party/pixelarticons/tea.svg').read_text().replace('currentColor', ORANGE)
png = subprocess.run(['rsvg-convert', '-w', '24', '-h', '24'], input=svg.encode(), capture_output=True, check=True).stdout
glyph = Image.open(io.BytesIO(png)).convert('RGBA')
# rsvg anti-aliases nothing on this grid, but snap alpha anyway so scaling stays crisp.
glyph.putalpha(glyph.getchannel('A').point(lambda a: 255 if a >= 128 else 0))

icon = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
icon.alpha_composite(glyph.resize((480, 480), Image.NEAREST), (16, 16))
icon.save(REPO / 'common/src/main/resources/assets/steamed/icon.png')
(PAGE / 'images').mkdir(exist_ok=True)
glyph.save(PAGE / 'images/icon-24.png')
