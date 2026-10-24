package com.flower.net.utils;

import com.flower.net.config.access.Access;

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
 * Trie of wildcard patterns supporting '*' (any sequence, including empty) and '?' (any single character),
 * matched against the whole text.
 * <p>
 * Each pattern can carry host rules (pattern only) and address rules (pattern + port), each either ALLOW or DENY.
 * Rules are reference-counted: a rule added N times must be removed N times before it disappears.
 * <p>
 * Patterns are stored verbatim (e.g. "a**b" and "a*b" are distinct patterns), so that removal mirrors addition.
 * <p>
 * Thread-safe via RWLock: matches run concurrently under a read lock, mutations are exclusive.
 */
public class WildcardTrie {
    static final char ANY_SEQUENCE = '*';
    static final char ANY_CHAR = '?';

    static class PortAccess {
        int allowCount;
        int denyCount;

        boolean isEmpty() {
            return allowCount == 0 && denyCount == 0;
        }
    }

    /** Rule counts for a pattern ending at a trie node. */
    static class RuleCounts {
        // Pattern shape, for specificity ranking: number of literal (non-wildcard) chars, number of '?',
        // and number of '*' runs (consecutive '*' count as one, as "a**b" is equivalent to "a*b")
        final int literalCount;
        final int anyCharCount;
        final int starRunCount;
        int allowHostRuleCount;
        int denyHostRuleCount;
        final Map<Integer, PortAccess> portAccess = new HashMap<>();

        RuleCounts(int literalCount, int anyCharCount, int starRunCount) {
            this.literalCount = literalCount;
            this.anyCharCount = anyCharCount;
            this.starRunCount = starRunCount;
        }

        boolean isEmpty() {
            return allowHostRuleCount == 0 && denyHostRuleCount == 0 && portAccess.isEmpty();
        }
    }

    static class Node {
        final Map<Character, Node> children = new HashMap<>();
        // True if this node is reached via a '*' edge; such a node can consume any number of characters.
        final boolean isStar;
        @Nullable RuleCounts rules;

        Node(boolean isStar) {
            this.isStar = isStar;
        }

        boolean isRemovable() {
            return rules == null && children.isEmpty();
        }
    }

    /** Immutable allow/deny rule counts. */
    public static final class AccessCounts {
        public static final AccessCounts EMPTY = new AccessCounts(0, 0);

        private final int allowCount;
        private final int denyCount;

        public AccessCounts(int allowCount, int denyCount) {
            this.allowCount = allowCount;
            this.denyCount = denyCount;
        }

