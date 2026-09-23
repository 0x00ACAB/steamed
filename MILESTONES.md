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

## Known issues

- `SteamLobbyScreen.tick` calls `lobbyId.unwrap()` outside a lobby, which throws after about 5 s on the screen
- Member refresh uses `n - 1` instead of `i - 1`, so every entry is the last member
- `IPv4.into` overflows for first octets >= 128 (uses `Double` math instead of shifts)
- `port.toShort()` wraps for ports above 32767 with no unsigned handling on the read side
- `Steamed.timer` (companion) is never assigned, so the poller in `PreLaunch` never runs. The instance timer starts regardless of init success and is not a daemon
- `restartAppIfNecessary` runs after init, and both `init()` and `initEx()` are called
- `onLobbyMatchList` overwrites `lobbyId` with every result
- License mismatch: `fabric.mod.json` says GPL v3, `LICENSE` and README say CC0
- README is still the Fabric template
- Debug `println` calls and unused steamworks4j demo callbacks in `PreLaunch`

## Planned

### M4: Stabilise
- [ ] Fix the issues above
- [ ] Gate the poller on successful init
- [ ] Replace `println` with the logger

### M5: Joining
- [ ] Handle `onLobbyGameCreated` / read `getLobbyGameServer` on the joiner
- [ ] Connect to the host from the lobby screen or automatically after joining
- [ ] Show join failures in the UI

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
