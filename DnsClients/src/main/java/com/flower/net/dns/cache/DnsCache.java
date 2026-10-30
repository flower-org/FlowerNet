package com.flower.net.dns.cache;

import com.flower.net.dns.DnsClient;
import com.flower.net.utils.ImmediateFuture;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.Future;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Caches responses that contain addresses, per host name, for as long as their DNS TTL allows,
 * capped at a maximum. The wrapped client's responses must stay valid after they're returned (not be released).
 */
public class DnsCache implements DnsClient {
    public static final int DEFAULT_MAXIMUM_CACHE_SIZE = 10_000;
    /** The longest a response is kept, whatever its TTL. */
    public static final int DEFAULT_EXPIRATION_MS = 900_000;//15 minutes

    private record CachedResponse(DnsResponse response, long ttlNanos) { }

    protected final Cache<String, CachedResponse> dnsResponseCache;
    protected final DnsClient dnsClient;
    private final long maxTtlNanos;

    public DnsCache(DnsClient dnsClient) {
        this(dnsClient, DEFAULT_MAXIMUM_CACHE_SIZE, DEFAULT_EXPIRATION_MS);
    }

    /** @param expirationMs the longest a response is kept, whatever its TTL */
    public DnsCache(DnsClient dnsClient, int maximumSize, int expirationMs) {
        this(dnsClient, maximumSize, expirationMs, Ticker.systemTicker());
    }

    DnsCache(DnsClient dnsClient, int maximumSize, int expirationMs, Ticker ticker) {
        this.dnsClient = dnsClient;
        this.maxTtlNanos = TimeUnit.MILLISECONDS.toNanos(expirationMs);
        this.dnsResponseCache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .ticker(ticker)
                .expireAfter(new Expiry<String, CachedResponse>() {
                    @Override
                    public long expireAfterCreate(String key, CachedResponse value, long currentTime) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, CachedResponse value, long currentTime,
                                                  long currentDuration) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, CachedResponse value, long currentTime,
                                                long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public Future<DnsResponse> query(String hostname) {
        return query(hostname, () -> dnsClient.query(hostname));
    }

    @Override
    public Future<DnsResponse> query(String hostname, long promiseTimeoutMs) {
        return query(hostname, () -> dnsClient.query(hostname, promiseTimeoutMs));
    }

    private Future<DnsResponse> query(String hostname, Supplier<Future<DnsResponse>> upstream) {
        String key = normalize(hostname);
        CachedResponse cached = dnsResponseCache.getIfPresent(key);
        if (cached != null) {
            return ImmediateFuture.of(cached.response());
        }
        Future<DnsResponse> promise = upstream.get();
        promise.addListener(future -> {
            if (future.isSuccess()) {
                DnsResponse dnsResponse = (DnsResponse) future.getNow();
                long ttlNanos = addressTtlNanos(dnsResponse);
                if (ttlNanos > 0) {
                    dnsResponseCache.put(key, new CachedResponse(dnsResponse, ttlNanos));
                }
            }
        });
        return promise;
    }

    /** The shortest TTL among the response's addresses, capped at the maximum; 0 if it has none. */
    long addressTtlNanos(DnsResponse response) {
        long ttlSeconds = -1;
        for (int i = 0, count = response.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = response.recordAt(DnsSection.ANSWER, i);
            if (DnsRecordType.A.equals(record.type()) || DnsRecordType.AAAA.equals(record.type())) {
                long ttl = Math.max(0, record.timeToLive());
                ttlSeconds = ttlSeconds < 0 ? ttl : Math.min(ttlSeconds, ttl);
            }
        }
        return ttlSeconds <= 0 ? 0 : Math.min(maxTtlNanos, TimeUnit.SECONDS.toNanos(ttlSeconds));
    }

    /** DNS names are case-insensitive, and a trailing dot doesn't change the name. */
    static String normalize(String hostname) {
        String lower = hostname.toLowerCase(Locale.ROOT);
        return lower.endsWith(".") ? lower.substring(0, lower.length() - 1) : lower;
    }

    @Override
    public void shutdown() {
        dnsClient.shutdown();
    }
}
