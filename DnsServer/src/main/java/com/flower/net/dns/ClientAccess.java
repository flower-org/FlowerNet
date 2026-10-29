package com.flower.net.dns;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Decides which clients may query the server, so it isn't an open resolver usable for amplification attacks:
 * clients outside the allowed subnets are refused, and each client IP is rate limited with a token bucket.
 * Shared by all of a server's handlers. Thread-safe.
 */
public final class ClientAccess {
    public enum Decision {
        ALLOW,
        /** Reply REFUSED: no bigger than the query, so it can't amplify. */
        REFUSE,
        /** Don't reply at all. */
        DROP
    }

    /** Loopback, private (RFC 1918, RFC 4193) and link-local networks. */
    public static final List<String> LOCAL_NETWORKS = List.of(
            "127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16",
            "fc00::/7", "fe80::/10");
    public static final int DEFAULT_QUERIES_PER_SECOND = 50;
    public static final int DEFAULT_BURST = 100;
    static final int MAX_TRACKED_CLIENTS = 100_000;

    private static final ClientAccess DEFAULT = defaults();

    private final List<IpSubnetFilterRule> allowed;
    private final double tokensPerNano;
    private final double burst;
    private final LongSupplier nanoTime;
    private final Cache<InetAddress, TokenBucket> buckets;

    /** Local networks only, {@value #DEFAULT_QUERIES_PER_SECOND} queries/s per client, bursts of {@value #DEFAULT_BURST}. */
    public static ClientAccess defaults() {
        return new ClientAccess(LOCAL_NETWORKS, DEFAULT_QUERIES_PER_SECOND, DEFAULT_BURST);
    }

    /** A shared instance with the defaults, for handlers created without their own. */
    static ClientAccess sharedDefault() {
        return DEFAULT;
    }

    /**
     * @param allowedCidrs subnets clients may query from, such as {@code "192.168.1.0/24"} or {@code "0.0.0.0/0"}
     * @param queriesPerSecond sustained queries per second allowed per client IP
     * @param burst queries a client IP may send at once after being idle
     */
    public ClientAccess(Collection<String> allowedCidrs, int queriesPerSecond, int burst) {
        this(allowedCidrs, queriesPerSecond, burst, System::nanoTime);
    }

    ClientAccess(Collection<String> allowedCidrs, int queriesPerSecond, int burst, LongSupplier nanoTime) {
        if (queriesPerSecond < 1 || burst < 1) {
            throw new IllegalArgumentException("queriesPerSecond and burst must be positive");
        }
        List<IpSubnetFilterRule> rules = new ArrayList<>();
        for (String cidr : allowedCidrs) {
            rules.add(parse(cidr));
        }
        this.allowed = List.copyOf(rules);
        this.tokensPerNano = queriesPerSecond / 1e9;
        this.burst = burst;
        this.nanoTime = nanoTime;
        // An idle client's bucket refills completely, so forgetting it after a while changes nothing
        long refillSeconds = (long) Math.ceil((double) burst / queriesPerSecond);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_CLIENTS)
                .expireAfterAccess(Duration.ofSeconds(refillSeconds + 1))
                .build();
    }

    /** Clients that aren't on the network (in-process channels) are always allowed. */
    public Decision check(SocketAddress client) {
        if (!(client instanceof InetSocketAddress inet) || inet.getAddress() == null) {
            return Decision.ALLOW;
        }
        if (allowed.stream().noneMatch(rule -> rule.matches(inet))) {
            return Decision.REFUSE;
        }
        TokenBucket bucket = buckets.get(inet.getAddress(), k -> new TokenBucket(burst, nanoTime.getAsLong()));
        return bucket.tryTake(nanoTime.getAsLong()) ? Decision.ALLOW : Decision.DROP;
    }

    private static IpSubnetFilterRule parse(String cidr) {
        int slash = cidr.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("Not a CIDR subnet: " + cidr);
        }
        return new IpSubnetFilterRule(cidr.substring(0, slash), Integer.parseInt(cidr.substring(slash + 1)),
                IpFilterRuleType.ACCEPT);
    }

    private final class TokenBucket {
        private double tokens;
        private long lastNanos;

        TokenBucket(double tokens, long nowNanos) {
            this.tokens = tokens;
            this.lastNanos = nowNanos;
        }

        synchronized boolean tryTake(long nowNanos) {
            tokens = Math.min(burst, tokens + (nowNanos - lastNanos) * tokensPerNano);
            lastNanos = nowNanos;
            if (tokens < 1) {
                return false;
            }
            tokens--;
            return true;
        }
    }
}
