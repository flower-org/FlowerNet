package com.flower.dns.cache;

import com.flower.utils.DnsClient;
import com.flower.utils.ImmediatePromise;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;

public class DnsCache implements DnsClient {
    public static final int DEFAULT_MAXIMUM_CACHE_SIZE = 10_000;
    public static final int DEFAULT_EXPIRATION_MS = 900_000;//15 minutes

    static boolean dnsResponseHasIp(DnsResponse dnsResponse) {
        for (int i = 0, count = dnsResponse.count(DnsSection.ANSWER); i < count; i++) {
            DnsRecord record = dnsResponse.recordAt(DnsSection.ANSWER, i);
            if (record.type() == DnsRecordType.A) {
                return true;
            }
        }
        return false;
    }

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
    public Promise<DnsResponse> query(String hostname) {
        DefaultDnsResponse cacheResponse = dnsResponseCache.getIfPresent(hostname);
        if (cacheResponse != null) {
            return ImmediatePromise.of(cacheResponse);
        } else {
            Promise<DnsResponse> promise = dnsClient.query(hostname);
            promise.addListener(future -> {
                if (future.isSuccess()) {
                    DefaultDnsResponse dnsResponse = (DefaultDnsResponse) future.get();
                    if (dnsResponseHasIp(dnsResponse)) {
                        dnsResponseCache.put(hostname, dnsResponse);
                    }
                }
            });
            return promise;
        }
    }

    @Override
    public Promise<DnsResponse> query(String hostname, long promiseTimeoutMs) {
        DefaultDnsResponse cacheResponse = dnsResponseCache.getIfPresent(hostname);
        if (cacheResponse != null) {
            return ImmediatePromise.of(cacheResponse);
        } else {
            Promise<DnsResponse> promise = dnsClient.query(hostname, promiseTimeoutMs);
            promise.addListener(future -> {
                if (future.isSuccess()) {
                    DefaultDnsResponse dnsResponse = (DefaultDnsResponse) future.get();
                    if (dnsResponseHasIp(dnsResponse)) {
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
