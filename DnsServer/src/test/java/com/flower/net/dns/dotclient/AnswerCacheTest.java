package com.flower.net.dns.dotclient;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnswerCacheTest {
    static final String NAME = "example.org.";
    static final int AD = 0b010;
    static final int CD = 0b001;

    final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    final AnswerCache cache = new AnswerCache(nanos::get);
    final List<io.netty.util.ReferenceCounted> toRelease = new ArrayList<>();

    @AfterEach
    void release() {
        toRelease.forEach(io.netty.util.ReferenceCounted::release);
    }

    void advanceSeconds(long seconds) {
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
    }

    DnsQuery query(String name, int z, boolean dnssecOk) {
        DnsQuery query = new DefaultDnsQuery(4321).setZ(z)
                .addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(name, DnsRecordType.A));
        if (dnssecOk) {
            query.addRecord(DnsSection.ADDITIONAL, opt(true));
        }
        toRelease.add(query);
        return query;
    }

    DnsQuery query() {
        return query(NAME, 0, false);
    }

    static DnsRecord opt(boolean dnssecOk) {
        return new DefaultDnsRawRecord("", DnsRecordType.OPT, 1232, dnssecOk ? 0x8000 : 0, Unpooled.EMPTY_BUFFER);
    }

    static DnsRecord a(long ttl) {
        return new DefaultDnsRawRecord(NAME, DnsRecordType.A, ttl, Unpooled.wrappedBuffer(new byte[] {1, 2, 3, 4}));
    }

    /** Root MNAME and RNAME, then SERIAL, REFRESH, RETRY, EXPIRE, MINIMUM. */
    static DnsRecord soa(long ttl, int minimum) {
        ByteBuffer rdata = ByteBuffer.allocate(22).put((byte) 0).put((byte) 0)
                .putInt(1).putInt(7200).putInt(900).putInt(1209600).putInt(minimum);
        return new DefaultDnsRawRecord("org.", DnsRecordType.SOA, ttl, Unpooled.wrappedBuffer(rdata.array()));
    }

    DefaultDnsResponse response(DnsResponseCode code, DnsRecord... records) {
        DefaultDnsResponse response = new DefaultDnsResponse(1, DnsOpCode.QUERY, code);
        response.setRecursionAvailable(true).setRecursionDesired(true);
        response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(NAME, DnsRecordType.A));
        for (DnsRecord record : records) {
            DnsSection section = DnsRecordType.SOA.equals(record.type()) ? DnsSection.AUTHORITY
                    : DnsRecordType.OPT.equals(record.type()) ? DnsSection.ADDITIONAL : DnsSection.ANSWER;
            response.addRecord(section, record);
        }
        toRelease.add(response);
        return response;
    }

    void put(DnsQuery query, DefaultDnsResponse response) {
        cache.putResponse(AnswerCache.Key.of(query), response);
    }

    @Nullable
    DefaultDnsResponse get(DnsQuery query) {
        DefaultDnsResponse response = cache.getResponseIfPresent(query);
        if (response != null) {
            toRelease.add(response);
        }
        return response;
    }

    static long ttl(DefaultDnsResponse response, DnsSection section, int index) {
        return response.<DnsRecord>recordAt(section, index).timeToLive();
    }

    @Test
    void entryExpiresAtItsShortestTtlEvenWhenPopular() {
        put(query(), response(DnsResponseCode.NOERROR, a(300), a(30)));
        for (int second = 0; second < 30; second += 5) {
            DefaultDnsResponse hit = get(query());
            assertNotNull(hit, "at " + second + "s");
            assertEquals(300 - second, ttl(hit, DnsSection.ANSWER, 0));
            assertEquals(30 - second, ttl(hit, DnsSection.ANSWER, 1));
            advanceSeconds(5);
        }
        assertNull(get(query()));
    }

    @Test
    void hitCarriesTheQueryIdAndQuestion() {
        put(query(), response(DnsResponseCode.NOERROR, a(60)));
        DefaultDnsResponse hit = get(query());
        assertEquals(4321, hit.id());
        assertEquals(1, hit.count(DnsSection.QUESTION));
        assertEquals(NAME, hit.recordAt(DnsSection.QUESTION, 0).name());
        assertTrue(hit.isRecursionAvailable());
    }

    @Test
    void ttlsAreCapped() {
        put(query(), response(DnsResponseCode.NOERROR, a(10 * 86_400)));
        advanceSeconds(86_399);
        assertNotNull(get(query()));
        advanceSeconds(1);
        assertNull(get(query()));
    }

    @Test
    void nxDomainIsCachedForTheSoaMinimum() {
        put(query(), response(DnsResponseCode.NXDOMAIN, soa(3600, 60)));
        advanceSeconds(59);
        DefaultDnsResponse hit = get(query());
        assertNotNull(hit);
        assertEquals(DnsResponseCode.NXDOMAIN, hit.code());
        assertEquals(3600 - 59, ttl(hit, DnsSection.AUTHORITY, 0));
        advanceSeconds(1);
        assertNull(get(query()));
    }

    @Test
    void noDataIsCachedForTheSoaTtlWhenItIsLower() {
        put(query(), response(DnsResponseCode.NOERROR, soa(20, 300)));
        advanceSeconds(19);
        DefaultDnsResponse hit = get(query());
        assertNotNull(hit);
        assertEquals(DnsResponseCode.NOERROR, hit.code());
        assertEquals(0, hit.count(DnsSection.ANSWER));
        advanceSeconds(1);
        assertNull(get(query()));
    }

    @Test
    void negativeTtlIsCapped() {
        put(query(), response(DnsResponseCode.NXDOMAIN, soa(86_400, 86_400)));
        advanceSeconds(3_599);
        assertNotNull(get(query()));
        advanceSeconds(1);
        assertNull(get(query()));
    }

    @Test
    void uncacheableResponses() {
        put(query(), response(DnsResponseCode.NXDOMAIN));
        assertNull(get(query()), "negative without SOA");
        put(query(), response(DnsResponseCode.NOERROR));
        assertNull(get(query()), "NODATA without SOA");
        put(query(), response(DnsResponseCode.SERVFAIL, soa(3600, 60)));
        assertNull(get(query()), "SERVFAIL");
        put(query(), response(DnsResponseCode.REFUSED, a(60)));
        assertNull(get(query()), "REFUSED");
        put(query(), response(DnsResponseCode.NOERROR, a(0), a(60)));
        assertNull(get(query()), "zero TTL");
        put(query(), response(DnsResponseCode.NOERROR, a(0x8000_0000L)));
        assertNull(get(query()), "TTL with the top bit set counts as zero");
        DefaultDnsResponse truncated = response(DnsResponseCode.NOERROR, a(60));
        truncated.setTruncated(true);
        put(query(), truncated);
        assertNull(get(query()), "truncated");
    }

    @Test
    void namesAreCaseInsensitiveAndTheClientsCaseIsEchoed() {
        put(query("ExAmple.ORG.", 0, false), response(DnsResponseCode.NOERROR, a(60)));
        DefaultDnsResponse hit = get(query("eXample.org.", 0, false));
        assertNotNull(hit);
        assertEquals("eXample.org.", hit.recordAt(DnsSection.QUESTION, 0).name());
    }

    @Test
    void dnssecOkAndCheckingDisabledAreSeparateEntries() {
        put(query(NAME, 0, true), response(DnsResponseCode.NOERROR, a(60), opt(true)));
        assertNull(get(query(NAME, 0, false)));
        assertNull(get(query(NAME, CD, true)));
        assertNotNull(get(query(NAME, 0, true)));

        put(query(NAME, CD, false), response(DnsResponseCode.NOERROR, a(60)));
        assertNull(get(query(NAME, 0, false)));
        assertNotNull(get(query(NAME, CD, false)));
    }

    @Test
    void adIsOnlySetForClientsThatAskForIt() {
        DefaultDnsResponse validated = response(DnsResponseCode.NOERROR, a(60));
        validated.setZ(AD);
        put(query(NAME, AD, false), validated);
        assertEquals(AD, get(query(NAME, AD, false)).z() & AD);
        assertEquals(0, get(query(NAME, 0, false)).z() & AD);
    }

    @Test
    void optRecordFlagsAreNotTreatedAsTtl() {
        put(query(NAME, 0, true), response(DnsResponseCode.NOERROR, a(60), opt(true)));
        advanceSeconds(10);
        DefaultDnsResponse hit = get(query(NAME, 0, true));
        assertNotNull(hit);
        assertEquals(50, ttl(hit, DnsSection.ANSWER, 0));
        assertEquals(0x8000, ttl(hit, DnsSection.ADDITIONAL, 0));
        assertFalse(hit.isTruncated());
    }
}
