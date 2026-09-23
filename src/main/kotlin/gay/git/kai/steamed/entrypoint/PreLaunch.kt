package gay.git.kai.steamed.entrypoint

import com.codedisaster.steamworks.*
import com.codedisaster.steamworks.SteamAPI.InitResult
import com.mojang.blaze3d.platform.InputConstants.KEY_F5
import gay.git.kai.steamed.tasks.SteamFriendsCallbackEx
import gay.git.kai.steamed.Steamed
import gay.git.kai.steamed.Steamed.Companion.friends
import gay.git.kai.steamed.Steamed.Companion.matchmaking
import gay.git.kai.steamed.Steamed.Companion.networking
import gay.git.kai.steamed.Steamed.Companion.user
import gay.git.kai.steamed.Steamed.Companion.userStats
import gay.git.kai.steamed.net.SteamNetwork
import gay.git.kai.steamed.net.SteamP2PRelay
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

        try {
            if (SteamAPI.restartAppIfNecessary(480)) {
                LOGGER!!.info("Restarting through Steam, exiting this instance")
                kotlin.system.exitProcess(0)
            }
        } catch (e: SteamException) {
            throw RuntimeException(e)
        }

        val result: InitResult
        try {
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

        LOGGER!!.info("Registering user callback ...")
        user = SteamUser(userCallback)

        LOGGER.info("Register user stats callback ...")
        userStats = SteamUserStats(userStatsCallback)

        friends = SteamFriends(SteamFriendsCallbackEx())
        matchmaking = SteamMatchmaking(matchmakingCallback)

        networking = SteamNetworking(SteamNetwork())
        Steamed.p2pRelay = SteamP2PRelay(networking!!)

        LOGGER.info("Starting steam poller task")
        Steamed.timer = Timer("steam-callback-poller", true)
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
        val userCallback: SteamUserCallback = object : SteamUserCallback {}

        @JvmField
        val userStatsCallback: SteamUserStatsCallback = object : SteamUserStatsCallback {
            override fun onUserStatsReceived(gameId: Long, steamIDUser: SteamID, result: SteamResult) {
                LOGGER!!.info("User stats received: gameId={}, userId={}, result={}", gameId, steamIDUser.accountID, result)
            }
        }

        @JvmField
        val matchmakingCallback: SteamMatchmakingCallback = object : SteamMatchmakingCallback {
            private val chatMessage: ByteBuffer = ByteBuffer.allocateDirect(4096)
            private val chatEntry: SteamMatchmaking.ChatEntry = SteamMatchmaking.ChatEntry()
            private val messageCharset: Charset = StandardCharsets.UTF_8

            override fun onLobbyInvite(steamIDUser: SteamID, steamIDLobby: SteamID, gameID: Long) {
                LOGGER!!.info("Lobby invite received for {} from user {} (game {}), auto-joining", steamIDLobby, steamIDUser.accountID, gameID)

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
                LOGGER!!.info("Lobby entered: {} (response={})", steamIDLobby, response)

                val numMembers: Int = matchmaking?.getNumLobbyMembers(steamIDLobby)!!
                LOGGER.info("  - {} members in lobby", numMembers)
                for (i in 0..<numMembers) {
                    val member: SteamID = matchmaking?.getLobbyMemberByIndex(steamIDLobby, i)!!
                    LOGGER.info("    - {}: accountID={}", i, member.accountID)
                }
            }

            override fun onLobbyDataUpdate(steamIDLobby: SteamID?, steamIDMember: SteamID, success: Boolean) {
                LOGGER!!.info("Lobby data update for {} (member={}, success={})", steamIDLobby, steamIDMember.accountID, success)
            }

            override fun onLobbyChatUpdate(
                steamIDLobby: SteamID?,
                steamIDUserChanged: SteamID,
                steamIDMakingChange: SteamID,
                stateChange: SteamMatchmaking.ChatMemberStateChange
            ) {
                LOGGER!!.info(
                    "Lobby chat update for {}: user {} changed by {} ({})",
                    steamIDLobby, steamIDUserChanged.accountID, steamIDMakingChange.accountID, stateChange.name
                )
            }

            override fun onLobbyChatMessage(
                steamIDLobby: SteamID?,
                steamIDUser: SteamID,
                entryType: SteamMatchmaking.ChatEntryType?,
                chatID: Int
            ) {
                try {
                    val size: Int =
                        matchmaking?.getLobbyChatEntry(steamIDLobby, chatID, chatEntry, chatMessage)!!

                    val bytes = ByteArray(size)
                    chatMessage.get(bytes)

                    val message = String(bytes, messageCharset)

                    LOGGER!!.info(
                        "Lobby chat message #{} for {} from {} ({}): \"{}\"",
                        chatID, steamIDLobby, chatEntry.steamIDUser.accountID, chatEntry.chatEntryType, message
                    )
                } catch (e: SteamException) {
                    LOGGER!!.error("Failed to read lobby chat message #{}", chatID, e)
                }
            }

            override fun onLobbyGameCreated(
                steamIDLobby: SteamID?,
                steamIDGameServer: SteamID?,
                ip: Int,
                port: Short
            ) {
                if (steamIDGameServer == null || steamIDGameServer == user?.steamID) return

                LOGGER!!.info("Lobby game created, host is {}", steamIDGameServer.accountID)
                Steamed.connectToHost(steamIDGameServer)
            }

            override fun onLobbyMatchList(lobbiesMatching: Int) {
                LOGGER!!.info("Found {} matching lobbies.", lobbiesMatching)

                Steamed.lobbies.clear()
                for (i in 0..<lobbiesMatching) {
                    val lobby: SteamID = matchmaking?.getLobbyByIndex(i)!!
                    Steamed.lobbies[SteamNativeHandle.getNativeHandle<SteamID?>(lobby)] = lobby
                }
            }

            override fun onLobbyKicked(
                steamIDLobby: SteamID?,
                steamIDAdmin: SteamID,
                kickedDueToDisconnect: Boolean
            ) {
                LOGGER!!.info(
                    "Kicked from lobby {} by admin {} (due to disconnect: {})",
                    steamIDLobby, steamIDAdmin.accountID, kickedDueToDisconnect
                )
            }

            override fun onLobbyCreated(result: SteamResult, steamId: SteamID) {
                LOGGER!!.info("Lobby created: {} (result={})", steamId, result.name)
                if (result != SteamResult.OK) return

                val handle: Long = SteamNativeHandle.getNativeHandle<SteamID?>(steamId)
                Steamed.lobbies[handle] = steamId
                Steamed.lobbyId.some(steamId)

                val instance: Minecraft = Minecraft.getInstance()
                instance.execute {
                    val user: User = instance.user
                    kv(steamId, "name", UUID.randomUUID().toString())
                    kv(steamId, "max_players", "20")
                    kv(steamId, "owner", user.name)
                    kv(steamId, "owner_uuid", user.profileId.toString())

                    instance.window.setTitle(
                        GLFW.glfwGetWindowTitle(instance.window.handle()) + " in a steam lobby"
                    )

                    Steamed.startP2PGame()
                    instance.gui.screen()?.keyPressed(KeyEvent(KEY_F5, 0, 0))
                }
            }
        }

        fun kv(id: SteamID?, k: String?, v: String?) {
            matchmaking?.setLobbyData(id, SteamMatchmakingKeyValuePair(k, v))
        }
    }
}
