package io.github.nayrasolcr.forough.vpn

import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import ca.psiphon.Tun2SocksJniLoader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the VPN interface and the tun2socks library.
 *
 * Faithful Kotlin port of the Psiphon Android client's VpnManager
 * (com.psiphon3.VpnManager): private-address selection, VPN interface
 * establishment, LAN-sharing route exclusion, tun2socks lifecycle and its
 * logging bridge. Logging goes through android.util.Log instead of MyLog.
 */
class VpnManager private constructor() {

    interface VpnServiceBuilderProvider {
        fun vpnServiceBuilder(): VpnService.Builder
    }

    private class PrivateAddress(
        val ipAddress: String,
        val subnet: String,
        val prefixLength: Int,
        val router: String
    )

    private var privateAddress: PrivateAddress? = null
    private val tunFd = AtomicReference<ParcelFileDescriptor?>(null)
    private val isRoutingThroughTunnel = AtomicBoolean(false)
    @Volatile private var shareProxyOnNetwork = false
    private var tun2SocksThread: Thread? = null
    private var builderProviderRef: WeakRef? = null

    private class WeakRef(provider: VpnServiceBuilderProvider) :
        java.lang.ref.WeakReference<VpnServiceBuilderProvider>(provider)

    fun registerHostService(provider: VpnServiceBuilderProvider) {
        this.builderProviderRef = WeakRef(provider)
    }

    fun unregisterHostService() {
        builderProviderRef = null
    }

    /** Set whether LAN proxy sharing is enabled (excludes private LAN subnets). */
    fun setShareProxyOnNetwork(share: Boolean) {
        shareProxyOnNetwork = share
    }

    /** Pick a private address and create the VPN interface. */
    @Synchronized
    fun vpnEstablish() {
        val addr = selectPrivateAddress()
        privateAddress = addr

        val provider = builderProviderRef?.get()
            ?: throw IllegalStateException("VpnServiceBuilderProvider is not registered")

        val builder = provider.vpnServiceBuilder()
            .setMtu(VPN_INTERFACE_MTU)
            .addAddress(addr.ipAddress, addr.prefixLength)
            .addDnsServer(addr.router)

        if (shareProxyOnNetwork) {
            addLanExcludedRoutes(builder)
        } else {
            builder.addRoute("0.0.0.0", 0)
        }

        // Route for the VPN interface's own subnet so traffic to the
        // tun2socks gateway (DNS resolver) rides the tun interface.
        if (shareProxyOnNetwork) {
            builder.addRoute(addr.subnet, 24)
        } else {
            builder.addRoute(addr.subnet, addr.prefixLength)
        }

        val fd = builder.establish()
            ?: throw IllegalStateException("Application is no longer prepared or was revoked")
        tunFd.set(fd)
        isRoutingThroughTunnel.set(false)
    }

    @Synchronized
    fun vpnTeardown() {
        stopRouteThroughTunnel()
        val fd = tunFd.getAndSet(null)
        if (fd != null) {
            try {
                fd.close()
            } catch (_: java.io.IOException) {
            }
        }
        isRoutingThroughTunnel.set(false)
    }

