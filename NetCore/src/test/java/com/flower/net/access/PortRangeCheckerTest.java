package com.flower.net.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PortRangeCheckerTest {
    private PortRangeChecker checker;

    @BeforeEach
    void setUp() {
        checker = new PortRangeChecker();
    }

    @Test
    void testAddAndCheckPortInRange() {
        checker.addPattern("1000-2000");
        assertTrue(checker.isMatch(1500));
        assertFalse(checker.isMatch(999));
        assertFalse(checker.isMatch(2001));
    }

    @Test
    void testRemovePattern() {
        checker.addPattern("3000-4000");
        assertTrue(checker.isMatch(3500));

        checker.removePattern("3000-4000");
        assertFalse(checker.isMatch(3500));
    }

    @Test
    void testEmptyChecker() {
        assertTrue(checker.isEmpty());

        checker.addPattern("5000-6000");
        assertFalse(checker.isEmpty());

        checker.removePattern("5000-6000");
        assertTrue(checker.isEmpty());
    }

    @Test
    void testReverseOrderInput() {
        checker.addPattern("7000-6500"); // Should normalize to 6500-7000
        assertTrue(checker.isMatch(6750));
        assertFalse(checker.isMatch(6499));
        assertFalse(checker.isMatch(7001));
    }

    @Test
    void testMultipleRanges() {
        checker.addPattern("100-200");
        checker.addPattern("300-400");
        assertTrue(checker.isMatch(150));
        assertTrue(checker.isMatch(350));
        assertFalse(checker.isMatch(250));
    }
}
