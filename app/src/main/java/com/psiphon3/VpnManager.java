/*
 * Copyright (c) 2024, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3;

import android.net.IpPrefix;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import com.psiphon3.log.MyLog;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import ca.psiphon.Tun2SocksJniLoader;

// The VpnManager class manages the VPN interface and tun2socks library. It creates the VPN
// interface, starts tun2socks to route traffic through the VPN interface, and stops tun2socks.
// The class is a singleton and should be accessed via the getInstance() method.
// The host service implementation should be registered using the registerHostService() method before
// calling any other methods as the host service is required to create the VPN interface.

public class VpnManager {
    private static final int VPN_INTERFACE_MTU = 1500;
    private static final String VPN_INTERFACE_IPV4_NETMASK = "255.255.255.0";
    private static final int UDPGW_SERVER_PORT = 7300;

    // The underlying tun2socks library has global state, so we need to ensure that only one
    // instance of VpnManager is created and used at a time
    private static volatile VpnManager INSTANCE = null;

    private PrivateAddress mPrivateAddress;
    private final AtomicReference<ParcelFileDescriptor> tunFd;
    private final AtomicBoolean isRoutingThroughTunnel;
    private volatile boolean mShareProxyOnNetwork = false;
    private Thread mTun2SocksThread;
    private WeakReference<VpnServiceBuilderProvider> vpnServiceBuilderProviderRef;

    // Initialize the tun2socks logger with the class name and method name
    // This is called once when the class is loaded
    // The logTun2Socks method is called from the native tun2socks code to log messages
    static {
        Tun2SocksJniLoader.initializeLogger(VpnManager.class.getName(), "logTun2Socks");
    }

    private VpnManager() {
        tunFd = new AtomicReference<>();
        isRoutingThroughTunnel = new AtomicBoolean(false);
    }

    public static VpnManager getInstance() {
        if (INSTANCE == null) {
            synchronized (VpnManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new VpnManager();
                }
            }
        }
        return INSTANCE;
    }

    // Host service interface
    public interface VpnServiceBuilderProvider {
        // Return a VpnService.Builder instance to use for creating the VPN interface
        VpnService.Builder vpnServiceBuilder();
    }

    // Register a host service
    public synchronized void registerHostService(VpnServiceBuilderProvider vpnServiceBuilderProvider) {
        this.vpnServiceBuilderProviderRef = new WeakReference<>(vpnServiceBuilderProvider);
    }

    // Unregister the host service
    public synchronized void unregisterHostService() {
        if (vpnServiceBuilderProviderRef != null) {
            vpnServiceBuilderProviderRef.clear();
        }
    }

    // Helper class to pick and store a private address for the VPN interface
    private static class PrivateAddress {
        final String mIpAddress;
        final String mSubnet;
        final int mPrefixLength;
        final String mRouter;

        public PrivateAddress(String ipAddress, String subnet, int prefixLength, String router) {
            mIpAddress = ipAddress;
            mSubnet = subnet;
            mPrefixLength = prefixLength;
            mRouter = router;
        }
    }

    private static PrivateAddress selectPrivateAddress() throws IllegalStateException {
        // Select one of 10.0.0.1, 172.16.0.1, 192.168.0.1, or 169.254.1.1 depending on
        // which private address range isn't in use.

        Map<String, PrivateAddress> candidates = new HashMap<>();
        candidates.put("10", new PrivateAddress("10.0.0.1", "10.0.0.0", 8, "10.0.0.2"));
        candidates.put("172", new PrivateAddress("172.16.0.1", "172.16.0.0", 12, "172.16.0.2"));
        candidates.put("192", new PrivateAddress("192.168.0.1", "192.168.0.0", 16, "192.168.0.2"));
        candidates.put("169", new PrivateAddress("169.254.1.1", "169.254.1.0", 24, "169.254.1.2"));

        Enumeration<NetworkInterface> netInterfaces;
        try {
            netInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new IllegalStateException("Error getting network interfaces: " + e);
        }

        if (netInterfaces == null) {
            throw new IllegalStateException("No network interfaces found");
        }

        for (NetworkInterface netInterface : Collections.list(netInterfaces)) {
            for (InetAddress inetAddress : Collections.list(netInterface.getInetAddresses())) {
                if (inetAddress instanceof Inet4Address) {
                    String ipAddress = inetAddress.getHostAddress();
                    if (ipAddress == null) {
                        continue;
                    }
                    if (ipAddress.startsWith("10.")) {
                        candidates.remove("10");
                    } else if (
                            ipAddress.length() >= 6 &&
                                    ipAddress.substring(0, 6).compareTo("172.16") >= 0 &&
                                    ipAddress.substring(0, 6).compareTo("172.31") <= 0) {
                        candidates.remove("172");
                    } else if (ipAddress.startsWith("192.168")) {
                        candidates.remove("192");
                    }
                }
            }
        }

        if (candidates.size() > 0) {
            return candidates.values().iterator().next();
        }

        throw new IllegalStateException("No private address available");
    }

    // Set whether LAN proxy sharing is enabled. When enabled, VPN routes will exclude
    // private LAN subnets so that other devices on the local network can reach the proxy.
    public void setShareProxyOnNetwork(boolean share) {
        mShareProxyOnNetwork = share;
    }

    // Pick a private address and create the VPN interface
    public synchronized void vpnEstablish() {
        mPrivateAddress = selectPrivateAddress();

        Locale previousLocale = Locale.getDefault();

        try {
            // Workaround for https://code.google.com/p/android/issues/detail?id=61096
            Locale.setDefault(new Locale("en"));

            String dnsResolver = mPrivateAddress.mRouter;

            VpnServiceBuilderProvider vpnServiceBuilderProvider = vpnServiceBuilderProviderRef.get();
            if (vpnServiceBuilderProvider == null) {
                throw new IllegalStateException("HostService reference is null");
            }

            VpnService.Builder builder = vpnServiceBuilderProvider.vpnServiceBuilder()
                    .setMtu(VPN_INTERFACE_MTU)
                    .addAddress(mPrivateAddress.mIpAddress, mPrivateAddress.mPrefixLength)
                    .addDnsServer(dnsResolver);

            if (mShareProxyOnNetwork) {
                // When LAN sharing is enabled, route all traffic through VPN EXCEPT
                // private LAN subnets so other devices can reach the proxy directly.
                addLanExcludedRoutes(builder);
            } else {
                builder.addRoute("0.0.0.0", 0);
            }

            // Add route for the VPN interface's own subnet to ensure traffic to the
            // tun2socks gateway (DNS resolver) is routed through the tun interface.
            // When LAN sharing is enabled, use a /24 route for just the VPN gateway
            // instead of the full private subnet, to avoid re-adding the excluded range.
            if (mShareProxyOnNetwork) {
                builder.addRoute(mPrivateAddress.mSubnet, 24);
            } else {
                builder.addRoute(mPrivateAddress.mSubnet, mPrivateAddress.mPrefixLength);
            }

            ParcelFileDescriptor tunFd = builder.establish();
            if (tunFd == null) {
                // As per
                // http://developer.android.com/reference/android/net/VpnService.Builder.html#establish%28%29,
                // this application is no longer prepared or was revoked.
                throw new IllegalStateException("Application is no longer prepared or was revoked");
            }
            this.tunFd.set(tunFd);
            isRoutingThroughTunnel.set(false);
        } finally {
            // Restore the original locale
            Locale.setDefault(previousLocale);
        }
    }

    // RFC 1918 private ranges to exclude from VPN when LAN sharing is enabled.
    // These ranges are excluded so that LAN devices can reach the proxy directly.
    private static final String[][] LAN_SUBNETS = {
            {"10.0.0.0", "8"},
            {"172.16.0.0", "12"},
            {"192.168.0.0", "16"},
    };

    /**
     * Add VPN routes that cover 0.0.0.0/0 minus the RFC 1918 private LAN subnets.
     * On API 33+ uses {@code excludeRoute(IpPrefix)} for clarity.
     * On older APIs, computes the complementary CIDR routes programmatically.
     */
    private static void addLanExcludedRoutes(VpnService.Builder builder) {
        if (Build.VERSION.SDK_INT >= 33) {
            // API 33+ (Android 13): use excludeRoute directly — clean and declarative
            builder.addRoute("0.0.0.0", 0);
            for (String[] subnet : LAN_SUBNETS) {
                try {
                    InetAddress addr = InetAddress.getByName(subnet[0]);
                    builder.excludeRoute(new IpPrefix(addr, Integer.parseInt(subnet[1])));
                } catch (UnknownHostException e) {
                    // Static addresses, cannot fail
                    throw new RuntimeException(e);
                }
            }
        } else {
            // API 14-32: compute complementary routes by subtracting each private
            // range from the full IPv4 address space
            List<long[]> routes = new ArrayList<>();
            routes.add(new long[]{0L, 0}); // 0.0.0.0/0

            for (String[] subnet : LAN_SUBNETS) {
                long ip = ipToLong(subnet[0]);
                int prefix = Integer.parseInt(subnet[1]);
                routes = subtractCidr(routes, ip, prefix);
            }

            for (long[] route : routes) {
                builder.addRoute(longToIp(route[0]), (int) route[1]);
            }
        }
    }

    /**
     * Subtract a CIDR block from a list of CIDR blocks, returning the remaining blocks.
     * Each block is represented as {ipAsLong, prefixLength}.
     */
    private static List<long[]> subtractCidr(List<long[]> routes, long subtractIp, int subtractPrefix) {
        long subtractMask = prefixToMask(subtractPrefix);
        long subtractStart = subtractIp & subtractMask;
        long subtractEnd = subtractStart | ~subtractMask & 0xFFFFFFFFL;

        List<long[]> result = new ArrayList<>();
        for (long[] route : routes) {
            long routeMask = prefixToMask((int) route[1]);
            long routeStart = route[0] & routeMask;
            long routeEnd = routeStart | ~routeMask & 0xFFFFFFFFL;

            if (subtractStart > routeEnd || subtractEnd < routeStart) {
                // No overlap — keep this route unchanged
                result.add(route);
            } else if (subtractStart <= routeStart && subtractEnd >= routeEnd) {
                // Fully covered — remove this route entirely
            } else {
                // Partial overlap — split into sub-blocks that don't overlap
                splitExcluding(result, routeStart, (int) route[1], subtractStart, subtractEnd);
            }
        }
        return result;
    }

    /**
     * Split a CIDR block into the largest possible sub-blocks that don't overlap
     * with the excluded range [exclStart, exclEnd].
     */
    private static void splitExcluding(List<long[]> result, long blockStart, int blockPrefix,
                                       long exclStart, long exclEnd) {
        // Try each sub-block at one prefix level deeper (split in half)
        int childPrefix = blockPrefix + 1;
        if (childPrefix > 32) {
            return;
        }
        long childSize = 1L << (32 - childPrefix);

        // Left half
        long leftStart = blockStart;
        long leftEnd = leftStart + childSize - 1;
        // Right half
        long rightStart = blockStart + childSize;
        long rightEnd = rightStart + childSize - 1;

        // For each half: if no overlap with excluded range, add it whole.
        // If fully covered, skip. If partial overlap, recurse.
        long[][] children = {{leftStart, leftEnd}, {rightStart, rightEnd}};
        for (long[] child : children) {
            if (exclStart > child[1] || exclEnd < child[0]) {
                // No overlap — keep whole
                result.add(new long[]{child[0], childPrefix});
            } else if (exclStart <= child[0] && exclEnd >= child[1]) {
                // Fully excluded — skip
            } else {
                // Partial overlap — recurse
                splitExcluding(result, child[0], childPrefix, exclStart, exclEnd);
            }
        }
    }

    /** Convert a prefix length (0-32) to a 32-bit mask as an unsigned long. */
    private static long prefixToMask(int prefix) {
        if (prefix == 0) return 0L;
        return (~0L << (32 - prefix)) & 0xFFFFFFFFL;
    }

    /** Convert a dotted-quad IPv4 string to an unsigned long. */
    private static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        return (Long.parseLong(parts[0]) << 24)
                | (Long.parseLong(parts[1]) << 16)
                | (Long.parseLong(parts[2]) << 8)
                | Long.parseLong(parts[3]);
    }

    /** Convert an unsigned long to a dotted-quad IPv4 string. */
    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "."
                + (ip & 0xFF);
    }

    // Stop tun2socks if running and close tun FD
    public synchronized void vpnTeardown() {
        stopRouteThroughTunnel();
        ParcelFileDescriptor tunFd = this.tunFd.getAndSet(null);
        if (tunFd != null) {
            try {
                tunFd.close();
            } catch (IOException ignored) {
            }
        }
        isRoutingThroughTunnel.set(false);
    }

    // Start routing traffic via tunnel by starting tun2socks if it is not running already
    public synchronized void routeThroughTunnel(int socksProxyPort) {
        if (!isRoutingThroughTunnel.compareAndSet(false, true)) {
            return;
        }
        ParcelFileDescriptor tunFd = this.tunFd.get();
        if (tunFd == null) {
            return;
        }

        if (socksProxyPort <= 0) {
            MyLog.e("routeThroughTunnel: socks proxy port is not set");
            return;
        }
        String socksServerAddress = "127.0.0.1:" + socksProxyPort;
        String udpgwServerAddress = "127.0.0.1:" + UDPGW_SERVER_PORT;

        // We may call routeThroughTunnel and stopRouteThroughTunnel more than once within the same
        // VPN session. Since stopTun2Socks() closes the FD passed to startTun2Socks(), we will use a
        // dup of the original tun FD and close the original only when we call vpnTeardown().
        //
        // Note that ParcelFileDescriptor.dup() may throw an IOException.
        try {
            startTun2Socks(
                    tunFd.dup(),
                    VPN_INTERFACE_MTU,
                    mPrivateAddress.mRouter,
                    VPN_INTERFACE_IPV4_NETMASK,
                    socksServerAddress,
                    udpgwServerAddress,
                    true);
            MyLog.i("Routing through tunnel");
        } catch (IOException e) {
            MyLog.e("routeThroughTunnel: error duplicating tun FD: " + e);
        }
    }

    // Stop routing traffic via tunnel by stopping tun2socks if currently routing through tunnel
    public synchronized void stopRouteThroughTunnel() {
        if (isRoutingThroughTunnel.compareAndSet(true, false)) {
            stopTun2Socks();
        }
    }

    // Tun2Socks APIs
    private void startTun2Socks(
            final ParcelFileDescriptor vpnInterfaceFileDescriptor,
            final int vpnInterfaceMTU,
            final String vpnIpv4Address,
            final String vpnIpv4NetMask,
            final String socksServerAddress,
            final String udpgwServerAddress,
            final boolean udpgwTransparentDNS) {
        if (mTun2SocksThread != null) {
            return;
        }
        mTun2SocksThread = new Thread(() -> Tun2SocksJniLoader.runTun2Socks(
                vpnInterfaceFileDescriptor.detachFd(),
                vpnInterfaceMTU,
                vpnIpv4Address,
                vpnIpv4NetMask,
                null, // IPv4 only routing
                socksServerAddress,
                udpgwServerAddress,
                udpgwTransparentDNS ? 1 : 0));
        mTun2SocksThread.start();
        MyLog.i("tun2socks started");
    }

    private void stopTun2Socks() {
        if (mTun2SocksThread != null) {
            try {
                Tun2SocksJniLoader.terminateTun2Socks();
                mTun2SocksThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mTun2SocksThread = null;
            MyLog.i("tun2socks stopped");
        }
    }

    // Log messages from tun2socks, called from native tun2socks code
    public static void logTun2Socks(String level, String channel, String msg) {
        String logMsg = "tun2socks: " + level + "(" + channel + "): " + msg;

        // These are the levels as defined in the native code
        // static char *level_names[] = { NULL, "ERROR", "WARNING", "NOTICE", "INFO", "DEBUG" };

        // Keep redundant cases for each level to make it easier to modify in the future
        switch (level) {
            case "ERROR":
                MyLog.e(logMsg);
                break;
            case "WARNING":
                MyLog.w(logMsg);
                break;
            case "NOTICE":
                MyLog.i(logMsg);
                break;
            case "INFO":
                MyLog.i(logMsg);
                break;
            case "DEBUG":
                MyLog.v(logMsg);
                break;
            default:
                MyLog.i(logMsg);
                break;
        }
    }
}
