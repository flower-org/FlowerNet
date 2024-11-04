package com.flower.utils;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonDnsHostnameCheckerTest {
    @Test
    public void test() {
        // IPv4
        assertTrue(NonDnsHostnameChecker.isIPAddress("1.1.1.1"));
        assertTrue(NonDnsHostnameChecker.isIPv4Address("1.1.1.1"));
        assertFalse(NonDnsHostnameChecker.isIPv6Address("1.1.1.1"));

        // IPv6
        assertTrue(NonDnsHostnameChecker.isIPAddress("2001:0db8::1"));
        assertFalse(NonDnsHostnameChecker.isIPv4Address("2001:0db8::1"));
        assertTrue(NonDnsHostnameChecker.isIPv6Address("2001:0db8::1"));

        // hostname
        assertFalse(NonDnsHostnameChecker.isIPAddress("example.com"));
        assertFalse(NonDnsHostnameChecker.isIPv4Address("example.com"));
        assertFalse(NonDnsHostnameChecker.isIPv6Address("example.com"));
    }
}
