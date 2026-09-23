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
- `screen/SteamFriendListScreen.kt`: in-game friend picker for invites (avoids the Steam overlay)
- `mixin/`: `TitleScreenMixin`, `PauseMenuMixin` (register new mixins in `steamed.mixins.json`)
- `lib/`: `Option`, `IPv4` helpers
- `tasks/`: callback poller

## How it works

1. `PreLaunch` writes `steam_appid.txt` (480), initialises Steam, registers interfaces.
2. The lobby screen creates a lobby; `onLobbyCreated` sets metadata and calls `startP2PGame()`.
3. `startP2PGame()` publishes the integrated server, maps the port with UPnP, and writes IP and port to the lobby with `setLobbyGameServer`.
4. `onLobbyGameCreated` fires for every lobby member (both at publish time and for anyone who joins afterward). Non-owners read the IP/port and connect with `ConnectScreen.startConnecting`; the owner ignores its own broadcast.

## Conventions

- Logic in Kotlin; Java only when a mixin needs it
- Keep comments minimal
- Use the SLF4J logger, not `println`
- Check `Steamed.lobbyId.isSome()` before `unwrap()`, which throws when empty
- Steam callbacks fire from the poller thread; hop to the client thread before touching Minecraft state
- Client-only mod: do not reference server-only classes

## Gotchas

- Uses Spacewar (app ID 480), so lobbies are shared with every other 480 user. Filter lobby queries with a mod-specific key
- Minecraft 26.x renames things often and moves ownership between classes (for example `GuiGraphicsExtractor`/`extractRenderState`; `Minecraft.screen` is gone — current screen is `Minecraft.gui.screen()`, set with `Minecraft.setScreenAndShow(Screen)` or, for a nullable close, `Minecraft.gui.setScreen(Screen?)`; chat is `Minecraft.gui.hud.chat`, not `Minecraft.gui.chat`). Don't trust older tutorials — run `./gradlew genSourcesWithVineflower` and read the real decompiled source under `.gradle/loom-cache/minecraftMaven/.../*-sources.jar` (much faster than reconstructing signatures from `javap`)
- Mixin injection points are string-matched; they break on Minecraft updates
- The published server port is reachable from the internet until access control (M7) exists
- The Steam overlay (and `activateGameOverlayInviteDialog`) needs Steam to have launched the process (it sets `LD_PRELOAD` for `gameoverlayrenderer.so`); it doesn't work for a mod launched through a regular Minecraft launcher unless that launcher itself was started as a Non-Steam Game from within Steam. `SteamFriendListScreen` sidesteps this for invites by calling `SteamMatchmaking.inviteUserToLobby` directly instead of relying on the overlay
