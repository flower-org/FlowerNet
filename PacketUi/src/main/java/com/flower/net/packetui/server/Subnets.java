package com.flower.net.packetui.server;

import com.google.common.base.Splitter;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.AddressStringParameters.RangeParameters;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.IPAddressStringParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An immutable set of IPv4/IPv6 subnets in CIDR notation, e.g. {@code 10.42.0.0/24, 203.0.113.10, fd00::/8}.
 * An address without a prefix is a single host. Host bits after the prefix are ignored.
 * Only plain IP literals are accepted (no host names, wildcards, ranges or masks), so parsing and matching never
 * trigger a DNS lookup. Thread-safe.
 */
public final class Subnets {
    public static final Subnets EMPTY = new Subnets(List.of());

    private static final IPAddressStringParameters STRICT = new IPAddressStringParameters.Builder()
            .allowEmpty(false)
            .allowAll(false)
            .allowSingleSegment(false)
            .allowPrefixOnly(false)
            .allowMask(false)
            .setRangeOptions(RangeParameters.NO_RANGE)
            .getIPv4AddressParametersBuilder().allow_inet_aton(false).getParentBuilder()
            .toParams();

    private final List<IPAddress> subnets;

    private Subnets(List<IPAddress> subnets) {
        this.subnets = List.copyOf(subnets);
    }

    /**
     * Parses subnets separated by commas and/or whitespace. Blank text gives {@link #EMPTY}.
     * @throws IllegalArgumentException naming the first invalid entry
     */
    public static Subnets parse(String text) {
        List<IPAddress> subnets = new ArrayList<>();
        for (String entry : Splitter.onPattern("[,\\s]+").omitEmptyStrings().split(text)) {
            try {
                // toPrefixBlock clears host bits: 10.42.0.1/24 -> 10.42.0.0/24
                subnets.add(new IPAddressString(entry, STRICT).toAddress().toPrefixBlock());
            } catch (AddressStringException e) {
                throw new IllegalArgumentException("Not an IP address or subnet: " + entry, e);
            }
        }
        return subnets.isEmpty() ? EMPTY : new Subnets(subnets);
    }

    /** Whether {@code ip} (an IP literal) is in any of the subnets. False for anything that isn't an IP literal. */
    public boolean contains(String ip) {
        if (subnets.isEmpty()) {
            return false;
        }
        IPAddress address = new IPAddressString(ip, STRICT).getAddress();
        if (address == null || address.isPrefixed()) {
            return false;
        }
        for (IPAddress subnet : subnets) {
            if (subnet.contains(address)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return subnets.isEmpty();
    }

    /** Normalized form, e.g. {@code 10.42.0.0/24, 203.0.113.10}; parses back to the same subnets. */
    @Override
    public String toString() {
        return subnets.stream().map(IPAddress::toCanonicalString).collect(Collectors.joining(", "));
    }
}
