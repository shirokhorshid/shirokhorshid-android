/*
 * Copyright (c) 2022, Psiphon Inc.
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

package com.psiphon3.psiphonlibrary;

import android.content.Context;

import com.psiphon3.R;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LocalProxyInterfaces {
    private LocalProxyInterfaces() {}

    public static final class InterfaceAddress {
        private final String interfaceName;
        private final String address;
        private final int typeStringId;
        private final int rank;

        private InterfaceAddress(String interfaceName, String address, int typeStringId, int rank) {
            this.interfaceName = interfaceName;
            this.address = address;
            this.typeStringId = typeStringId;
            this.rank = rank;
        }

        public String interfaceName() {
            return interfaceName;
        }

        public String address() {
            return address;
        }

        public String displayName(Context context) {
            return context.getString(R.string.lan_proxy_interface_name,
                    context.getString(typeStringId), interfaceName);
        }
    }

    public static List<InterfaceAddress> getAvailableAddresses() {
        ArrayList<InterfaceAddress> addresses = new ArrayList<>();
        Set<String> seenAddresses = new HashSet<>();

        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return addresses;
            }

            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!isUsableInterface(networkInterface)) {
                    continue;
                }

                String interfaceName = networkInterface.getName();
                InterfaceType interfaceType = classify(interfaceName);

                for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                    if (!(inetAddress instanceof Inet4Address) ||
                            inetAddress.isLoopbackAddress() ||
                            !inetAddress.isSiteLocalAddress()) {
                        continue;
                    }

                    String hostAddress = inetAddress.getHostAddress();
                    if (hostAddress == null || !seenAddresses.add(hostAddress)) {
                        continue;
                    }

                    addresses.add(new InterfaceAddress(interfaceName, hostAddress,
                            interfaceType.stringId, interfaceType.rank));
                }
            }
        } catch (SocketException ignored) {
        }

        Collections.sort(addresses, new Comparator<InterfaceAddress>() {
            @Override
            public int compare(InterfaceAddress left, InterfaceAddress right) {
                if (left.rank != right.rank) {
                    return left.rank - right.rank;
                }
                return left.interfaceName.compareTo(right.interfaceName);
            }
        });

        return addresses;
    }

    public static String formatProxyAddresses(Context context, List<InterfaceAddress> addresses,
                                              int httpPort, int socksPort) {
        StringBuilder builder = new StringBuilder();
        for (InterfaceAddress address : addresses) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }

            builder.append(address.displayName(context));

            if (httpPort > 0) {
                builder.append('\n')
                        .append(context.getString(R.string.lan_proxy_http_address,
                                address.address(), httpPort));
            }
            if (socksPort > 0) {
                builder.append('\n')
                        .append(context.getString(R.string.lan_proxy_socks_address,
                                address.address(), socksPort));
            }
        }
        return builder.toString();
    }

    public static String formatInterfaceAddresses(Context context, List<InterfaceAddress> addresses) {
        StringBuilder builder = new StringBuilder();
        for (InterfaceAddress address : addresses) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(context.getString(R.string.lan_proxy_interface_address,
                    address.displayName(context), address.address()));
        }
        return builder.toString();
    }

    private static boolean isUsableInterface(NetworkInterface networkInterface) throws SocketException {
        if (!networkInterface.isUp() || networkInterface.isLoopback()) {
            return false;
        }

        String name = networkInterface.getName();
        if (name == null) {
            return false;
        }

        String lowerName = name.toLowerCase(Locale.US);
        return !(lowerName.startsWith("tun") ||
                lowerName.startsWith("ppp") ||
                lowerName.startsWith("ipsec") ||
                lowerName.startsWith("wg") ||
                lowerName.startsWith("rmnet") ||
                lowerName.startsWith("ccmni") ||
                lowerName.startsWith("ccemni") ||
                lowerName.startsWith("rev_rmnet") ||
                lowerName.startsWith("lo") ||
                lowerName.startsWith("dummy") ||
                lowerName.startsWith("p2p") ||
                lowerName.startsWith("wigig") ||
                lowerName.startsWith("v4-") ||
                lowerName.startsWith("clat") ||
                lowerName.startsWith("sit") ||
                lowerName.startsWith("ip6tnl"));
    }

    private static InterfaceType classify(String interfaceName) {
        String name = interfaceName.toLowerCase(Locale.US);

        if (name.startsWith("ap") || name.startsWith("swlan")) {
            return new InterfaceType(R.string.lan_proxy_interface_hotspot, 0);
        }
        if (name.startsWith("wlan") || name.startsWith("wifi")) {
            return new InterfaceType(R.string.lan_proxy_interface_wifi, 1);
        }
        if (name.startsWith("rndis") || name.startsWith("usb")) {
            return new InterfaceType(R.string.lan_proxy_interface_usb, 2);
        }
        if (name.startsWith("eth") || name.startsWith("en")) {
            return new InterfaceType(R.string.lan_proxy_interface_ethernet, 3);
        }
        if (name.startsWith("bt-pan") || name.startsWith("bnep")) {
            return new InterfaceType(R.string.lan_proxy_interface_bluetooth, 4);
        }
        return new InterfaceType(R.string.lan_proxy_interface_local, 5);
    }

    private static final class InterfaceType {
        final int stringId;
        final int rank;

        InterfaceType(int stringId, int rank) {
            this.stringId = stringId;
            this.rank = rank;
        }
    }
}
