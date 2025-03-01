package com.flower.net.access;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WildcardTrieTest {
    @Test
    void testExactMatch() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("hello");
        assertTrue(trie.isMatch("hello"));
        assertFalse(trie.isMatch("hell"));
        assertFalse(trie.isMatch("helloo"));
    }

    @Test
    void testQuestionMarkWildcard() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("h?llo");
        assertTrue(trie.isMatch("hello"));
        assertTrue(trie.isMatch("hallo"));
        assertTrue(trie.isMatch("hillo"));
        assertFalse(trie.isMatch("helloc"));
    }

    @Test
    void testAsteriskWildcard() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("h*o");
        assertTrue(trie.isMatch("ho"));
        assertTrue(trie.isMatch("hello"));
        assertTrue(trie.isMatch("h123o"));
        assertFalse(trie.isMatch("h123"));
    }

    @Test
    void testAsteriskWildcard2() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("h*o");
        trie.addPattern("hg");
        assertFalse(trie.isMatch("hggg"));
    }

    @Test
    void testCombinationWildcards() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("h*l?o");
        assertTrue(trie.isMatch("hillo"));
        assertTrue(trie.isMatch("hello"));
        assertTrue(trie.isMatch("hlllllo"));
        assertFalse(trie.isMatch("hlo"));
    }

    @Test
    void testEdgeCases() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("*");
        assertTrue(trie.isMatch("anything"));
        assertTrue(trie.isMatch(""));
    }

    @Test
    void testEdgeCases2() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("****g");
        assertTrue(trie.isMatch("anything"));
        assertTrue(trie.isMatch("gg"));
        assertFalse(trie.isMatch(""));
    }

    @Test
    void testEdgeCases3() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("");
        assertTrue(trie.isMatch(""));
        assertFalse(trie.isMatch("a"));
    }

    @Test
    void testEdgeCases4() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("**");
        assertTrue(trie.isMatch("anything"));
        assertTrue(trie.isMatch(""));
    }

    @Test
    void testEdgeCases5() {
        WildcardTrieChecker trie = new WildcardTrieChecker();
        trie.addPattern("***");
        assertTrue(trie.isMatch("anything"));
        assertTrue(trie.isMatch(""));
    }
}