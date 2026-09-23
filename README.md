# Steamed

A client-side Fabric + NeoForge mod that lets players host and join Minecraft worlds through Steam lobbies, using Steamworks (via steamworks4j) for matchmaking and Steam's own P2P relay for the connection — no port forwarding needed.

Steam from the steamed craft we're having.

## Status

Hosting and joining both work: create a friends-only lobby from the pause menu or title screen, invite friends from the in-game picker, and Steamed publishes your integrated server and tunnels invited friends' connections over Steam P2P automatically. See `MILESTONES.md` for the full roadmap and known issues.

## Requirements

- The Steam client must be running to use any Steam features (dev client included)
- Uses Steam's Spacewar test app (ID 480), so lobbies are shared with everyone else testing against that app ID

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
