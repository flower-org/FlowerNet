package com.flower.net.access;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.AccessConfig;
import com.flower.net.config.access.AccessManager;
import com.flower.net.config.access.Rule;
import com.flower.net.utils.IpAddressUtil;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implements rule match priority as recommended in AccessManager javadoc
 */
public class ConfigAccessManager implements AccessManager {
    protected final AccessConfig accessConfig;

    protected final boolean directIpAccessAllowed;
    protected final Access defaultAccessRule;

    protected final Multimap<InetAddress, AccessManagerRule> addressRules;
    protected final List<Pair<IpRangeChecker, AccessManagerRule>> ipRangeRules;

    protected final Multimap<String, AccessManagerRule> nameRules;
    protected final List<Pair<WildcardTrieChecker, AccessManagerRule>> nameWildcardRules;

    protected final Multimap<Integer, AccessManagerRule> portRules;
    protected final List<AccessManagerRule> portRangeRules;

    public ConfigAccessManager(AccessConfig accessConfig) {
        //TODO: LRU Matches cache?
        this.accessConfig = accessConfig;

        this.directIpAccessAllowed = accessConfig.directIpAccess();
        this.defaultAccessRule = accessConfig.defaultAccessRule();

        this.addressRules = ArrayListMultimap.create();
        this.ipRangeRules = new ArrayList<>();

        this.nameRules = ArrayListMultimap.create();
        this.nameWildcardRules = new ArrayList<>();

        this.portRules = ArrayListMultimap.create();
        this.portRangeRules = new ArrayList<>();

        if (accessConfig.accessRules() != null) {
            for (Rule rule : accessConfig.accessRules()) {
                switch (rule.ruleType()) {
                    case IP_ADDRESS:
                        if (rule.rules() == null || rule.rules().isEmpty()) {
                            throw new RuntimeException(rule.ruleType() + " rule must contain IpAddress-es");
                        }
                        for (String ruleStr : rule.rules()) {
                            addressRules.put(IpAddressUtil.fromString(ruleStr), new AccessManagerRule(rule));
                        }
                        break;
                    case IP_RANGE:
                        if (rule.rules() == null || rule.rules().isEmpty()) {
                            throw new RuntimeException(rule.ruleType() + " rule must contain IpRange-es");
                        }
                        IpRangeChecker ipRangeChecker = new IpRangeChecker();
                        for (String cidr : rule.rules()) {
                            ipRangeChecker.addRange(cidr);
                        }
                        ipRangeRules.add(Pair.of(ipRangeChecker, new AccessManagerRule(rule)));
                        break;
                    case NAME:
                        if (rule.rules() == null || rule.rules().isEmpty()) {
                            throw new RuntimeException(rule.ruleType() + " rule must contain (host)names");
                        }
                        for (String ruleStr : rule.rules()) {
                            nameRules.put(ruleStr, new AccessManagerRule(rule));
                        }
                        break;
                    case NAME_WILDCARD:
                        if (rule.rules() == null || rule.rules().isEmpty()) {
                            throw new RuntimeException(rule.ruleType() + " rule must contain IpRange-es");
                        }
                        WildcardTrieChecker wildcardTrieChecker = new WildcardTrieChecker();
                        for (String pattern : rule.rules()) {
                            wildcardTrieChecker.addPattern(pattern);
                        }
                        nameWildcardRules.add(Pair.of(wildcardTrieChecker, new AccessManagerRule(rule)));
                        break;
                    case PORT:
                        if (rule.rules() != null && !rule.rules().isEmpty()) {
                            throw new RuntimeException(rule.ruleType() + " rule can only contain ports and portRanges, no rules");
                        }
                        if ((rule.ports() == null || rule.ports().isEmpty()) && (rule.portRanges() == null || rule.portRanges().isEmpty())) {
                            throw new RuntimeException(rule.ruleType() + " rule must contain ports or portRanges or both");
                        }
                        if (rule.ports() != null && !rule.ports().isEmpty()) {
                            for (int port : rule.ports()) {
                                portRules.put(port, new AccessManagerRule(rule));
                            }
                        }
                        if (rule.portRanges() != null && !rule.portRanges().isEmpty()) {
                            PortRangeChecker portRangeChecker = new PortRangeChecker();
                            for (String portRange : rule.portRanges()) {
                                portRangeChecker.addPattern(portRange);
                            }
                            portRangeRules.add(new AccessManagerRule(rule));
                        }
                        break;
                    default:
                        throw new RuntimeException("Unknown rule type " + rule.ruleType());
                }
            }
        }
    }

