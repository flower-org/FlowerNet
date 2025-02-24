package com.flower.dns.cache;

import com.flower.dns.DnsClient;
import com.flower.dns.utils.DnsUtils;
import com.flower.utils.ImmediateFuture;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DnsCache implements DnsClient {
    public static final int DEFAULT_MAXIMUM_CACHE_SIZE = 10_000;
    public static final int DEFAULT_EXPIRATION_MS = 900_000;//15 minutes

    protected final Cache<String, DefaultDnsResponse> dnsResponseCache;
    protected final DnsClient dnsClient;

    public DnsCache(DnsClient dnsClient) {
        this(dnsClient, DEFAULT_MAXIMUM_CACHE_SIZE, DEFAULT_EXPIRATION_MS);
    }

    public DnsCache(DnsClient dnsClient, int maximumSize, int expirationMs) {
        this.dnsClient = dnsClient;
        this.dnsResponseCache = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(expirationMs, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public Future<DnsResponse> query(String hostname) {
        DefaultDnsResponse cacheResponse = dnsResponseCache.getIfPresent(hostname);
        if (cacheResponse != null) {
            return ImmediateFuture.of(cacheResponse);
        } else {
            Future<DnsResponse> promise = dnsClient.query(hostname);
            promise.addListener(future -> {
                if (future.isSuccess()) {
                    DefaultDnsResponse dnsResponse = (DefaultDnsResponse) future.get();
                    if (DnsUtils.dnsResponseHasIp(dnsResponse)) {
                        dnsResponseCache.put(hostname, dnsResponse);
                    }
                }
            });
            return promise;
        }
    }

    @Override
    public Future<DnsResponse> query(String hostname, long promiseTimeoutMs) {
        DefaultDnsResponse cacheResponse = dnsResponseCache.getIfPresent(hostname);
        if (cacheResponse != null) {
            return ImmediateFuture.of(cacheResponse);
        } else {
            Future<DnsResponse> promise = dnsClient.query(hostname, promiseTimeoutMs);
            promise.addListener(future -> {
                if (future.isSuccess()) {
                    DefaultDnsResponse dnsResponse = (DefaultDnsResponse) future.get();
                    if (DnsUtils.dnsResponseHasIp(dnsResponse)) {
                        dnsResponseCache.put(hostname, dnsResponse);
                    }
                }
            });
            return promise;
        }
    }

    @Override
    public void shutdown() {
        dnsClient.shutdown();
    }
}
