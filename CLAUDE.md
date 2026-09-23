# Steamed

Client-side Fabric mod that lets players host and join Minecraft worlds through Steam lobbies (Steamworks via steamworks4j). Progress and plans are in `milestones.md`.

## Stack

- Minecraft 26.2, Fabric Loader 0.19.5, Fabric API, Fabric Language Kotlin
- Kotlin 2.4.20 for logic, Java 25 only for mixins
- Loom plugin `net.fabricmc.fabric-loom`
- steamworks4j 1.10.0 and weupnp 0.1.2 (bundled with `include`), Mod Menu
- Versions live in `gradle.properties`

## Commands

- `./gradlew build` builds the mod
- `./gradlew runClient` launches a dev client (the Steam client must be running)

No tests exist yet.

## Layout

Base package `gay.git.kai.steamed` under `src/main/kotlin` (mixins under `src/main/java`).

- `Steamed.kt`: entrypoint, shared Steam interfaces and lobby state (companion), `startP2PGame()`
- `entrypoint/PreLaunch.kt`: Steam init and all matchmaking callbacks
- `net/`: `UPnPublisher` (port mapping), `SteamNetwork` (P2P session callbacks)
- `screen/SteamLobbyScreen.kt`: lobby UI
- `mixin/`: `TitleScreenMixin`, `PauseMenuMixin` (register new mixins in `steamed.mixins.json`)
- `lib/`: `Option`, `IPv4` helpers
- `tasks/`: callback poller

## How it works

1. `PreLaunch` writes `steam_appid.txt` (480), initialises Steam, registers interfaces.
2. The lobby screen creates a lobby; `onLobbyCreated` sets metadata and calls `startP2PGame()`.
3. `startP2PGame()` publishes the integrated server, maps the port with UPnP, and writes IP and port to the lobby.
4. Joining is not implemented yet.

## Conventions

- Logic in Kotlin; Java only when a mixin needs it
- Keep comments minimal
- Use the SLF4J logger, not `println`
- Check `Steamed.lobbyId.isSome()` before `unwrap()`, which throws when empty
- Steam callbacks fire from the poller thread; hop to the client thread before touching Minecraft state
- Client-only mod: do not reference server-only classes

## Gotchas

- Uses Spacewar (app ID 480), so lobbies are shared with every other 480 user. Filter lobby queries with a mod-specific key
- Minecraft 26.x renames things often and moves ownership between classes (for example `GuiGraphicsExtractor`/`extractRenderState`; `Minecraft.screen` is gone — current screen is `Minecraft.gui.screen()`, set with `Minecraft.setScreenAndShow(Screen)` or, for a nullable close, `Minecraft.gui.setScreen(Screen?)`; chat is `Minecraft.gui.hud.chat`, not `Minecraft.gui.chat`). Check current mappings by decompiling/`javap`-ing the mapped Minecraft jar under `.gradle/loom-cache/minecraftMaven/`, not older tutorials
- Mixin injection points are string-matched; they break on Minecraft updates
- The published server port is reachable from the internet until access control (M7) exists
