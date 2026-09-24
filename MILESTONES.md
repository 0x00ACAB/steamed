# Milestones

Status is based on reading the code, not on running the mod, except where noted.

## Done

### M0: Scaffold
- [x] Fabric Loom + Kotlin project for Minecraft 26.1.2, Java 25
- [x] Kotlin entrypoints (`main`, `client`, `preLaunch`) and Java mixins
- [x] Dependencies: steamworks4j (+ lwjgl3), weupnp (both bundled with `include`), Mod Menu
- [x] Client-only mod metadata and mixin config

### M1: Steamworks init
- [x] `steam_appid.txt` (480, Spacewar) written to the game dir at pre-launch
- [x] Native libraries loaded and `SteamAPI` initialised
- [x] `SteamUser`, `SteamUserStats`, `SteamFriends`, `SteamMatchmaking`, `SteamNetworking` stored in `Steamed.Companion`
- [x] Callback poller (`SteamCallbackTask`, 70 ms timer started on client init)

### M2: UI
- [x] Title screen shows Steam login state; clicking it opens the lobby screen
- [x] "Steam Lobby" button on the pause menu
- [x] `SteamLobbyScreen`: create lobby, invite, back, lobby name, member list (refreshed every 5 s), F5 to reload
- [x] Buttons disabled with tooltips when not in a lobby or not the owner
- [x] `SteamFriendListScreen`: in-game friend picker (select an online friend, click Invite) using `SteamMatchmaking.inviteUserToLobby` directly, instead of `activateGameOverlayInviteDialog` — works even when the Steam overlay isn't available (notably on Linux without launching through Steam; see README)

### M3: Lobby and hosting
- [x] Create a friends-only lobby (max 20) with metadata: `name`, `max_players`, `owner`, `owner_uuid`
- [x] Auto-join when a lobby invite is received
- [x] On lobby creation, publish the integrated server on a free port
- [x] UPnP gateway discovery, TCP port mapping, external IP lookup
- [x] Host IP and port written to the lobby with `setLobbyGameServer`
- [x] P2P session request/failure logging (`SteamNetwork`)
- [x] Update to Minecraft 26.2

### M4: Stabilise
- [x] `SteamLobbyScreen.tick` no longer calls `lobbyId.unwrap()` outside a lobby
- [x] Member refresh uses `i - 1` instead of `n - 1`
- [x] `IPv4.into` uses `Int` shifts instead of `Double` math, no longer overflows for octets >= 128
- [x] `Steamed.timer` is assigned (as a daemon `Timer`) in `PreLaunch` once init succeeds, and only scheduled there; the old always-on instance timer in `Steamed` is gone, so the poller is gated on successful init
- [x] `restartAppIfNecessary` runs right after loading libraries and before `initEx()`; the redundant `init()` call is gone; the app now exits when Steam is going to restart it
- [x] `onLobbyMatchList` no longer overwrites `lobbyId` with search results
- [x] License mismatch resolved: `fabric.mod.json` now says CC0-1.0, matching `LICENSE` and README
- [x] README rewritten to describe the mod instead of the Fabric template
- [x] `println` replaced with the SLF4J logger; unused steamworks4j demo callbacks (favorites list, web API auth ticket, achievements/leaderboards) removed from `PreLaunch`

