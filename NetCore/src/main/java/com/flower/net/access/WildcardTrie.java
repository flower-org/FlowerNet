package com.flower.net.access;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class WildcardTrie {
    private static class TrieNode {
        final boolean isAStar;
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfPattern = false;

        private TrieNode(boolean isAStar) {
            this.isAStar = isAStar;
        }
    }

    private final TrieNode root = new TrieNode(false);

    public void addPattern(String pattern) {
        TrieNode node = root;
        for (char ch : pattern.toCharArray()) {
            node = node.children.computeIfAbsent(ch, k -> new TrieNode(k == '*'));
        }
        node.isEndOfPattern = true;
    }

    public boolean isMatch(String text) {
        return isMatchRecursive(text, 0, root);
    }

    private boolean isMatchRecursive(String text, int index, TrieNode node) {
        if (index == text.length()) {
            if (node.isEndOfPattern) {
                return true;
            } else {
                if (node.children.containsKey('*')) {
                    // traversing the possible "star tail", since star can match 0 characters
                    TrieNode starNode = checkNotNull(node.children.get('*'));
                    return isMatchRecursive(text, index, starNode);
                } else {
                    return false;
                }
            }
        }

        char ch = text.charAt(index);

        // Exact character match
        if (node.children.containsKey(ch)
                && isMatchRecursive(text, index + 1, checkNotNull(node.children.get(ch)))) {
            return true;
        }
        // '?' - any character match
        if (node.children.containsKey('?')
                && isMatchRecursive(text, index + 1, checkNotNull(node.children.get('?')))) {
            return true;
        }

        // '*' can match zero or more characters
        if (node.isAStar || node.children.containsKey('*')) {
            TrieNode starNode = node.children.get('*');
            return (starNode != null &&
                    (isMatchRecursive(text, index + 1, starNode) // * matches 1 character
                        || isMatchRecursive(text, index, starNode)))   // * matches 0 characters
                    || (node.isAStar && isMatchRecursive(text, index + 1, node)); // * matches more than 1 character
        }

        return false;
    }
}