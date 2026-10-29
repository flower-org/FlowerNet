package com.flower.net.dns.dotclient;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DecompressingDnsRecordDecoderTest {
    /** "gmail.com." in wire format; the question starts at offset 12, right after the header. */
    static final byte[] GMAIL = name("gmail", "com");
    static final int GMAIL_OFFSET = 12;
    static final byte[] POINTER_TO_GMAIL = {(byte) 0xC0, GMAIL_OFFSET};

    static byte[] name(String... labels) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : labels) {
            out.write(label.length());
            out.writeBytes(label.getBytes(StandardCharsets.US_ASCII));
        }
        out.write(0);
        return out.toByteArray();
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    static byte[] u16(int value) {
        return new byte[] {(byte) (value >> 8), (byte) value};
    }

    static byte[] u32(long value) {
        return concat(u16((int) (value >> 16)), u16((int) value));
    }

    /** One question for gmail.com, then one answer owned by gmail.com (compressed) with the given RDATA. */
    static byte[] message(DnsRecordType type, byte[] rdata) {
        return concat(
                u16(0x1234), u16(0x8180), u16(1), u16(1), u16(0), u16(0),
                GMAIL, u16(type.intValue()), u16(1),
                POINTER_TO_GMAIL, u16(type.intValue()), u16(1), u32(300), u16(rdata.length), rdata);
    }

    static byte[] decodeRdata(byte[] message) {
        EmbeddedChannel channel = new EmbeddedChannel(
                new TcpDnsResponseDecoder(new DecompressingDnsRecordDecoder(), 65_535));
        channel.writeInbound(Unpooled.wrappedBuffer(u16(message.length), message));
        DnsResponse response = channel.readInbound();
        try {
            assertEquals("gmail.com.", response.recordAt(DnsSection.ANSWER, 0).name());
            return ByteBufUtil.getBytes(response.<DnsRawRecord>recordAt(DnsSection.ANSWER, 0).content());
        } finally {
            response.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void mxExchangeIsExpanded() {
        byte[] rdata = concat(u16(10), new byte[] {4, 'a', 'l', 't', '1'}, POINTER_TO_GMAIL);
        assertArrayEquals(concat(u16(10), name("alt1", "gmail", "com")), decodeRdata(message(DnsRecordType.MX, rdata)));
    }

    @Test
    void soaNamesAreExpandedAndCountersKept() {
        byte[] counters = concat(u32(2026), u32(7200), u32(900), u32(1209600), u32(60));
        byte[] rdata = concat(new byte[] {3, 'n', 's', '1'}, POINTER_TO_GMAIL, POINTER_TO_GMAIL, counters);
        assertArrayEquals(concat(name("ns1", "gmail", "com"), GMAIL, counters),
                decodeRdata(message(DnsRecordType.SOA, rdata)));
    }

    @Test
    void srvTargetIsExpanded() {
        byte[] fixed = concat(u16(5), u16(0), u16(5269));
        assertArrayEquals(concat(fixed, GMAIL), decodeRdata(message(DnsRecordType.SRV, concat(fixed, POINTER_TO_GMAIL))));
    }

    @Test
    void naptrReplacementIsExpandedAfterCharacterStrings() {
        byte[] fixed = concat(u16(100), u16(10), new byte[] {1, 'S', 2, 'x', 'y', 0});
        assertArrayEquals(concat(fixed, GMAIL),
                decodeRdata(message(DnsRecordType.NAPTR, concat(fixed, POINTER_TO_GMAIL))));
    }

    @Test
    void pointerChainsAreFollowed() {
        // The answer's owner name follows the question (12 + name + type + class) and is itself a pointer
        int ownerOffset = GMAIL_OFFSET + GMAIL.length + 4;
        byte[] rdata = concat(new byte[] {3, 'w', 'w', 'w'}, new byte[] {(byte) 0xC0, (byte) ownerOffset});
        assertArrayEquals(name("www", "gmail", "com"), decodeRdata(message(DnsRecordType.CNAME, rdata)));
    }

    @Test
    void otherTypesAreUntouched() {
        byte[] address = {1, 2, 3, 4};
        assertArrayEquals(address, decodeRdata(message(DnsRecordType.A, address)));
        // TXT data that happens to look like a pointer stays as it is
        byte[] txt = {2, (byte) 0xC0, 12};
        assertArrayEquals(txt, decodeRdata(message(DnsRecordType.TXT, txt)));
    }

    @Test
    void malformedNamesAreRejected() {
        byte[] forward = concat(u16(10), new byte[] {(byte) 0xC0, (byte) 0xFF});
        assertThrows(DecoderException.class, () -> decodeRdata(message(DnsRecordType.MX, forward)));
        byte[] truncated = concat(u16(10), new byte[] {9, 'a'});
        assertThrows(DecoderException.class, () -> decodeRdata(message(DnsRecordType.MX, truncated)));
        byte[] trailing = concat(u16(10), GMAIL, new byte[] {1});
        assertThrows(DecoderException.class, () -> decodeRdata(message(DnsRecordType.MX, trailing)));
    }
}
