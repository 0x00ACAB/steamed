# Steamed

Client-side Fabric + NeoForge mod that lets players host and join Minecraft worlds through Steam lobbies (Steamworks via steamworks4j). Progress and plans are in `milestones.md`.

## Stack

- Minecraft 26.2, Java 25
- Kotlin 2.4.20 for logic, Java 25 only for mixins
- Multi-loader: `common`/`fabric`/`neoforge`/`quilt` Gradle modules, source-shared via a hand-rolled `build-logic` convention plugin (not Architectury) — see Layout below. `quilt` builds but `:quilt:runClient` doesn't launch (see Gotchas)
- Fabric: Fabric Loader 0.19.5, Fabric API (declared but unused), Fabric Language Kotlin, `net.fabricmc.fabric-loom`
- NeoForge: NeoForge 26.2.0.88, `net.neoforged.moddev` (ModDevGradle)
- Quilt: `org.quiltmc:quilt-loader`, reusing plain `net.fabricmc.fabric-loom` (not a separate Quilt Loom — see Gotchas)
- steamworks4j 1.10.0 and weupnp 0.1.2, bundled per-platform: Fabric and Quilt via Loom's `include()`, NeoForge via ModDevGradle's `jarJar`
- Versions live in `gradle.properties`

## Commands

- `./gradlew build` builds every module, including `common` on its own (harmless — it can build standalone since it also applies `net.neoforged.moddev` for its own compile-time Minecraft access). `./gradlew :fabric:build` / `:neoforge:build` / `:quilt:build` builds just one platform's jar
- `./gradlew :fabric:runClient` / `:neoforge:runClient` launches a dev client for that loader (the Steam client must be running). NeoForge's first run populates its own asset cache separately from Fabric's and takes a while; later runs are fast. `:quilt:runClient` does **not** work — see Gotchas. `runClient` always needs a specific `:module` target — there's no meaningful root-level one

No tests exist yet.

## Layout

Base package `gay.git.kai.steamed` throughout. Four modules:

