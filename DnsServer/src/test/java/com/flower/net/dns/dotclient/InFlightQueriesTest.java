package com.flower.net.dns.dotclient;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InFlightQueriesTest {
    static final byte[] ADDRESS = {1, 2, 3, 4};

    final InFlightQueries inFlight = new InFlightQueries();
    final List<DefaultDnsResponse> received = new ArrayList<>();
    final Consumer<DefaultDnsResponse> collect = received::add;

    static DnsQuery query(int id, String name, DnsRecordType type) {
        return new DefaultDnsQuery(id).setRecursionDesired(true)
                .addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(name, type));
    }

    @Test
    void identicalQueriesShareOneAnswer() {
        Consumer<DefaultDnsResponse> leader = inFlight.join(query(1, "example.org.", DnsRecordType.A), collect);
        assertNotNull(leader);
        assertNull(inFlight.join(query(2, "EXAMPLE.org.", DnsRecordType.A), collect));
        DnsQuery withAd = query(3, "example.ORG.", DnsRecordType.A);
        withAd.setZ(AnswerCache.Z_AUTHENTIC_DATA);
        assertNull(inFlight.join(withAd, collect));
        // Different questions or flags are separate flights
        assertNotNull(inFlight.join(query(4, "example.org.", DnsRecordType.AAAA), collect));
        assertNotNull(inFlight.join(query(5, "example.org.", DnsRecordType.A).setRecursionDesired(false), collect));
        assertEquals(3, inFlight.size());

        ByteBuf content = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes(ADDRESS);
        DefaultDnsResponse response = new DefaultDnsResponse(1);
        response.setRecursionAvailable(true).setZ(AnswerCache.Z_AUTHENTIC_DATA);
        response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion("example.org.", DnsRecordType.A));
        response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord("example.org.", DnsRecordType.A, 60, content));
        leader.accept(response);
        assertEquals(2, inFlight.size());

        assertEquals(3, received.size());
        String[] names = {"EXAMPLE.org.", "example.ORG.", "example.org."};
        int[] ids = {2, 3, 1};
        int[] ad = {0, AnswerCache.Z_AUTHENTIC_DATA, AnswerCache.Z_AUTHENTIC_DATA};
        for (int i = 0; i < 3; i++) {
            DefaultDnsResponse r = received.get(i);
            assertEquals(ids[i], r.id());
            assertEquals(names[i], r.<DnsQuestion>recordAt(DnsSection.QUESTION).name());
            assertEquals(ad[i], r.z() & AnswerCache.Z_AUTHENTIC_DATA);
            assertEquals(true, r.isRecursionAvailable());
            assertArrayEquals(ADDRESS, ByteBufUtil.getBytes(r.<DnsRawRecord>recordAt(DnsSection.ANSWER).content()));
        }
        // Each copy is owned independently
        received.get(2).release();
        assertArrayEquals(ADDRESS, ByteBufUtil.getBytes(
                received.get(0).<DnsRawRecord>recordAt(DnsSection.ANSWER).content()));
        received.get(0).release();
        received.get(1).release();
        assertEquals(0, content.refCnt());

        // After the flight ends, the next identical query leads a new one
        assertNotNull(inFlight.join(query(6, "example.org.", DnsRecordType.A), collect));
    }

    @Test
    void failingFollowerDoesNotStopTheOthers() {
        Consumer<DefaultDnsResponse> leader = inFlight.join(query(1, "a.", DnsRecordType.A), collect);
        assertNotNull(leader);
        assertNull(inFlight.join(query(2, "a.", DnsRecordType.A), r -> {
            r.release();
            throw new IllegalStateException("boom");
        }));
        DefaultDnsResponse response = new DefaultDnsResponse(1);
        response.addRecord(DnsSection.ANSWER,
                new DefaultDnsRawRecord("a.", DnsRecordType.A, 60, Unpooled.wrappedBuffer(ADDRESS)));
        leader.accept(response);
        assertEquals(1, received.size());
        received.get(0).release();
    }
}
