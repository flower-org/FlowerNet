package com.flower.net.access;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.ImmutableRule;
import com.flower.net.config.access.Rule;
import com.flower.net.config.access.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AccessManagerRuleTest {
    private AccessManagerRule accessManagerRule;

    @BeforeEach
    void setUp() {
        Rule rule = ImmutableRule.builder()
                .ruleType(RuleType.PORT)
                .access(Access.ALLOW)
                .addAllPorts(List.of(8080, 9090))
                .addAllPortRanges(List.of("1000-2000", "3000-4000"))
                .build();
        accessManagerRule = new AccessManagerRule(rule);
    }

    @Test
    void testAccess() {
        assertEquals(Access.ALLOW, accessManagerRule.access());
    }

    @Test
    void testHasPortMatchInfo() {
        assertTrue(accessManagerRule.hasPortMatchInfo());
    }

    @Test
    void testDirectPortMatch() {
        assertTrue(accessManagerRule.directPortMatch(8080));
        assertTrue(accessManagerRule.directPortMatch(9090));
        assertFalse(accessManagerRule.directPortMatch(80));
    }

    @Test
    void testPortRangeMatch() {
        assertTrue(accessManagerRule.portRangeMatch(1500));
        assertTrue(accessManagerRule.portRangeMatch(3500));
        assertFalse(accessManagerRule.portRangeMatch(2500));
    }

    @Test
    void testNoPortsAndRanges() {
        Rule emptyRule = ImmutableRule.builder()
                .ruleType(RuleType.PORT)
                .access(Access.DENY)
                .build();
        AccessManagerRule emptyAccessManagerRule = new AccessManagerRule(emptyRule);

        assertFalse(emptyAccessManagerRule.hasPortMatchInfo());
    }
}
