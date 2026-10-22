package com.flower.net.utils;

import com.flower.net.config.access.Access;
import com.flower.net.utils.WildcardTrie.AccessCounts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.flower.net.config.access.Access.ALLOW;
import static com.flower.net.config.access.Access.DENY;
import static org.junit.jupiter.api.Assertions.*;

class WildcardTrieTest {

    @Test
    void testHostRuleMatch() {
        WildcardTrie trie = new WildcardTrie();
        trie.addHostRule("*.google.com", ALLOW);
        trie.addHostRule("mail.*", DENY);
        trie.addHostRule("*.yahoo.com", DENY);

        assertEquals(2, trie.match("mail.google.com").size());
        assertEquals(new AccessCounts(1, 1), trie.matchHostRules("mail.google.com"));
        assertEquals(new AccessCounts(0, 1), trie.matchHostRules("www.yahoo.com"));
        assertEquals(AccessCounts.EMPTY, trie.matchHostRules("google.com"));
        assertTrue(trie.match("google.com").isEmpty());
    }

    @Test
    void testAddressRuleMatch() {
        WildcardTrie trie = new WildcardTrie();
        trie.addAddressRule("*.google.com", 443, ALLOW);
        trie.addAddressRule("*.google.com", 80, DENY);
        trie.addHostRule("*.google.com", DENY);
        trie.addAddressRule("www.?oogle.com", 443, DENY);

        List<WildcardTrie.Match> matches = trie.match("www.google.com");
        assertEquals(2, matches.size());
        WildcardTrie.Match m = matches.stream().filter(x -> x.hostAccess().hasDeny()).findFirst().orElseThrow();
        assertEquals(new AccessCounts(0, 1), m.hostAccess());
        assertEquals(Map.of(443, new AccessCounts(1, 0), 80, new AccessCounts(0, 1)), m.portAccess());
        assertEquals(AccessCounts.EMPTY, m.portAccess(8080));

        assertEquals(new AccessCounts(1, 1), trie.matchAddressRules("www.google.com", 443));
        assertEquals(new AccessCounts(0, 1), trie.matchAddressRules("www.google.com", 80));
        assertEquals(AccessCounts.EMPTY, trie.matchAddressRules("www.google.com", 8080));
        assertEquals(new AccessCounts(0, 1), trie.matchHostRules("www.google.com"));
    }

    @Test
    void testRefCountedHostRemoval() {
        WildcardTrie trie = new WildcardTrie();
        for (int i = 0; i < 5; i++) { trie.addHostRule("*.example.com", ALLOW); }
        trie.addHostRule("*.example.com", DENY);
        assertEquals(new AccessCounts(5, 1), trie.matchHostRules("a.example.com"));

        for (int i = 4; i >= 0; i--) {
            assertTrue(trie.removeHostRule("*.example.com", ALLOW));
            assertEquals(new AccessCounts(i, 1), trie.matchHostRules("a.example.com"));
        }
        assertFalse(trie.removeHostRule("*.example.com", ALLOW));
        assertTrue(trie.removeHostRule("*.example.com", DENY));
        assertFalse(trie.removeHostRule("*.example.com", DENY));
        assertTrue(trie.match("a.example.com").isEmpty());
        assertTrue(trie.isEmpty());
    }

    @Test
    void testRefCountedAddressRemoval() {
        WildcardTrie trie = new WildcardTrie();
        trie.addAddressRule("*.example.com", 443, ALLOW);
        trie.addAddressRule("*.example.com", 443, ALLOW);
        trie.addAddressRule("*.example.com", 443, DENY);
        trie.addAddressRule("*.example.com", 80, DENY);

        assertFalse(trie.removeAddressRule("*.example.com", 8080, ALLOW));
        assertFalse(trie.removeAddressRule("*.example.com", 80, ALLOW));
        assertFalse(trie.removeHostRule("*.example.com", ALLOW));

        assertTrue(trie.removeAddressRule("*.example.com", 443, ALLOW));
        assertEquals(new AccessCounts(1, 1), trie.matchAddressRules("a.example.com", 443));
        assertTrue(trie.removeAddressRule("*.example.com", 443, ALLOW));
        assertTrue(trie.removeAddressRule("*.example.com", 443, DENY));
        assertEquals(Map.of(80, new AccessCounts(0, 1)), trie.match("a.example.com").get(0).portAccess());
        assertTrue(trie.removeAddressRule("*.example.com", 80, DENY));
        assertTrue(trie.match("a.example.com").isEmpty());
        assertTrue(trie.isEmpty());
    }

    @Test
    void testRemovalKeepsSharedPrefixes() {
        WildcardTrie trie = new WildcardTrie();
        trie.addHostRule("abc", ALLOW);
        trie.addHostRule("abcdef", ALLOW);
        trie.addHostRule("ab*", DENY);

        assertTrue(trie.removeHostRule("abcdef", ALLOW));
        assertEquals(new AccessCounts(1, 1), trie.matchHostRules("abc"));
        assertEquals(new AccessCounts(0, 1), trie.matchHostRules("abcdef"));

        assertTrue(trie.removeHostRule("abc", ALLOW));
        assertEquals(new AccessCounts(0, 1), trie.matchHostRules("abc"));

        assertTrue(trie.removeHostRule("ab*", DENY));
        assertTrue(trie.isEmpty());
    }

