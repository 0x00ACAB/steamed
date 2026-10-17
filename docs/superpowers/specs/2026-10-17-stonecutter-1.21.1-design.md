# Stonecutter restructure and Minecraft 1.21.1 port

Date: 2026-10-17. Status: approved in conversation, awaiting spec review.

## Goal

Ship Steamed for Minecraft 1.21.1 (Fabric and NeoForge), because 1.21.1 is a popular base for new modpacks, while 26.2
stays the main version and keeps getting features. Future backports (1.21.x, 1.20.x from `MILESTONES.md`) should cost
a node in `settings.gradle.kts`, not a new branch.

Done means: 1.21.1 Fabric and NeoForge jars that load, initialise Steam and host a lobby on real Prism installs,
released alongside the 26.2 Fabric, NeoForge and Quilt jars as 0.2.0.

Out of scope: Quilt on 1.21.1, ViaFabric support, cross-version play (the `mc_protocol` lobby gate already keeps
1.21.1 and 26.2 players apart), anything still blocked on a second Steam account.

## Decisions

- **Stonecutter**, not a branch per version or a shared-core module split. The backport list makes a preprocessor pay
  off from the third version
- **Flatten to Multicutter's layout** ([pajicadvance/multicutter](https://github.com/pajicadvance/multicutter), commit
  `bf13a3b`, which already builds 1.21.1 and 26.2 on Fabric and NeoForge), rather than versioning the current
  `common`/`fabric`/`neoforge`/`quilt` modules in place: the `commonJava` source sharing plus Stonecutter is an untested
  combination
- 1.21.1 ships for **Fabric and NeoForge**; 26.2 keeps Fabric, NeoForge and Quilt
- Multicutter's Fletching Table, mod-publish-plugin, config and Mixson examples are not adopted

## 1. Build layout

```
settings.gradle.kts          Stonecutter 0.9.8 + loom-back-compat 0.4; the node list
stonecutter.gradle.kts       active node, tags (version, loader), swaps, replacements
stonecutter.properties.toml  mod metadata and per-version/loader dependency versions
build.fabric.gradle.kts      Fabric Loom through loom-back-compat (obfuscated 1.21.1 and unobfuscated 26.x)
build.neoforge.gradle.kts    ModDevGradle; steamworks4j packed into the jar (see 0.1.4); KFF required
build.quilt.gradle.kts       today's quilt/build.gradle.kts, including the runClient workarounds, unchanged
src/main/kotlin/xyz/kkaii/steamed/...          today's common/src/main/kotlin
src/main/kotlin/xyz/kkaii/steamed/platform/{fabric,neoforge,quilt}/   today's loader modules' code
src/main/java/xyz/kkaii/steamed/mixin/...      mixins
src/main/resources/          fabric.mod.json, neoforge.mods.toml, steamed.mixins.json, assets; templated per node
src/test/                    today's common/src/test
```

- Nodes: `1.21.1-fabric`, `1.21.1-neoforge`, `26.2-fabric`, `26.2-neoforge`, `26.2-quilt`. Checked-in active node:
  `26.2-fabric`
- Each node excludes the other loaders' `platform/*` packages from its source set; no `//? if fabric` around whole files
- Java toolchain: 21 for 1.21.1, 25 for 26.x. Kotlin 2.4.20 everywhere (KFF 5.12.0 and 6.4.0 both ship stdlib 2.4.0,
  which 0.1.4 proved works with 2.4.20 output)
- `build-logic/`, `common/`, `fabric/`, `neoforge/`, `quilt/` are removed once the 26.2 nodes match 0.1.4

| Node | Loader | Dependencies |
|---|---|---|
| 26.2-fabric | Fabric Loader 0.19.5 | Fabric API 0.161.0+26.2, FLK 1.14.1+kotlin.2.4.20 |
| 26.2-quilt | Quilt Loader 0.31.0-beta.4 (dev) | Fabric API 0.161.0+26.2, FLK 1.13.14+kotlin.2.4.20 |
| 26.2-neoforge | NeoForge 26.2.0.88 | KFF >=6.4 |
| 1.21.1-fabric | Fabric Loader 0.19.5 | Fabric API 0.116.17+1.21.1, FLK 1.14.1+kotlin.2.4.20 (FLK is not tied to a Minecraft version) |
| 1.21.1-neoforge | NeoForge 21.1.262 | KFF >=5.12 |

