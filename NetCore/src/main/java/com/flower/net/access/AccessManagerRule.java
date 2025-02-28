package com.flower.net.access;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.Rule;

import java.util.HashSet;
import java.util.Set;

public class AccessManagerRule {
    public final Rule rule;

    protected final Set<Integer> ports;
    protected final PortRangeChecker checker;

    public AccessManagerRule(Rule rule) {
        this.rule = rule;

        this.ports = new HashSet<>();
        if (rule.ports() != null) {
            this.ports.addAll(rule.ports());
        }

        checker = new PortRangeChecker();
        if (rule.portRanges() != null) {
            for (String portRange : rule.portRanges()) {
                checker.addPattern(portRange);
            }
        }
    }

    public Access access() {
        return rule.access();
    }

    public boolean hasPortMatchInfo() {
        return !ports.isEmpty() || !checker.isEmpty();
    }

    public boolean directPortMatch(int port) {
        return ports.contains(port);
    }

    public boolean portRangeMatch(int port) {
        return checker.isMatch(port);
    }
}
