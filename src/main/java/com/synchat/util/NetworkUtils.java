package com.synchat.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * NETWORKING - finds the machine's current Wi-Fi/LAN IPv4 address, so it
 * can be displayed to the hosting user and shared with other devices on
 * the same wireless network that want to join the chat.
 */
public final class NetworkUtils {
    private NetworkUtils() {}

    public static String getLocalWifiAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    // Site-local IPv4 addresses are the typical 192.168.x.x / 10.x.x.x Wi-Fi range.
                    if (addr.isSiteLocalAddress() && addr.getHostAddress().indexOf(':') == -1) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
