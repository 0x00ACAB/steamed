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

- [x] `net/SvcIntegration.kt`: asks the running SVC voice server for its bound port by reflection (`Voicechat.SERVER.getServer().getPort()`), so SVC is still not a compile-time dependency. Returns `null` if SVC isn't installed, its voice server isn't running, or the reflection fails. Never throws. (It originally read `port` from `config/voicechat/voicechat-server.properties`. That was wrong, see the correction below)
- [x] `net/SteamP2PVoiceRelay.kt`: a UDP sibling to `SteamP2PRelay`, kept as a fully separate class rather than folded into it — `MILESTONES.md`'s own M8 wake-ping bug (a stray byte on the shared channel corrupting the Minecraft connection) is a direct precedent for why voice traffic needs its own channel (1) and its own relay, not a shared one. Sends `Unreliable` instead of `Reliable`; the host side connects its local socket to a fixed remote (its own SVC server) while the joiner side leaves its socket unconnected and learns the remote from the first packet its local SVC client sends; an idle-timeout reaper (60s) closes a peer's voice bridge since `DatagramSocket` has no EOF signal. `closePeer()` calls `closeP2PChannelWithUser(peer, 1)`, never `closeP2PSessionWithUser` — a real correction caught before it shipped: the latter would tear down the whole session, including the live Minecraft channel, on something as routine as a voice idle-timeout
- [x] ~~The dead `port` field on `setLobbyGameServer`/`getLobbyGameServer` carries the host's detected SVC port to the joiner~~. Superseded by the Plasmo Voice work below: the voice ports now travel in the auth handshake's accepted reply, and `setLobbyGameServer`'s `ip`/`port` are both `0` again
- [x] Wired into `startP2PGame`/`connectToHost`/`joinHostedLobby` (`Steamed.kt`) and `onP2PSessionRequest`/`onP2PSessionConnectFail` (`SteamNetwork.kt`) — every voice-bridge failure path is logged and swallowed, never allowed to block or fail the Minecraft connection itself
- [x] On the joiner side, the local bridge binds on the *same port number* the host's SVC server uses, not an ephemeral one — necessary because SVC's client isn't told about any relay, it just tries to reach "the same host as Minecraft" on whatever port its own server told it about, so something has to be listening there
- [x] Verified against real Steam, not just by reading the code: a standalone harness sent a marker packet to this machine's own Steam account on channel 1 and confirmed channel 0 never saw it (proving channel isolation), then round-tripped a UDP packet through the actual compiled `SteamP2PVoiceRelay` class — a fake local "SVC client" socket sent data in, and got the identical bytes back, having gone through real Steam P2P transport on channel 1
- [x] `./gradlew build` across all four modules (fabric/neoforge/quilt/common) and a fresh `:fabric:runClient` both confirmed no regression: Steam init succeeds, no Steamed-related exceptions, only the already-documented benign noise (flite narrator, X11 cursor, Realms auth)

