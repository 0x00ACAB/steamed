"""Temporarily patches the mod with demo players and a screenshot hook (tools/shoot-hook.kt).

    python3 tools/demo_patch.py           # apply
    ./gradlew :fabric:runClient --args='--quickPlaySingleplayer "New World" --fullscreen'
    python3 tools/demo_patch.py --revert  # restore from git (always do this before committing)

The hook builds a small camp in the world, takes the screenshots into fabric/run/screenshots,
and logs "STEAMED-SHOT done". Copy those into raw/ (see README) and run tools/build.py.
"""
import pathlib
import shutil
import subprocess
import sys

R = pathlib.Path(__file__).resolve().parents[3]
TOOLS = pathlib.Path(__file__).resolve().parent
PATCHED = [
    'common/src/main/kotlin/xyz/kkaii/steamed/Steamed.kt',
    'common/src/main/kotlin/xyz/kkaii/steamed/screen/SteamLobbyScreen.kt',
    'common/src/main/kotlin/xyz/kkaii/steamed/screen/SteamFriendListScreen.kt',
    'fabric/src/main/kotlin/xyz/kkaii/steamed/fabric/SteamedFabric.kt',
]
DEMO = R / 'common/src/main/kotlin/xyz/kkaii/steamed/DemoData.kt'

if '--revert' in sys.argv:
    subprocess.run(['git', 'checkout', '--', *PATCHED], cwd=R, check=True)
    DEMO.unlink(missing_ok=True)
    print('reverted')
    sys.exit(0)
DEMO.write_text('''package xyz.kkaii.steamed

import com.codedisaster.steamworks.SteamFriends
import com.codedisaster.steamworks.SteamID
import java.util.UUID

// TEMPORARY: demo data for store-page screenshots. Never commit.
object DemoData {
    @Volatile var enabled = false
    private fun id(n: Long) = SteamID.createFromNativeHandle(76561197960265728L + n)
    data class Person(val id: SteamID, val name: String, val uuid: UUID)
    val people: List<Person> by lazy {
        listOf(
            Person(Steamed.user?.steamID ?: id(1), "Robin", UUID.fromString("3f9c2a1e-7b44-4d2a-9c1e-2b7d8e4f6a10")),
            Person(id(1001), "Kai", UUID.fromString("a81b5c3d-1e2f-4a6b-8c9d-0e1f2a3b4c5d")),
            Person(id(1002), "Sam", UUID.fromString("5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a")),
            Person(id(1003), "Ari", UUID.fromString("c0ffee00-1234-4abc-9def-0123456789ab")),
        )
    }
    val friends: List<Pair<Person, SteamFriends.PersonaState>> by lazy {
        listOf(
            people[1] to SteamFriends.PersonaState.Online,
            people[2] to SteamFriends.PersonaState.Online,
            people[3] to SteamFriends.PersonaState.Away,
            Person(id(1004), "Jules", UUID.fromString("0badf00d-4444-4ccc-8aaa-123412341234")) to SteamFriends.PersonaState.Online,
        )
    }
    private fun all() = people + friends.map { it.first }
    fun uuidOf(id: SteamID): UUID? = if (enabled) all().firstOrNull { it.id == id }?.uuid else null
    fun nameOf(id: SteamID): String? = if (enabled) all().firstOrNull { it.id == id }?.name else null
}
''')
def sub(path, pairs):
    p = R/path; s = p.read_text()
    for a, b in pairs:
        assert a in s, (path, a[:60]); s = s.replace(a, b)
    p.write_text(s)
sub('common/src/main/kotlin/xyz/kkaii/steamed/screen/SteamLobbyScreen.kt', [
 ('''            val current = (0..<matchmaking.getNumLobbyMembers(lobby)).map { matchmaking.getLobbyMemberByIndex(lobby, it) }''',
  '''            val current = if (xyz.kkaii.steamed.DemoData.enabled) xyz.kkaii.steamed.DemoData.people.map { it.id } else (0..<matchmaking.getNumLobbyMembers(lobby)).map { matchmaking.getLobbyMemberByIndex(lobby, it) }'''),
 ('''                val owner = matchmaking.getLobbyOwner(lobby)
                // Host first''', '''                val owner = if (xyz.kkaii.steamed.DemoData.enabled) current.first() else matchmaking.getLobbyOwner(lobby)
                // Host first'''),
 ('''            private val name: String = Steamed.friends?.getFriendPersonaName(steamId) ?: "unknown"''',
  '''            private val name: String = xyz.kkaii.steamed.DemoData.nameOf(steamId) ?: Steamed.friends?.getFriendPersonaName(steamId) ?: "unknown"'''),
 ('''                if (head == null) head = SteamPlayers.memberUuid(lobby, steamId)?.let { ResolvableProfile.createUnresolved(it) }''',
  '''                if (head == null) head = (xyz.kkaii.steamed.DemoData.uuidOf(steamId) ?: SteamPlayers.memberUuid(lobby, steamId))?.let { ResolvableProfile.createUnresolved(it) }'''),
])
sub('common/src/main/kotlin/xyz/kkaii/steamed/screen/SteamFriendListScreen.kt', [
 ('''            } else {
                val count = friends.getFriendCount(SteamFriends.FriendFlags.Immediate)''', '''            } else if (xyz.kkaii.steamed.DemoData.enabled) {
                xyz.kkaii.steamed.DemoData.friends.forEach { (p, st) -> this.addEntry(Entry(p.id, p.name, st)) }
            } else {
                val count = friends.getFriendCount(SteamFriends.FriendFlags.Immediate)'''),
 ('''                if (head == null) head = SteamPlayers.friendUuid(steamId)?.let { SteamPlayers.head(it) }''',
  '''                if (head == null) head = (xyz.kkaii.steamed.DemoData.uuidOf(steamId) ?: SteamPlayers.friendUuid(steamId))?.let { SteamPlayers.head(it) }'''),
])
sub('common/src/main/kotlin/xyz/kkaii/steamed/Steamed.kt', [
 ('''        val name = friends?.getFriendPersonaName(inviter) ?: "Someone"''', '''        val name = DemoData.nameOf(inviter) ?: friends?.getFriendPersonaName(inviter) ?: "Someone"'''),
 ('''        val hostName = friends?.getFriendPersonaName(host) ?: "The host"''', '''        val hostName = DemoData.nameOf(host) ?: friends?.getFriendPersonaName(host) ?: "The host"'''),
])
shutil.copy(TOOLS / 'shoot-hook.kt', R / 'fabric/src/main/kotlin/xyz/kkaii/steamed/fabric/SteamedFabric.kt')
print("patched")
