package com.flower.net.access;

import com.flower.net.utils.WildcardMatcher;

import java.util.HashSet;
import java.util.Set;

public class WildcardChecker {
    private final Set<String> patterns = new HashSet<>();

    public void addPattern(String pattern) {
        patterns.add(pattern);
    }

    public void removePattern(String pattern) {
        patterns.remove(pattern);
    }

    public boolean isMatch(String text) {
        return patterns.stream()
                .anyMatch(pattern -> WildcardMatcher.isMatch(text, pattern));
    }
}
