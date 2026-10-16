# Modrinth page

Sources for the description on [modrinth.com/mod/steamedmc](https://modrinth.com/mod/steamedmc).

| Path | What |
|---|---|
| `description.md` | The page, with `{name}` placeholders for images |
| `cdn-urls.json` | Where each image lives on Modrinth's CDN |
| `images/` | The published images |
| `fonts/` | Pixelify Sans (SIL Open Font License, see `fonts/OFL.txt`), used for the headlines |
| `third_party/pixelarticons/` | The "tea" glyph the icon is made from, and its MIT license |
| `tools/` | Scripts to regenerate everything |

Needs Python 3 with Pillow.

## Change the icon

`python3 tools/icon.py` renders `third_party/pixelarticons/tea.svg` in Steamed's orange into the mod's `icon.png` and `images/icon-24.png` (needs `rsvg-convert`). Then `python3 tools/build.py` for the page banner and `python3 tools/social.py` for `.github/social-preview.png`.

## Change the text only

1. Edit `description.md`
2. `python3 tools/fill.py` prints the final markdown
3. Paste it into the project's Description settings and save

## Retake the screenshots

The screenshots show demo players (Robin, Kai, Sam, Ari) with default skins, set up by a temporary patch.

1. `python3 tools/demo_patch.py` (patches the mod with demo data and `tools/shoot-hook.kt`)
2. `./gradlew :fabric:runClient --args='--quickPlaySingleplayer "New World" --fullscreen'` with Steam running. The hook builds a small camp and takes six screenshots, then logs `STEAMED-SHOT done`
3. Close the game, then `python3 tools/demo_patch.py --revert`. **Always revert before committing**
4. Copy the six new files from `fabric/run/screenshots/` into `raw/` as `raw-nametags.png`, `raw-maplink.png`, `raw-lobby.png`, `raw-invite.png`, `raw-invited.png`, `raw-connecting.png` (that's the order they're taken in)
5. `python3 tools/build.py` rebuilds `images/`. `python3 tools/preview.py dark` (or `light`) renders a rough preview

The hook expects the dev world "New World" from this repo's `fabric/run/saves`. It places the camp at x 320, z 734; in another world, change those coordinates in `tools/shoot-hook.kt`.

## Publish new images

Upload each changed image through the Description editor's Image button (Upload tab), copy its `cdn.modrinth.com` URL into `cdn-urls.json`, then follow "Change the text only". Saving through the editor, rather than the API, lets Modrinth attach the uploads to the project.
