package com.flower.net.access;

import org.apache.commons.lang3.tuple.Pair;
import java.util.HashMap;
import java.util.Map;

public class PortRangeChecker {
    private final Map<String, Pair<Integer, Integer>> patterns = new HashMap<>();

    public void addPattern(String portRange) {
        String[] parts = portRange.split("-");
        int from = Integer.parseInt(parts[0]);
        int to = Integer.parseInt(parts[1]);
        if (from > to) {
            int tmp = to;
            to = from;
            from = tmp;
        }
        patterns.put(portRange, Pair.of(from, to));
    }

    public void removePattern(String portRange) {
        patterns.remove(portRange);
    }

    public boolean isMatch(int port) {
        return patterns.values().stream()
                .anyMatch(portRange -> (port >= portRange.getLeft() && port <= portRange.getRight())
            );
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }
}
