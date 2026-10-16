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
private const val HEADER_SIZE = 2
private const val PEER_IDLE_TIMEOUT_MS = 60_000L

// Mirrors SteamP2PRelay but over UDP on channel 1, kept fully separate so a stalled or
// misbehaving voice bridge can never corrupt or take down the Minecraft connection.
// Several UDP ports (one per voice mod) share the channel: every packet starts with the
// host-side port it belongs to, which the joiner also binds locally.
class SteamP2PVoiceRelay(private val networking: SteamNetworking) {
    private val logger: Logger = LoggerFactory.getLogger("SteamP2PVoiceRelay")

    private data class Key(val peer: SteamID, val port: Int)

    private class Bridge(val socket: DatagramSocket) {
        @Volatile
        var remote: InetSocketAddress? = null

        @Volatile
        var lastSeen: Long = System.currentTimeMillis()
    }

    private val bridges = ConcurrentHashMap<Key, Bridge>()

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
            if (read <= HEADER_SIZE) continue

            val port = buffer.getShort(0).toInt() and 0xFFFF
            val key = Key(SteamID(sender), port)
            val bridge = bridges[key]
            if (bridge == null) {
                logger.warn("Got a voice packet from {} for port {} with no bridge, dropping", sender.accountID, port)
                continue
            }

            // Joiner side hasn't heard from its local voice client yet - nothing to relay to.
            val remote = bridge.remote ?: continue

            try {
                val bytes = ByteArray(read - HEADER_SIZE)
                buffer.get(HEADER_SIZE, bytes)
                bridge.socket.send(DatagramPacket(bytes, bytes.size, remote))
                bridge.lastSeen = System.currentTimeMillis()
            } catch (e: IOException) {
                closeBridge(key)
            }
        }
    }

    private fun reapLoop() {
        while (running) {
            Thread.sleep(10_000)
            val now = System.currentTimeMillis()
            bridges.entries
                .filter { now - it.value.lastSeen > PEER_IDLE_TIMEOUT_MS }
                .forEach {
                    logger.info("Reaping idle voice bridge for {} on port {}", it.key.peer.accountID, it.key.port)
                    closeBridge(it.key)
                }
        }
    }

    // Pass a socket already connect()-ed to the fixed remote on the hosting side; leave it
    // unconnected on the joining side so the remote is learned from the local voice client's traffic.
    fun addPeer(peer: SteamID, port: Int, localSocket: DatagramSocket) {
        val key = Key(SteamID(peer), port)
        val bridge = Bridge(localSocket)
        if (localSocket.isConnected) {
            bridge.remote = localSocket.remoteSocketAddress as InetSocketAddress
        }
        bridges.put(key, bridge)?.socket?.close()

        thread(name = "steamed-p2p-voice-send-${peer.accountID}-$port", isDaemon = true) {
            val buffer = ByteArray(65536)
            try {
                while (running && !localSocket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    localSocket.receive(packet)

                    if (!localSocket.isConnected) {
                        bridge.remote = packet.socketAddress as InetSocketAddress
                    }
                    bridge.lastSeen = System.currentTimeMillis()

                    val direct = ByteBuffer.allocateDirect(HEADER_SIZE + packet.length)
                    direct.putShort(port.toShort())
                    direct.put(packet.data, packet.offset, packet.length)
                    direct.flip()
                    networking.sendP2PPacket(peer, direct, SteamNetworking.P2PSend.Unreliable, VOICE_CHANNEL)
                }
            } catch (e: IOException) {
                logger.info("Local voice bridge for {} on port {} closed: {}", peer.accountID, port, e.message)
            } finally {
                localSocket.close()
                if (bridges.remove(key, bridge)) closeChannelIfIdle(key.peer)
            }
        }
    }

    private fun closeBridge(key: Key) {
        bridges.remove(key)?.socket?.close()
        closeChannelIfIdle(key.peer)
    }

    private fun closeChannelIfIdle(peer: SteamID) {
        if (bridges.keys.none { it.peer == peer }) networking.closeP2PChannelWithUser(peer, VOICE_CHANNEL)
    }

    fun closePeer(peer: SteamID) {
        bridges.keys.filter { it.peer == peer }.forEach { key -> bridges.remove(key)?.socket?.close() }
        networking.closeP2PChannelWithUser(peer, VOICE_CHANNEL)
    }

    fun stop() {
        running = false
        bridges.keys.map { it.peer }.distinct().forEach { closePeer(it) }
    }
}
