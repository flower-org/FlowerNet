package com.flower.net.access;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.AccessConfig;
import com.flower.net.config.access.ImmutableAccessConfig;
import com.flower.net.config.access.ImmutableRule;
import com.flower.net.config.access.Rule;
import com.flower.net.config.access.RuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigAccessManagerTest {
    private ConfigAccessManager manager;

    Rule rule(RuleType type, Access access, Collection<String> rules) {
        return ImmutableRule.builder()
                .ruleType(type)
                .access(access)
                .addAllRules(rules)
                .build();
    }

    Rule rule(RuleType type, Access access, Collection<Integer> ports, Collection<String> portRanges) {
        return ImmutableRule.builder()
                .ruleType(type)
                .access(access)
                .addAllPorts(ports)
                .addAllPortRanges(portRanges)
                .build();
    }

    @BeforeEach
    void setUp() {
        // Mocking an AccessConfig with predefined rules
        AccessConfig accessConfig = ImmutableAccessConfig.builder()
                .directIpAccess(true)
                .defaultAccessRule(Access.DENY)
                .addAllAccessRules(List.of(
                        rule(RuleType.IP_ADDRESS, Access.ALLOW, Set.of("192.168.1.10")),
                        rule(RuleType.IP_RANGE, Access.DENY, Set.of("192.168.1.0/24")),
                        rule(RuleType.NAME, Access.ALLOW, Set.of("example.com")),
                        rule(RuleType.NAME_WILDCARD, Access.DENY, Set.of("*.bad.com")),
                        rule(RuleType.PORT, Access.ALLOW, Set.of(80, 443), Set.of("5000-6000"))
                    ))
                .build();

        manager = new ConfigAccessManager(accessConfig);
    }

    @Test
    void testIpAddressAllow() throws UnknownHostException {
        InetAddress allowedIp = InetAddress.getByName("192.168.1.10");
        assertEquals(Access.ALLOW, manager.accessCheck(allowedIp, 80));
    }

    @Test
    void testIpRangeDeny() throws UnknownHostException {
        InetAddress deniedIp = InetAddress.getByName("192.168.1.50");
        assertEquals(Access.DENY, manager.accessCheck(deniedIp, 80));
    }

    @Test
    void testUnknownIpUsesDefaultRule() throws UnknownHostException {
        InetAddress unknownIp = InetAddress.getByName("10.0.0.1");
        assertEquals(Access.DENY, manager.accessCheck(unknownIp, 8080));
    }

    @Test
    void testHostnameAllow() {
        assertEquals(Access.ALLOW, manager.accessCheck("example.com", 80));
    }

    @Test
    void testWildcardDeny() {
        assertEquals(Access.DENY, manager.accessCheck("malware.bad.com", 80));
    }

    @Test
    void testPortAllow() {
        assertEquals(Access.ALLOW, manager.accessCheck("example.com", 5000));
    }

    @Test
    void testPortOutOfRangeUsesDefault() {
        assertEquals(Access.DENY, manager.accessCheck("unknown-host.com", 7000));
    }

    @Test
    void testAllowByPort() {
        assertEquals(Access.ALLOW, manager.accessCheck("unknown-host.com", 80));
    }

    @Test
    void testAllowByPort2() {
        assertEquals(Access.ALLOW, manager.accessCheck("unknown-host.com", 443));
    }

    @Test
    void testAllowByPortRange() {
        assertEquals(Access.ALLOW, manager.accessCheck("unknown-host.com", 5500));
    }
}
