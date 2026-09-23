# Milestones

Status is based on reading the code, not on running the mod.

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
- [x] `SteamLobbyScreen`: create lobby, invite (Steam overlay), back, lobby name, member list (refreshed every 5 s), F5 to reload
- [x] Buttons disabled with tooltips when not in a lobby or not the owner

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

## Known issues

- `port.toShort()` wraps for ports above 32767; there's no read side yet to add unsigned handling to (tracked under M5)

## Planned

### M5: Joining
- [ ] Handle `onLobbyGameCreated` / read `getLobbyGameServer` on the joiner
- [ ] Connect to the host from the lobby screen or automatically after joining
- [ ] Show join failures in the UI
- [ ] Read the port as unsigned (`port.toShort()` on the host side wraps above 32767)

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
