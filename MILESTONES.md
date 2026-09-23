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
- [x] `onLobbyGameCreated` reads the host's IP/port (this fires for members already in the lobby when the host publishes, and for anyone joining afterward, since the game server is persistent lobby state)
- [x] Non-owners auto-connect via `ConnectScreen.startConnecting` when the callback fires; the owner ignores its own broadcast
- [x] Join failures are shown in the UI for free, since `ConnectScreen.startConnecting` already shows a disconnect screen on failure
- [x] Port is read as unsigned (`port.toInt() and 0xFFFF`) so hosts above 32767 connect correctly

### MVP hardening: create lobby -> invite -> join
Before starting M6, traced and fixed what was blocking the actual host -> invite -> join loop:
- [x] "Create Lobby" is disabled with a tooltip when there's no singleplayer world loaded (`hasSingleplayerServer()`), instead of silently creating an empty lobby with no server behind it (`startP2PGame` used to just log an error nobody would see)
- [x] `startP2PGame`'s failure path now also posts a system chat message, as a fallback for whatever the button gate doesn't catch
- [x] Fixed a bug where `startP2PGame` closed `SteamLobbyScreen` before `onLobbyCreated`'s F5 refresh ran, so the F5 landed on the parent screen (a no-op) instead of reopening a fresh lobby screen — the host was silently bounced back to the pause/title screen with a stale, disabled Invite button instead of seeing the now-active lobby
- [x] `onLobbyCreated` now hops to the client thread (`Minecraft.execute`) before touching `Minecraft`/window/screen state, matching `onLobbyGameCreated` and the threading convention in `CLAUDE.md` — it was previously doing all of this from the Steam callback poller thread
- [x] Smoke-tested: `./gradlew runClient` against a real, running Steam client reaches the title screen cleanly with Steam initialised and the poller running, no Steamed-related errors.
- [x] Found and fixed a real join-blocking bug: `UPnPublisher.publish` logged a port-mapping failure but still returned the external IP as if it had succeeded, so a router that rejected the mapping would silently get advertised to friends as a working host. It now returns `Option.none()` on mapping failure, and `startP2PGame` posts a host-facing chat warning ("check your router's UPnP settings") instead of quietly publishing an unreachable address.
- [x] Verified the exact join-side data path against the real Steam backend, not just by reading the code: wrote a standalone steamworks4j program (no Minecraft) that creates a real lobby under this machine's Steam account, calls `setLobbyGameServer` with a test IP and a port above 32767 (the case M4 fixed), and reads it back with `getLobbyGameServer`. Round-tripped bit-for-bit, confirming `onLobbyGameCreated`'s callback signature, `setLobbyGameServer`'s parameter order, and the unsigned-port math are all correct against Steam's actual servers, not just local arithmetic (separately confirmed with a JVM-level round-trip test of `IPv4.into`/`IPv4.from` on both regular and >=128-first-octet addresses).
- [x] Re-read `ConnectScreen.startConnecting`'s real decompiled source: it calls `minecraft.disconnectWithProgressScreen(false)` before switching screens, so a joiner who's mid-game in their own singleplayer world when the invite lands should have that session torn down cleanly rather than hitting undefined behavior — this resolves the "known risk" noted in the previous pass.

What's still genuinely unverified, and can't be from this environment: whether a *second* Steam identity actually receives `onLobbyGameCreated` and can open a socket to the host's advertised address — that needs a live two-account test with an actual friend, since there's no GUI automation here to drive a real join, and a single Steam login can't simulate being a non-owner lobby member. If a friend can't connect despite an invite going through, the most likely real-world cause is UPnP/router reachability (the class of bug just found and fixed for the *detection* side) rather than the Steamworks/connect logic itself, which is now about as verified as it can be without that second party.

## Planned

### M6: Lobby management
- [ ] Leave lobby and clear `lobbyId`; close the lobby when the world closes
- [ ] Implement Kick
- [ ] Remove the UPnP mapping on shutdown
- [ ] Lobby browser (`requestLobbyList` with a mod-specific filter, since app 480 is shared)

### M7: Access control
- [ ] Only lobby members can connect (auth ticket handshake or whitelist by Steam ID)
- [ ] Optional lobby types (private / friends / public)

### M8: Transport
- [ ] Decide between UPnP + TCP and Steam P2P relay (`SendP2PPacket`), which avoids port forwarding
- [ ] Fallback when no UPnP gateway is found (currently publishes `0.0.0.0`)

### M9: Release
- [ ] Document spacewar limitations
- [ ] Resolve the license
