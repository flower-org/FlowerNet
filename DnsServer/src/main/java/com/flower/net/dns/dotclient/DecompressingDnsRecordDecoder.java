package com.flower.net.dns.dotclient;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;

/**
 * Decodes records with the domain names in their RDATA decompressed.
 *
 * <p>Compression pointers are offsets into the message they came in. Netty keeps most RDATA as raw bytes, so a
 * compressed name would point at random data once the record is re-encoded into another message. This decoder
 * expands names for every type whose RDATA may legitimately be compressed: the RFC 1035 types, and the types that
 * RFC 3597 section 4 requires receivers to decompress. All other types are kept as they are.
 */
public class DecompressingDnsRecordDecoder extends DefaultDnsRecordDecoder {
    /** RDATA layout steps: a positive number is a fixed-size field of that many bytes. */
    static final int NAME = -1;
    static final int CHARACTER_STRING = -2;
    static final int REST = -3;

    static final int MAX_NAME_LENGTH = 255;

    static final ImmutableMap<Integer, int[]> LAYOUTS = ImmutableMap.<Integer, int[]>builder()
            // RFC 1035
            .put(DnsRecordType.NS.intValue(), new int[] {NAME})
            .put(3 /* MD */, new int[] {NAME})
            .put(4 /* MF */, new int[] {NAME})
            .put(DnsRecordType.CNAME.intValue(), new int[] {NAME})
            .put(DnsRecordType.SOA.intValue(), new int[] {NAME, NAME, 20})
            .put(7 /* MB */, new int[] {NAME})
            .put(8 /* MG */, new int[] {NAME})
            .put(9 /* MR */, new int[] {NAME})
            .put(DnsRecordType.PTR.intValue(), new int[] {NAME})
            .put(14 /* MINFO */, new int[] {NAME, NAME})
            .put(DnsRecordType.MX.intValue(), new int[] {2, NAME})
            // RFC 3597 section 4
            .put(DnsRecordType.RP.intValue(), new int[] {NAME, NAME})
            .put(DnsRecordType.AFSDB.intValue(), new int[] {2, NAME})
            .put(21 /* RT */, new int[] {2, NAME})
            .put(DnsRecordType.SIG.intValue(), new int[] {18, NAME, REST})
            .put(26 /* PX */, new int[] {2, NAME, NAME})
            .put(30 /* NXT */, new int[] {NAME, REST})
            .put(DnsRecordType.NAPTR.intValue(),
                    new int[] {4, CHARACTER_STRING, CHARACTER_STRING, CHARACTER_STRING, NAME})
            .put(DnsRecordType.SRV.intValue(), new int[] {6, NAME})
            // RFC 2230; the RFC 3597 list predates it, but servers compress it like MX
            .put(DnsRecordType.KX.intValue(), new int[] {2, NAME})
            .build();

    @Override
    protected DnsRecord decodeRecord(String name, DnsRecordType type, int dnsClass, long timeToLive,
                                     ByteBuf in, int offset, int length) throws Exception {
        int[] layout = LAYOUTS.get(type.intValue());
        if (layout == null) {
            return super.decodeRecord(name, type, dnsClass, timeToLive, in, offset, length);
        }
        ByteBuf rdata = in.alloc().buffer(length + 64);
        try {
            decompress(in, offset, offset + length, layout, rdata);
            return new DefaultDnsRawRecord(name, type, dnsClass, timeToLive, rdata);
        } catch (Throwable t) {
            rdata.release();
            throw t;
        }
    }

    /** Copies the RDATA at {@code [start, end)} of the message {@code in} to {@code out}, expanding names. */
    static void decompress(ByteBuf in, int start, int end, int[] layout, ByteBuf out) {
        int pos = start;
        for (int step : layout) {
            if (step == NAME) {
                pos = copyName(in, pos, end, out);
            } else if (step == CHARACTER_STRING) {
                checkBounds(pos, 1, end);
                pos = copy(in, pos, 1 + in.getUnsignedByte(pos), end, out);
            } else if (step == REST) {
                pos = copy(in, pos, end - pos, end, out);
            } else {
                pos = copy(in, pos, step, end, out);
            }
        }
        if (pos != end) {
            throw new CorruptedFrameException("RDATA has " + (end - pos) + " unexpected trailing bytes");
        }
    }

    static int copy(ByteBuf in, int pos, int length, int end, ByteBuf out) {
        checkBounds(pos, length, end);
        out.writeBytes(in, pos, length);
        return pos + length;
    }

    static void checkBounds(int pos, int length, int end) {
        if (pos + length > end) {
            throw new CorruptedFrameException("truncated RDATA");
        }
    }

    /**
     * Writes the name at {@code pos} uncompressed and returns the position after it in the RDATA.
     * Labels are copied byte for byte, so their content is preserved exactly.
     */
    static int copyName(ByteBuf in, int pos, int end, ByteBuf out) {
        int next = -1;
        int limit = end;
        int written = 0;
        while (true) {
            checkBounds(pos, 1, limit);
            int len = in.getUnsignedByte(pos);
            if ((len & 0xC0) == 0xC0) {
                checkBounds(pos, 2, limit);
                if (next == -1) {
                    next = pos + 2;
                }
                int target = (len & 0x3F) << 8 | in.getUnsignedByte(pos + 1);
                // Pointers must go backwards (RFC 1035 4.1.4), which also rules out loops
                if (target >= pos) {
                    throw new CorruptedFrameException("name has a forward or looping pointer");
                }
                pos = target;
                limit = in.writerIndex();
            } else if ((len & 0xC0) != 0) {
                throw new CorruptedFrameException("unsupported label type " + (len >> 6));
            } else {
                checkBounds(pos, 1 + len, limit);
                written += 1 + len;
                if (written > MAX_NAME_LENGTH) {
                    throw new CorruptedFrameException("name longer than " + MAX_NAME_LENGTH + " bytes");
                }
                out.writeBytes(in, pos, 1 + len);
                pos += 1 + len;
                if (len == 0) {
                    return next == -1 ? pos : next;
                }
            }
        }
    }
}
