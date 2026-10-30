package com.flower.net.dns.cache;

import com.flower.net.dns.DnsClient;
import com.flower.net.utils.ImmediateFuture;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DnsCacheTest {
    final AtomicLong nanos = new AtomicLong();
    final AtomicInteger upstreamQueries = new AtomicInteger();
    /** TTLs of the A records the fake upstream answers with; none means NXDOMAIN. */
    volatile long[] ttls = {60};

    final DnsClient upstream = new DnsClient() {
        @Override
        public Future<DnsResponse> query(String hostname) {
            upstreamQueries.incrementAndGet();
            DefaultDnsResponse response = new DefaultDnsResponse(1, DnsOpCode.QUERY);
            if (ttls.length == 0) {
                response.setCode(DnsResponseCode.NXDOMAIN);
            }
            for (long ttl : ttls) {
                response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(hostname, DnsRecordType.A, ttl,
                        Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4})));
            }
            return ImmediateFuture.of(response);
        }

        @Override
        public Future<DnsResponse> query(String hostname, long promiseTimeoutMs) {
            return query(hostname);
        }

        @Override
        public void shutdown() { }
    };

    final DnsCache cache = new DnsCache(upstream, 100, 900_000, nanos::get);

    void advanceSeconds(long seconds) {
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
    }

    @Test
    void expiresAfterTheShortestTtl() {
        ttls = new long[] {120, 30};
        cache.query("a.com", 1000);
        advanceSeconds(29);
        cache.query("a.com", 1000);
        assertEquals(1, upstreamQueries.get());
        advanceSeconds(2);
        cache.query("a.com", 1000);
        assertEquals(2, upstreamQueries.get());
    }

    @Test
    void readsDontExtendTheLifetime() {
        ttls = new long[] {30};
        cache.query("a.com");
        for (int i = 0; i < 5; i++) {
            advanceSeconds(11);
            cache.query("a.com");
        }
        // Queried at 0s, then again at 33s once the first answer expired
        assertEquals(2, upstreamQueries.get());
    }

    @Test
    void longTtlsAreCapped() {
        ttls = new long[] {86_400};
        cache.query("a.com");
        advanceSeconds(899);
        cache.query("a.com");
        assertEquals(1, upstreamQueries.get());
        advanceSeconds(2);
        cache.query("a.com");
        assertEquals(2, upstreamQueries.get());
    }

    @Test
    void responsesWithoutAddressesOrTtlAreNotCached() {
        ttls = new long[0];
        cache.query("a.com");
        cache.query("a.com");
        ttls = new long[] {0};
        cache.query("b.com");
        cache.query("b.com");
        assertEquals(4, upstreamQueries.get());
    }

    @Test
    void namesAreCaseInsensitive() {
        cache.query("Example.COM");
        cache.query("example.com.");
        assertEquals(1, upstreamQueries.get());
    }
}
