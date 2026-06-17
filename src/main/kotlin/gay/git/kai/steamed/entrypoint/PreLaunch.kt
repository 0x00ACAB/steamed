package gay.git.kai.steamed.entrypoint

import com.codedisaster.steamworks.*
import com.codedisaster.steamworks.SteamAPI.InitResult
import com.mojang.blaze3d.platform.InputConstants.KEY_F5
import gay.git.kai.steamed.SteamFriendsCallbackEx
import gay.git.kai.steamed.Steamed
import gay.git.kai.steamed.Steamed.Companion.friends
import gay.git.kai.steamed.Steamed.Companion.matchmaking
import gay.git.kai.steamed.Steamed.Companion.user
import gay.git.kai.steamed.Steamed.Companion.userStats
import gay.git.kai.steamed.tasks.SteamCallbackTask
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*


class PreLaunch : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        createAppid()

        val loader: SteamLibraryLoader = SteamLibraryLoaderLwjgl3()
        if (!SteamAPI.loadLibraries(loader)) LOGGER!!.error("Failed to load steam libraries")

        var result: InitResult? = null
        try {
            SteamAPI.init()
            result = SteamAPI.initEx()
        } catch (e: SteamException) {
            throw RuntimeException(e)
        }
        if (result != InitResult.OK) {
            LOGGER!!.error("SteamAPI.initEx() failed: {}", result.name)
            SteamAPI.printDebugInfo(System.err)
            return
        }

        SteamAPI.printDebugInfo(System.out)

        try {
            SteamAPI.restartAppIfNecessary(480)
        } catch (e: SteamException) {
            throw RuntimeException(e)
        }

        LOGGER!!.info("Registering user callback ...")
        user = SteamUser(userCallback)

        LOGGER.info("Register user stats callback ...")
        userStats = SteamUserStats(userStatsCallback)

        friends = SteamFriends(SteamFriendsCallbackEx())
        matchmaking = SteamMatchmaking(matchmakingCallback)

        LOGGER.info("Starting steam poller task")
        Steamed.timer?.schedule(SteamCallbackTask(), 0, 15)
    }

    fun createAppid(): Boolean {
        try {
            File(
                FabricLoader.getInstance().gameDir.toString() + "/steam_appid.txt"
            ).writeText("480")
            return true
        } catch (e: NullPointerException) {
            LOGGER!!.error("Error whilst initialising steam api (E01): could not create steam_appid.txt in the game directory")
            LOGGER.error(e.message)
            return false
        }
    }


    companion object {
        private val LOGGER: Logger? = LoggerFactory.getLogger("SteamP2P#PreLaunch")

        @JvmField
        val userCallback: SteamUserCallback = object : SteamUserCallback {
            override fun onGetTicketForWebApi(
                authTicket: SteamAuthTicket?,
                result: SteamResult,
                ticketData: ByteArray
            ) {
                println(
                    "auth ticket for web API: " + ticketData.size + " bytes" +
                            ", result=" + result.toString()
                )
            }
        }

        @JvmField
        val userStatsCallback: SteamUserStatsCallback = object : SteamUserStatsCallback {
            override fun onUserStatsReceived(gameId: Long, steamIDUser: SteamID, result: SteamResult) {
                println(
                    "User stats received: gameId=" + gameId + ", userId=" + steamIDUser.accountID +
                            ", result=" + result.toString()
                )

                val numAchievements: Int = userStats?.numAchievements!!
                println("Num of achievements: $numAchievements")

                for (i in 0..<numAchievements) {
                    val name: String? = userStats?.getAchievementName(i)
                    val achieved: Boolean = userStats?.isAchieved(name, false)!!
                    println("# " + i + " : name=" + name + ", achieved=" + (if (achieved) "yes" else "no"))
                }
            }

            override fun onUserStatsStored(gameId: Long, result: SteamResult) {
                println(
                    "User stats stored: gameId=" + gameId +
                            ", result=" + result.toString()
                )
            }

            override fun onUserStatsUnloaded(steamIDUser: SteamID) {
                println("User stats unloaded: userId=" + steamIDUser.accountID)
            }

            override fun onUserAchievementStored(
                gameId: Long, isGroupAchievement: Boolean, achievementName: String?,
                curProgress: Int, maxProgress: Int
            ) {
                println(
                    "User achievement stored: gameId=" + gameId + ", name=" + achievementName +
                            ", progress=" + curProgress + "/" + maxProgress
                )
            }

            override fun onLeaderboardFindResult(leaderboard: SteamLeaderboardHandle, found: Boolean) {
                println(
                    "Leaderboard find result: handle=" + leaderboard.toString() +
                            ", found=" + (if (found) "yes" else "no")
                )

                if (found) {
                    println(
                        "Leaderboard: name=" + userStats?.getLeaderboardName(leaderboard) +
                                ", entries=" + userStats?.getLeaderboardEntryCount(leaderboard)
                    )

                    Steamed.currentLeaderboard = leaderboard
                }
            }
        }

        @JvmField
        val matchmakingCallback: SteamMatchmakingCallback = object : SteamMatchmakingCallback {
            private val chatMessage: ByteBuffer = ByteBuffer.allocateDirect(4096)
            private val chatEntry: SteamMatchmaking.ChatEntry = SteamMatchmaking.ChatEntry()
            private val messageCharset: Charset = StandardCharsets.UTF_8

            override fun onFavoritesListChanged(
                ip: Int,
                queryPort: Int,
                connPort: Int,
                appID: Int,
                flags: Int,
                add: Boolean,
                accountID: Int
            ) {
                println("[onFavoritesListChanged]")
            }

            override fun onLobbyInvite(steamIDUser: SteamID, steamIDLobby: SteamID, gameID: Long) {
                println("Lobby invite received for " + steamIDLobby)
                println("  - from user: " + steamIDUser.accountID)
                println("  - for game: " + gameID)

                println("  - auto-joining lobby ...")

                Steamed.lobbies.put(SteamNativeHandle.getNativeHandle<SteamID?>(steamIDLobby), steamIDLobby)
                Steamed.lobbyId.some(steamIDLobby)
                matchmaking?.joinLobby(steamIDLobby)
            }

            override fun onLobbyEnter(
                steamIDLobby: SteamID?,
                chatPermissions: Int,
                blocked: Boolean,
                response: SteamMatchmaking.ChatRoomEnterResponse?
            ) {
                println("Lobby entered: " + steamIDLobby)
                println("  - response: " + response)

                val numMembers: Int = matchmaking?.getNumLobbyMembers(steamIDLobby)!!
                println("  - " + numMembers + " members in lobby")
                for (i in 0..<numMembers) {
                    val member: SteamID = matchmaking?.getLobbyMemberByIndex(steamIDLobby, i)!!
                    println("    - " + i + ": accountID=" + member.accountID)
                }
            }

            override fun onLobbyDataUpdate(steamIDLobby: SteamID?, steamIDMember: SteamID, success: Boolean) {
                println("Lobby data update for " + steamIDLobby)
                println("  - member: " + steamIDMember.accountID)
                println("  - success: " + success)
            }

            override fun onLobbyChatUpdate(
                steamIDLobby: SteamID?,
                steamIDUserChanged: SteamID,
                steamIDMakingChange: SteamID,
                stateChange: SteamMatchmaking.ChatMemberStateChange
            ) {
                println("Lobby chat update for " + steamIDLobby)
                println("  - user changed: " + steamIDUserChanged.accountID)
                println("  - made by user: " + steamIDMakingChange.accountID)
                println("  - state changed: " + stateChange.name)
            }

            override fun onLobbyChatMessage(
                steamIDLobby: SteamID?,
                steamIDUser: SteamID,
                entryType: SteamMatchmaking.ChatEntryType?,
                chatID: Int
            ) {
                println("Lobby chat message for " + steamIDLobby)
                println("  - from user: " + steamIDUser.accountID)
                println("  - chat entry type: " + entryType)
                println("  - chat id: #" + chatID)

                try {
                    val size: Int =
                        matchmaking?.getLobbyChatEntry(steamIDLobby, chatID, chatEntry, chatMessage)!!
                    println("Lobby chat message #" + chatID + " has " + size + " bytes")

                    val bytes = ByteArray(size)
                    chatMessage.get(bytes)

                    val message = String(bytes, messageCharset)

                    println("  - from user: " + chatEntry.steamIDUser.accountID)
                    println("  - chat entry type: " + chatEntry.chatEntryType)
                    println("  - content: \"" + message + "\"")
                } catch (e: SteamException) {
                    e.printStackTrace()
                }
            }

            override fun onLobbyGameCreated(
                steamIDLobby: SteamID?,
                steamIDGameServer: SteamID?,
                ip: Int,
                port: Short
            ) {
                println("[onLobbyGameCreated]")
            }

            override fun onLobbyMatchList(lobbiesMatching: Int) {
                println("Found $lobbiesMatching matching lobbies.")

                Steamed.lobbies.clear()
                for (i in 0..<lobbiesMatching) {
                    val lobby: SteamID = matchmaking?.getLobbyByIndex(i)!!
                    Steamed.lobbies[SteamNativeHandle.getNativeHandle<SteamID?>(lobby)] = lobby
                    Steamed.lobbyId.some(lobby)
                }
            }

            override fun onLobbyKicked(
                steamIDLobby: SteamID?,
                steamIDAdmin: SteamID,
                kickedDueToDisconnect: Boolean
            ) {
                println("Kicked from lobby: $steamIDLobby")
                println("  - by user (admin): " + steamIDAdmin.accountID)
                println("  - kicked due to disconnect: " + (if (kickedDueToDisconnect) "yes" else "no"))
            }

            override fun onLobbyCreated(result: SteamResult, steamId: SteamID) {
                println("Lobby created: $steamId")
                println("  - result: " + result.name)
                val instance: Minecraft = Minecraft.getInstance()
                val user: User = instance.user
                if (result != SteamResult.OK) return

                val handle: Long = SteamNativeHandle.getNativeHandle<SteamID?>(steamId)
                Steamed.lobbies[handle] = steamId
                Steamed.lobbyId.some(steamId)
                kv(steamId, "name", UUID.randomUUID().toString())
                kv(steamId, "max_players", "20")
                kv(steamId, "owner", user.name)
                kv(steamId, "owner_uuid", user.profileId.toString())

                instance.window.setTitle(
                    GLFW.glfwGetWindowTitle(instance.window.handle()) + " in a steam lobby"
                )

                Steamed.StartP2PGame()
                instance?.screen?.keyPressed(KeyEvent(KEY_F5, 0, 0))
            }

            override fun onFavoritesListAccountsUpdated(result: SteamResult?) {
                println("[onFavoritesListAccountsUpdated]")
            }
        }

        fun kv(id: SteamID?, k: String?, v: String?) {
            matchmaking?.setLobbyData(id, SteamMatchmakingKeyValuePair(k, v))
        }
    }
}
