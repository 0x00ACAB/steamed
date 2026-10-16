![Steamed: your world, your Steam friends]({hero})

Host your singleplayer world in a Steam lobby and play with your Steam friends. Steam carries the connection, so there's no port forwarding, no public IP and no tunnelling service.

<br>

![Host in two clicks]({h-host})

Open your world to a Steam lobby from the pause menu and invite friends from an in-game list. Everyone in the lobby shows up with their Minecraft head, and the host can kick from any row.

![The Steam Lobby screen listing four players with their heads and a Kick button on each]({s-lobby})

<br>

![Invites ask first]({h-invites})

Your friend gets a notification and picks Accept or Decline. Every joiner proves who they are with a Steam auth ticket before they can reach your world, and your world isn't opened to your LAN while you host through Steam.

![An invite notification with Accept and Decline buttons]({s-invited})

<br>

![Spot the Steam players]({h-diamond})

Players who joined through Steam get an orange diamond on their name tag and in the Tab list, handy when Steam and LAN players share a world.

![Two players, one with an orange diamond before their name]({s-nametags})

<br>

![Voice and maps too]({h-extras})

[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) and [Plasmo Voice](https://modrinth.com/plugin/plasmo-voice) travel over Steam too. Running [BlueMap](https://modrinth.com/plugin/bluemap) or [squaremap](https://modrinth.com/plugin/squaremap)? Joiners get a chat link that opens your web map.

![A chat message with a link to open the host's web map]({s-maplink})

<br>

## How to play

1. Load a world, open the pause menu and click **Steam Lobby**
2. Click **Create Lobby** (friends-only or invite-only)
3. Click **Invite** and pick a friend from the list
4. Your friend clicks **Accept** in their own Steam Lobby screen and joins your world

## Requirements

- The **Steam client must be running**, signed in, on everyone's computer
- **Everyone in the lobby needs Steamed installed** (the same version)
- **Fabric**: [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- **NeoForge**: no other dependencies (Kotlin is bundled)
- **Quilt** (experimental): Fabric Language Kotlin **1.13.14+kotlin.2.4.20** specifically, unless you're on a Quilt Loader 0.30.2 beta or newer

## Things to know

Steamed doesn't have its own Steam app, so it uses Spacewar (app ID 480), Valve's public test app:

- Your Steam friends will see you as **"playing Spacewar"** while Minecraft is running with Steamed, and the playtime counts towards Spacewar
- Steam's own "Invite to Game" / "Join Game" from the friends list can't launch Minecraft for you. Use Steamed's in-game screens instead. If Minecraft is already running with Steamed, "Join Game" does join your friend's lobby
- Steamed lobbies are friends-only or invite-only, so strangers on app 480 can't join them

Steamed shares your Minecraft UUID with your Steam friends (as Steam rich presence and lobby data) so it can show player heads, and remembers the UUIDs it has seen in `config/steamed-known-players.properties`.

squaremap defaults to port 8080, which the Steam client already uses on your computer. Change its web server port (for example to 8081) or the map won't start.

If you also use **e4mc** or **e4all**, they will open a public tunnel to any world that's published, including one hosted through Steamed. Steamed warns you in chat; turn off their hosting if you only want Steam friends to join.

The screenshots use example players. Steamed is not affiliated with or endorsed by Valve. Steam and Steamworks are trademarks of Valve Corporation. Headlines are set in [Pixelify Sans](https://fonts.google.com/specimen/Pixelify+Sans) (SIL Open Font License).
