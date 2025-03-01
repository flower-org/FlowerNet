package com.flower.net.access;

import com.flower.net.utils.IpAddressUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IpRangeCheckerTest {
    @Test
    void testAddRangeAndCheckIpInRange() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        ipRangeChecker.addRange("192.168.1.0/24");
        assertTrue(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("192.168.1.100")));
        assertFalse(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("192.168.2.100")));
    }

    @Test
    void testIpNotInAnyRange() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        assertFalse(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("8.8.8.8")));
    }

    @Test
    void testInvalidCidr() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        assertThrows(IllegalArgumentException.class, () -> ipRangeChecker.addRange("invalid-cidr"));
    }

    @Test
    void testRemoveRange() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        String cidr = "10.0.0.0/8";
        ipRangeChecker.addRange(cidr);
        assertTrue(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("10.0.1.1")));
        ipRangeChecker.removeRange(cidr);
        assertFalse(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("10.0.1.1")));
    }

    @Test
    void testMultipleRanges() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        ipRangeChecker.addRange("192.168.0.0/16");
        ipRangeChecker.addRange("10.0.0.0/8");

        assertTrue(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("192.168.1.1")));
        assertTrue(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("10.0.10.10")));
        assertFalse(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("172.16.0.1")));
    }

    @Test
    void testIPv6Range() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        ipRangeChecker.addRange("2001:db8::/32");
        assertTrue(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("2001:db8::1")));
        assertFalse(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("2001:db9::1")));
    }

    @Test
    void testRemoveIPv6Range() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        String cidr = "2001:db8::/32";
        ipRangeChecker.addRange(cidr);
        assertTrue(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("2001:db8::1234")));
        ipRangeChecker.removeRange(cidr);
        assertFalse(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("2001:db8::1234")));
    }

    @Test
    void testIPv6NotInAnyRange() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        assertFalse(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("fd00::1")));
    }

    @Test
    void testMultipleIPv6Ranges() {
        IpRangeChecker ipRangeChecker = new IpRangeChecker();
        ipRangeChecker.addRange("2001:db8::/32");
        ipRangeChecker.addRange("fd00::/8");

        assertTrue(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("2001:db8::1")));
        assertTrue(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("fd00::1")));
        assertFalse(ipRangeChecker.isIpInRange(IpAddressUtil.fromString("fe80::1")));
    }
}
