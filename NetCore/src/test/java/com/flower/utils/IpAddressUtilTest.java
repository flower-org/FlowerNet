package com.flower.utils;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
