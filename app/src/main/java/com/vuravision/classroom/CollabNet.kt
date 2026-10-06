package com.vuravision.classroom

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.*
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

const val COLLAB_PROTOCOL = 1
const val COLLAB_SERVICE = "_vuravision._tcp"

/**
 * One length-prefixed JSON stream. Reading and writing happen on their own threads; every
 * callback is delivered on the main thread, in order.
 */
class CollabConnection(private val socket: Socket, private val onMessage: (CollabConnection, JsonObject) -> Unit, private val onClosed: (CollabConnection, String?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    @Volatile var lastHeard = System.currentTimeMillis(); private set
    /** Free for the session to attach the participant this connection belongs to. */
    var peerId = ""
    val remoteAddress: String get() = socket.inetAddress?.hostAddress.orEmpty()

    fun start() {
        socket.tcpNoDelay = true
        Thread({
            try {
                val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 65536))
                while (!closed.get()) {
                    val size = input.readInt()
                    lastHeard = System.currentTimeMillis()
                    require(size in 1..MAX_MESSAGE) { "Message size" }
                    val bytes = ByteArray(size); input.readFully(bytes)
                    lastHeard = System.currentTimeMillis()
                    val json = JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
                    main.post { if (!closed.get()) onMessage(this, json) }
                }
            } catch (e: Exception) {
                close(if (closed.get()) null else e.message ?: e.javaClass.simpleName)
            }
        }, "vura-collab-read").apply { isDaemon = true }.start()
    }

    /** [build] runs on the writer thread so large documents never serialise on the UI thread. */
    fun send(build: () -> JsonObject) {
        if (closed.get()) return
        try {
            writer.execute {
                try {
                    val bytes = CollabSync.gson.toJson(build()).toByteArray(Charsets.UTF_8)
                    require(bytes.size <= MAX_MESSAGE) { "Message too large" }
                    val out = socket.getOutputStream()
                    synchronized(out) {
                        out.write(byteArrayOf((bytes.size ushr 24).toByte(), (bytes.size ushr 16).toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()))
                        out.write(bytes); out.flush()
                    }
                } catch (e: Exception) { close(e.message ?: e.javaClass.simpleName) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }

    fun send(message: JsonObject) = send { message }

    fun close(reason: String? = null) {
        if (!closed.compareAndSet(false, true)) return
        writer.execute { runCatching { socket.close() } }
        writer.shutdown()
        main.post { onClosed(this, reason) }
    }

    val isOpen get() = !closed.get()

    companion object {
        const val MAX_MESSAGE = 48 * 1024 * 1024

        fun connect(host: String, port: Int, onMessage: (CollabConnection, JsonObject) -> Unit, onClosed: (CollabConnection, String?) -> Unit, ready: (CollabConnection?) -> Unit) {
            val main = Handler(Looper.getMainLooper())
            Thread({
                val connection = try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 6000)
                    CollabConnection(socket, onMessage, onClosed)
                } catch (_: Exception) { null }
                main.post { ready(connection) }
            }, "vura-collab-connect").apply { isDaemon = true }.start()
        }
    }
}

/** Accepts guests on a free port until stopped. */
class CollabServer(private val accepted: (Socket) -> Unit) {
    private val server = ServerSocket(0)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = true
    val port: Int get() = server.localPort

    fun start() {
        Thread({
            while (running) {
                try { val socket = server.accept(); main.post { if (running) accepted(socket) else runCatching { socket.close() } } }
                catch (_: Exception) { if (!running) break }
            }
        }, "vura-collab-accept").apply { isDaemon = true }.start()
    }

    fun stop() { running = false; runCatching { server.close() } }
}

/** A room seen on the local network. */
data class CollabRoom(val name: String, val host: String, val port: Int, val roomId: String, val profile: CollabProfile)

