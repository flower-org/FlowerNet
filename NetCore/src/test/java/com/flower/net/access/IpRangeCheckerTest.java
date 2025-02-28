package com.flower.net.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IpRangeCheckerTest {
    private IpRangeChecker checker;

    @BeforeEach
    void setUp() {
        checker = new IpRangeChecker();
    }

    @Test
    void testAddAndCheckIpInRange() {
        checker.addRange("192.168.1.0/24");
        assertTrue(checker.isIpInRange("192.168.1.100"));
        assertFalse(checker.isIpInRange("192.168.2.100"));
    }

    @Test
    void testRemoveRange() {
        checker.addRange("10.0.0.0/8");
        assertTrue(checker.isIpInRange("10.1.2.3"));

        checker.removeRange("10.0.0.0/8");
        assertFalse(checker.isIpInRange("10.1.2.3"));
    }

    @Test
    void testInvalidCidr() {
        assertThrows(IllegalArgumentException.class, () -> checker.addRange("invalid-cidr"));
    }

    @Test
    void testInvalidIp() {
        checker.addRange("172.16.0.0/16");
        assertFalse(checker.isIpInRange("invalid-ip"));
    }

    @Test
    void testIpNotInAnyRange() {
        assertFalse(checker.isIpInRange("8.8.8.8"));
    }
}
