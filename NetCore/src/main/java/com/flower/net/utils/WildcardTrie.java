package com.flower.net.utils;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Multimap from wildcard patterns to sets of values, supporting '*' (any sequence, including empty)
 * and '?' (any single character), matched against the whole text.
 * <p>
 * Each pattern holds a set of values: adding an equal value twice keeps one, and a pattern disappears
 * once its last value is removed.
 * <p>
 * Patterns are stored verbatim (e.g. "a**b" and "a*b" are distinct patterns), so that removal mirrors addition.
 * <p>
 * Each {@link Match} carries the matched pattern's shape (literal, '?' and '*'-run counts) so callers can rank
 * overlapping matches by specificity; the trie itself imposes no ordering.
 * <p>
 * Thread-safe via RWLock: matches run concurrently under a read lock, mutations are exclusive.
 * Copy-on-write: each pattern's value set is immutable and replaced on mutation, so lookups hand out
 * the stored sets without copying, and returned sets never change.
 *
 * @param <T> value type; must have consistent equals/hashCode
 */
public class WildcardTrie<T> {
    static final char ANY_SEQUENCE = '*';
    static final char ANY_CHAR = '?';

    /** Immutable: values of a pattern ending at a trie node, plus the pattern's shape. Replaced on mutation. */
    static final class Entry<T> {
        // Pattern shape, for specificity ranking: number of literal (non-wildcard) chars, number of '?',
        // and number of '*' runs (consecutive '*' count as one, as "a**b" is equivalent to "a*b")
        final int literalCount;
        final int anyCharCount;
        final int starRunCount;
        // Unmodifiable and never mutated after construction
        final Set<T> values;

        Entry(int literalCount, int anyCharCount, int starRunCount, Set<T> values) {
            this.literalCount = literalCount;
            this.anyCharCount = anyCharCount;
            this.starRunCount = starRunCount;
            this.values = values;
        }

        Entry<T> withValues(Set<T> newValues) {
            return new Entry<>(literalCount, anyCharCount, starRunCount, newValues);
        }
    }

    static class Node<T> {
        final Map<Character, Node<T>> children = new HashMap<>();
        // True if this node is reached via a '*' edge; such a node can consume any number of characters.
        final boolean isStar;
        @Nullable Entry<T> entry;

        Node(boolean isStar) {
            this.isStar = isStar;
        }

        boolean isRemovable() {
            return entry == null && children.isEmpty();
        }
    }

    /** Immutable snapshot of one pattern that matched the text: its values and shape. */
    public static final class Match<T> {
        private final Set<T> values;
        private final int literalCount;
        private final int anyCharCount;
        private final int starRunCount;

        Match(Entry<T> entry) {
            this.values = entry.values;
            this.literalCount = entry.literalCount;
            this.anyCharCount = entry.anyCharCount;
            this.starRunCount = entry.starRunCount;
        }

        /** @return values stored under the matched pattern. */
        public Set<T> values() { return values; }
        /** @return number of literal (non-wildcard) characters in the matched pattern. */
        public int literalCount() { return literalCount; }
        /** @return number of '?' characters in the matched pattern. */
        public int anyCharCount() { return anyCharCount; }
        /** @return number of '*' runs in the matched pattern; consecutive '*' count as one. */
        public int starRunCount() { return starRunCount; }

        @Override
        public String toString() {
            return "Match{values=" + values + ", literalCount=" + literalCount + ", anyCharCount=" + anyCharCount
                    + ", starRunCount=" + starRunCount + '}';
        }
    }

    private final Node<T> root = new Node<>(false);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // ---------------------------------------- Mutations ----------------------------------------

