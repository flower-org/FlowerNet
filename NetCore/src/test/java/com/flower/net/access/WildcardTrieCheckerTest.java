package com.flower.net.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WildcardTrieCheckerTest {
    private WildcardTrieChecker checker;

    @BeforeEach
    void setUp() {
        checker = new WildcardTrieChecker();
    }

    @Test
    void testAddAndMatchPattern() {
        checker.addPattern("hello*");
        assertTrue(checker.isMatch("helloWorld"));
        assertTrue(checker.isMatch("hello123"));
        assertFalse(checker.isMatch("hiWorld"));
    }

    @Test
    void testMultiplePatterns() {
        checker.addPattern("abc*");
        checker.addPattern("*xyz");
        assertTrue(checker.isMatch("abc123"));
        assertTrue(checker.isMatch("456xyz"));
        assertFalse(checker.isMatch("middle"));
    }

    @Test
    void testExactMatch() {
        checker.addPattern("flower");
        assertTrue(checker.isMatch("flower"));
        assertFalse(checker.isMatch("flowers"));
    }

    @Test
    void testNoPatterns() {
        assertFalse(checker.isMatch("anything"));
    }
}