## 2. Version-specific code

Three tiers, picked by how far an API moved:

1. **Plain renames**: Stonecutter `replacements` (string swaps), e.g. `Identifier` / `ResourceLocation`
2. **Changed signatures, same concept**: inline `//? if >=26.1 {` / `//?} else {` blocks. Expected:
   - screen rendering `extractRenderState(GuiGraphicsExtractor, ...)` / `render(GuiGraphics, mouseX, mouseY, delta)`
   - input `keyPressed(KeyEvent)`, `mouseClicked(MouseButtonEvent, ...)` / the `int` and `double` overloads
   - `Minecraft.gui.screen()`, `setScreenAndShow` / `Minecraft.screen`, `setScreen`
   - `publishServer(MultiplayerScope, port)` / `publishServer(GameType, boolean, port)` in `IntegratedServerMixin`
   - `ClickEvent`/`HoverEvent` records / constructors with `Action`; `ARGB` / `FastColor.ARGB32`;
     `FontDescription` / `Style.withFont(ResourceLocation)`
3. **Absent in 1.21.1**: separate files compiled only for their versions
   - nametag marker: `AvatarRendererMixin` (26.x render-state pipeline) / a `PlayerRendererMixin` on `renderNameTag`
     for 1.21.1. `steamed.mixins.json` lists the right one per node
   - player heads inside chat lines (`PlayerSprite` object components) are dropped on 1.21.1; join/leave lines keep the
     orange colour and diamond. Heads in the lobby and invite screens stay, via `PlayerFaceRenderer`

Not split: the Steam relays, `SteamAuthHandshake`, voice and web-map tunnels, `SteamNatives`, `SteamPlayers`' cache
logic, `WebMapConfig`/`VoicePorts`/`WebMapList`.

The tier 2 and 3 lists are expected differences, not verified ones. Before porting each file, check it against
`genSourcesWithVineflower` output for 1.21.1 (per `CLAUDE.md`), and move anything that turns out different to the
right tier.

## 3. CI and releases

- Version 0.2.0, one tag (`v0.2.0`), one notes file shared by every jar. The tag check reads `mod.version` from
  `stonecutter.properties.toml`
- Jars: `steamed-<loader>-<version>+<mc>.jar`, all five attached to the GitHub pre-release
- Modrinth: one version per node, `<version>+<mc>-<loader>`, beta. Game versions: `26.2` for 26.2 nodes; `1.21` and
  `1.21.1` for 1.21.1 nodes (same protocol, 767). Dependencies per node: Fabric API + FLK for Fabric and Quilt, KFF
  for NeoForge. The existing curl loop in `build.yml` stays and iterates nodes instead of loaders
- `./gradlew build` builds and tests all nodes
- CodeQL compiles `26.2-fabric` and `1.21.1-fabric` only, to stay inside the heap
- Dependabot unchanged (it can't read the TOML, as it can't read `gradle.properties` today)
- `CLAUDE.md` is rewritten for the new layout and commands as part of the restructure

## 4. Testing

Restructure gate (before any 1.21.1 code):

- `./gradlew build` produces the three 26.2 jars; tests pass
- Per loader, diff the unzipped 0.1.4 jar against the new one (file list, `fabric.mod.json`, `neoforge.mods.toml`):
  only version strings and the jar name may differ
- The three existing Prism instances (`Steamed Fabric dev`, `Steamed Quilt 0.1.3`, `Steamed NeoForge 0.1.3`) reach
  the title screen with Steam initialised

1.21.1 gate:

- `:1.21.1-fabric:build` and `:1.21.1-neoforge:build` pass, with tests
- Every mixin target and changed signature checked against 1.21.1 decompiled sources
- Two new Prism instances (1.21.1 Fabric with Fabric API 0.116.17 + FLK 1.14.1; NeoForge 21.1.262 with KFF 5.12.0)
  reach the title screen with Steam initialised and the lobby icon rendered
- Per 1.21.1 loader: open a world, create a lobby, the lobby screen lists you with your face, the invite screen opens,
  and vanilla "Open to LAN" still works after closing the lobby

Still blocked on a second Steam account, for both versions: joining, auth, kick, voice, web maps, the nametag marker
seen from another client, and the version-gate greying in a friend's list.

## Estimate

Restructure about a day; 1.21.1 port about 2-3 days, mostly `SteamLobbyScreen` and the eight mixins.
