package gay.git.kai.steamed.net

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNetworking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private const val VOICE_CHANNEL = 1
private const val PEER_IDLE_TIMEOUT_MS = 60_000L

// Mirrors SteamP2PRelay but over UDP on channel 1, kept fully separate so a stalled or
// misbehaving voice bridge can never corrupt or take down the Minecraft connection.
class SteamP2PVoiceRelay(private val networking: SteamNetworking) {
    private val logger: Logger = LoggerFactory.getLogger("SteamP2PVoiceRelay")

    private class Peer(val socket: DatagramSocket) {
        @Volatile
        var remote: InetSocketAddress? = null

        @Volatile
        var lastSeen: Long = System.currentTimeMillis()
    }

    private val peers = ConcurrentHashMap<SteamID, Peer>()

    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        if (running) return
        running = true
        networking.allowP2PPacketRelay(true)
        thread(name = "steamed-p2p-voice-recv", isDaemon = true) { receiveLoop() }
        thread(name = "steamed-p2p-voice-reap", isDaemon = true) { reapLoop() }
    }

    private fun receiveLoop() {
        val buffer = ByteBuffer.allocateDirect(65536)
        val sizeHolder = IntArray(1)
        val sender = SteamID()

        while (running) {
            if (!networking.isP2PPacketAvailable(VOICE_CHANNEL, sizeHolder)) {
                Thread.sleep(5)
                continue
            }

            buffer.clear()
            val read = networking.readP2PPacket(sender, buffer, VOICE_CHANNEL)
            if (read <= 0) continue

            val peer = peers[sender]
            if (peer == null) {
                logger.warn("Got a voice packet from {} with no bridged connection, dropping", sender.accountID)
                continue
            }

            val remote = peer.remote
            if (remote == null) {
                // Joiner side hasn't heard from its local SVC client yet - nothing to relay to.
                continue
            }

            try {
                val bytes = ByteArray(read)
                buffer.get(0, bytes)
                peer.socket.send(DatagramPacket(bytes, bytes.size, remote))
                peer.lastSeen = System.currentTimeMillis()
            } catch (e: IOException) {
                closePeer(sender)
            }
        }
    }

    private fun reapLoop() {
        while (running) {
            Thread.sleep(10_000)
            val now = System.currentTimeMillis()
            peers.entries
                .filter { now - it.value.lastSeen > PEER_IDLE_TIMEOUT_MS }
                .forEach {
                    logger.info("Reaping idle voice bridge for {}", it.key.accountID)
                    closePeer(it.key)
                }
        }
    }

    // Pass a socket already connect()-ed to the fixed remote on the hosting side; leave it
    // unconnected on the joining side so the remote is learned from the local SVC client's traffic.
    fun addPeer(peer: SteamID, localSocket: DatagramSocket) {
        val entry = Peer(localSocket)
        if (localSocket.isConnected) {
            entry.remote = localSocket.remoteSocketAddress as InetSocketAddress
        }
        peers.put(peer, entry)?.socket?.close()

        thread(name = "steamed-p2p-voice-send-${peer.accountID}", isDaemon = true) {
            val buffer = ByteArray(65536)
            try {
                while (running && !localSocket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    localSocket.receive(packet)

                    if (!localSocket.isConnected) {
                        entry.remote = packet.socketAddress as InetSocketAddress
                    }
                    entry.lastSeen = System.currentTimeMillis()

                    val direct = ByteBuffer.allocateDirect(packet.length)
                    direct.put(packet.data, packet.offset, packet.length)
                    direct.flip()
                    networking.sendP2PPacket(peer, direct, SteamNetworking.P2PSend.Unreliable, VOICE_CHANNEL)
                }
            } catch (e: IOException) {
                logger.info("Local voice bridge for {} closed: {}", peer.accountID, e.message)
            } finally {
                localSocket.close()
                if (peers.remove(peer, entry)) networking.closeP2PChannelWithUser(peer, VOICE_CHANNEL)
            }
        }
    }

    fun closePeer(peer: SteamID) {
        peers.remove(peer)?.socket?.close()
        networking.closeP2PChannelWithUser(peer, VOICE_CHANNEL)
    }

    fun stop() {
        running = false
        peers.keys.toList().forEach { closePeer(it) }
    }
}
