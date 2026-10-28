package com.flower.net.utils;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv4.IPv4Address;
import inet.ipaddr.ipv6.IPv6Address;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.UnsupportedAddressTypeException;

public class IpAddressUtil {
    /**
     * Canonical text form of a raw 4- or 16-byte address: dotted decimal for IPv4, RFC 5952 for IPv6
     * (lowercase, zero-compressed), so it can be matched against rules as a plain string.
     */
    public static String toCanonicalString(byte[] address) {
        if (address.length == 4) {
            return new IPv4Address(address).toCanonicalString();
        } else if (address.length == 16) {
            return new IPv6Address(address).toCanonicalString();
        }
        throw new IllegalArgumentException("Invalid IP address length: " + address.length);
    }

    public static String toCanonicalString(byte[] buffer, int offset, int length) {
        byte[] address = new byte[length];
        System.arraycopy(buffer, offset, address, 0, length);
        return toCanonicalString(address);
    }

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
