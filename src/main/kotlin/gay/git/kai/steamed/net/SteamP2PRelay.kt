package gay.git.kai.steamed.net

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNetworking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class SteamP2PRelay(private val networking: SteamNetworking) {
    private val logger: Logger = LoggerFactory.getLogger("SteamP2PRelay")
    private val channel = 0
    private val peers = ConcurrentHashMap<SteamID, Socket>()

    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        if (running) return
        running = true
        networking.allowP2PPacketRelay(true)
        thread(name = "steamed-p2p-recv", isDaemon = true) { receiveLoop() }
    }

    private fun receiveLoop() {
        val buffer = ByteBuffer.allocateDirect(65536)
        val sizeHolder = IntArray(1)
        val sender = SteamID()

        while (running) {
            if (!networking.isP2PPacketAvailable(channel, sizeHolder)) {
                Thread.sleep(5)
                continue
            }

            buffer.clear()
            val read = networking.readP2PPacket(sender, buffer, channel)
            if (read <= 0) continue

            val socket = peers[sender]
            if (socket == null) {
                logger.warn("Got a P2P packet from {} with no bridged connection, dropping", sender.accountID)
                continue
            }

            try {
                val bytes = ByteArray(read)
                buffer.get(0, bytes)
                socket.getOutputStream().write(bytes)
            } catch (e: IOException) {
                closePeer(sender)
            }
        }
    }

    fun addPeer(peer: SteamID, localSocket: Socket) {
        peers[peer] = localSocket
        thread(name = "steamed-p2p-send-${peer.accountID}", isDaemon = true) {
            val buffer = ByteArray(65536)
            try {
                val input = localSocket.getInputStream()
                while (running && !localSocket.isClosed) {
                    val read = input.read(buffer)
                    if (read < 0) break

                    val direct = ByteBuffer.allocateDirect(read)
                    direct.put(buffer, 0, read)
                    direct.flip()
                    networking.sendP2PPacket(peer, direct, SteamNetworking.P2PSend.Reliable, channel)
                }
            } catch (e: IOException) {
                logger.info("Local bridge connection for {} closed: {}", peer.accountID, e.message)
            } finally {
                closePeer(peer)
            }
        }
    }

    fun closePeer(peer: SteamID) {
        peers.remove(peer)?.let {
            try {
                it.close()
            } catch (e: IOException) {
                // already closed
            }
        }
        networking.closeP2PSessionWithUser(peer)
    }

    fun stop() {
        running = false
        peers.keys.toList().forEach { closePeer(it) }
    }
}
