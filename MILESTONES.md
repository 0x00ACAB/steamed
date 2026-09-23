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
- [x] `onLobbyGameCreated` reads the host's identity from `steamIDGameServer` (this fires for members already in the lobby when the host publishes, and for anyone joining afterward, since the game server is persistent lobby state) — originally read a direct IP:port instead; see M8 for why that changed
- [x] Non-owners connect over the Steam P2P bridge (M8) when the callback fires; the owner ignores its own broadcast
- [x] Join failures are shown in the UI for free, since `ConnectScreen.startConnecting` already shows a disconnect screen on failure

### M8: Transport
- [x] Decided: Steam P2P relay (`SendP2PPacket`/`ReadP2PPacket`) is the primary transport, not UPnP + direct TCP. It works through NAT, CGNAT, and routers with no UPnP support at all, via Steam's own automatic relay fallback when direct NAT punch-through fails — no port forwarding needed
- [x] `net/SteamP2PRelay.kt`: a generic TCP<->P2P bridge. One shared thread polls `isP2PPacketAvailable`/`readP2PPacket` and dispatches incoming bytes by sender SteamID; each bridged peer gets its own thread pumping bytes from a local TCP socket into `sendP2PPacket`
- [x] Host side (`SteamNetwork.onP2PSessionRequest`): only accepts a session from a Steam ID that's currently a member of the lobby (checked live via `getLobbyMemberByIndex`), then bridges it to a loopback connection on the published server's port
- [x] Joiner side (`onLobbyGameCreated`): binds a local loopback listener, sends a 1-byte ping to wake the P2P session early, and points `ConnectScreen.startConnecting` at `127.0.0.1:<local port>` instead of a real address
- [x] `setLobbyGameServer`'s `steamIDGameServer` parameter now carries the host's actual SteamID (it used to be an empty placeholder); the joiner reads it straight off `onLobbyGameCreated`. The `ip`/`port` fields are unused now (dummied to `0`)
- [x] Found and fixed a real bug while verifying this against real Steam calls: `readP2PPacket` doesn't advance the destination buffer's position, so the original receive loop's `buffer.flip()` set `limit=0` and threw `BufferUnderflowException` on the very first packet ever received — silently killing the receive thread on first use. Fixed with an absolute `buffer.get(0, bytes)` read instead of `flip()` + relative `get()`
- [x] Verified against real Steam, not just by reading the code: a standalone harness sent a packet to this machine's own Steam account and read it back, confirming the buffer bug and its fix; a second harness round-tripped data through the actual compiled `SteamP2PRelay` class via a local socket pair and real `sendP2PPacket`/`readP2PPacket` calls — bytes written into one end came back out the other, correctly, having gone through real Steam P2P transport
- [x] `net/UPnPublisher.kt`, `net/IServerPublisher.kt`, and `lib/IPv4.kt` are kept in the codebase, unused, as the documented fallback if the P2P relay proves unreliable in practice (the alternatives considered: try NAT-PMP/PCP alongside UPnP via `com.offbynull:portmapper`, a manual-forward fallback message, and preferring IPv6 when available)

Still unverified, same limitation as M5: whether a genuinely different Steam identity — not self-loopback — can pass `SteamNetwork`'s lobby-membership gate and successfully bridge into a real Minecraft server. Self-loopback validated the transport mechanics but not the cross-account accept flow or the `onP2PSessionRequest` handshake against a real second party. That's the next thing to test with an actual friend.

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

What's still genuinely unverified, and can't be from this environment: whether a *second* Steam identity actually receives `onLobbyGameCreated`, passes the lobby-membership gate, and can bridge a real connection through to a running Minecraft server — that needs a live two-account test with an actual friend, since there's no GUI automation here to drive a real join, and a single Steam login can't simulate being a non-owner lobby member. See M8 for how far that's been verified without a second party.

## Planned

### M6: Lobby management
- [ ] Leave lobby and clear `lobbyId`; close the lobby when the world closes
- [ ] Implement Kick
- [ ] Stop the P2P relay and close open sessions when the lobby/world closes
- [ ] Lobby browser (`requestLobbyList` with a mod-specific filter, since app 480 is shared)

### M7: Access control
- [ ] Auth ticket handshake, so the P2P accept gate isn't just "is a current lobby member" (see M8)
- [ ] Optional lobby types (private / friends / public)

### M9: Release
- [ ] Document spacewar limitations
- [ ] Resolve the license
