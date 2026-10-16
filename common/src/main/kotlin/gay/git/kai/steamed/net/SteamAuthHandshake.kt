package gay.git.kai.steamed.net

import com.codedisaster.steamworks.SteamAuth
import com.codedisaster.steamworks.SteamAuthTicket
import com.codedisaster.steamworks.SteamException
import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNetworking
import com.codedisaster.steamworks.SteamResult
import com.codedisaster.steamworks.SteamUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private const val AUTH_CHANNEL = 2
private const val MSG_TICKET: Byte = 1
private const val MSG_ACCEPTED: Byte = 2
private const val MSG_DENIED: Byte = 3
private const val ACCESS_TIMEOUT_MS = 20_000L

// Steam session ticket exchange on its own channel. The joiner sends a ticket and waits; the host only
// bridges a peer into the relays once Steam validates that ticket, so channel 0 traffic from anyone
// who skipped this is dropped by SteamP2PRelay (it has no bridge to deliver it to).
class SteamAuthHandshake(
    private val networking: SteamNetworking,
    private val user: SteamUser,
    // Host side: null to allow, otherwise the reason to refuse. Checked when a ticket arrives.
    private val authorize: (SteamID) -> String?,
    // Host side: bridge an authenticated peer into the relays. False if that failed.
    private val onAuthenticated: (SteamID) -> Boolean,
    // Host side: a previously valid ticket stopped being valid (cancelled, peer logged off, ...).
    private val onRevoked: (SteamID) -> Unit,
) {
    private val logger: Logger = LoggerFactory.getLogger("SteamAuthHandshake")

    private class PendingAccess(
        val host: SteamID,
        val onAccepted: () -> Unit,
        val onDenied: (String) -> Unit,
    ) {
        @Volatile
        var ticket: SteamAuthTicket? = null
        var ticketBytes: ByteArray? = null

        @Volatile
        var settled = false
    }

    private val ticketLock = Any()

    @Volatile
    private var pending: PendingAccess? = null

    @Volatile
    private var activeTicket: SteamAuthTicket? = null

    private val sessions: MutableSet<SteamID> = ConcurrentHashMap.newKeySet()
    private val bridged: MutableSet<SteamID> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        if (running) return
        running = true
        networking.allowP2PPacketRelay(true)
        thread(name = "steamed-p2p-auth-recv", isDaemon = true) { receiveLoop() }
    }

    // Joiner side. Exactly one of the callbacks runs, from a background thread.
    fun requestAccess(host: SteamID, onAccepted: () -> Unit, onDenied: (String) -> Unit) {
        start()
        cancelTicket()
        pending?.let { settle(it) { it.onDenied("Superseded by a newer connection attempt") } }

        val access = PendingAccess(SteamID(host), onAccepted, onDenied)
        pending = access

        val buffer = ByteBuffer.allocateDirect(8192)
        val size = IntArray(1)
        // Held until the ticket is recorded, so onAuthSessionTicket (poller thread) can't run in between.
        val ticket = synchronized(ticketLock) {
            val ticket = try {
                user.getAuthSessionTicket(buffer, size)
            } catch (e: SteamException) {
                logger.error("Failed to create a Steam auth ticket", e)
                null
            }
            if (ticket != null && ticket.isValid) {
                val bytes = ByteArray(size[0])
                buffer.get(0, bytes)
                access.ticketBytes = bytes
                access.ticket = ticket
                activeTicket = ticket
            }
            ticket
        }
        if (ticket == null || !ticket.isValid) {
            settle(access) { access.onDenied("Couldn't create a Steam auth ticket") }
            return
        }
        // The ticket is sent from onAuthSessionTicket, once Steam says it's ready to be validated.

        thread(name = "steamed-p2p-auth-timeout", isDaemon = true) {
            Thread.sleep(ACCESS_TIMEOUT_MS)
            settle(access) { access.onDenied("The host didn't respond") }
        }
    }

    fun onAuthSessionTicket(ticket: SteamAuthTicket, result: SteamResult) {
        val access = synchronized(ticketLock) { pending } ?: return
        if (access.ticket != ticket || access.settled) return

        if (result != SteamResult.OK) {
            logger.error("Steam auth ticket failed: {}", result)
            settle(access) { access.onDenied("Steam refused to issue an auth ticket ($result)") }
            return
        }

        val bytes = access.ticketBytes ?: return
        send(access.host, MSG_TICKET, bytes)
        logger.info("Sent auth ticket to host {}", access.host.accountID)
    }

    fun onValidateAuthTicket(peer: SteamID, response: SteamAuth.AuthSessionResponse) {
        if (peer !in sessions) return

        if (response != SteamAuth.AuthSessionResponse.OK) {
            logger.warn("Auth ticket for {} is no longer valid: {}", peer.accountID, response)
            val wasBridged = bridged.remove(peer)
            endSession(peer)
            if (wasBridged) onRevoked(peer) else deny(peer, "Steam rejected your auth ticket ($response)")
            return
        }

        if (peer in bridged) return
        if (!onAuthenticated(peer)) {
            endSession(peer)
            deny(peer, "The host couldn't open a connection to its world")
            return
        }

        bridged.add(SteamID(peer))
        send(peer, MSG_ACCEPTED, ByteArray(0))
        logger.info("Authenticated {}", peer.accountID)
    }

    private fun receiveLoop() {
        val buffer = ByteBuffer.allocateDirect(16384)
        val sizeHolder = IntArray(1)
        val sender = SteamID()

        while (running) {
            if (!networking.isP2PPacketAvailable(AUTH_CHANNEL, sizeHolder)) {
                Thread.sleep(5)
                continue
            }

            buffer.clear()
            val read = try {
                networking.readP2PPacket(sender, buffer, AUTH_CHANNEL)
            } catch (e: SteamException) {
                logger.error("Failed to read an auth packet", e)
                continue
            }
            if (read <= 0) continue

            val payload = ByteArray(read - 1)
            buffer.get(1, payload)
            when (buffer.get(0)) {
                MSG_TICKET -> onTicket(SteamID(sender), payload)
                MSG_ACCEPTED -> fromHost(sender)?.let { settle(it) { it.onAccepted() } }
                MSG_DENIED -> fromHost(sender)?.let {
                    val reason = String(payload, StandardCharsets.UTF_8)
                    settle(it) { it.onDenied(reason) }
                }
                else -> logger.warn("Unknown auth message from {}", sender.accountID)
            }
        }
    }

    private fun fromHost(sender: SteamID): PendingAccess? = pending?.takeIf { it.host == sender }

    private fun onTicket(peer: SteamID, ticket: ByteArray) {
        authorize(peer)?.let {
            logger.warn("Refusing auth ticket from {}: {}", peer.accountID, it)
            deny(peer, it)
            return
        }

        // A second ticket from the same peer (a "Join Host" retry) replaces the first.
        if (sessions.contains(peer)) {
            if (bridged.remove(peer)) onRevoked(peer)
            endSession(peer)
        }

        val direct = ByteBuffer.allocateDirect(ticket.size)
        direct.put(ticket)
        direct.flip()
        val result = try {
            user.beginAuthSession(direct, peer)
        } catch (e: SteamException) {
            logger.error("beginAuthSession failed for {}", peer.accountID, e)
            null
        }

        if (result != SteamAuth.BeginAuthSessionResult.OK) {
            logger.warn("Refusing auth ticket from {}: {}", peer.accountID, result)
            deny(peer, "Invalid Steam auth ticket (${result ?: "error"})")
            return
        }

        sessions.add(peer)
        logger.info("Validating auth ticket from {}", peer.accountID)
    }

    private fun deny(peer: SteamID, reason: String) {
        send(peer, MSG_DENIED, reason.toByteArray(StandardCharsets.UTF_8))
    }

    private fun send(peer: SteamID, type: Byte, payload: ByteArray) {
        val direct = ByteBuffer.allocateDirect(payload.size + 1)
        direct.put(type)
        direct.put(payload)
        direct.flip()
        try {
            networking.sendP2PPacket(peer, direct, SteamNetworking.P2PSend.Reliable, AUTH_CHANNEL)
        } catch (e: SteamException) {
            logger.error("Failed to send an auth message to {}", peer.accountID, e)
        }
    }

    private inline fun settle(access: PendingAccess, action: () -> Unit) {
        synchronized(access) {
            if (access.settled) return
            access.settled = true
        }
        if (pending === access) pending = null
        action()
    }

    private fun endSession(peer: SteamID) {
        if (sessions.remove(peer)) user.endAuthSession(peer)
    }

    // Host side: forget a peer (kicked, disconnected).
    fun endPeer(peer: SteamID) {
        bridged.remove(peer)
        endSession(peer)
    }

    private fun cancelTicket() {
        activeTicket?.let { user.cancelAuthTicket(it) }
        activeTicket = null
    }

    fun stop() {
        pending?.let { settle(it) { it.onDenied("Left the lobby") } }
        cancelTicket()
        sessions.toList().forEach { endSession(it) }
        bridged.clear()
    }
}
