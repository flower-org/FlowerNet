package com.flower.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.UnsupportedAddressTypeException;

public class IpAddressUtil {
    public static boolean isIPv4Address(String input) {
        String[] parts = input.split("\\.");
        if (parts.length != 4) {
            return false; // IPv4 addresses have exactly 4 parts
        }
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false; // Each part must be within 0-255
                }
            } catch (NumberFormatException e) {
                return false; // Each part must be a number
            }
        }
        return true;
    }

    public static boolean isIPv6Address(String input) {
        String[] parts = input.split(":");
        if (parts.length > 8) {
            return false; // IPv6 addresses can have at most 8 parts
        }

        int doubleColonCount = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                doubleColonCount++;
                if (doubleColonCount > 1) {
                    return false; // Only one "::" allowed in IPv6
                }
                continue;
            }
            if (part.length() > 4) {
                return false; // Each part should have at most 4 hex digits
            }
            try {
                Integer.parseInt(part, 16); // Parse as hexadecimal
            } catch (NumberFormatException e) {
                return false; // Each part must be a hexadecimal number
            }
        }
        return true;
    }

    public static boolean isIPAddress(String input) {
        return isIPv4Address(input) || isIPv6Address(input);
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