- `common/`: almost everything — `Steamed.kt` (a plain `object`: Steam interface refs, lobby state, `startP2PGame()`/`connectToHost()`/`joinHostedLobby()`), `SteamInit.kt` (Steam init sequence + all matchmaking callbacks, was `entrypoint/PreLaunch.kt` before the multi-loader split), `net/SteamP2PRelay.kt` (generic TCP<->Steam P2P bridge, the actual transport), `net/SteamNetwork.kt` (accepts P2P sessions from lobby members), `net/UPnPublisher.kt`/`net/IServerPublisher.kt` (unused fallback transport, see M8 in `MILESTONES.md`), `screen/SteamLobbyScreen.kt`, `screen/SteamFriendListScreen.kt` (in-game friend picker, avoids the Steam overlay), `mixin/TitleScreenMixin.java`/`PauseMenuMixin.java`, `steamed.mixins.json`, `platform/IPlatformHelper.kt`+`Services.kt` (the one thing that needs a per-loader implementation — currently just the game directory)
- `fabric/`: `SteamedFabric.kt` (`ModInitializer`/`ClientModInitializer`, just a log line), `SteamPreLaunchFabric.kt` (`PreLaunchEntrypoint`, calls `SteamInit.initSteam()`), `FabricPlatformHelper.kt`, `fabric.mod.json`
- `neoforge/`: `SteamedNeoForge.kt` (`@Mod` class — its constructor is the earliest hook NeoForge offers, calls `SteamInit.initSteam()` there; also listens for `FMLClientSetupEvent` for the same log line Fabric's `ClientModInitializer` prints), `NeoForgePlatformHelper.kt`, `neoforge.mods.toml`
- `quilt/`: mirrors `fabric/` almost exactly (`SteamedQuilt.kt`, `SteamPreLaunchQuilt.kt`, `QuiltPlatformHelper.kt`, its own `fabric.mod.json` — Quilt Loader reads that format directly, no `quilt.mod.json` needed) — kept as separate files rather than sharing Fabric's, since they're tiny and this avoids new cross-module sharing complexity for two modules that could plausibly diverge later
- `build-logic/`: two convention plugins — `multiloader-common` (java-library + kotlin toolchain, shared by all four modules) and `multiloader-loader` (applied by `fabric`/`neoforge`/`quilt`; pulls `common`'s raw source files directly into that platform's own `compileKotlin`/`compileJava`/`processResources` tasks via `commonJava`/`commonResources` outgoing configurations on `common`). This means common code is recompiled per-platform, not consumed as a binary jar — deliberate: it's why steamworks4j only needs bundling once per platform, with each platform's own already-proven mechanism, instead of needing a shared strategy that works identically across different loaders' jar-in-jar systems

## How it works

1. `SteamInit.initSteam()` writes `steam_appid.txt` (480), initialises Steam, registers interfaces, and creates `Steamed.p2pRelay`. Fabric calls this from `PreLaunchEntrypoint`; NeoForge calls it from its `@Mod` constructor.
2. The lobby screen creates a lobby; `onLobbyCreated` sets metadata and calls `startP2PGame()`.
3. `startP2PGame()` publishes the integrated server locally, starts the P2P relay, and calls `setLobbyGameServer` with the host's own SteamID in the `steamIDGameServer` param (the `ip`/`port` params are unused, always `0`).
4. `onLobbyGameCreated` only broadcasts to members already in the lobby at the moment `setLobbyGameServer` is called — it's *not* resent to anyone who joins afterward, which in practice is nearly everyone (hosting starts immediately on lobby creation, before an invite could realistically be accepted). So `onLobbyEnter` also calls `Steamed.joinHostedLobby()` for non-owners on every lobby entry, which polls `getLobbyGameServer` directly instead of waiting on the broadcast. Either path ends up in `Steamed.connectToHost`: bind a local loopback listener, point `ConnectScreen.startConnecting` at that local port. There's also a manual "Join Host" button in `SteamLobbyScreen` using the same poll, as a visible retry.
5. On the host, `SteamNetwork.onP2PSessionRequest` only accepts sessions from Steam IDs that are current lobby members, then bridges the session to a loopback connection on the published server's port. `SteamP2PRelay` pumps bytes both ways between each bridged local socket and `sendP2PPacket`/`readP2PPacket`.

## Conventions

- Logic in Kotlin; Java only when a mixin needs it
- Keep comments minimal
- Use the SLF4J logger, not `println`
- Check `Steamed.lobbyId.isSome()` before `unwrap()`, which throws when empty
- Steam callbacks fire from the poller thread; hop to the client thread before touching Minecraft state
- Client-only mod: do not reference server-only classes
- New code goes in `common` unless it genuinely needs a loader-specific API (check by grepping for `net.fabricmc`/`net.neoforged` before assuming something needs a platform split — most things don't; steamworks4j and vanilla Minecraft APIs are both loader-agnostic already). If it does, add the platform-specific piece behind `IPlatformHelper`/`Services` rather than reaching for a whole new abstraction layer

## Gotchas

- Uses Spacewar (app ID 480), so lobbies are shared with every other 480 user. Filter lobby queries with a mod-specific key
- Minecraft 26.x renames things often and moves ownership between classes (for example `GuiGraphicsExtractor`/`extractRenderState`; `Minecraft.screen` is gone — current screen is `Minecraft.gui.screen()`, set with `Minecraft.setScreenAndShow(Screen)` or, for a nullable close, `Minecraft.gui.setScreen(Screen?)`; chat is `Minecraft.gui.hud.chat`, not `Minecraft.gui.chat`). Don't trust older tutorials — run `./gradlew :fabric:genSourcesWithVineflower` and read the real decompiled source under `fabric/.gradle/loom-cache/minecraftMaven/.../*-sources.jar` (much faster than reconstructing signatures from `javap`). Both loaders build against the same official-mapped vanilla Minecraft, so signatures found this way apply to both
- Mixin injection points are string-matched; they break on Minecraft updates
- `IntegratedServer.publishServer` still binds to all interfaces (same as vanilla's "Open to LAN"), so the port is reachable on the host's LAN regardless of the P2P relay; it's just no longer advertised or forwarded to the internet via UPnP. Full access control is still M7
- `SteamNetwork.onP2PSessionRequest` only accepts sessions from current lobby members — a live membership check, not cryptographic auth. Good enough while the lobby stays friends-only, but not a substitute for the auth ticket handshake planned in M7
- `SteamNetworking.readP2PPacket` does **not** advance the destination `ByteBuffer`'s position — don't `flip()` it afterward (that sets `limit=0` and throws `BufferUnderflowException`). Read the `read` bytes with an absolute `buffer.get(0, bytes)` instead. Found this by testing against a real Steam session; see M8 in `MILESTONES.md`
- The Steam overlay (and `activateGameOverlayInviteDialog`) needs Steam to have launched the process (it sets `LD_PRELOAD` for `gameoverlayrenderer.so`); it doesn't work for a mod launched through a regular Minecraft launcher unless that launcher itself was started as a Non-Steam Game from within Steam. `SteamFriendListScreen` sidesteps this for invites by calling `SteamMatchmaking.inviteUserToLobby` directly instead of relying on the overlay
- `GridLayout.arrangeElements()` (which `LinearLayout` wraps) sizes rows from *every* child's width unconditionally — it does not check `.visible`. An invisible widget still reserves its full width in the row, which throws off centering for anything else sharing that row. Don't add a widget and toggle `.visible` to hide it if something else might be centered alongside it in the same `LinearLayout`; only add the widget that should actually be there (see `SteamLobbyScreen`'s "Create Lobby"/"Join Host" toggle)
- For multi-loader Gradle specifics (exact plugin/version choices, why not Architectury, the `commonJava`/`commonResources` source-sharing mechanism), don't guess from generic tutorials — the WFPhantom/MultiLoader-Template GitHub repo has a `26.2` branch that was verified working and is what this project's `build-logic` is adapted from
- `:quilt:runClient` throws `SecurityException: ... signer information does not match ...` for `net.fabricmc.loader.impl.launch.knot.Knot`. `quilt-loader` transitively pulls in real `fabric-loader`, and plain Fabric Loom's dev-launch tooling adds another copy regardless of dependency excludes — both define the same package with different signers. The real fix is Quilt's own Loom fork (`org.quiltmc:loom`), but as of writing it's snapshot-only and hasn't been published since 2025-10-31 (QSL is similarly stale, capped at MC 1.21.1) — not solid ground to build further verification on. `:quilt:build` itself works fine; only the dev-launch simulation is broken, so this may well not affect a real Quilt install (which never has a second fabric-loader jar present) — that's a plausible theory, not something verified here
