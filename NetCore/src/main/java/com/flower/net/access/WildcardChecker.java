package com.flower.net.access;

//TODO: do we need this class?
public class WildcardChecker {
    private final WildcardTrie trie = new WildcardTrie();

    public void addPattern(String pattern) {
        trie.addPattern(pattern);
    }

    public boolean isMatch(String text) {
        return trie.isMatch(text);
    }
}
