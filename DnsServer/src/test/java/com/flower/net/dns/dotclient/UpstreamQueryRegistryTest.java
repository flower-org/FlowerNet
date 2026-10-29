package com.flower.net.dns.dotclient;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UpstreamQueryRegistryTest {
    final DefaultEventExecutorGroup scheduler = new DefaultEventExecutorGroup(1);

    @AfterEach
    void tearDown() {
        scheduler.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
    }

    static DnsResponse response(int id, DnsQuestion question) {
        return new DefaultDnsResponse(id).setRecord(DnsSection.QUESTION, question);
    }

    static DnsQuestion question(String name, DnsRecordType type) {
        return new DefaultDnsQuestion(name, type);
    }

    @Test
    void sameClientIdGetsDistinctUpstreamIds() {
        UpstreamQueryRegistry registry = new UpstreamQueryRegistry(scheduler, 10_000);
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(registry.register(42, question("a.com.", DnsRecordType.A), r -> {}).upstreamId());
        }
        assertEquals(1000, ids.size());
        assertEquals(1000, registry.inFlightCount());
    }

    @Test
    void responsesAreRoutedByUpstreamIdNotClientId() {
        UpstreamQueryRegistry registry = new UpstreamQueryRegistry(scheduler, 10_000);
        DnsQuestion qa = question("a.com.", DnsRecordType.A);
        DnsQuestion qb = question("b.com.", DnsRecordType.A);
        List<String> received = new ArrayList<>();
        // Two clients using the same query ID
        UpstreamQueryRegistry.PendingQuery a = registry.register(7, qa, r -> received.add("a"));
        UpstreamQueryRegistry.PendingQuery b = registry.register(7, qb, r -> received.add("b"));
        assertNotEquals(a.upstreamId(), b.upstreamId());

        UpstreamQueryRegistry.PendingQuery matched = registry.complete(response(b.upstreamId(), qb));
        assertSame(b, matched);
        assertEquals(7, matched.originalId());
        matched.callback().accept(new DefaultDnsResponse(7));
        assertEquals(List.of("b"), received);
        assertEquals(1, registry.inFlightCount());

        // A duplicate response for an already completed query is ignored
        assertNull(registry.complete(response(b.upstreamId(), qb)));
    }

    @Test
    void mismatchedQuestionIsRejectedAndQueryStaysPending() {
        UpstreamQueryRegistry registry = new UpstreamQueryRegistry(scheduler, 10_000);
        UpstreamQueryRegistry.PendingQuery p = registry.register(1, question("a.com.", DnsRecordType.A), r -> {});

        assertNull(registry.complete(response(p.upstreamId(), question("evil.com.", DnsRecordType.A))));
        assertNull(registry.complete(response(p.upstreamId(), question("a.com.", DnsRecordType.AAAA))));
        assertNull(registry.complete(new DefaultDnsResponse(p.upstreamId())));
        assertEquals(1, registry.inFlightCount());

        // Names are compared case-insensitively
        assertSame(p, registry.complete(response(p.upstreamId(), question("A.Com.", DnsRecordType.A))));
        assertEquals(0, registry.inFlightCount());
    }

    @Test
    void unknownIdIsIgnored() {
        UpstreamQueryRegistry registry = new UpstreamQueryRegistry(scheduler, 10_000);
        assertNull(registry.complete(response(1234, question("a.com.", DnsRecordType.A))));
    }

    @Test
    void pendingQueryTimesOut() throws InterruptedException {
        UpstreamQueryRegistry registry = new UpstreamQueryRegistry(scheduler, 50);
        DnsQuestion q = question("a.com.", DnsRecordType.A);
        UpstreamQueryRegistry.PendingQuery p = registry.register(1, q, r -> {});
        for (int i = 0; i < 100 && registry.inFlightCount() > 0; i++) {
            Thread.sleep(20);
        }
        assertEquals(0, registry.inFlightCount());
        assertNull(registry.complete(response(p.upstreamId(), q)));
    }

    @Test
    void cancelRemovesQuery() {
        UpstreamQueryRegistry registry = new UpstreamQueryRegistry(scheduler, 10_000);
        DnsQuestion q = question("a.com.", DnsRecordType.A);
        UpstreamQueryRegistry.PendingQuery p = registry.register(1, q, r -> {});
        registry.cancel(p);
        assertEquals(0, registry.inFlightCount());
        assertNull(registry.complete(response(p.upstreamId(), q)));
    }

    @Test
    void failsWhenIdSpaceIsExhausted() {
        UpstreamQueryRegistry registry = new UpstreamQueryRegistry(scheduler, 60_000);
        DnsQuestion q = question("a.com.", DnsRecordType.A);
        for (int i = 0; i < UpstreamQueryRegistry.ID_SPACE; i++) {
            registry.register(1, q, r -> {});
        }
        assertThrows(IllegalStateException.class, () -> registry.register(1, q, r -> {}));
    }

    @Test
    void copyWithIdKeepsHeaderAndRecordsWithIndependentReferences() {
        DatagramDnsQuery original = new DatagramDnsQuery(
                new InetSocketAddress("127.0.0.1", 1111), new InetSocketAddress("127.0.0.1", 53), 99, DnsOpCode.QUERY);
        original.setRecursionDesired(true);
        original.addRecord(DnsSection.QUESTION, question("a.com.", DnsRecordType.A));
        DnsRawRecord opt = new DefaultDnsRawRecord("", DnsRecordType.OPT, 4096, 0,
                Unpooled.wrappedBuffer(new byte[] {1, 2}));
        original.addRecord(DnsSection.ADDITIONAL, opt);

        DnsQuery copy = DnsOverTlsClient.copyWithId(original, 12345);
        assertEquals(12345, copy.id());
        assertEquals(DnsOpCode.QUERY, copy.opCode());
        assertTrue(copy.isRecursionDesired());
        assertEquals("a.com.", copy.recordAt(DnsSection.QUESTION, 0).name());
        DnsRawRecord copiedOpt = copy.recordAt(DnsSection.ADDITIONAL, 0);
        assertNotSame(opt, copiedOpt);
        assertEquals(DnsRecordType.OPT, copiedOpt.type());
        assertEquals(4096, copiedOpt.dnsClass());

        // Releasing the original must not free the copy's buffer
        original.release();
        assertEquals(1, copiedOpt.content().refCnt());
        assertEquals(2, copiedOpt.content().getByte(1));
        copy.release();
        assertEquals(0, copiedOpt.content().refCnt());
    }
}
