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
- `net/SteamP2PRelay.kt`: generic TCP<->Steam P2P bridge (the actual transport)
- `net/SteamNetwork.kt`: accepts P2P sessions from lobby members and bridges them via the relay
- `net/UPnPublisher.kt`, `net/IServerPublisher.kt`: unused, kept as a fallback transport if the P2P relay proves unreliable (see M8 in `MILESTONES.md`)
- `screen/SteamLobbyScreen.kt`: lobby UI
- `screen/SteamFriendListScreen.kt`: in-game friend picker for invites (avoids the Steam overlay)
- `mixin/`: `TitleScreenMixin`, `PauseMenuMixin` (register new mixins in `steamed.mixins.json`)
- `lib/`: `Option`, `IPv4` helpers
- `tasks/`: callback poller

## How it works

1. `PreLaunch` writes `steam_appid.txt` (480), initialises Steam, registers interfaces, and creates `Steamed.p2pRelay`.
2. The lobby screen creates a lobby; `onLobbyCreated` sets metadata and calls `startP2PGame()`.
3. `startP2PGame()` publishes the integrated server locally, starts the P2P relay, and calls `setLobbyGameServer` with the host's own SteamID in the `steamIDGameServer` param (the `ip`/`port` params are unused, always `0`).
4. `onLobbyGameCreated` only broadcasts to members already in the lobby at the moment `setLobbyGameServer` is called — it's *not* resent to anyone who joins afterward, which in practice is nearly everyone (hosting starts immediately on lobby creation, before an invite could realistically be accepted). So `onLobbyEnter` also calls `Steamed.joinHostedLobby()` for non-owners on every lobby entry, which polls `getLobbyGameServer` directly instead of waiting on the broadcast. Either path ends up in `Steamed.connectToHost`: bind a local loopback listener, wake the P2P session, point `ConnectScreen.startConnecting` at that local port. There's also a manual "Join Host" button in `SteamLobbyScreen` using the same poll, as a visible retry.
5. On the host, `SteamNetwork.onP2PSessionRequest` only accepts sessions from Steam IDs that are current lobby members, then bridges the session to a loopback connection on the published server's port. `SteamP2PRelay` pumps bytes both ways between each bridged local socket and `sendP2PPacket`/`readP2PPacket`.

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
- `IntegratedServer.publishServer` still binds to all interfaces (same as vanilla's "Open to LAN"), so the port is reachable on the host's LAN regardless of the P2P relay; it's just no longer advertised or forwarded to the internet via UPnP. Full access control is still M7
- `SteamNetwork.onP2PSessionRequest` only accepts sessions from current lobby members — a live membership check, not cryptographic auth. Good enough while the lobby stays friends-only, but not a substitute for the auth ticket handshake planned in M7
- `SteamNetworking.readP2PPacket` does **not** advance the destination `ByteBuffer`'s position — don't `flip()` it afterward (that sets `limit=0` and throws `BufferUnderflowException`). Read the `read` bytes with an absolute `buffer.get(0, bytes)` instead. Found this by testing against a real Steam session; see M8 in `MILESTONES.md`
- The Steam overlay (and `activateGameOverlayInviteDialog`) needs Steam to have launched the process (it sets `LD_PRELOAD` for `gameoverlayrenderer.so`); it doesn't work for a mod launched through a regular Minecraft launcher unless that launcher itself was started as a Non-Steam Game from within Steam. `SteamFriendListScreen` sidesteps this for invites by calling `SteamMatchmaking.inviteUserToLobby` directly instead of relying on the overlay
- `GridLayout.arrangeElements()` (which `LinearLayout` wraps) sizes rows from *every* child's width unconditionally — it does not check `.visible`. An invisible widget still reserves its full width in the row, which throws off centering for anything else sharing that row. Don't add a widget and toggle `.visible` to hide it if something else might be centered alongside it in the same `LinearLayout`; only add the widget that should actually be there (see `SteamLobbyScreen`'s "Create Lobby"/"Join Host" toggle)
