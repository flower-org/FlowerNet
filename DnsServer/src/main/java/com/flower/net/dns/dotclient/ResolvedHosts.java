package com.flower.net.dns.dotclient;

import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.buffer.ByteBufUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Remembers which IPs this server handed out for which host names, so an IP can be mapped back to its host.
 * It only knows answers this server gave its clients; it never resolves anything itself.
 * <p>
 * Entries are (IP, host) pairs, kept by recency up to a fixed number and evicted least-recently-used first;
 * DNS TTLs don't apply. Resolving a host again bumps its IPs; looking up an IP bumps its hosts.
 * Hosts are the names clients asked for (lowercase, without the trailing dot), even when the answer came
 * through a CNAME chain. Thread-safe.
 */
public class ResolvedHosts {
    public static final int DEFAULT_MAX_ENTRIES = 100_000;

    private record Pair(InetAddress ip, String host) { }

    /** In access order: the eldest pair is the least recently used. Values are the tick of the last use. */
    private final LinkedHashMap<Pair, Long> pairs;
    private final Map<InetAddress, Set<String>> hostsByIp = new HashMap<>();
    private long tick;

    public ResolvedHosts() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public ResolvedHosts(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive: " + maxEntries);
        }
        this.pairs = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Pair, Long> eldest) {
                if (size() <= maxEntries) {
                    return false;
                }
                unindex(eldest.getKey());
                return true;
            }
        };
    }

    /** Records that {@code host} resolved to {@code ips}, bumping each pair. */
    public synchronized void record(String host, Collection<InetAddress> ips) {
        String normalized = normalize(host);
        for (InetAddress ip : ips) {
            pairs.put(new Pair(ip, normalized), ++tick);
            hostsByIp.computeIfAbsent(ip, k -> new LinkedHashSet<>()).add(normalized);
        }
    }

    /** Records the A and AAAA addresses of a successful response to a query for {@code host}. */
    public void record(String host, DnsResponse response) {
        if (!DnsResponseCode.NOERROR.equals(response.code())) {
            return;
        }
        List<InetAddress> ips = new ArrayList<>();
        for (int i = 0, count = response.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = response.recordAt(DnsSection.ANSWER, i);
            boolean address = DnsRecordType.A.equals(record.type()) || DnsRecordType.AAAA.equals(record.type());
            if (address && record instanceof DnsRawRecord raw) {
                int length = raw.content().readableBytes();
                if (length == 4 || length == 16) {
                    try {
                        ips.add(InetAddress.getByAddress(ByteBufUtil.getBytes(raw.content())));
                    } catch (UnknownHostException e) {
                        // Can't happen: the length was checked
                    }
                }
            }
        }
        if (!ips.isEmpty()) {
            record(host, ips);
        }
    }

    /**
     * Returns the hosts that resolved to {@code ip}, most recently used first, and bumps them.
     * Empty if this server never handed out the IP, or it was evicted.
     */
    public synchronized List<String> hostsFor(InetAddress ip) {
        Set<String> hosts = hostsByIp.get(ip);
        if (hosts == null) {
            return List.of();
        }
        List<Pair> found = new ArrayList<>(hosts.size());
        for (String host : hosts) {
            found.add(new Pair(ip, host));
        }
        found.sort(Comparator.comparingLong((Pair pair) -> pairs.getOrDefault(pair, 0L)).reversed());
        List<String> result = new ArrayList<>(found.size());
        // Bumped from least to most recent, so their relative order is kept
        for (int i = found.size() - 1; i >= 0; i--) {
            pairs.put(found.get(i), ++tick);
        }
        for (Pair pair : found) {
            result.add(pair.host());
        }
        return result;
    }

    /** The number of (IP, host) pairs held. */
    public synchronized int size() {
        return pairs.size();
    }

    private void unindex(Pair pair) {
        Set<String> hosts = hostsByIp.get(pair.ip());
        if (hosts != null) {
            hosts.remove(pair.host());
            if (hosts.isEmpty()) {
                hostsByIp.remove(pair.ip());
            }
        }
    }

    static String normalize(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.endsWith(".") ? lower.substring(0, lower.length() - 1) : lower;
    }
}
