package com.flower.net.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.UnsupportedAddressTypeException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IpAddressUtilTest {
    @Test
    public void test() {
        // IPv4
        assertTrue(IpAddressUtil.isIPAddress("1.1.1.1"));
        assertTrue(IpAddressUtil.isIPv4Address("1.1.1.1"));
        assertFalse(IpAddressUtil.isIPv6Address("1.1.1.1"));

        // IPv6
        assertTrue(IpAddressUtil.isIPAddress("2001:0db8::1"));
        assertFalse(IpAddressUtil.isIPv4Address("2001:0db8::1"));
        assertTrue(IpAddressUtil.isIPv6Address("2001:0db8::1"));

        // hostname
        assertFalse(IpAddressUtil.isIPAddress("example.com"));
        assertFalse(IpAddressUtil.isIPv4Address("example.com"));
        assertFalse(IpAddressUtil.isIPv6Address("example.com"));
    }

    @Test
    void testValidIPv4Addresses() {
        assertTrue(IpAddressUtil.isIPv4Address("192.168.1.1"));
        assertTrue(IpAddressUtil.isIPv4Address("0.0.0.0"));
        assertTrue(IpAddressUtil.isIPv4Address("255.255.255.255"));
    }

    @Test
    void testInvalidIPv4Addresses() {
        assertFalse(IpAddressUtil.isIPv4Address("256.0.0.1")); // Out of range
        //TODO: wait for fix https://github.com/seancfoley/IPAddress/issues/138
        //assertFalse(IpAddressUtil.isIPv4Address("192.168.1")); // Missing part
        assertFalse(IpAddressUtil.isIPv4Address("192.168.1.abc")); // Non-numeric
        assertFalse(IpAddressUtil.isIPv4Address("...")); // Invalid format
    }

    @Test
    void testValidIPv6Addresses() {
        assertTrue(IpAddressUtil.isIPv6Address("2001:db8::ff00:42:8329"));
        assertTrue(IpAddressUtil.isIPv6Address("::1"));
        assertTrue(IpAddressUtil.isIPv6Address("fe80::1"));
    }

    @Test
    void testInvalidIPv6Addresses() {
        assertFalse(IpAddressUtil.isIPv6Address("2001:db8:::1")); // Multiple "::"
        assertFalse(IpAddressUtil.isIPv6Address("12345::1")); // Invalid hex part
        assertFalse(IpAddressUtil.isIPv6Address("::g123")); // Invalid hex character
    }

    @Test
    void testIsIPAddress() {
        assertTrue(IpAddressUtil.isIPAddress("192.168.1.1"));
        assertTrue(IpAddressUtil.isIPAddress("::1"));
        assertFalse(IpAddressUtil.isIPAddress("invalid_ip"));
    }

    @Test
    void testFromStringValidIPv4() throws UnknownHostException {
        InetAddress address = IpAddressUtil.fromString("192.168.1.1");
        assertEquals("192.168.1.1", address.getHostAddress());
    }

    @Test
    void testFromStringValidIPv6() throws UnknownHostException {
        InetAddress address = IpAddressUtil.fromString("::1");
        assertEquals("0:0:0:0:0:0:0:1", address.getHostAddress());
    }

    @Test
    void testFromStringInvalid() {
        assertThrows(UnsupportedAddressTypeException.class, () -> IpAddressUtil.fromString("invalid_ip"));
    }

    @Test
    void moreTests() {
        assertTrue(IpAddressUtil.isIPv6Address("2001:db8::ff00:42:8329")); // true
        assertTrue(IpAddressUtil.isIPv6Address("::1")); // true
        assertTrue(IpAddressUtil.isIPv6Address("::ffff:192.168.1.1")); // true
        assertFalse(IpAddressUtil.isIPv6Address("192.168.1.1")); // false
        assertFalse(IpAddressUtil.isIPv6Address("invalid::ip")); // false
    }
}