    @Override
    public Access accessCheck(InetAddress address, int port) {
        if (!directIpAccessAllowed) {
            return Access.DENY;
        }

        Collection<AccessManagerRule> ipRules = addressRules.get(address);
        Access ipAccess = secondaryMatch(ipRules, port);
        if (ipAccess != null) {
            return ipAccess;
        }

        List<AccessManagerRule> matchRules = ipRangeRules
                .stream().filter(r -> r.getLeft().isIpInRange(address))
                .map(Pair::getRight)
                .toList();

        Access ipRangeAccess = secondaryMatch(matchRules, port);
        if (ipRangeAccess != null) {
            return ipRangeAccess;
        }

        Access portAccess = portRuleCheck(port);
        if (portAccess != null) {
            return portAccess;
        }

        return defaultAccessRule;
    }

    @Override
    public Access accessCheck(String name, int port) {
        Collection<AccessManagerRule> ipRules = nameRules.get(name);
        Access ipAccess = secondaryMatch(ipRules, port);
        if (ipAccess != null) {
            return ipAccess;
        }

        List<AccessManagerRule> matchRules = nameWildcardRules
                .stream().filter(r -> r.getLeft().isMatch(name))
                .map(Pair::getRight)
                .toList();

        Access ipRangeAccess = secondaryMatch(matchRules, port);
        if (ipRangeAccess != null) {
            return ipRangeAccess;
        }

        Access portAccess = portRuleCheck(port);
        if (portAccess != null) {
            return portAccess;
        }

        return defaultAccessRule;
    }

    protected @Nullable Access secondaryMatch(@Nullable Collection<AccessManagerRule> rules, int port) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        List<AccessManagerRule> matchedRules = new ArrayList<>();

        //1. primary match + direct port match
        for (AccessManagerRule portRule : rules) {
            if (portRule.directPortMatch(port)) {
                matchedRules.add(portRule);
            }
        }

        //2. primary match + port range match
        if (matchedRules.isEmpty()) {
            for (AccessManagerRule portRule : rules) {
                if (portRule.portRangeMatch(port)) {
                    matchedRules.add(portRule);
                }
            }
        }

        //3. primary match without ports specified
        if (matchedRules.isEmpty()) {
            for (AccessManagerRule portRule : rules) {
                if (!portRule.hasPortMatchInfo()) {
                    matchedRules.add(portRule);
                }
            }
        }

        if (!matchedRules.isEmpty()) {
            for (AccessManagerRule matchedRule : matchedRules) {
                if (matchedRule.access() == Access.DENY) {
                    return Access.DENY;
                }
            }
            return Access.ALLOW;
        } else {
            return portRuleCheck(port);
        }
    }

    protected Access portRuleCheck(int port) {
        Collection<AccessManagerRule> matchedRules = portRules.get(port);

        if (matchedRules == null || matchedRules.isEmpty()) {
            List<AccessManagerRule> matchedPortRangeRules = new ArrayList<>();
            for (AccessManagerRule portRangeRule : portRangeRules) {
                if (portRangeRule.portRangeMatch(port)) {
                    matchedPortRangeRules.add(portRangeRule);
                }
            }
            matchedRules = matchedPortRangeRules;
        }

        if (!matchedRules.isEmpty()) {
            for (AccessManagerRule matchedRule : matchedRules) {
                if (matchedRule.access() == Access.DENY) {
                    return Access.DENY;
                }
            }
            return Access.ALLOW;
        } else {
            return defaultAccessRule;
        }
    }
}
