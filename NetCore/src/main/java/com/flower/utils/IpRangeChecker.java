package com.flower.utils;

import org.apache.commons.net.util.SubnetUtils;

import java.util.HashMap;
import java.util.Map;

public class IpRangeChecker {
    private final Map<String, SubnetUtils> cidrMap = new HashMap<>();

    public void addRange(String cidr) {
        cidrMap.put(cidr, new SubnetUtils(cidr));
    }

    public void removeRange(String cidr) {
        cidrMap.remove(cidr);
    }

    public boolean isIpInRange(String ip) {
        return cidrMap.values().stream()
                .anyMatch(subnet -> subnet.getInfo().isInRange(ip));
    }
}