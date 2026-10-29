package com.flower.net.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsMessageHandlerTest {
    static final InetSocketAddress SERVER = new InetSocketAddress("127.0.0.1", 53);
    static final InetSocketAddress CLIENT = new InetSocketAddress("127.0.0.1", 40000);
    static final DnsQuestion QUESTION = new DefaultDnsQuestion("example.org.", DnsRecordType.A);
    static final byte[] ADDRESS = {9, 8, 7, 6};
    /** In Netty's 3-bit Z field: AD is 0b010 and CD is 0b001. */
    static final int AD_AND_CD = 0b011;

    /** Answers from a pooled buffer, either right away or when {@link #answerPending()} is called. */
    static class FakeClient extends DnsOverTlsClient {
        final boolean deferred;
        final List<Runnable> pending = new ArrayList<>();
        final List<ByteBuf> buffers = new ArrayList<>();
        int queries;

        FakeClient(boolean deferred) throws Exception {
            super("1.1.1.1", 853, InsecureTrustManagerFactory.INSTANCE, false, null);
            this.deferred = deferred;
        }

        @Override
        public void query(DnsQuery query, Consumer<DefaultDnsResponse> consumer) {
            queries++;
            int id = query.id();
            Runnable answer = () -> {
                ByteBuf content = PooledByteBufAllocator.DEFAULT.directBuffer().writeBytes(ADDRESS);
                buffers.add(content);
                DefaultDnsResponse response = new DefaultDnsResponse(id, DnsOpCode.QUERY, DnsResponseCode.NXDOMAIN);
                response.setAuthoritativeAnswer(true)
                        .setTruncated(true)
                        .setRecursionDesired(true)
                        .setRecursionAvailable(true)
                        .setZ(AD_AND_CD);
                response.addRecord(DnsSection.QUESTION, QUESTION);
                response.addRecord(DnsSection.ANSWER,
                        new DefaultDnsRawRecord(QUESTION.name(), DnsRecordType.A, 60, content));
                consumer.accept(response);
            };
            if (deferred) {
                pending.add(answer);
            } else {
                answer.run();
            }
        }

        void answerPending() {
            pending.forEach(Runnable::run);
            pending.clear();
        }
    }

    FakeClient client;

    @AfterEach
    void tearDown() {
        for (ByteBuf buf : client.buffers) {
            assertEquals(0, buf.refCnt(), "leaked buffer");
        }
        client.shutdown();
    }

    void check(EmbeddedChannel channel) {
        DatagramDnsResponse response = channel.readOutbound();
        assertEquals(1234, response.id());
        assertEquals(DnsOpCode.QUERY, response.opCode());
        assertEquals(DnsResponseCode.NXDOMAIN, response.code());
        assertTrue(response.isAuthoritativeAnswer());
        assertTrue(response.isTruncated());
        assertTrue(response.isRecursionDesired());
        assertTrue(response.isRecursionAvailable());
        assertEquals(AD_AND_CD, response.z());
        assertEquals(SERVER, response.sender());
        assertEquals(CLIENT, response.recipient());
        assertEquals(QUESTION.name(), response.recordAt(DnsSection.QUESTION, 0).name());
        assertArrayEquals(ADDRESS,
                ByteBufUtil.getBytes(response.<DnsRawRecord>recordAt(DnsSection.ANSWER, 0).content()));
        // Stands in for the encoder, which releases what it writes
        response.release();
        assertNull(channel.readOutbound());
    }

    DatagramDnsQuery query() {
        return (DatagramDnsQuery) new DatagramDnsQuery(CLIENT, SERVER, 1234).addRecord(DnsSection.QUESTION, QUESTION);
    }

    @Test
    void headerFlagsReachTheWire() throws Exception {
        client = new FakeClient(false);
        EmbeddedChannel channel = new EmbeddedChannel(new DatagramDnsResponseEncoder(), new DnsMessageHandler(client));
        channel.writeInbound(query());
        DatagramPacket packet = channel.readOutbound();
        try {
            ByteBuf wire = packet.content();
            assertEquals(1234, wire.getUnsignedShort(0));
            // QR=1 OPCODE=0 AA=1 TC=1 RD=1 | RA=1 Z=0 AD=1 CD=1 RCODE=3 (NXDOMAIN)
            assertEquals(0b1000_0111, wire.getUnsignedByte(2));
            assertEquals(0b1011_0011, wire.getUnsignedByte(3));
        } finally {
            packet.release();
        }
    }

    DatagramDnsResponse rejected(DatagramDnsQuery query, DnsResponseCode expectedCode) throws Exception {
        client = new FakeClient(false);
        EmbeddedChannel channel = new EmbeddedChannel(new DnsMessageHandler(client));
        channel.writeInbound(query);
        DatagramDnsResponse response = channel.readOutbound();
        assertEquals(0, client.queries, "must not be forwarded");
        assertEquals(1234, response.id());
        assertEquals(expectedCode, response.code());
        assertEquals(query.opCode(), response.opCode());
        assertTrue(response.isRecursionDesired());
        assertTrue(response.isRecursionAvailable());
        assertEquals(CLIENT, response.recipient());
        assertEquals(0, response.count(DnsSection.ANSWER));
        response.release();
        return response;
    }

    @Test
    void queryWithoutQuestionGetsFormErr() throws Exception {
        DatagramDnsQuery query = new DatagramDnsQuery(CLIENT, SERVER, 1234);
        query.setRecursionDesired(true);
        rejected(query, DnsResponseCode.FORMERR);
    }

    @Test
    void queryWithTwoQuestionsGetsFormErr() throws Exception {
        DatagramDnsQuery query = query();
        query.setRecursionDesired(true);
        query.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion("other.org.", DnsRecordType.AAAA));
        rejected(query, DnsResponseCode.FORMERR);
    }

    @Test
    void nonQueryOpcodesGetNotImp() throws Exception {
        for (DnsOpCode opCode : new DnsOpCode[] {DnsOpCode.IQUERY, DnsOpCode.STATUS, DnsOpCode.NOTIFY,
                DnsOpCode.UPDATE}) {
            DatagramDnsQuery query = new DatagramDnsQuery(CLIENT, SERVER, 1234, opCode);
            query.setRecursionDesired(true);
            query.addRecord(DnsSection.QUESTION, QUESTION);
            rejected(query, DnsResponseCode.NOTIMP);
            client.shutdown();
        }
    }

    @Test
    void immediateAnswerIsRepliedWithoutLeaks() throws Exception {
        client = new FakeClient(false);
        EmbeddedChannel channel = new EmbeddedChannel(new DnsMessageHandler(client));
        channel.writeInbound(query());
        check(channel);
    }

    @Test
    void answerAfterQueryWasReleasedIsRepliedWithoutLeaks() throws Exception {
        client = new FakeClient(true);
        EmbeddedChannel channel = new EmbeddedChannel(new DnsMessageHandler(client));
        DatagramDnsQuery query = query();
        channel.writeInbound(query);
        assertEquals(0, query.refCnt());
        client.answerPending();
        check(channel);
    }
}
