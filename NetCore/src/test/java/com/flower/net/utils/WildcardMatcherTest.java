package com.flower.net.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WildcardMatcherTest {

    @Test
    void testExactMatch() {
        assertTrue(WildcardMatcher.isMatch("flower", "flower"));
    }

    @Test
    void testSingleCharacterWildcard() {
        assertTrue(WildcardMatcher.isMatch("flower", "f?ower"));
        assertTrue(WildcardMatcher.isMatch("hello", "h?llo"));
        assertFalse(WildcardMatcher.isMatch("world", "w?rldd"));
    }

    @Test
    void testStarWildcard() {
        assertTrue(WildcardMatcher.isMatch("flower", "f*er"));
        assertTrue(WildcardMatcher.isMatch("abcdef", "a*d*f"));
        assertTrue(WildcardMatcher.isMatch("abc", "*"));
        assertTrue(WildcardMatcher.isMatch("", "*"));
    }

    @Test
    void testStarAndQuestionMarkCombination() {
        assertTrue(WildcardMatcher.isMatch("abcdef", "a*d?f"));
        assertFalse(WildcardMatcher.isMatch("abcdef", "a*d?e"));
    }

    @Test
    void testNoMatchCases() {
        assertFalse(WildcardMatcher.isMatch("flower", "flow"));
        assertFalse(WildcardMatcher.isMatch("hello", "h?l?o?"));
        assertFalse(WildcardMatcher.isMatch("abc", "a*c?"));
    }

    @Test
    void testEmptyStrings() {
        assertTrue(WildcardMatcher.isMatch("", ""));
        assertFalse(WildcardMatcher.isMatch("", "?"));
        assertTrue(WildcardMatcher.isMatch("", "*"));
    }

    @Test
    void testMainExample1() {
        assertTrue(WildcardMatcher.isMatch("rjdokrjdokrjdokrjdok", "*jdok"));
    }

    @Test
    void testMainExample2() {
        assertTrue(WildcardMatcher.isMatch("baaabab", "*****ba*****ab"));
    }
}
