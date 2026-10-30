package com.flower.net.conntrack.allowdenylist;

import com.flower.net.config.access.Access;
import com.flower.net.utils.WildcardTrie;
import com.flower.net.utils.WildcardTrie.Match;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.flower.net.config.access.Access.ALLOW;
import static com.flower.net.config.access.Access.DENY;
import static org.junit.jupiter.api.Assertions.*;

class AllowDenyConnectionFilterTest {
    private static AddressFilterList.HostRecord host(String host, Access access, boolean wildcard) {
        return ImmutableHostRecord.builder().dstHost(host).access(access)
                .creationTimestamp(0L).isWildcard(wildcard).build();
    }

    private static AddressFilterList.AddressRecord address(String host, int port, Access access, boolean wildcard) {
        return ImmutableAddressRecord.builder().dstHost(host).dstPort(port).access(access)
                .creationTimestamp(0L).isWildcard(wildcard).build();
    }

    private static AddressFilterList.PortRecord port(int port, Access access) {
        return ImmutablePortRecord.builder().dstPort(port).access(access).creationTimestamp(0L).build();
    }

    private static int compareSpecificity(String pattern1, String pattern2) {
        WildcardTrie<String> trie1 = new WildcardTrie<>();
        trie1.addPatternValue(pattern1, pattern1);
        WildcardTrie<String> trie2 = new WildcardTrie<>();
        trie2.addPatternValue(pattern2, pattern2);
        // Match each pattern against itself with wildcards removed, which every pattern accepts
        Match<String> match1 = trie1.matchText(pattern1.replace("*", "").replace('?', 'x')).get(0);
        Match<String> match2 = trie2.matchText(pattern2.replace("*", "").replace('?', 'x')).get(0);
        return AllowDenyConnectionFilter.compareSpecificity(match1, match2);
    }

    @Test
    void testCompareSpecificity() {
        assertTrue(compareSpecificity("www.google.*", "*.com") > 0);
        assertTrue(compareSpecificity("a?c", "a*c") > 0);
        assertTrue(compareSpecificity("*", "?") < 0);
        assertEquals(0, compareSpecificity("*.google.com", "www.google.*"));
        assertEquals(0, compareSpecificity("a**b", "a*b"));
        assertTrue(compareSpecificity("a*bc", "a*b*c") > 0);
        assertTrue(compareSpecificity("*abc", "*a*b*c*") > 0);
        assertEquals(0, compareSpecificity("*ab", "a*b"));
    }

    @Test
    void testFewerStarsWinOnLiteralTie() {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addHostRecord(host("a*b*c", DENY, true), true);
        filter.addHostRecord(host("a*bc", ALLOW, true), true);

        assertEquals(ALLOW, filter.getRecordRule("axxbc", 80));
        assertEquals(DENY, filter.getRecordRule("abxc", 80));
    }

    @Test
    void testMostSpecificWildcardHostWins() {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addHostRecord(host("*", DENY, true), true);
        filter.addHostRecord(host("*.google.com", ALLOW, true), true);
        filter.addHostRecord(host("ads.google.com*", DENY, true), true);

        assertEquals(ALLOW, filter.getRecordRule("www.google.com", 443));
        assertEquals(DENY, filter.getRecordRule("ads.google.com", 443));
        assertEquals(DENY, filter.getRecordRule("example.org", 443));
    }

    @Test
    void testSpecificityTieDenyWins() {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addHostRecord(host("*.google.com", ALLOW, true), true);
        filter.addHostRecord(host("www.google.*", DENY, true), true);

        assertEquals(DENY, filter.getRecordRule("www.google.com", 443));
        assertEquals(ALLOW, filter.getRecordRule("mail.google.com", 443));
    }

    @Test
    void testPriorities() {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addPortRecord(port(443, DENY), true);
        assertEquals(DENY, filter.getRecordRule("a.com", 443));

        // Wildcard host beats port
        filter.addHostRecord(host("*.com", ALLOW, true), true);
        assertEquals(ALLOW, filter.getRecordRule("a.com", 443));

        // Exact host beats wildcard host
        filter.addHostRecord(host("a.com", DENY, false), true);
        assertEquals(DENY, filter.getRecordRule("a.com", 443));
        assertEquals(DENY, filter.getRecordRule("a.com", 80));

        // Wildcard host+port beats exact host, even if the pattern is less specific
        filter.addAddressRecord(address("*", 443, ALLOW, true), true);
        assertEquals(ALLOW, filter.getRecordRule("a.com", 443));
        assertEquals(DENY, filter.getRecordRule("a.com", 80));

        // Exact host+port beats everything
        filter.addAddressRecord(address("a.com", 443, DENY, false), true);
        assertEquals(DENY, filter.getRecordRule("a.com", 443));

        assertNull(filter.getRecordRule("a.org", 80));
    }

