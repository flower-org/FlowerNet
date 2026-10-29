package com.flower.net.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DnsSection;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LenientDnsQueryDecoderTest {
    static final InetSocketAddress SERVER = new InetSocketAddress("127.0.0.1", 53);
    static final InetSocketAddress CLIENT = new InetSocketAddress("127.0.0.1", 40000);

    final EmbeddedChannel channel = new EmbeddedChannel(new LenientDnsQueryDecoder());

    void receive(int... bytes) {
        ByteBuf buf = Unpooled.buffer();
        for (int b : bytes) {
            buf.writeByte(b);
        }
        channel.writeInbound(new DatagramPacket(buf, SERVER, CLIENT));
    }

    @Test
    void validQueryIsDecoded() {
        // ID 0x1234, RD, one question: "a." A IN
        receive(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0, 1, 'a', 0, 0, 1, 0, 1);
        DatagramDnsQuery query = channel.readInbound();
        assertEquals(0x1234, query.id());
        assertEquals("a.", query.recordAt(DnsSection.QUESTION, 0).name());
        query.release();
        assertNull(channel.readOutbound());
    }

    @Test
    void truncatedQueryGetsFormErr() {
        // Claims one question, but the name runs past the end of the packet
        receive(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0, 5, 'a');
        assertNull(channel.readInbound());
        DatagramPacket reply = channel.readOutbound();
        ByteBuf wire = reply.content();
        assertEquals(CLIENT, reply.recipient());
        assertEquals(SERVER, reply.sender());
        assertEquals(0x1234, wire.getUnsignedShort(0));
        // QR=1 OPCODE=0 AA=0 TC=0 RD=1 | RA=1 Z=0 RCODE=1 (FORMERR)
        assertEquals(0b1000_0001, wire.getUnsignedByte(2));
        assertEquals(0b1000_0001, wire.getUnsignedByte(3));
        assertEquals(0, wire.getUnsignedShort(4), "no question echoed");
        reply.release();
    }

    @Test
    void responsesAndRuntsAreDroppedSilently() {
        receive(0x12, 0x34, 0x81, 0x80, 0, 1, 0, 0, 0, 0, 0, 0);
        receive(0x12, 0x34, 0x01);
        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());
        channel.checkException();
    }
}