    /** Start routing traffic through the tunnel via tun2socks. */
    @Synchronized
    fun routeThroughTunnel(socksProxyPort: Int) {
        if (!isRoutingThroughTunnel.compareAndSet(false, true)) return
        val fd = tunFd.get() ?: return
        if (socksProxyPort <= 0) {
            Log.e(TAG, "routeThroughTunnel: socks proxy port is not set")
            return
        }
        val socksServerAddress = "127.0.0.1:$socksProxyPort"
        val udpgwServerAddress = "127.0.0.1:$UDPGW_SERVER_PORT"
        val addr = privateAddress

        try {
            startTun2Socks(
                fd.dup(),
                VPN_INTERFACE_MTU,
                addr?.router ?: "10.0.0.2",
                VPN_INTERFACE_IPV4_NETMASK,
                socksServerAddress,
                udpgwServerAddress,
                true
            )
            Log.i(TAG, "Routing through tunnel")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "routeThroughTunnel: error duplicating tun FD: $e")
        }
    }

    /** Stop routing traffic through the tunnel. */
    @Synchronized
    fun stopRouteThroughTunnel() {
        if (isRoutingThroughTunnel.compareAndSet(true, false)) {
            stopTun2Socks()
        }
    }

    private fun startTun2Socks(
        vpnInterfaceFileDescriptor: ParcelFileDescriptor,
        vpnInterfaceMTU: Int,
        vpnIpv4Address: String,
        vpnIpv4NetMask: String,
        socksServerAddress: String,
        udpgwServerAddress: String,
        udpgwTransparentDNS: Boolean
    ) {
        if (tun2SocksThread != null) return
        tun2SocksThread = Thread {
            Tun2SocksJniLoader.runTun2Socks(
                vpnInterfaceFileDescriptor.detachFd(),
                vpnInterfaceMTU,
                vpnIpv4Address,
                vpnIpv4NetMask,
                null, // IPv4-only routing
                socksServerAddress,
                udpgwServerAddress,
                if (udpgwTransparentDNS) 1 else 0
            )
        }
        tun2SocksThread?.start()
        Log.i(TAG, "tun2socks started")
    }

    private fun stopTun2Socks() {
        val thread = tun2SocksThread ?: return
        try {
            Tun2SocksJniLoader.terminateTun2Socks()
            thread.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        tun2SocksThread = null
        Log.i(TAG, "tun2socks stopped")
    }

    private fun selectPrivateAddress(): PrivateAddress {
        val candidates = linkedMapOf(
            "10" to PrivateAddress("10.0.0.1", "10.0.0.0", 8, "10.0.0.2"),
            "172" to PrivateAddress("172.16.0.1", "172.16.0.0", 12, "172.16.0.2"),
            "192" to PrivateAddress("192.168.0.1", "192.168.0.0", 16, "192.168.0.2"),
            "169" to PrivateAddress("169.254.1.1", "169.254.1.0", 24, "169.254.1.2")
        )

        val netInterfaces = NetworkInterface.getNetworkInterfaces()
            ?: throw IllegalStateException("No network interfaces found")

        for (netInterface in netInterfaces.toList()) {
            for (inetAddress in netInterface.inetAddresses) {
                if (inetAddress is Inet4Address) {
                    val ipAddress = inetAddress.hostAddress ?: continue
                    when {
                        ipAddress.startsWith("10.") -> candidates.remove("10")
                        ipAddress.length >= 6 &&
                            ipAddress.substring(0, 6) >= "172.16" &&
                            ipAddress.substring(0, 6) <= "172.31" -> candidates.remove("172")
                        ipAddress.startsWith("192.168") -> candidates.remove("192")
                    }
                }
            }
        }

        if (candidates.isNotEmpty()) {
            return candidates.values.iterator().next()
        }
        throw IllegalStateException("No private address available")
    }

    // ----- LAN-sharing route exclusion (RFC 1918), ported verbatim -----

    private fun addLanExcludedRoutes(builder: VpnService.Builder) {
        if (Build.VERSION.SDK_INT >= 33) {
            builder.addRoute("0.0.0.0", 0)
            for (subnet in LAN_SUBNETS) {
                val addr = java.net.InetAddress.getByName(subnet[0])
                builder.excludeRoute(IpPrefix(addr, subnet[1].toInt()))
            }
        } else {
            val routes = ArrayList<LongArray>()
            routes.add(longArrayOf(0L, 0))
            for (subnet in LAN_SUBNETS) {
                val ip = ipToLong(subnet[0])
                val prefix = subnet[1].toInt()
                subtractCidr(routes, ip, prefix)
            }
            for (route in routes) {
                builder.addRoute(longToIp(route[0]), route[1].toInt())
            }
        }
    }

    private fun subtractCidr(
        routes: MutableList<LongArray>,
        subtractIp: Long,
        subtractPrefix: Int
    ): MutableList<LongArray> {
        val subtractMask = prefixToMask(subtractPrefix)
        val subtractStart = subtractIp and subtractMask
        val subtractEnd = subtractStart or (subtractMask.inv() and 0xFFFFFFFFL)

        val result = ArrayList<LongArray>()
        for (route in routes) {
            val routeMask = prefixToMask(route[1].toInt())
            val routeStart = route[0] and routeMask
            val routeEnd = routeStart or (routeMask.inv() and 0xFFFFFFFFL)

            when {
                subtractStart > routeEnd || subtractEnd < routeStart -> result.add(route)
                subtractStart <= routeStart && subtractEnd >= routeEnd -> { /* fully covered */ }
                else -> splitExcluding(result, routeStart, route[1].toInt(), subtractStart, subtractEnd)
            }
        }
        routes.clear()
        routes.addAll(result)
        return routes
    }

    private fun splitExcluding(
        result: MutableList<LongArray>,
        blockStart: Long,
        blockPrefix: Int,
        exclStart: Long,
        exclEnd: Long
    ) {
        val childPrefix = blockPrefix + 1
        if (childPrefix > 32) return
        val childSize = 1L shl (32 - childPrefix)

        val leftStart = blockStart
        val leftEnd = leftStart + childSize - 1
        val rightStart = blockStart + childSize
        val rightEnd = rightStart + childSize - 1

        val children = arrayOf(longArrayOf(leftStart, leftEnd), longArrayOf(rightStart, rightEnd))
        for (child in children) {
            when {
                exclStart > child[1] || exclEnd < child[0] ->
                    result.add(longArrayOf(child[0], childPrefix.toLong()))
                exclStart <= child[0] && exclEnd >= child[1] -> { /* fully excluded */ }
                else -> splitExcluding(result, child[0], childPrefix, exclStart, exclEnd)
            }
        }
    }

    companion object {
        private const val TAG = "ForoughVpn"
        private const val VPN_INTERFACE_MTU = 1500
        private const val VPN_INTERFACE_IPV4_NETMASK = "255.255.255.0"
        private const val UDPGW_SERVER_PORT = 7300

        private val LAN_SUBNETS = arrayOf(
            arrayOf("10.0.0.0", "8"),
            arrayOf("172.16.0.0", "12"),
            arrayOf("192.168.0.0", "16")
        )

        private fun prefixToMask(prefix: Int): Long {
            if (prefix == 0) return 0L
            return (0L.inv() shl (32 - prefix)) and 0xFFFFFFFFL
        }

        private fun ipToLong(ip: String): Long {
            val parts = ip.split(".")
            return (parts[0].toLong() shl 24) or
                (parts[1].toLong() shl 16) or
                (parts[2].toLong() shl 8) or
                parts[3].toLong()
        }

        private fun longToIp(ip: Long): String =
            "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"

        // The tun2socks library has global state — one VpnManager per process.
        @Volatile private var instance: VpnManager? = null

        fun getInstance(): VpnManager {
            return instance ?: synchronized(this) {
                instance ?: VpnManager().also { instance = it }
            }
        }

        // Called from native tun2socks code via Tun2SocksJniLoader.initializeLogger.
        @JvmStatic
        fun logTun2Socks(level: String, channel: String, msg: String) {
            val logMsg = "tun2socks: $level($channel): $msg"
            when (level) {
                "ERROR" -> Log.e(TAG, logMsg)
                "WARNING" -> Log.w(TAG, logMsg)
                "DEBUG" -> Log.v(TAG, logMsg)
                else -> Log.i(TAG, logMsg)
            }
        }
    }

    init {
        Tun2SocksJniLoader.initializeLogger(VpnManager::class.java.name, "logTun2Socks")
    }
}
