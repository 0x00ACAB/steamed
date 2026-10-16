package xyz.kkaii.steamed.net

import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamNetworking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

// Tunnels web map traffic (channel 3). A browser opens several TCP connections at once, so each one gets an id and
// travels as OPEN/DATA/CLOSE frames ("up" is joiner to host, "down" the reverse). The host only ever connects to the map ports it offered, and only for peers
// that passed the auth handshake.
class SteamP2PWebRelay(private val networking: SteamNetworking) {
    private val logger: Logger = LoggerFactory.getLogger("SteamP2PWebRelay")
    private val channel = 3

    private data class Key(val peer: SteamID, val id: Int)

    // Host side: connections a joiner opened to one of our maps. Joiner side: browser connections we opened.
    private val served = ConcurrentHashMap<Key, Socket>()
    private val opened = ConcurrentHashMap<Key, Socket>()

    // Host side: maps offered to each authenticated peer, by service index.
    private val offered = ConcurrentHashMap<SteamID, List<WebMapIntegration.WebMap>>()

    // Joiner side.
    private val listeners = ConcurrentHashMap<SteamID, List<ServerSocket>>()
    private val nextId = AtomicInteger()

    // Joiner: the host whose map list we'll accept.
    @Volatile
    var expectedHost: SteamID? = null

    @Volatile
    var onMapsAvailable: ((host: SteamID, maps: List<Pair<String, Int>>) -> Unit)? = null

    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread(name = "steamed-p2p-web-recv", isDaemon = true) { receiveLoop() }
    }

    // Host: called once a peer is authenticated.
    fun offer(peer: SteamID, maps: List<WebMapIntegration.WebMap>) {
        closePeer(peer)
        if (maps.isEmpty()) return
        start()
        offered[SteamID(peer)] = maps
        send(peer, WebMapList.encode(SERVICES, maps.map { it.name }))
    }

    private fun receiveLoop() {
        val buffer = ByteBuffer.allocateDirect(65536 + 8)
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
            val frame = ByteArray(read)
            buffer.get(0, frame)
            try {
                handle(SteamID(sender), ByteBuffer.wrap(frame))
            } catch (e: Exception) {
                logger.warn("Dropping a malformed web relay frame from {}", sender.accountID, e)
            }
        }
    }

    private fun handle(sender: SteamID, frame: ByteBuffer) {
        when (frame.get()) {
            SERVICES -> {
                if (sender != expectedHost) return
                listenFor(sender, WebMapList.decode(frame))
            }
            OPEN -> {
                val id = frame.getInt()
                val map = offered[sender]?.getOrNull(frame.get().toInt() and 0xFF)
                if (served.containsKey(Key(sender, id))) return
                if (map == null) {
                    send(sender, closeFrame(CLOSE_DOWN, id))
                    return
                }
                val socket = try {
                    Socket(InetAddress.getLoopbackAddress(), map.port)
                } catch (e: IOException) {
                    logger.warn("Couldn't reach {} on port {}", map.name, map.port, e)
                    send(sender, closeFrame(CLOSE_DOWN, id))
                    return
                }
                pump(sender, id, socket, hostSide = true)
            }
            DATA_UP -> deliver(served, sender, frame, hostSide = true)
            DATA_DOWN -> deliver(opened, sender, frame, hostSide = false)
            CLOSE_UP -> close(sender, frame.getInt(), hostSide = true, notify = false)
            CLOSE_DOWN -> close(sender, frame.getInt(), hostSide = false, notify = false)
        }
    }

    private fun deliver(table: Map<Key, Socket>, sender: SteamID, frame: ByteBuffer, hostSide: Boolean) {
        val id = frame.getInt()
        val socket = table[Key(sender, id)] ?: return
        try {
            socket.getOutputStream().write(frame.array(), frame.position(), frame.remaining())
        } catch (_: IOException) {
            close(sender, id, hostSide, notify = true)
        }
    }

    // Joiner: one loopback listener per map; each accepted browser connection becomes a tunnelled stream.
    private fun listenFor(host: SteamID, names: List<String>) {
        closeListeners(host)
        val sockets = names.mapIndexedNotNull { index, name ->
            try {
                val server = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
                thread(name = "steamed-p2p-web-accept", isDaemon = true) {
                    while (running && !server.isClosed) {
                        val client = try {
                            server.accept()
                        } catch (_: IOException) {
                            break
                        }
                        val id = nextId.incrementAndGet()
                        send(host, ByteBuffer.allocate(6).put(OPEN).putInt(id).put(index.toByte()).array())
                        pump(host, id, client, hostSide = false)
                    }
                }
                name to server
            } catch (e: IOException) {
                logger.warn("Couldn't open a local listener for {}", name, e)
                null
            }
        }
        listeners[host] = sockets.map { it.second }
        onMapsAvailable?.invoke(host, sockets.map { it.first to it.second.localPort })
    }

    private fun pump(peer: SteamID, id: Int, socket: Socket, hostSide: Boolean) {
        (if (hostSide) served else opened)[Key(peer, id)] = socket
        val data = if (hostSide) DATA_DOWN else DATA_UP
        thread(name = "steamed-p2p-web-send", isDaemon = true) {
            val chunk = ByteArray(65536)
            try {
                val input = socket.getInputStream()
                while (running) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    send(peer, ByteBuffer.allocate(5 + read).put(data).putInt(id).put(chunk, 0, read).array())
                }
            } catch (_: IOException) {
                // closed from the other side
            } finally {
                close(peer, id, hostSide, notify = true)
            }
        }
    }

    private fun close(peer: SteamID, id: Int, hostSide: Boolean, notify: Boolean) {
        val socket = (if (hostSide) served else opened).remove(Key(peer, id)) ?: return
        try {
            socket.close()
        } catch (_: IOException) {
        }
        if (notify) send(peer, closeFrame(if (hostSide) CLOSE_DOWN else CLOSE_UP, id))
    }

    private fun closeFrame(type: Byte, id: Int): ByteArray = ByteBuffer.allocate(5).put(type).putInt(id).array()

    private fun send(peer: SteamID, bytes: ByteArray) {
        val direct = ByteBuffer.allocateDirect(bytes.size)
        direct.put(bytes).flip()
        networking.sendP2PPacket(peer, direct, SteamNetworking.P2PSend.Reliable, channel)
    }

    private fun closeListeners(host: SteamID) {
        listeners.remove(host)?.forEach {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
    }

    fun closePeer(peer: SteamID) {
        offered.remove(peer)
        closeListeners(peer)
        served.keys.filter { it.peer == peer }.forEach { close(peer, it.id, hostSide = true, notify = false) }
        opened.keys.filter { it.peer == peer }.forEach { close(peer, it.id, hostSide = false, notify = false) }
    }

    fun stop() {
        running = false
        (offered.keys + listeners.keys + served.keys.map { it.peer } + opened.keys.map { it.peer }).toSet().forEach { closePeer(it) }
    }

    companion object {
        private const val SERVICES: Byte = 0
        private const val OPEN: Byte = 1
        private const val DATA_UP: Byte = 2
        private const val CLOSE_UP: Byte = 3
        private const val DATA_DOWN: Byte = 4
        private const val CLOSE_DOWN: Byte = 5
    }
}
