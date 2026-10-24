package com.flower.net.utils;

import com.flower.net.utils.WildcardTrie.Match;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WildcardTrieTest {

    @Test
    void testMatchReturnsValuesPerPattern() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("*.google.com", "allow-google");
        trie.addPatternValue("mail.*", "deny-mail");
        trie.addPatternValue("mail.*", "log-mail");
        trie.addPatternValue("*.yahoo.com", "deny-yahoo");

        assertEquals(Set.of(Set.of("allow-google"), Set.of("deny-mail", "log-mail")),
                valueSets(trie.matchText("mail.google.com")));
        assertEquals(Set.of(Set.of("deny-yahoo")), valueSets(trie.matchText("www.yahoo.com")));
        assertTrue(trie.matchText("google.com").isEmpty());
    }

    @Test
    void testAddPatternValueIsSetSemantics() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        assertTrue(trie.addPatternValue("*.com", "a"));
        assertFalse(trie.addPatternValue("*.com", "a"));
        assertTrue(trie.addPatternValue("*.com", "b"));
        assertEquals(Set.of("a", "b"), trie.getPatternValues("*.com"));

        assertTrue(trie.removePatternValue("*.com", "a"));
        assertFalse(trie.removePatternValue("*.com", "a"));
        assertEquals(Set.of("b"), trie.getPatternValues("*.com"));
        assertTrue(trie.removePatternValue("*.com", "b"));
        assertTrue(trie.getPatternValues("*.com").isEmpty());
        assertTrue(trie.matchText("a.com").isEmpty());
        assertTrue(trie.isEmpty());
    }

    @Test
    void testGetPatternValuesIsExact() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("*.com", "a");
        assertTrue(trie.getPatternValues("a.com").isEmpty());
        assertTrue(trie.getPatternValues("*.co").isEmpty());
        assertTrue(trie.getPatternValues("*.com.").isEmpty());
        assertEquals(Set.of("a"), trie.getPatternValues("*.com"));
    }

    @Test
    void testRemovePattern() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("*.com", "a");
        trie.addPatternValue("*.com", "b");
        trie.addPatternValue("*.co*", "c");
        assertEquals(Set.of("a", "b"), trie.removePattern("*.com"));
        assertTrue(trie.removePattern("*.com").isEmpty());
        assertEquals(Set.of(Set.of("c")), valueSets(trie.matchText("a.com")));
    }

    @Test
    void testSnapshotsAreDetached() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("*", "a");
        Set<String> got = trie.getPatternValues("*");
        Match<String> match = trie.matchText("x").get(0);
        trie.addPatternValue("*", "b");
        assertEquals(Set.of("a"), got);
        assertEquals(Set.of("a"), match.values());
        assertThrows(UnsupportedOperationException.class, () -> match.values().add("c"));
    }

    @Test
    void testLookupsShareStoredSetUntilMutation() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("*", "a");
        Set<String> before = trie.matchText("x").get(0).values();
        assertSame(before, trie.matchText("y").get(0).values());
        assertSame(before, trie.getPatternValues("*"));

        trie.addPatternValue("*", "b");
        assertNotSame(before, trie.getPatternValues("*"));
        assertEquals(Set.of("a"), before);

        Set<String> removed = trie.removePattern("*");
        assertEquals(Set.of("a", "b"), removed);
        assertTrue(trie.isEmpty());
    }

    @Test
    void testRemovalKeepsSharedPrefixes() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("abc", "1");
        trie.addPatternValue("abcdef", "2");
        trie.addPatternValue("ab*", "3");

        assertTrue(trie.removePatternValue("abcdef", "2"));
        assertEquals(Set.of(Set.of("1"), Set.of("3")), valueSets(trie.matchText("abc")));
        assertEquals(Set.of(Set.of("3")), valueSets(trie.matchText("abcdef")));

        assertTrue(trie.removePatternValue("abc", "1"));
        assertEquals(Set.of(Set.of("3")), valueSets(trie.matchText("abc")));

        assertTrue(trie.removePatternValue("ab*", "3"));
        assertTrue(trie.isEmpty());
    }

    @Test
    void testRemoveNonExisting() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        assertFalse(trie.removePatternValue("abc", "x"));
        trie.addPatternValue("abcd", "x");
        assertFalse(trie.removePatternValue("abc", "x"));
        assertFalse(trie.removePatternValue("abcde", "x"));
        assertFalse(trie.removePatternValue("abcd", "y"));
        assertEquals(1, trie.matchText("abcd").size());
    }

    @Test
    void testStarEdgeCases() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("*", "star");
        trie.addPatternValue("**", "star2");
        trie.addPatternValue("", "empty");
        trie.addPatternValue("a**b", "a**b");
        trie.addPatternValue("*****ba*****ab", "ba-ab");

        assertEquals(Set.of("star", "star2", "empty"), flatValues(trie.matchText("")));
        assertEquals(Set.of("star", "star2", "a**b"), flatValues(trie.matchText("ab")));
        assertEquals(Set.of("star", "star2", "a**b", "ba-ab"), flatValues(trie.matchText("abaab")));
        assertEquals(Set.of("star", "star2", "ba-ab"), flatValues(trie.matchText("baaabab")));
    }

    @Test
    void testQuestionMark() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("h?llo", "h?llo");
        trie.addPatternValue("?", "?");
        assertEquals(Set.of("h?llo"), flatValues(trie.matchText("hello")));
        assertEquals(Set.of("?"), flatValues(trie.matchText("x")));
        assertTrue(trie.matchText("").isEmpty());
        assertTrue(trie.matchText("hllo").isEmpty());
    }

    @Test
    void testClear() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("*", "a");
        trie.addPatternValue("a*", "b");
        trie.clear();
        assertTrue(trie.isEmpty());
        assertTrue(trie.matchText("abc").isEmpty());
    }

    @Test
    void testExactMatch() {
        assertTrue(isMatch("flower", "flower"));
    }

    @Test
    void testSingleCharWildcard() {
        assertTrue(isMatch("flower", "f?ower"));
        assertTrue(isMatch("hello", "h?llo"));
        assertFalse(isMatch("world", "w?rldd"));
    }

    @Test
    void testMultiCharWildcard() {
        assertTrue(isMatch("flower", "f*er"));
        assertTrue(isMatch("abcdef", "a*d*f"));
        assertTrue(isMatch("abc", "*"));
        assertTrue(isMatch("", "*"));
    }

    @Test
    void testMixedWildcards() {
        assertTrue(isMatch("abcdef", "a*d?f"));
        assertFalse(isMatch("abcdef", "a*d?e"));
    }

    @Test
    void testNoMatch() {
        assertFalse(isMatch("flower", "flow"));
        assertFalse(isMatch("hello", "h?l?o?"));
        assertFalse(isMatch("abc", "a*c?"));
    }

    @Test
    void testEmptyTextAndPattern() {
        assertTrue(isMatch("", ""));
        assertFalse(isMatch("", "?"));
        assertTrue(isMatch("", "*"));
    }

    @Test
    void testBacktracking() {
        assertTrue(isMatch("rjdokrjdokrjdokrjdok", "*jdok"));
        assertTrue(isMatch("baaabab", "*****ba*****ab"));
    }

    @Test
    void testMatchReportsPatternShape() {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue("*.g??gle.com", "x");
        List<Match<String>> matches = trie.matchText("www.google.com");
        assertEquals(1, matches.size());
        assertEquals(9, matches.get(0).literalCount());
        assertEquals(2, matches.get(0).anyCharCount());
        assertEquals(1, matches.get(0).starRunCount());

        WildcardTrie<String> starTrie = new WildcardTrie<>();
        starTrie.addPatternValue("**a***b*", "x");
        assertEquals(3, starTrie.matchText("ab").get(0).starRunCount());
    }

    @Test
    void testRandomizedAgainstReferenceMatcher() {
        Random random = new Random(12345);
        String alphabet = "ab.";
        String patternAlphabet = "ab.*?";

        for (int round = 0; round < 50; round++) {
            WildcardTrie<Integer> trie = new WildcardTrie<>();
            Map<String, Set<Integer>> model = new HashMap<>();
            List<String> addedPatterns = new ArrayList<>();

            for (int i = 0; i < 60; i++) {
                String p = randomString(random, patternAlphabet, random.nextInt(7));
                int value = random.nextInt(4);
                boolean added = model.computeIfAbsent(p, k -> new HashSet<>()).add(value);
                assertEquals(added, trie.addPatternValue(p, value));
                addedPatterns.add(p);
            }
            for (int i = 0; i < 25; i++) {
                String p = addedPatterns.get(random.nextInt(addedPatterns.size()));
                int value = random.nextInt(4);
                Set<Integer> values = model.getOrDefault(p, new HashSet<>());
                boolean removed = values.remove(value);
                if (values.isEmpty()) { model.remove(p); }
                assertEquals(removed, trie.removePatternValue(p, value));
            }

            for (Map.Entry<String, Set<Integer>> e : model.entrySet()) {
                assertEquals(e.getValue(), trie.getPatternValues(e.getKey()));
            }

            for (int t = 0; t < 100; t++) {
                String text = randomString(random, alphabet, random.nextInt(8));
                Set<Set<Integer>> expected = new HashSet<>();
                int expectedCount = 0;
                for (Map.Entry<String, Set<Integer>> e : model.entrySet()) {
                    if (referenceMatch(text, e.getKey())) {
                        expected.add(e.getValue());
                        expectedCount++;
                    }
                }
                List<Match<Integer>> matches = trie.matchText(text);
                assertEquals(expectedCount, matches.size(), "text=" + text);
                assertEquals(expected, valueSets(matches), "text=" + text);
            }
        }
    }

    private static <T> Set<Set<T>> valueSets(List<Match<T>> matches) {
        Set<Set<T>> result = new HashSet<>();
        for (Match<T> match : matches) { result.add(match.values()); }
        return result;
    }

    private static <T> Set<T> flatValues(List<Match<T>> matches) {
        Set<T> result = new HashSet<>();
        for (Match<T> match : matches) { result.addAll(match.values()); }
        return result;
    }

    private static boolean isMatch(String text, String pattern) {
        WildcardTrie<String> trie = new WildcardTrie<>();
        trie.addPatternValue(pattern, pattern);
        return !trie.matchText(text).isEmpty();
    }

    /** Straightforward DP matcher, used as an oracle for randomized tests. */
    private static boolean referenceMatch(String text, String pattern) {
        boolean[][] dp = new boolean[text.length() + 1][pattern.length() + 1];
        dp[0][0] = true;
        for (int j = 1; j <= pattern.length(); j++) {
            dp[0][j] = dp[0][j - 1] && pattern.charAt(j - 1) == '*';
        }
        for (int i = 1; i <= text.length(); i++) {
            for (int j = 1; j <= pattern.length(); j++) {
                char pc = pattern.charAt(j - 1);
                if (pc == '*') {
                    dp[i][j] = dp[i][j - 1] || dp[i - 1][j];
                } else {
                    dp[i][j] = dp[i - 1][j - 1] && (pc == '?' || pc == text.charAt(i - 1));
                }
            }
        }
        return dp[text.length()][pattern.length()];
    }

    private static String randomString(Random random, String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
