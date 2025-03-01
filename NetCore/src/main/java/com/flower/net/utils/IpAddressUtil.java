package com.flower.net.utils;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.UnsupportedAddressTypeException;

public class IpAddressUtil {
    public static boolean isIPv4Address(String input) {
        IPAddressString ipAddressString = new IPAddressString(input);
        IPAddress ipAddress = ipAddressString.getAddress();
        System.out.println(ipAddress);
        return ipAddress != null && ipAddress.isIPv4();
    }

    public static boolean isIPv6Address(String input) {
        IPAddressString ipAddressString = new IPAddressString(input);
        IPAddress ipAddress = ipAddressString.getAddress();
        return ipAddress != null && ipAddress.isIPv6();
    }

    public static boolean isIPAddress(String input) {
        IPAddressString ipAddressString = new IPAddressString(input);
        IPAddress ipAddress = ipAddressString.getAddress();
        return ipAddress != null;
    }

    public static InetAddress fromString(String ipAddress) throws UnsupportedAddressTypeException {
        try {
            if (isIPAddress(ipAddress)) {
                return InetAddress.getByName(ipAddress);
            } else {
                throw new UnsupportedAddressTypeException();
            }
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
