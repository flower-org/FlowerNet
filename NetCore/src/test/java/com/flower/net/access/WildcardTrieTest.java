package com.flower.net.access;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WildcardTrieTest {
    @Test
    void testExactMatch() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("hello");
        assertTrue(trie.isMatch("hello"));
        assertFalse(trie.isMatch("hell"));
        assertFalse(trie.isMatch("helloo"));
    }

    @Test
    void testQuestionMarkWildcard() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("h?llo");
        assertTrue(trie.isMatch("hello"));
        assertTrue(trie.isMatch("hallo"));
        assertTrue(trie.isMatch("hillo"));
        assertFalse(trie.isMatch("helloc"));
    }

    @Test
    void testAsteriskWildcard() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("h*o");
        assertTrue(trie.isMatch("ho"));
        assertTrue(trie.isMatch("hello"));
        assertTrue(trie.isMatch("h123o"));
        assertFalse(trie.isMatch("h123"));
    }

    @Test
    void testAsteriskWildcard2() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("h*o");
        trie.addPattern("hg");
        assertFalse(trie.isMatch("hggg"));
    }

    @Test
    void testCombinationWildcards() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("h*l?o");
        assertTrue(trie.isMatch("hillo"));
        assertTrue(trie.isMatch("hello"));
        assertTrue(trie.isMatch("hlllllo"));
        assertFalse(trie.isMatch("hlo"));
    }

    @Test
    void testEdgeCases() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("*");
        assertTrue(trie.isMatch("anything"));
        assertTrue(trie.isMatch(""));
    }

    @Test
    void testEdgeCases2() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("****g");
        assertTrue(trie.isMatch("anything"));
        assertTrue(trie.isMatch("gg"));
    }

    @Test
    void testEdgeCases3() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("");
        assertTrue(trie.isMatch(""));
        assertFalse(trie.isMatch("a"));
    }

    @Test
    void testEdgeCases4() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("**");
        assertTrue(trie.isMatch("anything"));
        assertTrue(trie.isMatch(""));
    }

    @Test
    void testEdgeCases5() {
        WildcardTrie trie = new WildcardTrie();
        trie.addPattern("***");
        assertTrue(trie.isMatch("anything"));
        assertTrue(trie.isMatch(""));
    }
}