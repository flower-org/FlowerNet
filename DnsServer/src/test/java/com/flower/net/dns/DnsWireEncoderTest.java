package com.flower.net.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsWireEncoderTest {
    static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 53);
    static final String NAME = "Example.ORG.";
    /** DO flag and EDNS version 0, with an extended RCODE upper part of 1 (BADVERS = 16 when combined). */
    static final long UPSTREAM_OPT_TTL = 0x01_00_8000L;

    static DnsRecord a(String name, int lastByte) {
        return new DefaultDnsRawRecord(name, DnsRecordType.A, 300, Unpooled.wrappedBuffer(new byte[] {1, 2, 3,
                (byte) lastByte}));
    }

    static DnsRecord opt(int payloadSize, long ttl, byte[] options) {
        return new DefaultDnsRawRecord("", DnsRecordType.OPT, payloadSize, ttl, Unpooled.wrappedBuffer(options));
    }

    static DefaultDnsResponse response(int answers, int additional, boolean withOpt) {
        DefaultDnsResponse response = new DefaultDnsResponse(77, DnsOpCode.QUERY, DnsResponseCode.NOERROR);
        response.setRecursionDesired(true).setRecursionAvailable(true);
        response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(NAME, DnsRecordType.A));
        for (int i = 0; i < answers; i++) {
            response.addRecord(DnsSection.ANSWER, a(NAME, i));
        }
        for (int i = 0; i < additional; i++) {
            response.addRecord(DnsSection.ADDITIONAL, a("ns" + i + "." + NAME, i));
        }
        if (withOpt) {
            response.addRecord(DnsSection.ADDITIONAL, opt(4096, UPSTREAM_OPT_TTL, new byte[] {0, 10, 0, 0}));
        }
        return response;
    }

    static DatagramDnsResponse decode(ByteBuf wire) {
        EmbeddedChannel channel = new EmbeddedChannel(new DatagramDnsResponseDecoder());
        channel.writeInbound(new DatagramPacket(wire, ADDRESS, ADDRESS));
        return channel.readInbound();
    }

    static DnsWireEncoder.ClientEdns edns(int payloadSize) {
        DefaultDnsQuery query = new DefaultDnsQuery(1);
        query.addRecord(DnsSection.ADDITIONAL, opt(payloadSize, 0, new byte[0]));
        DnsWireEncoder.ClientEdns edns = DnsWireEncoder.ClientEdns.of(query);
        query.release();
        return edns;
    }

    @Test
    void maxUdpSize() {
        assertNull(DnsWireEncoder.ClientEdns.of(new DefaultDnsQuery(1)));
        assertEquals(512, DnsWireEncoder.maxUdpSize(null));
        assertEquals(512, DnsWireEncoder.maxUdpSize(edns(100)));
        assertEquals(1000, DnsWireEncoder.maxUdpSize(edns(1000)));
        assertEquals(1232, DnsWireEncoder.maxUdpSize(edns(4096)));
    }

    @Test
    void roundTripWithCompression() {
        DefaultDnsResponse response = response(3, 1, false);
        ByteBuf wire = Unpooled.buffer();
        assertFalse(DnsWireEncoder.encode(response, null, 512, wire));
        // Compression is case-exact, so the client's 0x20 casing survives.
        // Header 12, question 13 + 4, each answer a 2-byte pointer + 14, additional "ns0" + pointer + 14
        assertEquals(12 + 17 + 3 * 16 + (4 + 2 + 14), wire.readableBytes());
        DatagramDnsResponse decoded = decode(wire);
        try {
            assertEquals(77, decoded.id());
            assertTrue(decoded.isRecursionDesired());
            assertTrue(decoded.isRecursionAvailable());
            assertFalse(decoded.isTruncated());
            assertEquals(NAME, decoded.recordAt(DnsSection.QUESTION).name());
            assertEquals(3, decoded.count(DnsSection.ANSWER));
            for (int i = 0; i < 3; i++) {
                DnsRawRecord answer = decoded.recordAt(DnsSection.ANSWER, i);
                assertEquals(NAME, answer.name());
                assertEquals(300, answer.timeToLive());
                assertArrayEquals(new byte[] {1, 2, 3, (byte) i}, ByteBufUtil.getBytes(answer.content()));
            }
            assertEquals("ns0." + NAME, decoded.recordAt(DnsSection.ADDITIONAL, 0).name());
        } finally {
            decoded.release();
            response.release();
        }
    }

    @Test
    void additionalSectionIsDroppedBeforeTruncating() {
        DefaultDnsResponse response = response(20, 20, false);
        ByteBuf wire = Unpooled.buffer();
        assertFalse(DnsWireEncoder.encode(response, null, 512, wire));
        assertTrue(wire.readableBytes() <= 512);
        DatagramDnsResponse decoded = decode(wire);
        try {
            assertFalse(decoded.isTruncated());
            assertEquals(20, decoded.count(DnsSection.ANSWER));
            assertEquals(0, decoded.count(DnsSection.ADDITIONAL));
        } finally {
            decoded.release();
            response.release();
        }
    }

    @Test
    void tooLargeIsTruncatedToTheQuestion() {
        DefaultDnsResponse response = response(40, 0, true);
        ByteBuf wire = Unpooled.buffer();
        assertTrue(DnsWireEncoder.encode(response, null, 512, wire));
        DatagramDnsResponse decoded = decode(wire);
        try {
            assertTrue(decoded.isTruncated());
            assertEquals(1, decoded.count(DnsSection.QUESTION));
            assertEquals(0, decoded.count(DnsSection.ANSWER));
            assertEquals(0, decoded.count(DnsSection.ADDITIONAL), "the upstream OPT isn't forwarded");
        } finally {
            decoded.release();
        }

        // The same response fits over TCP
        wire = Unpooled.buffer();
        assertFalse(DnsWireEncoder.encode(response, null, DnsWireEncoder.MAX_TCP_MESSAGE_SIZE, wire));
        decoded = decode(wire);
        try {
            assertEquals(40, decoded.count(DnsSection.ANSWER));
        } finally {
            decoded.release();
            response.release();
        }
    }

    @Test
    void ednsClientGetsOurOptWithUpstreamFlags() {
        DefaultDnsResponse response = response(1, 0, true);
        ByteBuf wire = Unpooled.buffer();
        assertFalse(DnsWireEncoder.encode(response, edns(4096), 1232, wire));
        DatagramDnsResponse decoded = decode(wire);
        try {
            assertEquals(1, decoded.count(DnsSection.ADDITIONAL));
            DnsRawRecord opt = decoded.recordAt(DnsSection.ADDITIONAL, 0);
            assertEquals(DnsRecordType.OPT, opt.type());
            assertEquals(DnsWireEncoder.EDNS_UDP_PAYLOAD_SIZE, opt.dnsClass());
            assertEquals(UPSTREAM_OPT_TTL, opt.timeToLive());
            assertArrayEquals(new byte[] {0, 10, 0, 0}, ByteBufUtil.getBytes(opt.content()));
        } finally {
            decoded.release();
            response.release();
        }
    }

    @Test
    void ednsClientGetsAnOptEvenWithoutAnUpstreamOne() {
        DefaultDnsResponse response = new DefaultDnsResponse(5, DnsOpCode.QUERY, DnsResponseCode.SERVFAIL);
        ByteBuf wire = Unpooled.buffer();
        assertFalse(DnsWireEncoder.encode(response, edns(1232), 1232, wire));
        DatagramDnsResponse decoded = decode(wire);
        try {
            assertEquals(DnsResponseCode.SERVFAIL, decoded.code());
            DnsRecord opt = decoded.recordAt(DnsSection.ADDITIONAL, 0);
            assertEquals(DnsRecordType.OPT, opt.type());
            assertEquals(0, opt.timeToLive());
        } finally {
            decoded.release();
            response.release();
        }
    }
}