    @Test
    void testHostPortRuleBeatsHostRule() {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addHostRecord(host("a.com", DENY, false), true);
        filter.addAddressRecord(address("*.com", 443, ALLOW, true), true);
        filter.addPortRecord(port(80, ALLOW), true);

        assertEquals(ALLOW, filter.getRecordRule("a.com", 443));
        // Host rule beats port rule
        assertEquals(DENY, filter.getRecordRule("a.com", 80));
        assertEquals(ALLOW, filter.getRecordRule("b.org", 80));
    }

    @Test
    void testHasAddressRecord() {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addHostRecord(host("a.com", ALLOW, false), true);
        filter.addPortRecord(port(443, ALLOW), true);
        filter.addHostRecord(host("*.org", DENY, true), true);
        assertFalse(filter.hasAddressRecord("a.com", 443));
        assertFalse(filter.hasAddressRecord("b.org", 443));

        filter.addAddressRecord(address("a.com", 443, DENY, false), true);
        filter.addAddressRecord(address("*.org", 80, ALLOW, true), true);
        assertTrue(filter.hasAddressRecord("a.com", 443));
        assertFalse(filter.hasAddressRecord("a.com", 80));
        assertTrue(filter.hasAddressRecord("b.org", 80));
        assertFalse(filter.hasAddressRecord("b.org", 443));
    }

    @Test
    void testOverrideAndRemoveKeepTrieInSync() {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addHostRecord(host("*.com", ALLOW, true), true);
        filter.addHostRecord(host("*.com", DENY, true), false);
        assertEquals(ALLOW, filter.getRecordRule("a.com", 80));

        filter.addHostRecord(host("*.com", DENY, true), true);
        assertEquals(DENY, filter.getRecordRule("a.com", 80));

        filter.removeHostRecord(host("*.com", DENY, true));
        assertNull(filter.getRecordRule("a.com", 80));
        assertTrue(filter.wildcardHostTrie.isEmpty() && filter.wildcardAddressTrie.isEmpty());

        filter.addAddressRecord(address("*.com", 80, ALLOW, true), true);
        filter.addAddressRecord(address("*.com", 80, DENY, true), true);
        assertEquals(DENY, filter.getRecordRule("a.com", 80));
        filter.removeAddressRecord("*.com", 80, true);
        assertNull(filter.getRecordRule("a.com", 80));
        assertTrue(filter.wildcardHostTrie.isEmpty() && filter.wildcardAddressTrie.isEmpty());
    }

    @Test
    void testClearFilterTypeAndClear() {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addHostRecord(host("*.com", ALLOW, true), true);
        filter.addHostRecord(host("*.org", DENY, true), true);
        filter.addAddressRecord(address("*.net", 80, DENY, true), true);

        filter.clearFilterType(DENY);
        assertEquals(ALLOW, filter.getRecordRule("a.com", 80));
        assertNull(filter.getRecordRule("a.org", 80));
        assertNull(filter.getRecordRule("a.net", 80));

        filter.clear();
        assertNull(filter.getRecordRule("a.com", 80));
        assertTrue(filter.wildcardHostTrie.isEmpty() && filter.wildcardAddressTrie.isEmpty());
    }

    @Test
    void testLookupNeverSeesHalfAppliedOverride() throws InterruptedException {
        AllowDenyConnectionFilter filter = new AllowDenyConnectionFilter();
        filter.addHostRecord(host("*.com", ALLOW, true), true);

        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger nullResults = new AtomicInteger();
        Thread reader = new Thread(() -> {
            while (!stop.get()) {
                if (filter.getRecordRule("a.com", 80) == null) {
                    nullResults.incrementAndGet();
                }
            }
        });
        reader.start();
        for (int i = 0; i < 20_000; i++) {
            filter.addHostRecord(host("*.com", i % 2 == 0 ? DENY : ALLOW, true), true);
        }
        stop.set(true);
        reader.join();

        assertEquals(0, nullResults.get());
    }

    @Test
    void testAddListAndGetFullListRoundTrip() {
        AllowDenyConnectionFilter source = new AllowDenyConnectionFilter();
        source.addHostRecord(host("*.com", ALLOW, true), true);
        source.addAddressRecord(address("*.com", 25, DENY, true), true);

        AllowDenyConnectionFilter copy = new AllowDenyConnectionFilter();
        copy.addList(source.getFullList(), true);
        assertEquals(ALLOW, copy.getRecordRule("a.com", 80));
        assertEquals(DENY, copy.getRecordRule("a.com", 25));
    }
}
