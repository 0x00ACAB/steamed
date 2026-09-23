# Steamed

A client-side Fabric mod that lets players host and join Minecraft worlds through Steam lobbies, using Steamworks (via steamworks4j) for matchmaking and UPnP for port forwarding.

Steam from the steamed craft we're having.

## Status

Hosting works: create a friends-only lobby from the pause menu or title screen, and Steamed publishes your integrated server, maps the port with UPnP, and shares your IP with the lobby. Joining is not implemented yet. See `MILESTONES.md` for the full roadmap and known issues.

## Requirements

- The Steam client must be running to use any Steam features (`./gradlew runClient` included)
- Uses Steam's Spacewar test app (ID 480), so lobbies are shared with everyone else testing against that app ID

## Building

```
./gradlew build
```

## Running a dev client

```
./gradlew runClient
```

## License

This project is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