### M5: Joining
- [x] `onLobbyGameCreated` reads the host's identity from `steamIDGameServer` — originally read a direct IP:port instead; see M8 for why that changed
- [x] Non-owners connect over the Steam P2P bridge (M8) when the callback fires; the owner ignores its own broadcast
- [x] Join failures are shown in the UI for free, since `ConnectScreen.startConnecting` already shows a disconnect screen on failure
- [x] **First real two-account test surfaced a real bug**: `LobbyGameCreated_t` only broadcasts to members who are already in the lobby at the moment `SetLobbyGameServer` is called — it is *not* resent to people who join afterward (confirmed against Valve's docs and a Steamworks.NET issue describing the same "fires once per lobby" behavior). Since hosting starts immediately on lobby creation with no separate "start hosting" step, a friend joining after an invite is the *normal* case, not an edge case — so the automatic connect essentially never fired in practice. Fixed two ways: `onLobbyEnter` now calls `Steamed.joinHostedLobby()` for non-owners, which polls `getLobbyGameServer` directly instead of waiting on the broadcast (works regardless of join order); and `SteamLobbyScreen` got a manual "Join Host" button using the same poll, as a visible fallback/retry a player can act on themselves

### M8: Transport
- [x] Decided: Steam P2P relay (`SendP2PPacket`/`ReadP2PPacket`) is the primary transport, not UPnP + direct TCP. It works through NAT, CGNAT, and routers with no UPnP support at all, via Steam's own automatic relay fallback when direct NAT punch-through fails — no port forwarding needed
- [x] `net/SteamP2PRelay.kt`: a generic TCP<->P2P bridge. One shared thread polls `isP2PPacketAvailable`/`readP2PPacket` and dispatches incoming bytes by sender SteamID; each bridged peer gets its own thread pumping bytes from a local TCP socket into `sendP2PPacket`
- [x] Host side (`SteamNetwork.onP2PSessionRequest`): only accepts a session from a Steam ID that's currently a member of the lobby (checked live via `getLobbyMemberByIndex`), then bridges it to a loopback connection on the published server's port
- [x] Joiner side (`onLobbyGameCreated`): binds a local loopback listener and points `ConnectScreen.startConnecting` at `127.0.0.1:<local port>` instead of a real address; the P2P session itself gets established implicitly by the first real byte the relay sends, once Minecraft's client connects locally and the bridge starts pumping
- [x] `setLobbyGameServer`'s `steamIDGameServer` parameter now carries the host's actual SteamID (it used to be an empty placeholder); the joiner reads it straight off `onLobbyGameCreated`. The `ip`/`port` fields are unused now (dummied to `0`)
- [x] Found and fixed a real bug while verifying this against real Steam calls: `readP2PPacket` doesn't advance the destination buffer's position, so the original receive loop's `buffer.flip()` set `limit=0` and threw `BufferUnderflowException` on the very first packet ever received — silently killing the receive thread on first use. Fixed with an absolute `buffer.get(0, bytes)` read instead of `flip()` + relative `get()`
- [x] Verified against real Steam, not just by reading the code: a standalone harness sent a packet to this machine's own Steam account and read it back, confirming the buffer bug and its fix; a second harness round-tripped data through the actual compiled `SteamP2PRelay` class via a local socket pair and real `sendP2PPacket`/`readP2PPacket` calls — bytes written into one end came back out the other, correctly, having gone through real Steam P2P transport
- [x] `net/UPnPublisher.kt`, `net/IServerPublisher.kt`, and `lib/IPv4.kt` are kept in the codebase, unused, as the documented fallback if the P2P relay proves unreliable in practice (the alternatives considered: try NAT-PMP/PCP alongside UPnP via `com.offbynull:portmapper`, a manual-forward fallback message, and preferring IPv6 when available)

A second live two-account test (after the M5 lobby-timing fix) got further — past the lobby-timing bug, into an actual P2P connect attempt — and found a second real bug: `connectToHost` sent a 1-byte "wake ping" over the same P2P channel used for real data, to shave a bit of latency off session setup. But the host's relay can't tell a wake ping from real Minecraft protocol bytes — it forwards everything it receives into the local socket connected to the real server. That stray leading byte corrupted the packet framing right at the start of the connection, which explains everything observed: the joiner's log stopping dead right after "Connecting to 127.0.0.1", and the host's log showing `onP2PSessionRequest` firing three separate times ~30s apart for the same account, alongside two separate lobby "Entered" events — a corrupt-then-timeout-then-retry loop. Fixed by removing the wake ping entirely; the relay's own first real send already establishes the P2P session implicitly, so it wasn't needed for correctness, only for a small latency win that wasn't worth the bug.

### MVP hardening: create lobby -> invite -> join
Before starting M6, traced and fixed what was blocking the actual host -> invite -> join loop:
- [x] "Create Lobby" is disabled with a tooltip when there's no singleplayer world loaded (`hasSingleplayerServer()`), instead of silently creating an empty lobby with no server behind it (`startP2PGame` used to just log an error nobody would see)
- [x] `startP2PGame`'s failure path now also posts a system chat message, as a fallback for whatever the button gate doesn't catch
- [x] Fixed a bug where `startP2PGame` closed `SteamLobbyScreen` before `onLobbyCreated`'s F5 refresh ran, so the F5 landed on the parent screen (a no-op) instead of reopening a fresh lobby screen — the host was silently bounced back to the pause/title screen with a stale, disabled Invite button instead of seeing the now-active lobby
- [x] `onLobbyCreated` now hops to the client thread (`Minecraft.execute`) before touching `Minecraft`/window/screen state, matching `onLobbyGameCreated` and the threading convention in `CLAUDE.md` — it was previously doing all of this from the Steam callback poller thread
- [x] Smoke-tested: `./gradlew runClient` against a real, running Steam client reaches the title screen cleanly with Steam initialised and the poller running, no Steamed-related errors.
- [x] Re-read `ConnectScreen.startConnecting`'s real decompiled source: it calls `minecraft.disconnectWithProgressScreen(false)` before switching screens, so a joiner who's mid-game in their own singleplayer world when the invite lands should have that session torn down cleanly rather than hitting undefined behavior — this resolves the "known risk" noted in the previous pass.

The next two items are from when UPnP + direct TCP was still the transport, before M8 replaced it with the Steam P2P relay — keeping them for the record, since `UPnPublisher` is still in the codebase as a fallback:
- [x] Found and fixed a join-blocking bug in the UPnP path: `UPnPublisher.publish` logged a port-mapping failure but still returned the external IP as if it had succeeded, so a router that rejected the mapping would silently get advertised to friends as a working host. It now returns `Option.none()` on mapping failure, and (at the time) `startP2PGame` posted a host-facing chat warning instead of quietly publishing an unreachable address
- [x] Verified the IP/port data path against the real Steam backend for that same UPnP-based flow: a standalone steamworks4j program wrote a test IP and a port above 32767 through `setLobbyGameServer` and read it back bit-for-bit with `getLobbyGameServer`, confirming the callback signature, parameter order, and unsigned-port math were all correct (separately confirmed with a JVM-level round-trip test of `IPv4.into`/`IPv4.from`)

**Confirmed working**: a real two-account test — a friend hosting, joining from a second account — successfully connected end to end after the M8/M5 fixes above (lobby-join timing, the P2P wake-ping corruption). This is the first time the full create lobby -> invite -> join loop has been verified with an actual second party, not self-loopback.

### M10: Multi-loader (Fabric + NeoForge, Quilt attempted)
Restructured into `common`/`fabric`/`neoforge` Gradle modules, targeting NeoForge specifically (not classic Forge — chosen since it's the actively-developed fork most current Forge-ecosystem mods target for a version this new). Not Architectury Loom — a Plan agent's research turned up an actively-maintained "MultiLoader-Template" pattern (plain `fabric-loom` + NeoForge's own `net.neoforged.moddev`, plus a hand-rolled `build-logic` convention plugin sharing source sets across modules) with a real, working branch for MC 26.2, which grounded the whole migration in verified specifics instead of generic Architectury tutorial knowledge.

- [x] 12 of 15 source files (everything except `Steamed.kt`, the old `PreLaunch.kt`, and `fabric.mod.json`) moved to `common` completely unchanged — confirmed via grep that only those files ever referenced anything Fabric-specific
- [x] `Steamed.kt` split: state/logic became a loader-agnostic `object` in `common`; the Fabric `ModInitializer`/`ClientModInitializer` shape moved to a two-line `fabric/.../SteamedFabric.kt` (their bodies carried no real logic to begin with)
- [x] `PreLaunch.kt` became `common/.../SteamInit.kt` (`fun initSteam()`), called from Fabric's `PreLaunchEntrypoint` as before, and from NeoForge's `@Mod` constructor (the earliest hook NeoForge offers — no literal equivalent to Fabric's very-early `preLaunch`, but early enough since nothing here needs to run before Minecraft's own bootstrap)
- [x] The one genuine platform-specific call (`FabricLoader.getInstance().gameDir`) became a `ServiceLoader`-based `IPlatformHelper` abstraction (matching the reference template's own convention) rather than pulling in Architectury API for a single method
- [x] Both mixins moved to `common` unchanged and are referenced from both `fabric.mod.json` and the new `neoforge.mods.toml` as the same shared `steamed.mixins.json` — no per-loader mixin files needed, since neither mixin is actually loader-specific
- [x] steamworks4j/steamworks4j-lwjgl3/weupnp are declared directly in each platform module (not routed through a shared binary dependency), since `common`'s source files get recompiled directly into each platform's own compilation rather than consumed as a jar — this sidestepped the native-bundling risk entirely: Fabric keeps using its already-proven `include()`, NeoForge dev-environment testing used a plain `implementation()` dependency (jarJar for production packaging is still a followup, see Planned)
- [x] ModMenu dropped — turned out to have zero functional integration already (no `ModMenuApi` implementation anywhere, just a cosmetic update-nag suppression flag), so nothing was actually lost
- [x] **The biggest open risk — does steamworks4j's native library loading work under NeoForge's classloader — verified directly, not assumed**: `:neoforge:runClient` produced `SteamAPI_Init(): Loaded '.../steamclient.so' OK` and `Steam API initialized: true`, identical to the Fabric side
- [x] Both mixins apply cleanly on NeoForge with zero `MixinApplyError`/`InvalidInjectionException` — including `PauseMenuMixin`'s MixinExtras `@Local` usage, since NeoForge bundles a compatible MixinExtras (0.5.4) natively
- [x] `:fabric:runClient` re-tested after all the NeoForge-focused work to confirm zero regression — identical clean startup to before the migration
- [x] NeoForge production packaging finished: steamworks4j/steamworks4j-lwjgl3/weupnp are `jarJar`'d into the real jar (`META-INF/jarjar/...`, same shape as Fabric's `META-INF/jars/...`) instead of only resolved via the dev-environment classpath. Re-verified with a fresh `:neoforge:runClient` after switching — same clean `SteamAPI_Init(...) OK` / `Steam API initialized: true` as before

Not yet done: an actual create-lobby/host test specifically on the NeoForge build (verified Steam init and mixins load, but hosting/joining itself has only been exercised on Fabric so far).

### Quilt: tried, builds, but not verified end to end
Added a `quilt` module the same way as `fabric`/`neoforge` — same `common` reuse, plain `net.fabricmc.fabric-loom` (not a separate Quilt Loom), `org.quiltmc:quilt-loader` instead of `fabric-loader`. Steamed doesn't call any actual Fabric API surface (confirmed during the NeoForge migration — `fabric-api` was only ever a declared dependency, never imported), so no QSL/QFAPI dependency was needed at all, which matters because **QSL hasn't been updated since 2025-09-21 and tops out at Minecraft 1.21.1** — it would have been a hard blocker for a mod actually using it.

- [x] `:quilt:build` succeeds — compiles cleanly against `common`, produces a real jar with `steamed.mixins.json`, both mixins, and steamworks4j/weupnp bundled via Loom's `include()` exactly like Fabric's
- [ ] `:quilt:runClient` does **not** work: `quilt-loader` transitively pulls in real `fabric-loader` (confirmed via `./gradlew :quilt:dependencies`), and Fabric Loom's own dev-environment tooling adds another copy independently regardless of dependency excludes — both define classes in the same `net.fabricmc.loader.impl.*` package with different jar signers, which throws `SecurityException: ... signer information does not match ...` at launch. This looks like it's specific to using *Loom's* dev-launch classpath assembly to simulate a Quilt runtime, not something a real end user's Quilt installation would hit (a real install only ever has quilt-loader present, never a second literal fabric-loader jar) — but that's a plausible theory, not something verified here
- The actual fix for this is Quilt's own Loom fork (`org.quiltmc:loom`), which exists but **only as a snapshot, last published 2025-10-31** — about as stale as QSL. Not a solid foundation to build further verification on right now
- Left the module in the tree since it builds a real jar and costs nothing to keep, but `:quilt:runClient` specifically is known-broken and there's no live-verified answer yet on whether the built jar actually works in a real Quilt install

### M11: Voice chat tunneling (Simple Voice Chat)
Companion mods that open their own network port (Simple Voice Chat first) can now have that traffic tunneled over Steam P2P the same way the Minecraft connection itself is (M8) — same session, a second channel, rather than a new negotiation. Confirmed via `javap` against the real `steamworks4j-1.10.0.jar` that P2P sessions are per-peer, not per-channel (`acceptP2PSessionWithUser(SteamID)`/`onP2PSessionRequest(SteamID)` carry no channel argument), so the existing lobby-membership accept gate in `SteamNetwork` already covers a second channel for free.

- [x] `net/SvcIntegration.kt`: reads SVC's port out of `config/voicechat/voicechat-server.properties` (`Services.PLATFORM.gameDir()`, the same helper `SteamInit.createAppid()` already uses); returns `null` if the file is absent (SVC not installed) or unreadable, falls back to the default `24454` if the file exists but the key doesn't. Never throws
- [x] `net/SteamP2PVoiceRelay.kt`: a UDP sibling to `SteamP2PRelay`, kept as a fully separate class rather than folded into it — `MILESTONES.md`'s own M8 wake-ping bug (a stray byte on the shared channel corrupting the Minecraft connection) is a direct precedent for why voice traffic needs its own channel (1) and its own relay, not a shared one. Sends `Unreliable` instead of `Reliable`; the host side connects its local socket to a fixed remote (its own SVC server) while the joiner side leaves its socket unconnected and learns the remote from the first packet its local SVC client sends; an idle-timeout reaper (60s) closes a peer's voice bridge since `DatagramSocket` has no EOF signal. `closePeer()` calls `closeP2PChannelWithUser(peer, 1)`, never `closeP2PSessionWithUser` — a real correction caught before it shipped: the latter would tear down the whole session, including the live Minecraft channel, on something as routine as a voice idle-timeout
- [x] The dead `port` field on `setLobbyGameServer`/`getLobbyGameServer` (always `0` since M8's switch away from direct TCP) now carries the host's detected SVC port to the joiner; `ip` stays unused
- [x] Wired into `startP2PGame`/`connectToHost`/`joinHostedLobby` (`Steamed.kt`) and `onP2PSessionRequest`/`onP2PSessionConnectFail` (`SteamNetwork.kt`) — every voice-bridge failure path is logged and swallowed, never allowed to block or fail the Minecraft connection itself
- [x] On the joiner side, the local bridge binds on the *same port number* the host's SVC server uses, not an ephemeral one — necessary because SVC's client isn't told about any relay, it just tries to reach "the same host as Minecraft" on whatever port its own server told it about, so something has to be listening there
- [x] Verified against real Steam, not just by reading the code: a standalone harness sent a marker packet to this machine's own Steam account on channel 1 and confirmed channel 0 never saw it (proving channel isolation), then round-tripped a UDP packet through the actual compiled `SteamP2PVoiceRelay` class — a fake local "SVC client" socket sent data in, and got the identical bytes back, having gone through real Steam P2P transport on channel 1
- [x] `./gradlew build` across all four modules (fabric/neoforge/quilt/common) and a fresh `:fabric:runClient` both confirmed no regression: Steam init succeeds, no Steamed-related exceptions, only the already-documented benign noise (flite narrator, X11 cursor, Realms auth)

**Not verified, stated plainly**: whether Simple Voice Chat's client actually defaults to "same host as the Minecraft connection" when its own `voice_host` config is blank — this is inferred from convention, not read from SVC's source, and there's no way to install/test real SVC in this environment. Also not verified: an actual host/join UI flow with a real SVC install and two people talking. The mechanism (channel isolation, the relay class itself, the plumbing wiring) is proven against real Steam P2P; real voice audio end to end is not.

### M6 (partial): leave the lobby and stop relays when the world closes
Until now there was no way to leave a lobby short of quitting the whole game — closing a world (host quitting to title, or a joiner disconnecting) left both P2P relays running and the Steam lobby occupied indefinitely.

- [x] `Steamed.leaveLobby()`: stops `p2pRelay` and `p2pVoiceRelay` (closing every open peer session), calls `matchmaking.leaveLobby` if currently in one, and clears `lobbyId`/`hostedPort`/`hostedVoicePort`
- [x] Wired into `TitleScreenMixin.init()` — reaching the title screen reliably means whatever world was running (hosted or joined) just closed, and this mixin was already injecting there. Unconditional and safe to call even when nothing was happening: guarded by `lobbyId.isSome()` and null-safe calls throughout, confirmed by a smoke test where Steam itself wasn't even running
- [x] Verified: `./gradlew build` across all four modules, and a `:fabric:runClient` smoke test confirmed the mixin still applies cleanly (no `MixinApplyError`) and the client reaches the title screen without exception

## Planned

### M6: Lobby management
- [ ] Implement Kick — note: `ISteamMatchmaking` has no owner-forces-member-out primitive (confirmed via `javap` against the real jar: only `leaveLobby(SteamID)` exists, which is self-directed). A real "Kick" would have to work at the P2P layer instead — close the target's session on both relays and maintain a ban list `SteamNetwork.onP2PSessionRequest` checks, since Steam lobby membership itself can't be revoked by the owner
- [ ] Lobby browser (`requestLobbyList` with a mod-specific filter, since app 480 is shared)

### M7: Access control
- [ ] Auth ticket handshake, so the P2P accept gate isn't just "is a current lobby member" (see M8)
- [ ] Optional lobby types (private / friends / public)

### M9: Release
- [ ] Document spacewar limitations
- [ ] Resolve the license
- [ ] Replace vanilla `ConnectScreen`'s generic "Connecting to the server..." text with "Establishing connection with <name>...". `ConnectScreen`'s `status` field is private and there's no hook to set it from outside, so this needs either a mixin (fragile — injection points break on MC updates, per the Gotchas above) or a small custom screen instead of calling `ConnectScreen.startConnecting` directly. In the meantime, `connectToHost` posts a chat message naming the host right before `ConnectScreen` opens, as a stopgap

### M11 followups
- [ ] Live two-account test with a real Simple Voice Chat install: confirm the port value actually flows host -> lobby -> joiner (checkable from logs alone) and, separately, that real voice audio works
- [ ] If SVC's client doesn't default to "same host as Minecraft" for voice when `voice_host` is blank: have `SvcIntegration` write `voice_host=127.0.0.1` into the host's config, but only when that key is currently blank (never override a deliberate setting) — don't build this speculatively, only after a live test shows it's actually needed
- [ ] Extend the same per-channel tunneling pattern to other companion mods that open their own port, once SVC itself is confirmed working end to end

### M10 followups
- [ ] `jarJar` steamworks4j/steamworks4j-lwjgl3/weupnp into the NeoForge production jar (dev-environment `implementation()` was enough to verify native loading works at all, but isn't how the shipped jar should bundle them)
- [ ] Actually host/join a lobby on the NeoForge build specifically, not just verify Steam init + mixins load
- [ ] `neoforge.mods.toml`'s version/description/etc. are hardcoded literals mirroring `fabric.mod.json`, same as the original single-module setup — neither actually reads from `gradle.properties` at build time; fine for now but would drift if one gets updated without the other