**Correction (2026-10-16), from reading SVC's source (`henkelmax/simple-voice-chat` @ `f8d8146`) and running SVC 2.6.24+26.2 in `:fabric:runClient`:**
- [x] On an integrated server, SVC **ignores its configured `port`** and binds an ephemeral one (`Server`'s constructor: `port = 0` unless `server instanceof DedicatedServer`). Once the world is published, SVC's `IntegratedServerMixin` restarts it on the Minecraft server's port instead (seen in a later run: started on 58333, moved to the published port 40749). `startP2PGame` detects after publishing, so it gets the right one either way. The test run's voice server came up on UDP 46996 while the config said 24454, so the config-file approach advertised a port nothing listened on and voice could never have worked. `SvcIntegration.detectPort()` now reads the live port instead; the same run logged `detectPort() = 46996`, matching SVC's own "Voice chat server started at port 46996"
- [x] SVC's client **does** use the Minecraft connection's address: `InitializationData.parseAddress` falls back to the server IP the client connected to when `voice_host` is blank, and on a non-dedicated server `ServerVoiceEvents` always sends a blank `voice_host` (the config value is only used on dedicated servers). So the planned "write `voice_host=127.0.0.1`" fallback isn't needed, and wouldn't have done anything on an integrated server
- [x] SVC on an integrated server binds the wildcard address (`*:46996` in `ss -ulpn`), confirming the M7 note that voice stays reachable from the LAN

**Still not verified**: an actual host/join UI flow with a real SVC install and two people talking. The mechanism (channel isolation, the relay class, port discovery) is proven; real voice audio end to end is not.

### M6 (partial): leave the lobby and stop relays when the world closes
Until now there was no way to leave a lobby short of quitting the whole game — closing a world (host quitting to title, or a joiner disconnecting) left both P2P relays running and the Steam lobby occupied indefinitely.

- [x] `Steamed.leaveLobby()`: stops `p2pRelay` and `p2pVoiceRelay` (closing every open peer session), calls `matchmaking.leaveLobby` if currently in one, and clears `lobbyId`/`hostedPort`/`hostedVoicePort`
- [x] Wired into `TitleScreenMixin.init()` — reaching the title screen reliably means whatever world was running (hosted or joined) just closed, and this mixin was already injecting there. Unconditional and safe to call even when nothing was happening: guarded by `lobbyId.isSome()` and null-safe calls throughout, confirmed by a smoke test where Steam itself wasn't even running
- [x] Verified: `./gradlew build` across all four modules, and a `:fabric:runClient` smoke test confirmed the mixin still applies cleanly (no `MixinApplyError`) and the client reaches the title screen without exception

### M6 (partial): Kick
`ISteamMatchmaking` has no way for an owner to force a member out (only a self-directed `leaveLobby`), so Kick is enforced at the P2P layer, with a cooperative lobby-chat signal on top so the kicked client finds out immediately rather than timing out.

- [x] `Steamed.kick(peer)` (owner only): adds the peer to `Steamed.kickedPeers`, sends a `steamed:kick:<steamid64>` lobby chat message, then closes that peer's bridges on both relays
- [x] `SteamNetwork.onP2PSessionRequest` rejects anyone in `kickedPeers` before the lobby-membership check, so a kicked player's client can't just reconnect even if it ignores the signal. The list is cleared on `leaveLobby()`; re-inviting someone from `SteamFriendListScreen` lifts their kick
- [x] `Steamed.onLobbyChatMessage`: a kick message is only honored if it comes from the current lobby owner and names this client; the kicked client then leaves the lobby and, if it's connected to the lobby's host (matched by the `ServerData` name `connectToHost` uses, so an unrelated singleplayer world is never torn down), disconnects to a "Kicked" `DisconnectedScreen`
- [x] `screen/SteamKickScreen.kt`: member picker (every lobby member but yourself), same shape as `SteamFriendListScreen`. The lobby screen's Kick button opens it, and is now enabled for the owner whenever there's anyone else in the lobby (it was a no-op, always disabled, before)
- [x] Found and fixed an existing bug in `SteamInit.onLobbyChatMessage`, found by reading steamworks4j's source: `getLobbyChatEntry` writes at the buffer's current position without advancing it (same quirk as `readP2PPacket`, see M8), but the handler did a relative `get()` and never cleared the buffer, so each message was read from further into the 4 KB buffer until it eventually threw. It now `clear()`s first and reads absolutely. The `String` overload of `sendLobbyChatMsg` also sends `strlen + 1` bytes, so received messages carry a trailing NUL, which is now trimmed
- [x] `./gradlew build` passes for all four modules

**Not verified**: none of this has run against real Steam yet (the Steam client wasn't running when this was written). Needs a live two-account test: host kicks joiner -> joiner lands on the "Kicked" screen, host's server logs the disconnect, and the joiner's "Join Host" retry is rejected until the host re-invites them.

### M11b: Plasmo Voice, multi-port voice relay
From a survey of every Modrinth mod with 100K+ downloads (6,149 on 2026-10-16), keyword-filtered and then checked against source. Plasmo Voice (4.1M) was the only other popular 26.2 mod that opens its own port that joiners must reach. The web maps are listed under M11 followups; VoiceMessages and SVC add-ons ride Minecraft's own connection or SVC.

- [x] `net/PlasmoVoiceIntegration.kt`: reads `ModVoiceServer.INSTANCE.getUdpServer().getRemoteAddress()` by reflection (methods looked up on the `PlasmoVoiceServer`/`UdpServer` API interfaces). With its default config (`host.port = 0`) Plasmo binds an ephemeral UDP port, then on publish (its own `publishServer` mixin, synchronous `bind().sync()`) restarts on the Minecraft server's port number, `0.0.0.0`. Its client uses the Minecraft connection's address when the advertised IP is `0.0.0.0`, so the same "joiner binds the host's port number on loopback" trick works
- [x] `SteamP2PVoiceRelay` now carries several UDP ports on channel 1: each packet starts with the 2-byte host-side port, and bridges are keyed by (peer, port). The joiner binds the same port number locally, so both sides key on it. The channel is only closed once a peer has no bridges left
- [x] Voice ports travel in `SteamAuthHandshake`'s accepted reply (comma-separated, only the ports the host actually bridged for that peer) instead of the lobby game-server `port` field, which only held one. Only authenticated peers ever see them. `onAuthenticated` now returns that payload (null = failure), and `onAccepted` receives it. Wire-incompatible with the previous build, like the handshake itself
- [x] Verified in `:fabric:runClient` with Plasmo Voice `fabric-26.2-2.2.0-beta.1`, publishing the way `startP2PGame` does (loopback, `publishingForSteam`): Plasmo moved from 34419 to the published port 46589, `detectPort()` returned 46589, and its own client reconnected to `127.0.0.1:46589`
- [x] Verified the relay over real Steam P2P (self-loopback harness, compiled classes): two bridges on ports 47011/47012, three echo rounds each, every packet came back on the right socket byte-for-byte
- [x] Found: SVC and Plasmo Voice installed together both rebind UDP on the Minecraft port at publish, so whichever goes second fails (`Address already in use`). That's a conflict between those two mods, not Steamed; Steamed tunnels whichever one is running

**Not verified**: two people actually talking through Plasmo Voice over Steam.

### M7: Access control
- [x] **Steam auth ticket handshake** (`net/SteamAuthHandshake.kt`, its own P2P channel 2). Valve's P2P docs don't say whether a P2P sender's SteamID can be spoofed, and the auth docs recommend session tickets for client-to-client auth, so it's done properly rather than trusting the transport:
  - Joiner: `connectToHost` gets a ticket with `getAuthSessionTicket` and sends it once `onAuthSessionTicket` says it's ready. It then waits up to 20s for an accepted/denied reply, and only after acceptance binds the local listener and opens `ConnectScreen`. A denial or timeout is posted to chat with the host's reason
  - Host: `SteamNetwork.onP2PSessionRequest` still does the kicked / lobby-member / hosting checks, but accepting a session now only lets the peer reach the handshake. `beginAuthSession` runs on the ticket, and only an OK `onValidateAuthTicket` bridges the peer into the relays (`SteamNetwork.bridgePeer`, the code that used to run directly in `onP2PSessionRequest`). A client that skips the handshake gets nothing, because `SteamP2PRelay` drops channel 0 packets from peers it has no bridge for
  - A later non-OK `onValidateAuthTicket` for a bridged peer (ticket cancelled, peer logged off) unbridges them. A second ticket from the same peer (a "Join Host" retry) replaces the first session. `endAuthSession`/`cancelAuthTicket` run on kick, P2P connect failure, and `leaveLobby()`
  - **Wire-incompatible with earlier builds**: an older host never answers the ticket, so a joiner on this build times out with "The host didn't respond"
- [x] **The published server is no longer reachable from the LAN.** `IntegratedServerMixin` rebinds `publishServer`'s TCP listener to loopback and skips the LAN broadcast (`LanServerPinger`), but only while `Steamed.publishingForSteam` is set around `startP2PGame`'s own publish call. Vanilla "Open to LAN" is untouched. If the player had already opened the world to LAN before creating the lobby, `startP2PGame` reuses that port (it used to advertise a fresh port nothing listened on) and says the world is still on the LAN too
- [x] Lobby visibility: a Friends / Invite only cycle button next to "Create Lobby" (`LobbyType.FriendsOnly` / `LobbyType.Private`). No public lobbies or lobby browser: decided against, since Steamed is for playing with friends
- [x] Verified: `./gradlew build` across all modules, and a `:fabric:runClient` quick-play into a singleplayer world, so `IntegratedServer` actually loaded with `IntegratedServerMixin` applied, with no mixin errors. Steam wasn't running, so nothing Steam-side has been exercised
- [x] **App 480 tickets validate `OK` (2026-10-16)**: a standalone steamworks4j 1.10.0 harness against the real Steam client got a ticket (`getAuthSessionTicket` leaves the buffer at `position=0, limit=size`, so `buffer.get(0, bytes)` is right), `onAuthSessionTicket` returned `OK` with a `SteamAuthTicket` that `equals()` the issued one (steamworks4j compares native handles, so `SteamAuthHandshake`'s `access.ticket != ticket` check works), then `beginAuthSession` -> `OK` and `onValidateAuthTicket` -> `OK`, owner = the ticket's account. Caveat: one account validating its own ticket, not two different accounts
- [x] A member leaving the lobby (`onLobbyChatUpdate` with any state other than `Entered`) now makes the host end their auth session and close their bridges (`Steamed.onMemberLeft`, sharing `dropPeer` with kick). Before, only kick and P2P connect failure did, so a member who left kept a bridged connection and an open Steam auth session

**Not verified**: the handshake hasn't run between two different accounts. App 480 tickets validate `OK` (see above), so the remaining unknown is the P2P exchange itself. Also, the loopback bind only covers the Minecraft port: Simple Voice Chat runs its own UDP server, which this doesn't touch, so voice is still reachable from the LAN.

### M9 (partial): Release
- [x] README: per-loader requirements (including the Quilt / Fabric Language Kotlin pairing below) and a Spacewar limitations section
- [x] License: already consistent (CC0 in `LICENSE`, both `fabric.mod.json`s, and `neoforge.mods.toml`, as settled in M4)
- [x] `ConnectScreen` now reads "Establishing connection with <name>..." through `ConnectScreenAccessor`, an `@Invoker` on its private `updateStatus`. That targets a method name, not an injection point, so it's less fragile than the options noted before. Vanilla's own later statuses ("Logging in...", etc.) still replace it as the connection progresses
- [x] **Quilt fix (reported from a real install)**: Fabric Language Kotlin 1.14.x requires `fabricloader >=0.19.5`, and the latest stable Quilt Loader (0.30.1) only provides `fabricloader` 0.19.3 (only the 0.30.2/0.31 betas provide 0.19.5, and the module builds against 0.31.0-beta.4, which is why the build never showed it). The Quilt module now builds against FLK `1.13.14+kotlin.2.4.20` (same Kotlin 2.4.20 stdlib, requires only `>=0.16.9`), its `fabric.mod.json` requires FLK `>=1.13.14`, and the README tells Quilt players which FLK to install

### Lobby lifecycle, packaging, and robustness (unsorted, done while the kick/auth live tests are pending)
- [x] "Leave Lobby" button in the lobby screen. A joiner connected to the host is also disconnected from the world; the host keeps playing their world, just without the lobby
- [x] When the owner leaves (the button, or quitting to title via `TitleScreenMixin`), `leaveLobby()` first sends a `steamed:close` lobby chat message. Members then leave and disconnect with "The host closed the Steam lobby" instead of timing out, and Steam never hands ownership to a member who has no world to host. Shares the owner-only chat handling with kick (`Steamed.onLobbyChatMessage`)
- [x] `onLobbyEnter` checks the enter response: a failed join (full, doesn't exist, not allowed, ...) clears `lobbyId` and says why in chat, instead of carrying on as if inside the lobby
- [x] Steam's own friends-list "Join Game" (`onGameLobbyJoinRequested`, delivered while Minecraft is already running) joins that lobby. All joins now go through `Steamed.joinLobby`, which leaves any current lobby first
- [x] Lobby `name` metadata is the world's name instead of a random UUID. The unused `Steamed.lobbies` map is gone
- [x] Relay race: re-adding a peer (a "Join Host" retry, or an auth handshake retry) used to let the old bridge's send thread tear down the *new* one on exit. Both relays now only clean up if their socket is still the current one for that peer. The joiner also drops its previous voice bridge before rebinding the fixed voice port, which otherwise failed with the port still in use
- [x] Re-hosting the same world after leaving a lobby no longer claims the world is "also open to your LAN"; it remembers which server Steamed published itself (loopback-only)
- [x] Mod metadata (version, name, description, authors, license, URLs, contact) now comes from `gradle.properties` and is expanded into both `fabric.mod.json`s and `neoforge.mods.toml` at build time. Checked in all three built jars
- [x] Fabric's `fabric.mod.json` no longer requires Fabric API (nothing imports it), so Fabric players don't need to install it. Fabric Language Kotlin is required at `>=1.13.14` on both Fabric and Quilt, since the code needs the Kotlin 2.4.20 stdlib
- [x] `THIRD_PARTY_NOTICES` (steamworks4j's MIT notice, a pointer to Valve's SDK terms for the bundled `steam_api` libraries, and weupnp's LGPL, whose full text is already in its jar) ships in every jar next to `LICENSE_steamed`
- [x] Verified: `./gradlew build`, and a `:fabric:runClient` quick-play into a world with the templated metadata (loads as `steamed 0.1.0`, no Fabric API needed, no mixin errors). Not live-tested against Steam; `:neoforge:runClient` wasn't re-run after the metadata change

**Needs a live test**: whether Steam delivers the `steamed:close` message when it's sent right before `leaveLobby` (if not, members fall back to the old timeout), and friends-list "Join Game".

### Steam marker on nametags
- [x] Players who joined through Steam P2P get an orange diamond left of their nametag, so a host playing with LAN and Steam players at once can tell them apart. Each lobby member publishes its Minecraft UUID as lobby member data (`mc_uuid`); `SteamPlayers` collects the UUIDs of every member except the lobby owner, refreshed on lobby enter, member data updates and member changes, and cleared on leave. `AvatarRendererMixin` prefixes the name tag with a glyph from the `steamed:nametag` bitmap font
- [x] Verified in a Fabric dev client (2026-10-16) with two mannequins, one whose UUID was seeded into `SteamPlayers`: only that one got the diamond. NeoForge launches to the title screen with the mixin applied and no errors
- Only lobby members see the marker, since only they can read the lobby's member data. A LAN player isn't in the lobby, so sees nothing even with Steamed installed. The UUIDs are self-reported, so a lobby member could put the marker on someone else; it's cosmetic

## Planned

### M6: Lobby management
- [ ] Live-test Kick (see above). Postponed until a second account is available for testing

### M7 followups
- [ ] Live two-account test of the auth handshake (see M7 above), including a "Join Host" retry and a kick-then-reconnect attempt

### M9: Release
- [ ] Decide whether to keep weupnp: it's LGPL and only used by the dormant `UPnPublisher` fallback. `THIRD_PARTY_NOTICES` covers it for now, but dropping it (and the fallback) would be simpler
- [ ] Confirm a real Quilt install actually loads with FLK 1.13.14

### M11 followups
- [ ] Live two-account test with a real Simple Voice Chat install and with Plasmo Voice: voice audio actually reaching the other side
- [ ] Web maps (BlueMap 8100, squaremap / Pl3xMap 8080, all bind `0.0.0.0` and run on an integrated server): would need a joiner-side TCP forward plus an "open map" link, not a transparent tunnel. Not started

### Compatibility with other hosting mods (checked 2026-10-16 against source; 26.2 builds only)
- [ ] **e4mc (25.9M downloads) and e4all expose a Steam-hosted world publicly.** Both inject at the TAIL of `ServerConnectionListener.startTcpServerListener` and, with their default `hostEnabled = true`, start a public QUIC tunnel for *any* listener, including `startP2PGame`'s loopback-only publish. That bypasses both the loopback bind and the Steam auth gate. No mixin conflict; it's behavioral. Options: stop their session after Steamed's own publish (`link.e4mc.E4mcClient.session`, `link.e4all.E4allClient.session`, by reflection) and say so in chat, or only warn. Not decided
- [x] PolyPlus: wraps `channel`/`localAddress` in `startTcpServerListener` only while its own EOS P2P listen is pending. No conflict
- [x] OfflineLAN: sets `usesAuthentication` at the HEAD of `publishServer`, so a host with its offline option on also runs the Steam-hosted world in offline mode. Peers are still Steam-authenticated, so this only drops Minecraft's own account check
- [x] Essential: its `publishServer`-related mixins are compiled out for 26.2 (`unpublishServer` exists now). No conflict found
- [x] mcwifipnp: only acts through its own Open-to-LAN screen. No conflict
- World Host, Custom LAN, LAN Server Properties, OpenLink, EasyLAN: no 26.2 builds, not checked

### Steam marker followups
- [ ] Live two-account test: the marker shows on the joiner for the host, and on other joiners
- [ ] Possibly the same marker in the player list (Tab)

### M10 followups
- [ ] Actually host/join a lobby on the NeoForge build specifically, not just verify Steam init + mixins load
- [x] Re-ran `:neoforge:runClient` (2026-10-16) with Steam running: NeoForge loaded the templated `neoforge.mods.toml`, `SteamAPI` initialised from the `@Mod` constructor, the poller started, and the title screen came up with no Steamed errors
