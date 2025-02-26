package com.flower.net.utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IpRangeCheckerTest {
    private IpRangeChecker ipRangeChecker;

    @BeforeEach
    void setUp() {
        ipRangeChecker = new IpRangeChecker();
        ipRangeChecker.addRange("10.0.0.0/8");
        ipRangeChecker.addRange("192.168.0.0/16");
        ipRangeChecker.addRange("172.16.0.0/12");
    }

    @Test
    void testIpInRange() {
        assertTrue(ipRangeChecker.isIpInRange("10.1.2.3"), "10.1.2.3 should be in range");
        assertTrue(ipRangeChecker.isIpInRange("192.168.1.100"), "192.168.1.100 should be in range");
        assertTrue(ipRangeChecker.isIpInRange("172.20.5.10"), "172.20.5.10 should be in range");
    }

    @Test
    void testIpOutOfRange() {
        assertFalse(ipRangeChecker.isIpInRange("8.8.8.8"), "8.8.8.8 should not be in range");
        assertFalse(ipRangeChecker.isIpInRange("200.200.200.200"), "200.200.200.200 should not be in range");
    }

    @Test
    void testAddingRange() {
        ipRangeChecker.addRange("8.8.8.0/24");
        assertTrue(ipRangeChecker.isIpInRange("8.8.8.8"), "8.8.8.8 should be in range after adding 8.8.8.0/24");
    }

    @Test
    void testRemovingRange() {
        ipRangeChecker.addRange("8.8.8.0/24");
        ipRangeChecker.removeRange("8.8.8.0/24");
        assertFalse(ipRangeChecker.isIpInRange("8.8.8.8"), "8.8.8.8 should not be in range after removing 8.8.8.0/24");
    }
}
