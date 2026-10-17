# Steamed

Client-side Fabric + NeoForge mod that lets players host and join Minecraft worlds through Steam lobbies (Steamworks via
steamworks4j). Progress and plans are in `milestones.md`.

## Stack

- Minecraft 26.2, Java 25
- Kotlin 2.4.20 for logic, Java 25 only for mixins
- Multi-loader: `common`/`fabric`/`neoforge`/`quilt` Gradle modules, source-shared via a hand-rolled `build-logic`
  convention plugin (not Architectury) — see Layout below
- Fabric: Fabric Loader 0.19.5, Fabric API (required: nothing imports it, but its resource loader is what loads
  `assets/steamed`; neither Fabric nor Quilt Loader do that alone), Fabric Language Kotlin, `net.fabricmc.fabric-loom`
- NeoForge: NeoForge 26.2.0.88, `net.neoforged.moddev` (ModDevGradle)
- Quilt: `org.quiltmc:quilt-loader` plus plain Fabric API (Quilted Fabric API stops at 1.20.1), reusing plain
  `net.fabricmc.fabric-loom` (not a separate Quilt Loom — see Gotchas)
- steamworks4j 1.10.0, bundled per-platform: Fabric and Quilt via Loom's `include()`, NeoForge via ModDevGradle's
  `jarJar`
- Versions live in `gradle.properties`, and so does the mod's metadata (`mod_*` keys): `multiloader-loader` expands them
  into `fabric.mod.json`/`neoforge.mods.toml`, so edit the properties, not those files. Gradle reads the file as
  ISO-8859-1, so non-ASCII needs `\u` escapes

## Release

- Releases should go both on modrinth and github releases
- Each release should include the three platform-specific jars (Fabric, NeoForge, Quilt; classic Forge exempt for now)
- Release notes should detail the changes and improvements in each version

## Commands

- `./gradlew build` builds every module, including `common` on its own (harmless — it can build standalone since it also
  applies `net.neoforged.moddev` for its own compile-time Minecraft access). `./gradlew :fabric:build` /
  `:neoforge:build` / `:quilt:build` builds just one platform's jar
- `./gradlew :fabric:runClient` / `:neoforge:runClient` / `:quilt:runClient` launches a dev client for that loader (the Steam client must be
  running). NeoForge's first run populates its own asset cache separately from Fabric's and takes a while; later runs
  are fast. `runClient` always needs a specific `:module` target —
  there's no meaningful root-level one

