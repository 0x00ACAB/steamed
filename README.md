# Steamed

A client-side Fabric + NeoForge (and experimental Quilt) mod that lets players host and join Minecraft worlds through Steam lobbies, using Steamworks (via steamworks4j) for matchmaking and Steam's own P2P relay for the connection — no port forwarding needed.

Steam from the steamed craft we're having.

## Status

Hosting and joining both work: create a friends-only or invite-only lobby from the pause menu or title screen, invite friends from the in-game picker, and Steamed publishes your integrated server and tunnels invited friends' connections over Steam P2P automatically. See `MILESTONES.md` for the full roadmap and known issues.

## Requirements

- The Steam client must be running to use any Steam features (dev client included)
- Minecraft 26.2, Java 25, and everyone in the lobby needs the same version of Steamed installed
- **Fabric**: Fabric Loader 0.19.5+ and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- **NeoForge**: NeoForge 26.2; no other dependencies (Kotlin is bundled)
- **Quilt** (experimental, less tested): Fabric Language Kotlin **1.13.14+kotlin.2.4.20** specifically, unless you're on a Quilt Loader 0.30.2 beta or newer. Fabric Language Kotlin 1.14.x requires `fabricloader` 0.19.5, and stable Quilt Loader (0.30.1) only provides 0.19.3, so Quilt refuses to load it. 1.13.14 ships the same Kotlin version and works with it

## Spacewar (app ID 480) limitations

Steamed doesn't have its own Steam app ID, so it borrows Spacewar, Valve's public test app. Things that follow from that:

- Your Steam friends will see you as "playing Spacewar" while Minecraft is running with Steamed, and the playtime counts towards Spacewar
- Every developer testing against app 480 shares the same lobby space. Steamed lobbies are friends-only or invite-only, so strangers can't join them
- Use Steamed's in-game invite and lobby screens. Steam's own "Join Game" / "Invite to Game" from the friends list is tied to app 480, so it can't launch Minecraft for you; if Minecraft is already running with Steamed, "Join Game" should join the friend's lobby
- The Steam overlay generally won't show in Minecraft (see Linux notes below), so all lobby management is in-game

## Linux notes

Inviting friends uses an in-game friend picker rather than the native Steam overlay invite dialog, so it works even though the Steam overlay itself generally doesn't render for a Minecraft launcher (Prism, MultiMC, the official launcher, etc.) unless that launcher was started as a Non-Steam Game from within the Steam client. That's a platform limitation of how the overlay is injected (`LD_PRELOAD`, set only for processes Steam itself launches), not something the mod can work around by itself. Everything Steamed does — lobby creation, invites, matchmaking — talks to the running Steam client over its regular API and doesn't need the overlay to render at all.

## Building

```
./gradlew :fabric:build
./gradlew :neoforge:build
```

## Running a dev client

```
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

NeoForge's first run populates its own asset cache separately from Fabric's and takes a while; later runs are fast.

## License

This project is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