    @Test
    void testRemoveNonExisting() {
        WildcardTrie trie = new WildcardTrie();
        assertFalse(trie.removeHostRule("abc", ALLOW));
        assertFalse(trie.removeAddressRule("abc", 1, DENY));
        trie.addHostRule("abcd", ALLOW);
        assertFalse(trie.removeHostRule("abc", ALLOW));
        assertFalse(trie.removeHostRule("abcde", ALLOW));
        assertFalse(trie.removeHostRule("abcd", DENY));
        assertEquals(1, trie.match("abcd").size());
    }

    @Test
    void testStarEdgeCases() {
        WildcardTrie trie = new WildcardTrie();
        trie.addHostRule("*", ALLOW);
        trie.addHostRule("**", ALLOW);
        trie.addHostRule("", DENY);
        trie.addHostRule("a**b", DENY);
        trie.addHostRule("*****ba*****ab", DENY);

        assertEquals(new AccessCounts(2, 1), trie.matchHostRules(""));
        assertEquals(new AccessCounts(2, 1), trie.matchHostRules("ab"));
        assertEquals(new AccessCounts(2, 2), trie.matchHostRules("abaab"));
        assertEquals(new AccessCounts(2, 1), trie.matchHostRules("baaabab"));
    }

    @Test
    void testQuestionMark() {
        WildcardTrie trie = new WildcardTrie();
        trie.addHostRule("h?llo", ALLOW);
        trie.addHostRule("?", DENY);
        assertEquals(new AccessCounts(1, 0), trie.matchHostRules("hello"));
        assertEquals(new AccessCounts(0, 1), trie.matchHostRules("x"));
        assertTrue(trie.match("").isEmpty());
        assertTrue(trie.match("hllo").isEmpty());
    }

    @Test
    void testClear() {
        WildcardTrie trie = new WildcardTrie();
        trie.addHostRule("*", ALLOW);
        trie.addAddressRule("a*", 1, DENY);
        trie.clear();
        assertTrue(trie.isEmpty());
        assertTrue(trie.match("abc").isEmpty());
    }

    @Test
    void testRandomizedAgainstWildcardMatcher() {
        Random random = new Random(12345);
        String alphabet = "ab.";
        String patternAlphabet = "ab.*?";
        int[] ports = {80, 443};

        for (int round = 0; round < 50; round++) {
            WildcardTrie trie = new WildcardTrie();
            // Reference model: key = pattern + "|" + port (port -1 for host rules) -> {allow, deny}
            Map<String, int[]> model = new HashMap<>();
            List<String> keys = new ArrayList<>();

            for (int i = 0; i < 60; i++) {
                String p = randomString(random, patternAlphabet, random.nextInt(7));
                int port = random.nextBoolean() ? -1 : ports[random.nextInt(ports.length)];
                Access access = random.nextBoolean() ? ALLOW : DENY;
                if (port == -1) {
                    trie.addHostRule(p, access);
                } else {
                    trie.addAddressRule(p, port, access);
                }
                String key = p + "|" + port;
                model.computeIfAbsent(key, k -> new int[2])[access == ALLOW ? 0 : 1]++;
                keys.add(key + "|" + access);
            }
            for (int i = 0; i < 25; i++) {
                String[] parts = keys.get(random.nextInt(keys.size())).split("\\|", -1);
                String p = parts[0];
                int port = Integer.parseInt(parts[1]);
                Access access = Access.valueOf(parts[2]);
                int[] counts = model.get(p + "|" + port);
                int idx = access == ALLOW ? 0 : 1;
                boolean removed = port == -1 ? trie.removeHostRule(p, access) : trie.removeAddressRule(p, port, access);
                assertEquals(counts[idx] > 0, removed);
                if (removed) { counts[idx]--; }
            }

            for (int t = 0; t < 100; t++) {
                String text = randomString(random, alphabet, random.nextInt(8));
                int expHostAllow = 0, expHostDeny = 0;
                Map<Integer, int[]> expPorts = new HashMap<>();
                for (Map.Entry<String, int[]> e : model.entrySet()) {
                    String[] parts = e.getKey().split("\\|", -1);
                    if (!WildcardMatcher.isMatch(text, parts[0])) { continue; }
                    int port = Integer.parseInt(parts[1]);
                    if (port == -1) {
                        expHostAllow += e.getValue()[0];
                        expHostDeny += e.getValue()[1];
                    } else {
                        int[] pc = expPorts.computeIfAbsent(port, k -> new int[2]);
                        pc[0] += e.getValue()[0];
                        pc[1] += e.getValue()[1];
                    }
                }
                assertEquals(new AccessCounts(expHostAllow, expHostDeny), trie.matchHostRules(text), "text=" + text);
                for (int port : ports) {
                    int[] pc = expPorts.getOrDefault(port, new int[2]);
                    assertEquals(new AccessCounts(pc[0], pc[1]), trie.matchAddressRules(text, port),
                            "text=" + text + " port=" + port);
                }
            }
        }
    }

    private static String randomString(Random random, String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
