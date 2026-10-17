package xyz.kkaii.steamed

import com.codedisaster.steamworks.*
import com.codedisaster.steamworks.SteamAPI.InitResult
import com.mojang.blaze3d.platform.InputConstants.KEY_F5
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.kkaii.steamed.Steamed.friends
import xyz.kkaii.steamed.Steamed.matchmaking
import xyz.kkaii.steamed.Steamed.networking
import xyz.kkaii.steamed.Steamed.user
import xyz.kkaii.steamed.Steamed.userStats
import xyz.kkaii.steamed.net.*
import xyz.kkaii.steamed.platform.Services
import xyz.kkaii.steamed.tasks.SteamCallbackTask
import xyz.kkaii.steamed.tasks.SteamFriendsCallbackEx
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

object SteamInit {
    private val LOGGER: Logger = LoggerFactory.getLogger("SteamP2P#PreLaunch")

    fun initSteam() {
        createAppid()

        val loader: SteamLibraryLoader = SteamLibraryLoaderLwjgl3()
        if (!SteamAPI.loadLibraries(loader)) LOGGER.error("Failed to load steam libraries")

        try {
            if (SteamAPI.restartAppIfNecessary(480)) {
                LOGGER.info("Restarting through Steam, exiting this instance")
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
            LOGGER.error("SteamAPI.initEx() failed: {}", result.name)
            SteamAPI.printDebugInfo(System.err)
            return
        }

        SteamAPI.printDebugInfo(System.out)

        LOGGER.info("Registering user callback ...")
        user = SteamUser(userCallback)

        LOGGER.info("Register user stats callback ...")
        userStats = SteamUserStats(userStatsCallback)

        friends = SteamFriends(SteamFriendsCallbackEx())
        matchmaking = SteamMatchmaking(matchmakingCallback)

        val network = SteamNetwork()
        networking = SteamNetworking(network)
        Steamed.p2pRelay = SteamP2PRelay(networking!!)
        Steamed.p2pVoiceRelay = SteamP2PVoiceRelay(networking!!)
        Steamed.p2pWebRelay = SteamP2PWebRelay(networking!!).apply { onMapsAvailable = Steamed::onMapsAvailable }
        Steamed.authHandshake = SteamAuthHandshake(
            networking!!, user!!,
            authorize = network::refusalReason,
            onAuthenticated = network::bridgePeer,
            onRevoked = network::unbridgePeer,
        )

        LOGGER.info("Starting steam poller task")
        Steamed.timer = Timer("steam-callback-poller", true)
        Steamed.timer?.schedule(SteamCallbackTask(), 0, 15)
    }

    private fun createAppid(): Boolean {
        try {
            File(
                Services.PLATFORM.gameDir() + "/steam_appid.txt"
            ).writeText("480")
            return true
        } catch (e: NullPointerException) {
            LOGGER.error("Error whilst initialising steam api (E01): could not create steam_appid.txt in the game directory")
            LOGGER.error(e.message)
            return false
        }
    }

    @JvmField
    val userCallback: SteamUserCallback = object : SteamUserCallback {
        override fun onAuthSessionTicket(authTicket: SteamAuthTicket, result: SteamResult) {
            Steamed.authHandshake?.onAuthSessionTicket(authTicket, result)
        }

        override fun onValidateAuthTicket(
            steamID: SteamID,
            authSessionResponse: SteamAuth.AuthSessionResponse,
            ownerSteamID: SteamID
        ) {
            Steamed.authHandshake?.onValidateAuthTicket(steamID, authSessionResponse)
        }
    }

    @JvmField
    val userStatsCallback: SteamUserStatsCallback = object : SteamUserStatsCallback {
        override fun onUserStatsReceived(gameId: Long, steamIDUser: SteamID, result: SteamResult) {
            LOGGER.info("User stats received: gameId={}, userId={}, result={}", gameId, steamIDUser.accountID, result)
        }
    }

    @JvmField
    val matchmakingCallback: SteamMatchmakingCallback = object : SteamMatchmakingCallback {
        private val chatMessage: ByteBuffer = ByteBuffer.allocateDirect(4096)
        private val chatEntry: SteamMatchmaking.ChatEntry = SteamMatchmaking.ChatEntry()
        private val messageCharset: Charset = StandardCharsets.UTF_8

        override fun onLobbyInvite(steamIDUser: SteamID, steamIDLobby: SteamID, gameID: Long) {
            LOGGER.info(
                "Lobby invite received for {} from user {} (game {})",
                steamIDLobby,
                steamIDUser.accountID,
                gameID
            )
            Steamed.onLobbyInvite(steamIDLobby, steamIDUser)
        }

        override fun onLobbyEnter(
            steamIDLobby: SteamID?,
            chatPermissions: Int,
            blocked: Boolean,
            response: SteamMatchmaking.ChatRoomEnterResponse?
        ) {
            LOGGER.info("Lobby entered: {} (response={})", steamIDLobby, response)

            if (response != SteamMatchmaking.ChatRoomEnterResponse.Success) {
                if (Steamed.lobbyId.isSome() && Steamed.lobbyId.unwrap() == steamIDLobby) Steamed.lobbyId.some(null)
                val instance = Minecraft.getInstance()
                instance.execute {
                    instance.gui.hud.chat.addClientSystemMessage(
                        Component.translatable("steamed.lobby.enter_failed", enterFailureReason(response))
                    )
                }
                return
            }

            if (steamIDLobby != null) SteamPlayers.publishSelf(steamIDLobby)
            SteamPlayers.refresh()

            val numMembers: Int = matchmaking?.getNumLobbyMembers(steamIDLobby)!!
            LOGGER.info("  - {} members in lobby", numMembers)
            for (i in 0..<numMembers) {
                val member: SteamID = matchmaking?.getLobbyMemberByIndex(steamIDLobby, i)!!
                LOGGER.info("    - {}: accountID={}", i, member.accountID)
            }

            if (matchmaking?.getLobbyOwner(steamIDLobby) != user?.steamID) {
                LOGGER.info("Checking whether the host is already hosting")
                Steamed.joinHostedLobby()
            }
        }

        override fun onLobbyDataUpdate(steamIDLobby: SteamID?, steamIDMember: SteamID, success: Boolean) {
            LOGGER.info(
                "Lobby data update for {} (member={}, success={})",
                steamIDLobby,
                steamIDMember.accountID,
                success
            )
            SteamPlayers.refresh()
        }

        override fun onLobbyChatUpdate(
            steamIDLobby: SteamID?,
            steamIDUserChanged: SteamID,
            steamIDMakingChange: SteamID,
            stateChange: SteamMatchmaking.ChatMemberStateChange
        ) {
            LOGGER.info(
                "Lobby chat update for {}: user {} changed by {} ({})",
                steamIDLobby, steamIDUserChanged.accountID, steamIDMakingChange.accountID, stateChange.name
            )
            if (steamIDLobby != null && stateChange != SteamMatchmaking.ChatMemberStateChange.Entered) {
                Steamed.onMemberLeft(steamIDLobby, steamIDUserChanged)
            }
            SteamPlayers.refresh()
        }

        override fun onLobbyChatMessage(
            steamIDLobby: SteamID?,
            steamIDUser: SteamID,
            entryType: SteamMatchmaking.ChatEntryType?,
            chatID: Int
        ) {
            try {
                // getLobbyChatEntry writes at the buffer's position without advancing it, same as readP2PPacket
                chatMessage.clear()
                val size: Int =
                    matchmaking?.getLobbyChatEntry(steamIDLobby, chatID, chatEntry, chatMessage)!!

                val bytes = ByteArray(size)
                chatMessage.get(0, bytes)

                val message = String(bytes, messageCharset).trimEnd('\u0000')

                LOGGER.info(
                    "Lobby chat message #{} for {} from {} ({}): \"{}\"",
                    chatID, steamIDLobby, chatEntry.steamIDUser.accountID, chatEntry.chatEntryType, message
                )

                if (steamIDLobby != null) Steamed.onLobbyChatMessage(steamIDLobby, chatEntry.steamIDUser, message)
            } catch (e: SteamException) {
                LOGGER.error("Failed to read lobby chat message #{}", chatID, e)
            }
        }

        override fun onLobbyGameCreated(
            steamIDLobby: SteamID?,
            steamIDGameServer: SteamID?,
            ip: Int,
            port: Short
        ) {
            if (steamIDGameServer == null || steamIDGameServer == user?.steamID) return

            LOGGER.info("Lobby game created, host is {}", steamIDGameServer.accountID)
            Steamed.connectToHost(steamIDGameServer)
        }

        override fun onLobbyMatchList(lobbiesMatching: Int) {
            LOGGER.info("Found {} matching lobbies.", lobbiesMatching)
        }

        override fun onLobbyKicked(
            steamIDLobby: SteamID?,
            steamIDAdmin: SteamID,
            kickedDueToDisconnect: Boolean
        ) {
            LOGGER.info(
                "Kicked from lobby {} by admin {} (due to disconnect: {})",
                steamIDLobby, steamIDAdmin.accountID, kickedDueToDisconnect
            )
        }

        override fun onLobbyCreated(result: SteamResult, steamId: SteamID) {
            LOGGER.info("Lobby created: {} (result={})", steamId, result.name)
            if (result != SteamResult.OK) return

            Steamed.lobbyId.some(steamId)
            // Steam doesn't send the creator a LobbyEnter, so publish here as well.
            SteamPlayers.publishSelf(steamId)

            val instance: Minecraft = Minecraft.getInstance()
            instance.execute {
                val user: User = instance.user
                kv(steamId, "name", instance.singleplayerServer?.worldData?.levelName ?: "${user.name}'s world")
                kv(steamId, "max_players", matchmaking?.getLobbyMemberLimit(steamId).toString())
                kv(steamId, "owner", user.name)
                kv(steamId, "owner_uuid", user.profileId.toString())
                // Marks the lobby as Steamed's among every other Spacewar (480) lobby, for FriendLobbies.
                kv(steamId, FriendLobbies.MARKER_KEY, Services.PLATFORM.modVersion())
                kv(steamId, FriendLobbies.VISIBILITY_KEY, FriendLobbies.visibilityOf(Steamed.creatingLobbyType))
                kv(steamId, FriendLobbies.MC_VERSION_KEY, SharedConstants.getCurrentVersion().name())
                kv(steamId, FriendLobbies.MC_PROTOCOL_KEY, SharedConstants.getProtocolVersion().toString())

                instance.window.setTitle(
                    GLFW.glfwGetWindowTitle(instance.window.handle()) + " in a steam lobby"
                )

                Steamed.startP2PGame()
                instance.gui.screen()?.keyPressed(KeyEvent(KEY_F5, 0, 0))
            }
        }
    }

    private fun enterFailureReason(response: SteamMatchmaking.ChatRoomEnterResponse?): Component = when (response) {
        SteamMatchmaking.ChatRoomEnterResponse.DoesntExist -> Component.translatable("steamed.lobby.enter_failed.doesnt_exist")
        SteamMatchmaking.ChatRoomEnterResponse.Full -> Component.translatable("steamed.lobby.enter_failed.full")
        SteamMatchmaking.ChatRoomEnterResponse.NotAllowed -> Component.translatable("steamed.lobby.enter_failed.not_allowed")
        SteamMatchmaking.ChatRoomEnterResponse.Banned -> Component.translatable("steamed.lobby.enter_failed.banned")
        SteamMatchmaking.ChatRoomEnterResponse.RatelimitExceeded -> Component.translatable("steamed.lobby.enter_failed.rate_limited")
        else -> Component.translatable("steamed.lobby.enter_failed.other", response?.name ?: "nothing")
    }

    private fun kv(id: SteamID?, k: String?, v: String?) {
        matchmaking?.setLobbyData(id, SteamMatchmakingKeyValuePair(k, v))
    }
}