    /** @return true if the value was not yet present under the pattern. */
    public boolean addPatternValue(String pattern, T value) {
        lock.writeLock().lock();
        try {
            Node<T> node = getOrCreateNode(pattern);
            Entry<T> entry = node.entry;
            if (entry == null) {
                node.entry = newEntry(pattern, Collections.singleton(value));
                return true;
            }
            if (entry.values.contains(value)) { return false; }
            Set<T> newValues = new HashSet<>(entry.values);
            newValues.add(value);
            node.entry = entry.withValues(Collections.unmodifiableSet(newValues));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** @return true if the value was present under the pattern and got removed. */
    public boolean removePatternValue(String pattern, T value) {
        lock.writeLock().lock();
        try {
            List<Node<T>> path = findPath(pattern);
            if (path == null) { return false; }
            Node<T> node = path.get(path.size() - 1);
            Entry<T> entry = node.entry;
            if (entry == null || !entry.values.contains(value)) { return false; }
            if (entry.values.size() == 1) {
                node.entry = null;
                prune(pattern, path);
            } else {
                Set<T> newValues = new HashSet<>(entry.values);
                newValues.remove(value);
                node.entry = entry.withValues(Collections.unmodifiableSet(newValues));
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** @return values that were stored under the pattern; empty if none. */
    public Set<T> removePattern(String pattern) {
        lock.writeLock().lock();
        try {
            List<Node<T>> path = findPath(pattern);
            if (path == null) { return Collections.emptySet(); }
            Node<T> node = path.get(path.size() - 1);
            Entry<T> entry = node.entry;
            if (entry == null) { return Collections.emptySet(); }
            node.entry = null;
            prune(pattern, path);
            return entry.values;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            root.children.clear();
            root.entry = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ---------------------------------------- Lookups ----------------------------------------

    /**
     * Exact lookup by pattern (no wildcard matching; use {@link #matchText} for that).
     * @return immutable snapshot of values stored under the pattern; empty if none.
     */
    public Set<T> getPatternValues(String pattern) {
        lock.readLock().lock();
        try {
            List<Node<T>> path = findPath(pattern);
            if (path == null) { return Collections.emptySet(); }
            Entry<T> entry = path.get(path.size() - 1).entry;
            return entry == null ? Collections.emptySet() : entry.values;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return root.isRemovable();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** @return one entry per stored pattern matching the text, in no particular order. */
    public List<Match<T>> matchText(String text) {
        lock.readLock().lock();
        try {
            List<Match<T>> matches = new ArrayList<>();
            for (Node<T> node : matchNodes(text)) {
                Entry<T> entry = node.entry;
                if (entry != null) {
                    matches.add(new Match<>(entry));
                }
            }
            return matches;
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---------------------------------------- Internals ----------------------------------------

    private Node<T> getOrCreateNode(String pattern) {
        Node<T> node = root;
        for (int i = 0; i < pattern.length(); i++) {
            node = node.children.computeIfAbsent(pattern.charAt(i), k -> new Node<>(k == ANY_SEQUENCE));
        }
        return node;
    }

    /** Computes the pattern's shape once, when the pattern's entry is first created. */
    private static <T> Entry<T> newEntry(String pattern, Set<T> values) {
        int literalCount = 0;
        int anyCharCount = 0;
        int starRunCount = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == ANY_SEQUENCE) {
                if (i == 0 || pattern.charAt(i - 1) != ANY_SEQUENCE) {
                    starRunCount++;
                }
            } else if (c == ANY_CHAR) {
                anyCharCount++;
            } else {
                literalCount++;
            }
        }
        return new Entry<>(literalCount, anyCharCount, starRunCount, values);
    }

    /** @return nodes from root to the pattern's end node (inclusive), or null if the pattern is absent. */
    @Nullable private List<Node<T>> findPath(String pattern) {
        List<Node<T>> path = new ArrayList<>(pattern.length() + 1);
        Node<T> node = root;
        path.add(node);
        for (int i = 0; i < pattern.length(); i++) {
            node = node.children.get(pattern.charAt(i));
            if (node == null) { return null; }
            path.add(node);
        }
        return path;
    }

    /** Prunes now-empty branches bottom-up along the pattern's path. */
    private static <T> void prune(String pattern, List<Node<T>> path) {
        for (int i = path.size() - 1; i > 0; i--) {
            Node<T> node = path.get(i);
            if (!node.isRemovable()) { break; }
            path.get(i - 1).children.remove(pattern.charAt(i - 1));
        }
    }

    /**
     * NFA-style simulation over the trie: keeps the set of nodes reachable after consuming each prefix of the text.
     * Each node is visited at most once per text position, so cost is O(text.length * trieSize) in the worst case.
     */
    private Set<Node<T>> matchNodes(String text) {
        Set<Node<T>> current = new HashSet<>();
        current.add(root);
        addStarClosure(current);

        for (int i = 0; i < text.length() && !current.isEmpty(); i++) {
            char c = text.charAt(i);
            Set<Node<T>> next = new HashSet<>();
            for (Node<T> node : current) {
                Node<T> exact = node.children.get(c);
                if (exact != null) { next.add(exact); }
                Node<T> anyChar = node.children.get(ANY_CHAR);
                if (anyChar != null) { next.add(anyChar); }
                if (node.isStar) { next.add(node); }
            }
            addStarClosure(next);
            current = next;
        }
        return current;
    }

    /** '*' can match an empty sequence, so a '*' child is reachable without consuming input. */
    private static <T> void addStarClosure(Set<Node<T>> nodes) {
        Deque<Node<T>> work = new ArrayDeque<>(nodes);
        while (!work.isEmpty()) {
            Node<T> star = work.pop().children.get(ANY_SEQUENCE);
            if (star != null && nodes.add(star)) {
                work.push(star);
            }
        }
    }
}
