package com.flower.net.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsTcpMessageHandlerTest {
    static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 53);

    DnsMessageHandlerTest.FakeClient client;

    @AfterEach
    void tearDown() {
        for (ByteBuf buf : client.buffers) {
            assertEquals(0, buf.refCnt(), "leaked buffer");
        }
        client.shutdown();
    }

    EmbeddedChannel channel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DnsTcpMessageHandler.addHandlers(channel.pipeline(), client);
        return channel;
    }

    /** A length-prefixed query for "example.org. A" with RD set. */
    static ByteBuf query(int id) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(0);
        buf.writeShort(id).writeShort(0x0100).writeShort(1).writeShort(0).writeShort(0).writeShort(0);
        buf.writeByte(7).writeBytes("example".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buf.writeByte(3).writeBytes("org".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).writeByte(0);
        buf.writeShort(1).writeShort(1);
        buf.setShort(0, buf.readableBytes() - 2);
        return buf;
    }

    static DatagramDnsResponse readReply(EmbeddedChannel channel) {
        ByteBuf framed = channel.readOutbound();
        assertEquals(framed.readableBytes() - 2, framed.readUnsignedShort());
        EmbeddedChannel decoder = new EmbeddedChannel(new DatagramDnsResponseDecoder());
        decoder.writeInbound(new DatagramPacket(framed, ADDRESS, ADDRESS));
        return decoder.readInbound();
    }

    @Test
    void largeAnswerIsNotTruncated() throws Exception {
        client = new DnsMessageHandlerTest.FakeClient(false, 100);
        EmbeddedChannel channel = channel();
        ByteBuf query = query(1234);
        channel.writeInbound(query);
        assertEquals(0, query.refCnt(), "the query is freed");
        DatagramDnsResponse response = readReply(channel);
        try {
            assertEquals(1234, response.id());
            assertEquals(100, response.count(DnsSection.ANSWER));
            // The fake upstream answer has TC set; it's passed through, but we add nothing
            assertTrue(response.isTruncated());
        } finally {
            response.release();
        }
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void pipelinedQueriesAreAnsweredAsTheyArrive() throws Exception {
        client = new DnsMessageHandlerTest.FakeClient(true);
        EmbeddedChannel channel = channel();
        // Two queries in one segment, the second split across two writes
        ByteBuf first = query(1);
        ByteBuf second = query(2);
        channel.writeInbound(Unpooled.wrappedBuffer(first, second.readRetainedSlice(5)));
        channel.writeInbound(second);
        assertEquals(2, client.queries);
        assertNull(channel.readOutbound());

        client.answerPending();
        for (int id = 1; id <= 2; id++) {
            DatagramDnsResponse response = readReply(channel);
            try {
                assertEquals(id, response.id());
                assertEquals(1, response.count(DnsSection.ANSWER));
            } finally {
                response.release();
            }
        }
        channel.finishAndReleaseAll();
    }

    @Test
    void invalidQueryGetsFormErrAndTheConnectionStaysOpen() throws Exception {
        client = new DnsMessageHandlerTest.FakeClient(false);
        EmbeddedChannel channel = channel();
        ByteBuf noQuestion = Unpooled.buffer().writeShort(12)
                .writeShort(9).writeShort(0x0100).writeZero(8);
        channel.writeInbound(noQuestion);
        DatagramDnsResponse response = readReply(channel);
        try {
            assertEquals(9, response.id());
            assertEquals(io.netty.handler.codec.dns.DnsResponseCode.FORMERR, response.code());
        } finally {
            response.release();
        }
        assertEquals(0, client.queries);
        assertTrue(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void garbageClosesTheConnection() throws Exception {
        client = new DnsMessageHandlerTest.FakeClient(false);
        EmbeddedChannel channel = channel();
        // Claims a question, but the name runs past the end of the message
        channel.writeInbound(Unpooled.buffer().writeShort(14)
                .writeShort(9).writeShort(0x0100).writeShort(1).writeZero(6).writeByte(5).writeByte('a'));
        assertFalse(channel.isActive());
        assertNull(channel.readOutbound());
        channel.finishAndReleaseAll();
    }
}
