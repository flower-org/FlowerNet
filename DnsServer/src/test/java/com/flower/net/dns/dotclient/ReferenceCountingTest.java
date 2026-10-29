package com.flower.net.dns.dotclient;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every response handed out must be independently owned: usable after its source is freed, and fully releasable. */
public class ReferenceCountingTest {
    static final DnsQuestion QUESTION = new DefaultDnsQuestion("example.org.", DnsRecordType.A);
    static final byte[] ADDRESS = {1, 2, 3, 4};

    final List<ByteBuf> buffers = new ArrayList<>();

    ByteBuf pooledBuffer(byte[] bytes) {
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes(bytes);
        buffers.add(buf);
        return buf;
    }

    DefaultDnsResponse upstreamResponse(int answers) {
        DefaultDnsResponse response = new DefaultDnsResponse(7);
        response.setRecursionAvailable(true);
        response.addRecord(DnsSection.QUESTION, QUESTION);
        for (int i = 0; i < answers; i++) {
            response.addRecord(DnsSection.ANSWER,
                    new DefaultDnsRawRecord(QUESTION.name(), DnsRecordType.A, 60, pooledBuffer(ADDRESS)));
        }
        return response;
    }

    static byte[] firstAnswer(DefaultDnsResponse response) {
        return ByteBufUtil.getBytes(response.<DnsRawRecord>recordAt(DnsSection.ANSWER, 0).content());
    }

    static DnsQuery query(int id) {
        return new DefaultDnsQuery(id).addRecord(DnsSection.QUESTION, QUESTION);
    }

    @AfterEach
    void noBufferLeaks() {
        for (ByteBuf buf : buffers) {
            assertEquals(0, buf.refCnt(), "leaked buffer");
        }
    }

    @Test
    void cacheDoesNotRetainTheResponseItStores() {
        AnswerCache cache = new AnswerCache();
        DefaultDnsResponse empty = upstreamResponse(0);
        cache.putResponse(AnswerCache.Key.of(query(1)), empty);
        assertNull(cache.getResponseIfPresent(query(1)));
        assertEquals(1, empty.refCnt());
        empty.release();

        DefaultDnsResponse withAnswer = upstreamResponse(1);
        cache.putResponse(AnswerCache.Key.of(query(1)), withAnswer);
        assertEquals(1, withAnswer.refCnt());
        assertEquals(1, buffers.get(0).refCnt());
        withAnswer.release();
    }

    @Test
    void cachedResponsesAreIndependentOfTheSourceEachOtherAndEviction() {
        AnswerCache cache = new AnswerCache();
        DefaultDnsResponse upstream = upstreamResponse(1);
        cache.putResponse(AnswerCache.Key.of(query(1)), upstream);
        upstream.release();

        DefaultDnsResponse first = cache.getResponseIfPresent(query(11));
        DefaultDnsResponse second = cache.getResponseIfPresent(query(22));
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(11, first.id());
        assertEquals(22, second.id());
        assertTrue(first.isRecursionAvailable());

        first.release();
        cache.cache.invalidateAll();
        cache.cache.cleanUp();
        // Still readable after another hit was released and the entry was evicted
        assertArrayEquals(ADDRESS, firstAnswer(second));
        DnsRawRecord answer = second.recordAt(DnsSection.ANSWER, 0);
        ByteBuf content = answer.content();
        second.release();
        assertEquals(0, content.refCnt());
    }

    @Test
    void upstreamResponseCopyOutlivesTheConnectionHandler() throws Exception {
        for (boolean useCache : new boolean[] {false, true}) {
            DnsOverTlsClient client = new DnsOverTlsClient("1.1.1.1", 853, InsecureTrustManagerFactory.INSTANCE,
                    useCache, null);
            try {
                EmbeddedChannel channel = new EmbeddedChannel();
                client.addDnsHandlers(channel.pipeline());
                List<DefaultDnsResponse> received = new ArrayList<>();
                UpstreamQueryRegistry.PendingQuery pending = client.pendingQueries().register(42, QUESTION, received::add);

                DefaultDnsResponse upstream = upstreamResponse(1);
                upstream.setId(pending.upstreamId());
                EmbeddedChannel encoder = new EmbeddedChannel(new TcpDnsResponseEncoder());
                encoder.writeOutbound(upstream);
                ByteBuf wire = encoder.readOutbound();
                // The decoded response is released by the handler as soon as the read completes
                channel.writeInbound(wire);

                assertEquals(1, received.size());
                DefaultDnsResponse response = received.get(0);
                assertEquals(42, response.id());
                assertEquals(DnsResponseCode.NOERROR, response.code());
                assertTrue(response.isRecursionAvailable());
                assertArrayEquals(ADDRESS, firstAnswer(response));
                DnsRawRecord answer = response.recordAt(DnsSection.ANSWER, 0);
                ByteBuf content = answer.content();
                response.release();
                assertEquals(0, content.refCnt());
                channel.finishAndReleaseAll();
            } finally {
                client.shutdown();
            }
        }
    }
}