/** Announces a room or lists nearby rooms with DNS-SD (Bonjour) on Wi-Fi, Ethernet or a hotspot. */
@Suppress("DEPRECATION")
class CollabDiscovery(private val context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var lock: android.net.wifi.WifiManager.MulticastLock? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val rooms = LinkedHashMap<String, CollabRoom>()
    var changed: (List<CollabRoom>) -> Unit = {}

    private fun acquireLock() {
        if (lock != null) return
        runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            lock = wifi.createMulticastLock("vura-collab").apply { setReferenceCounted(false); acquire() }
        }
    }

    fun announce(port: Int, roomId: String, profile: CollabProfile) {
        stopAnnounce(); acquireLock()
        val info = NsdServiceInfo().apply {
            serviceName = "VuraVision ${profile.name.take(24)}".trim()
            serviceType = COLLAB_SERVICE
            setPort(port)
            setAttribute("n", profile.name.take(40))
            setAttribute("e", profile.emoji.take(8))
            setAttribute("c", Integer.toHexString(profile.color))
            setAttribute("r", roomId)
            setAttribute("v", COLLAB_PROTOCOL.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stopAnnounce() { registration?.let { runCatching { nsd.unregisterService(it) } }; registration = null }

    fun browse() {
        stopBrowse(); acquireLock(); rooms.clear(); changed(emptyList())
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) {}
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { main.post { if (discovery != null) { resolveQueue.addLast(info); resolveNext() } } }
            override fun onServiceLost(info: NsdServiceInfo) { main.post { if (rooms.remove(info.serviceName) != null) changed(rooms.values.toList()) } }
        }
        discovery = listener
        runCatching { nsd.discoverServices(COLLAB_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    // Older Android versions resolve one service at a time.
    private fun resolveNext() {
        if (resolving || discovery == null) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        runCatching {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, error: Int) { main.post { resolving = false; resolveNext() } }
                override fun onServiceResolved(info: NsdServiceInfo) {
                    main.post {
                        resolving = false
                        val address = info.host?.hostAddress
                        val text = info.attributes.orEmpty().mapValues { (_, v) -> v?.toString(Charsets.UTF_8).orEmpty() }
                        if (address != null && text["v"] == COLLAB_PROTOCOL.toString() && discovery != null) {
                            val profile = CollabProfile(name = text["n"].orEmpty(), emoji = text["e"].orEmpty(), color = text["c"]?.toLongOrNull(16)?.toInt() ?: NAVY)
                            rooms[info.serviceName] = CollabRoom(info.serviceName, address, info.port, text["r"].orEmpty(), profile)
                            changed(rooms.values.toList())
                        }
                        resolveNext()
                    }
                }
            })
        }.onFailure { resolving = false }
    }

    fun stopBrowse() { discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }; discovery = null; resolveQueue.clear(); resolving = false }

    fun close() { stopAnnounce(); stopBrowse(); runCatching { lock?.release() }; lock = null }

    companion object {
        /** IPv4 addresses on Wi-Fi/Ethernet/hotspot interfaces, preferred first. */
        fun addresses(context: Context): List<String> {
            val preferred = mutableListOf<String>()
            runCatching {
                val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                manager.allNetworks.forEach { network ->
                    val caps = manager.getNetworkCapabilities(network)
                    if (caps != null && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) &&
                        (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)))
                        manager.getLinkProperties(network)?.linkAddresses?.forEach { a -> val ip = a.address; if (ip is Inet4Address && !ip.isLoopbackAddress) ip.hostAddress?.let(preferred::add) }
                }
            }
            val fallback = runCatching {
                NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") && !it.name.startsWith("rmnet") }
                    .flatMap { it.inetAddresses.toList() }.filter { it is Inet4Address && it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
            }.getOrDefault(emptyList())
            return (preferred + fallback).distinct()
        }
    }
}

/** What a room QR code carries: where to connect and the code that admits without asking. */
data class CollabInvite(val hosts: List<String>, val port: Int, val code: String, val roomId: String, val name: String) {
    fun uri(): String = "vura://join?h=${hosts.joinToString(",")}&p=$port&k=$code&r=$roomId&n=${URLEncoder.encode(name, "UTF-8")}"

    companion object {
        fun parse(text: String): CollabInvite? = runCatching {
            val uri = URI(text.trim())
            require(uri.scheme == "vura" && uri.host == "join")
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], "UTF-8") } }.toMap()
            val hosts = query["h"].orEmpty().split(',').map { it.trim() }.filter { it.matches(Regex("[0-9.]{7,15}")) }
            val port = query["p"]?.toIntOrNull() ?: 0
            require(hosts.isNotEmpty() && port in 1..65535)
            CollabInvite(hosts, port, query["k"].orEmpty().filter { it.isDigit() }.take(6), query["r"].orEmpty().take(40), query["n"].orEmpty().take(40))
        }.getOrNull()

        /** Accepts "192.168.1.5:40123" typed by hand. */
        fun parseAddress(text: String, code: String): CollabInvite? {
            val match = Regex("\\s*([0-9]{1,3}(?:\\.[0-9]{1,3}){3})\\s*:\\s*([0-9]{1,5})\\s*").matchEntire(text) ?: return null
            val port = match.groupValues[2].toInt(); if (port !in 1..65535) return null
            return CollabInvite(listOf(match.groupValues[1]), port, code.filter { it.isDigit() }.take(6), "", "")
        }
    }
}