        public int allowCount() { return allowCount; }
        public int denyCount() { return denyCount; }
        public boolean hasAllow() { return allowCount > 0; }
        public boolean hasDeny() { return denyCount > 0; }
        public boolean isEmpty() { return allowCount == 0 && denyCount == 0; }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) { return true; }
            if (!(o instanceof AccessCounts)) { return false; }
            AccessCounts that = (AccessCounts) o;
            return allowCount == that.allowCount && denyCount == that.denyCount;
        }

        @Override
        public int hashCode() {
            return 31 * allowCount + denyCount;
        }

        @Override
        public String toString() {
            return "AccessCounts{allow=" + allowCount + ", deny=" + denyCount + '}';
        }
    }

    /** Immutable snapshot of the rules of one pattern that matched the text. */
    public static final class Match {
        private final int literalCount;
        private final int anyCharCount;
        private final int starRunCount;
        private final AccessCounts hostAccess;
        private final Map<Integer, AccessCounts> portAccess;

        Match(RuleCounts rules) {
            this.literalCount = rules.literalCount;
            this.anyCharCount = rules.anyCharCount;
            this.starRunCount = rules.starRunCount;
            this.hostAccess = new AccessCounts(rules.allowHostRuleCount, rules.denyHostRuleCount);
            Map<Integer, AccessCounts> ports = new HashMap<>();
            for (Map.Entry<Integer, PortAccess> e : rules.portAccess.entrySet()) {
                ports.put(e.getKey(), new AccessCounts(e.getValue().allowCount, e.getValue().denyCount));
            }
            this.portAccess = Collections.unmodifiableMap(ports);
        }

        /** @return number of literal (non-wildcard) characters in the matched pattern. */
        public int literalCount() { return literalCount; }
        /** @return number of '?' characters in the matched pattern. */
        public int anyCharCount() { return anyCharCount; }
        /** @return number of '*' runs in the matched pattern; consecutive '*' count as one. */
        public int starRunCount() { return starRunCount; }
        public AccessCounts hostAccess() { return hostAccess; }
        public Map<Integer, AccessCounts> portAccess() { return portAccess; }

        public AccessCounts portAccess(int port) {
            AccessCounts counts = portAccess.get(port);
            return counts == null ? AccessCounts.EMPTY : counts;
        }

        @Override
        public String toString() {
            return "Match{literalCount=" + literalCount + ", anyCharCount=" + anyCharCount
                    + ", starRunCount=" + starRunCount
                    + ", hostAccess=" + hostAccess + ", portAccess=" + portAccess + '}';
        }
    }

    private final Node root = new Node(false);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // ---------------------------------------- Mutations ----------------------------------------

    public void addHostRule(String pattern, Access access) {
        lock.writeLock().lock();
        try {
            RuleCounts rules = getOrCreateRules(pattern);
            if (access == Access.ALLOW) {
                rules.allowHostRuleCount++;
            } else {
                rules.denyHostRuleCount++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void addAddressRule(String pattern, int port, Access access) {
        lock.writeLock().lock();
        try {
            PortAccess portAccess = getOrCreateRules(pattern).portAccess.computeIfAbsent(port, k -> new PortAccess());
            if (access == Access.ALLOW) {
                portAccess.allowCount++;
            } else {
                portAccess.denyCount++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** @return true if a host rule with the given pattern and access existed and its count was decremented. */
    public boolean removeHostRule(String pattern, Access access) {
        lock.writeLock().lock();
        try {
            List<Node> path = findPath(pattern);
            if (path == null) { return false; }
            RuleCounts rules = path.get(path.size() - 1).rules;
            if (rules == null) { return false; }
            if (access == Access.ALLOW) {
                if (rules.allowHostRuleCount == 0) { return false; }
                rules.allowHostRuleCount--;
            } else {
                if (rules.denyHostRuleCount == 0) { return false; }
                rules.denyHostRuleCount--;
            }
            cleanup(pattern, path);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** @return true if an address rule with the given pattern, port and access existed and its count was decremented. */
    public boolean removeAddressRule(String pattern, int port, Access access) {
        lock.writeLock().lock();
        try {
            List<Node> path = findPath(pattern);
            if (path == null) { return false; }
            RuleCounts rules = path.get(path.size() - 1).rules;
            if (rules == null) { return false; }
            PortAccess portAccess = rules.portAccess.get(port);
            if (portAccess == null) { return false; }
            if (access == Access.ALLOW) {
                if (portAccess.allowCount == 0) { return false; }
                portAccess.allowCount--;
            } else {
                if (portAccess.denyCount == 0) { return false; }
                portAccess.denyCount--;
            }
            if (portAccess.isEmpty()) {
                rules.portAccess.remove(port);
            }
            cleanup(pattern, path);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            root.children.clear();
            root.rules = null;
        } finally {
            lock.writeLock().unlock();
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

    // ---------------------------------------- Lookups ----------------------------------------

    /** @return rules of every pattern matching the text, one entry per matched pattern. */
    public List<Match> match(String text) {
        lock.readLock().lock();
        try {
            List<Match> matches = new ArrayList<>();
            for (Node node : matchNodes(text)) {
                RuleCounts rules = node.rules;
                if (rules != null) {
                    matches.add(new Match(rules));
                }
            }
            return matches;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** @return host rule counts summed across all patterns matching the text. */
    public AccessCounts matchHostRules(String text) {
        lock.readLock().lock();
        try {
            int allow = 0;
            int deny = 0;
            for (Node node : matchNodes(text)) {
                RuleCounts rules = node.rules;
                if (rules != null) {
                    allow += rules.allowHostRuleCount;
                    deny += rules.denyHostRuleCount;
                }
            }
            return allow == 0 && deny == 0 ? AccessCounts.EMPTY : new AccessCounts(allow, deny);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** @return address rule counts for the port, summed across all patterns matching the text. */
    public AccessCounts matchAddressRules(String text, int port) {
        lock.readLock().lock();
        try {
            int allow = 0;
            int deny = 0;
            for (Node node : matchNodes(text)) {
                RuleCounts rules = node.rules;
                if (rules != null) {
                    PortAccess portAccess = rules.portAccess.get(port);
                    if (portAccess != null) {
                        allow += portAccess.allowCount;
                        deny += portAccess.denyCount;
                    }
                }
            }
            return allow == 0 && deny == 0 ? AccessCounts.EMPTY : new AccessCounts(allow, deny);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ---------------------------------------- Internals ----------------------------------------

    private RuleCounts getOrCreateRules(String pattern) {
        Node node = root;
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
            node = node.children.computeIfAbsent(c, k -> new Node(k == ANY_SEQUENCE));
        }
        RuleCounts rules = node.rules;
        if (rules == null) {
            rules = new RuleCounts(literalCount, anyCharCount, starRunCount);
            node.rules = rules;
        }
        return rules;
    }

    /** @return nodes from root to the pattern's end node (inclusive), or null if the pattern is absent. */
    @Nullable private List<Node> findPath(String pattern) {
        List<Node> path = new ArrayList<>(pattern.length() + 1);
        Node node = root;
        path.add(node);
        for (int i = 0; i < pattern.length(); i++) {
            node = node.children.get(pattern.charAt(i));
            if (node == null) { return null; }
            path.add(node);
        }
        return path;
    }

    /** Drops empty rules at the pattern's end node and prunes now-empty branches bottom-up. */
    private static void cleanup(String pattern, List<Node> path) {
        Node last = path.get(path.size() - 1);
        RuleCounts rules = last.rules;
        if (rules != null && rules.isEmpty()) {
            last.rules = null;
        }
        for (int i = path.size() - 1; i > 0; i--) {
            Node node = path.get(i);
            if (!node.isRemovable()) { break; }
            path.get(i - 1).children.remove(pattern.charAt(i - 1));
        }
    }

    /**
     * NFA-style simulation over the trie: keeps the set of nodes reachable after consuming each prefix of the text.
     * Each node is visited at most once per text position, so cost is O(text.length * trieSize) in the worst case.
     */
    private Set<Node> matchNodes(String text) {
        Set<Node> current = new HashSet<>();
        current.add(root);
        addStarClosure(current);

        for (int i = 0; i < text.length() && !current.isEmpty(); i++) {
            char c = text.charAt(i);
            Set<Node> next = new HashSet<>();
            for (Node node : current) {
                Node exact = node.children.get(c);
                if (exact != null) { next.add(exact); }
                Node anyChar = node.children.get(ANY_CHAR);
                if (anyChar != null) { next.add(anyChar); }
                if (node.isStar) { next.add(node); }
            }
            addStarClosure(next);
            current = next;
        }
        return current;
    }

    /** '*' can match an empty sequence, so a '*' child is reachable without consuming input. */
    private static void addStarClosure(Set<Node> nodes) {
        Deque<Node> work = new ArrayDeque<>(nodes);
        while (!work.isEmpty()) {
            Node star = work.pop().children.get(ANY_SEQUENCE);
            if (star != null && nodes.add(star)) {
                work.push(star);
            }
        }
    }
}