GitHub Actions (`.github/workflows/build.yml`) builds every push and uploads the three jars as artifacts. Pushing a tag
`v<mod_version>` (it must match `gradle.properties`, or the build fails) also publishes a GitHub pre-release with the
jars; release immutability is on, so the job drafts, attaches, then publishes. The same job then uploads one Modrinth version per loader
(`<version>+<loader>`, beta, GitHub's generated notes as the changelog; Fabric and Quilt require Fabric API and FLK) to
project `SZuvIr1Y`, using the `MODRINTH_TOKEN` repo secret

`.github/workflows/codeql.yml` runs CodeQL on `main`, PRs and weekly: `java-kotlin` (manual `./gradlew classes`
build, since Kotlin can't use build-mode none) and `actions`. CodeQL's Kotlin support currently ends at 2.4.20, so check it
before bumping Kotlin. `.github/dependabot.yml` covers GitHub Actions and the Gradle versions in build files; it can't
see `gradle.properties`, and ignores Kotlin bumps (they have to move together with FLK)

`./gradlew :common:test` runs the unit tests (JUnit 5 via `kotlin("test")`, in `common/src/test`, also part of `build`
and CI). They cover pure logic only, pulled into small `internal` objects (`net/WebMapConfig.kt`, `net/VoicePorts.kt`,
`net/WebMapList.kt`); anything touching Steam or Minecraft at runtime still needs a dev client.

## Layout

Base package `xyz.kkaii.steamed` throughout. Four modules:

- `common/`: almost everything — `Steamed.kt` (a plain `object`: Steam interface refs, lobby state, `startP2PGame()`/
  `connectToHost()`/`joinHostedLobby()`), `SteamInit.kt` (Steam init sequence + all matchmaking callbacks, was
  `entrypoint/PreLaunch.kt` before the multi-loader split), `net/SteamP2PRelay.kt` (generic TCP<->Steam P2P bridge, the
  Minecraft transport, channel 0), `net/SteamP2PVoiceRelay.kt` (UDP sibling on channel 1, tunnels voice-mod traffic,
  several ports multiplexed by a 2-byte port header; see M11/M11b in `MILESTONES.md`), `net/SvcIntegration.kt`/
  `net/PlasmoVoiceIntegration.kt` (read the running Simple Voice Chat / Plasmo Voice server's UDP port by reflection),
  `net/PublicTunnelMods.kt` (detects e4mc/e4all so hosting can warn that they expose the world publicly),
  `net/WebMapIntegration.kt`/`net/SteamP2PWebRelay.kt` (finds BlueMap/squaremap web servers and tunnels browser
  connections to joiners on channel 3), `net/SteamNetwork.kt` (accepts P2P sessions from lobby members, bridges them
  once authenticated), `net/SteamAuthHandshake.kt` (Steam auth ticket exchange on channel 2, gates bridging),
  `FriendLobbies.kt` (finds the Steamed lobbies Steam friends are in, for the "join without an invite" list),
  `SteamPlayers.kt` (Steam ID to Minecraft UUID: lobby member data, rich presence, and an on-disk cache; drives the
  nametag marker and the player heads in the lobby and invite screens), `screen/SteamLobbyScreen.kt` (member list with
  faces and a per-row Kick for the host), `screen/SteamFriendListScreen.kt` (in-game friend picker, avoids the Steam
  overlay), `screen/SteamConnectingScreen.kt` (shown while the auth handshake runs), `mixin/TitleScreenMixin.java`/
  `AvatarRendererMixin.java` and `PlayerTabOverlayMixin.java` (orange diamond before Steam joiners' name tags and Tab
  list names, glyph from `assets/steamed/font/nametag.json`)/`PauseMenuMixin.java`/`IntegratedServerMixin.java`
  (loopback-only bind when hosting through Steam, and lifts vanilla's 8-player cap to the lobby limit)/
  `ChatListenerMixin.java` (orange join/leave lines for Steam joiners)/`ConnectScreenAccessor.java`, `steamed.mixins.json`,
  `platform/IPlatformHelper.kt`+`Services.kt` (the one thing that needs a per-loader implementation — currently just the
  game directory)
- `fabric/`: `SteamedFabric.kt` (`ModInitializer`/`ClientModInitializer`, just a log line), `SteamPreLaunchFabric.kt`
  (`PreLaunchEntrypoint`, calls `SteamInit.initSteam()`), `FabricPlatformHelper.kt`, `fabric.mod.json`
- `neoforge/`: `SteamedNeoForge.kt` (`@Mod` class — its constructor is the earliest hook NeoForge offers, calls
  `SteamInit.initSteam()` there; also listens for `FMLClientSetupEvent` for the same log line Fabric's
  `ClientModInitializer` prints), `NeoForgePlatformHelper.kt`, `neoforge.mods.toml`
- `quilt/`: mirrors `fabric/` almost exactly (`SteamedQuilt.kt`, `SteamPreLaunchQuilt.kt`, `QuiltPlatformHelper.kt`, its
  own `fabric.mod.json` — Quilt Loader reads that format directly, no `quilt.mod.json` needed) — kept as separate files
  rather than sharing Fabric's, since they're tiny and this avoids new cross-module sharing complexity for two modules
  that could plausibly diverge later
- `build-logic/`: two convention plugins — `multiloader-common` (java-library + kotlin toolchain, shared by all four
  modules) and `multiloader-loader` (applied by `fabric`/`neoforge`/`quilt`; pulls `common`'s raw source files directly
  into that platform's own `compileKotlin`/`compileJava`/`processResources` tasks via `commonJava`/`commonResources`
  outgoing configurations on `common`). This means common code is recompiled per-platform, not consumed as a binary
  jar — deliberate: it's why steamworks4j only needs bundling once per platform, with each platform's own already-proven
  mechanism, instead of needing a shared strategy that works identically across different loaders' jar-in-jar systems

## How it works

1. `SteamInit.initSteam()` writes `steam_appid.txt` (480), initialises Steam, registers interfaces, and creates
   `Steamed.p2pRelay`. Fabric calls this from `PreLaunchEntrypoint`; NeoForge calls it from its `@Mod` constructor.
2. The lobby screen creates a lobby; `onLobbyCreated` sets metadata and calls `startP2PGame()`.
3. `startP2PGame()` publishes the integrated server locally, starts the P2P relay, and calls `setLobbyGameServer` with
   the host's own SteamID in the `steamIDGameServer` param (the `ip`/`port` params are unused, always `0`).
4. `onLobbyGameCreated` only broadcasts to members already in the lobby at the moment `setLobbyGameServer` is called —
   it's *not* resent to anyone who joins afterward, which in practice is nearly everyone (hosting starts immediately on
   lobby creation, before an invite could realistically be accepted). So `onLobbyEnter` also calls
   `Steamed.joinHostedLobby()` for non-owners on every lobby entry, which polls `getLobbyGameServer` directly instead of
   waiting on the broadcast. Either path ends up in `Steamed.connectToHost`, which first sends a Steam auth ticket to
   the host over channel 2 (`SteamAuthHandshake`) and waits for an accepted/denied reply; only once accepted does it
   bind a local loopback listener and point `ConnectScreen.startConnecting` at that local port. There's also a manual
   "Join Host" button in `SteamLobbyScreen` using the same poll, as a visible retry.
5. On the host, `SteamNetwork.onP2PSessionRequest` only accepts sessions from Steam IDs that are current lobby members
   and not kicked. Accepting only lets the peer reach the handshake: the host runs `beginAuthSession` on its ticket, and
   once `onValidateAuthTicket` says OK, `SteamNetwork.bridgePeer` bridges the session to a loopback connection on the
   published server's port. `SteamP2PRelay` pumps bytes both ways between each bridged local socket and `sendP2PPacket`/
   `readP2PPacket`.

## Conventions

- Logic in Kotlin; Java only when a mixin needs it
- Keep comments minimal
- Use the SLF4J logger, not `println`
- Check `Steamed.lobbyId.isSome()` before `unwrap()`, which throws when empty
- Steam callbacks fire from the poller thread; hop to the client thread before touching Minecraft state
- Client-only mod: do not reference server-only classes
- New code goes in `common` unless it genuinely needs a loader-specific API (check by grepping for `net.fabricmc`/
  `net.neoforged` before assuming something needs a platform split — most things don't; steamworks4j and vanilla
  Minecraft APIs are both loader-agnostic already). If it does, add the platform-specific piece behind
  `IPlatformHelper`/`Services` rather than reaching for a whole new abstraction layer

## Gotchas

- Uses Spacewar (app ID 480), so lobbies are shared with every other 480 user. Steamed lobbies carry lobby data
  `steamed` (the host's mod version), `visibility` (`friends`/`private`), and `mc_version`/`mc_protocol` (the
  host's Minecraft; joining is gated on the protocol number), written in `onLobbyCreated`; anything that
  lists lobbies must check `steamed` first. `IPlatformHelper.modVersion()` is the per-loader source of the version
- Minecraft 26.x renames things often and moves ownership between classes (for example `GuiGraphicsExtractor`/
  `extractRenderState`; `Minecraft.screen` is gone — current screen is `Minecraft.gui.screen()`, set with
  `Minecraft.setScreenAndShow(Screen)` or, for a nullable close, `Minecraft.gui.setScreen(Screen?)`; chat is
  `Minecraft.gui.hud.chat`, not `Minecraft.gui.chat`). Don't trust older tutorials — run
  `./gradlew :fabric:genSourcesWithVineflower` and read the real decompiled source under
  `fabric/.gradle/loom-cache/minecraftMaven/.../*-sources.jar` (much faster than reconstructing signatures from
  `javap`). Both loaders build against the same official-mapped vanilla Minecraft, so signatures found this way apply to
  both
- Mixin injection points are string-matched; they break on Minecraft updates
- `IntegratedServer.getMaxPlayers()` is hardcoded to 8. Lobby sizes above that only work because
  `IntegratedServerMixin` overrides it with `Steamed.hostedMaxPlayers` while hosting
- `IntegratedServerMixin` makes `publishServer` bind to loopback and skip the LAN broadcast, but only while
  `Steamed.publishingForSteam` is set (around `startP2PGame`'s own publish), so vanilla "Open to LAN" behaves normally.
  A world the player already opened to LAN before creating a lobby stays on the LAN. Voice mods' own UDP servers aren't
  covered by this either: on an integrated server both Simple Voice Chat and Plasmo Voice bind the wildcard address,
  ignore their configured port, and move to the Minecraft port number when the world is published (which is why the
  integrations ask the running server, and only after publishing)
- Nothing gets bridged into the relays until `SteamAuthHandshake` has validated the peer's Steam ticket. Any new path
  that calls `relay.addPeer` on the host side must go through it too, or it bypasses auth. The handshake protocol isn't
  versioned: a joiner talking to a host on an older build just times out. The accepted reply carries the comma-separated
  voice ports the host bridged for that peer; the lobby game-server `ip`/`port` fields are unused (`0`)
- Steam doesn't send the lobby creator a `LobbyEnter` callback, only `LobbyCreated`. Anything that must run for every
  member on entering (like `SteamPlayers.publishSelf`) has to be called from `onLobbyCreated` as well
- Port 8080 on a machine running Steam is the Steam client's own CEF remote debugger (`steamwebhelper`). Never forward a
  loopback port to a joiner just because a config names it: `WebMapIntegration` checks the page actually belongs to the
  map, and the host only forwards ports it offered
- `SteamNetworking.closeP2PChannelWithUser(peer, channel)` closes just that one channel; `closeP2PSessionWithUser(peer)`
  closes the whole session, every channel included. `SteamP2PVoiceRelay` must only ever call the channel-scoped one — an
  idle voice bridge timing out is routine and must not take the live Minecraft connection (channel 0) down with it
- `SteamNetworking.readP2PPacket` does **not** advance the destination `ByteBuffer`'s position — don't `flip()` it
  afterward (that sets `limit=0` and throws `BufferUnderflowException`). Read the `read` bytes with an absolute
  `buffer.get(0, bytes)` instead. Found this by testing against a real Steam session; see M8 in `MILESTONES.md`.
  `SteamMatchmaking.getLobbyChatEntry` has the same quirk (it writes at the buffer's current position), so `clear()`
  before each call; and `sendLobbyChatMsg(SteamID, String)` sends a trailing NUL that the receiver has to trim
- The Steam overlay (and `activateGameOverlayInviteDialog`) needs Steam to have launched the process (it sets
  `LD_PRELOAD` for `gameoverlayrenderer.so`); it doesn't work for a mod launched through a regular Minecraft launcher
  unless that launcher itself was started as a Non-Steam Game from within Steam. `SteamFriendListScreen` sidesteps this
  for invites by calling `SteamMatchmaking.inviteUserToLobby` directly instead of relying on the overlay
- `GridLayout.arrangeElements()` (which `LinearLayout` wraps) sizes rows from *every* child's width unconditionally — it
  does not check `.visible`. An invisible widget still reserves its full width in the row, which throws off centering
  for anything else sharing that row. Don't add a widget and toggle `.visible` to hide it if something else might be
  centered alongside it in the same `LinearLayout`; only add the widget that should actually be there (see
  `SteamLobbyScreen`'s "Create Lobby"/"Join Host" toggle)
- For multi-loader Gradle specifics (exact plugin/version choices, why not Architectury, the `commonJava`/
  `commonResources` source-sharing mechanism), don't guess from generic tutorials — the WFPhantom/MultiLoader-Template
  GitHub repo has a `26.2` branch that was verified working and is what this project's `build-logic` is adapted from
- `:quilt:runClient` runs Quilt Loader under plain Fabric Loom, which only knows Fabric Loader. `quilt/build.gradle.kts`
  patches the gaps: excludes FLK's transitive `fabric-loader` from the runtime classpath (same package, different
  signer: `SecurityException`), adds quilt-loader's libraries by hand (its POM declares none; they're only in its
  `quilt_installer.json`), sets the run's main class to Quilt's `KnotClient`, and passes `loader.development`/
  `loader.classPathGroups` (Quilt ignores Loom's `fabric.*` properties). Bumping `quilt_loader_version` means re-checking
  that JSON's library list
