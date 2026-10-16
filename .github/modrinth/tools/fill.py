"""Prints description.md with the {image} placeholders replaced by the URLs in cdn-urls.json."""
import json
import re
from pathlib import Path

PAGE = Path(__file__).resolve().parent.parent
urls = json.loads((PAGE / 'cdn-urls.json').read_text())
text = (PAGE / 'description.md').read_text()
text = re.sub(r'\{([a-z-]+)\}', lambda m: urls[m.group(1)], text)
print(text, end='')
