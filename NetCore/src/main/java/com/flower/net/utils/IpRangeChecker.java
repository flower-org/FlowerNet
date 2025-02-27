package com.flower.net.utils;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import java.util.HashMap;
import java.util.Map;

public class IpRangeChecker {
    private final Map<String, IPAddress> cidrMap = new HashMap<>();

    public void addRange(String cidr) {
        IPAddress subnet = new IPAddressString(cidr).getAddress();
        if (subnet != null) {
            cidrMap.put(cidr, subnet);
        }
    }

    public void removeRange(String cidr) {
        cidrMap.remove(cidr);
    }

    public boolean isIpInRange(String ip) {
        IPAddress ipAddress = new IPAddressString(ip).getAddress();
        if (ipAddress == null) {
            return false;
        }
        return cidrMap.values().stream().anyMatch(subnet -> subnet.contains(ipAddress));
    }
}