package com.flower.net.dns.dotclient;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TcpDnsQueryFullHeaderEncoderTest {
    @Test
    void writesRdAdAndCdAndRoundTrips() {
        DnsQuery query = new DefaultDnsQuery(0xBEEF, DnsOpCode.QUERY)
                .setRecursionDesired(true)
                .setZ(0b011)
                .addRecord(DnsSection.QUESTION, new DefaultDnsQuestion("isc.org.", DnsRecordType.A));
        EmbeddedChannel encoder = new EmbeddedChannel(new TcpDnsQueryFullHeaderEncoder());
        assertTrue(encoder.writeOutbound(query));
        ByteBuf wire = encoder.readOutbound();

        assertEquals(wire.readableBytes() - 2, wire.getUnsignedShort(0));
        assertEquals(0xBEEF, wire.getUnsignedShort(2));
        // QR=0 OPCODE=0 AA=0 TC=0 RD=1 | RA=0 Z=0 AD=1 CD=1 RCODE=0
        assertEquals(0b0000_0001, wire.getUnsignedByte(4));
        assertEquals(0b0011_0000, wire.getUnsignedByte(5));

        EmbeddedChannel decoder = new EmbeddedChannel(new TcpDnsQueryDecoder());
        assertTrue(decoder.writeInbound(wire));
        DnsQuery decoded = decoder.readInbound();
        try {
            assertEquals(0xBEEF, decoded.id());
            assertTrue(decoded.isRecursionDesired());
            assertEquals(0b011, decoded.z());
            assertEquals("isc.org.", decoded.recordAt(DnsSection.QUESTION, 0).name());
        } finally {
            decoded.release();
        }
    }
}
