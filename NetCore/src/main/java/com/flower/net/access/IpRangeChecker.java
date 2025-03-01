package com.flower.net.access;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressNetwork;
import inet.ipaddr.IPAddressString;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class IpRangeChecker {
    private final IPAddressNetwork.IPAddressGenerator generator = new IPAddressNetwork.IPAddressGenerator();
    private final Map<String, IPAddress> cidrMap = new HashMap<>();

    public void addRange(String cidr) {
        IPAddress subnet = new IPAddressString(cidr).getAddress();
        if (subnet == null) {
            throw new IllegalArgumentException("Invalid CIDR: " + cidr);
        }
        cidrMap.put(cidr, subnet);
    }

    public void removeRange(String cidr) {
        cidrMap.remove(cidr);
    }

    public boolean isIpInRange(InetAddress address) {
        IPAddress ipAddress = generator.from(address);
        if (ipAddress == null) {
            return false;
        }
        return cidrMap.values().stream().anyMatch(subnet -> subnet.contains(ipAddress));
    }
}