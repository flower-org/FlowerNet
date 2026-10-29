package com.flower.net.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes responses for our clients, over UDP or TCP.
 *
 * <ul>
 *   <li>Owner and question names are compressed (RFC 1035 4.1.4), keeping replies close to the upstream's size.</li>
 *   <li>A response that doesn't fit {@code maxSize} first loses its additional records, which needs no TC bit
 *   (RFC 2181 9). If it still doesn't fit, only the question is sent, with TC set, so the client retries over TCP.</li>
 *   <li>The upstream's OPT record describes the upstream's EDNS, not ours: it's replaced by one advertising our own
 *   payload size, and only included when the client used EDNS (RFC 6891 7).</li>
 * </ul>
 */
public final class DnsWireEncoder {
    /** What a client without EDNS can receive over UDP (RFC 1035 4.2.1). */
    public static final int CLASSIC_UDP_PAYLOAD_SIZE = 512;
    /** The EDNS UDP payload size we accept and advertise, which avoids IP fragmentation (DNS Flag Day 2020). */
    public static final int EDNS_UDP_PAYLOAD_SIZE = 1232;
    /** The largest message the 2-byte TCP length prefix can describe (RFC 7766 8). */
    public static final int MAX_TCP_MESSAGE_SIZE = 65_535;

    static final int HEADER_SIZE = 12;
    static final int MAX_POINTER_OFFSET = 0x3FFF;

    private DnsWireEncoder() {
    }

    /** The client's EDNS parameters, read from its query's OPT record. */
    public static final class ClientEdns {
        final int udpPayloadSize;

        ClientEdns(int udpPayloadSize) {
            this.udpPayloadSize = udpPayloadSize;
        }

        /** Returns null if the query has no OPT record. */
        @Nullable
        public static ClientEdns of(DnsMessage query) {
            DnsRecord opt = findOpt(query);
            return opt == null ? null : new ClientEdns(opt.dnsClass());
        }
    }

    @Nullable
    static DnsRecord findOpt(DnsMessage message) {
        for (int i = 0, count = message.count(DnsSection.ADDITIONAL); i < count; i++) {
            DnsRecord record = message.recordAt(DnsSection.ADDITIONAL, i);
            if (DnsRecordType.OPT.equals(record.type())) {
                return record;
            }
        }
        return null;
    }

    /** How large a UDP reply to this client may be (RFC 6891 6.2.5: values below 512 are treated as 512). */
    public static int maxUdpSize(@Nullable ClientEdns edns) {
        if (edns == null) {
            return CLASSIC_UDP_PAYLOAD_SIZE;
        }
        return Math.min(EDNS_UDP_PAYLOAD_SIZE, Math.max(CLASSIC_UDP_PAYLOAD_SIZE, edns.udpPayloadSize));
    }

    /**
     * Appends the response to {@code out}, within {@code maxSize} bytes. Doesn't take ownership of {@code response}.
     *
     * @return true if the response had to be truncated (TC set)
     */
    public static boolean encode(DnsResponse response, @Nullable ClientEdns edns, int maxSize, ByteBuf out) {
        int start = out.writerIndex();
        if (tryEncode(response, edns, true, true, maxSize, out, start)
                || tryEncode(response, edns, true, false, maxSize, out, start)) {
            return false;
        }
        if (!tryEncode(response, edns, false, false, maxSize, out, start)) {
            throw new EncoderException("Even the truncated response is larger than " + maxSize + " bytes");
        }
        return true;
    }

    private static boolean tryEncode(DnsResponse response, @Nullable ClientEdns edns, boolean answers,
                                     boolean additional, int maxSize, ByteBuf out, int start) {
        out.writerIndex(start);
        new Writer(out, start).write(response, edns, answers, additional);
        if (out.writerIndex() - start <= maxSize) {
            return true;
        }
        out.writerIndex(start);
        return false;
    }

    static final class Writer {
        final ByteBuf out;
        final int messageStart;
        final Map<String, Integer> nameOffsets = new HashMap<>();

        Writer(ByteBuf out, int messageStart) {
            this.out = out;
            this.messageStart = messageStart;
        }

        void write(DnsResponse response, @Nullable ClientEdns edns, boolean answers, boolean additional) {
            DnsRecord upstreamOpt = findOpt(response);
            int additionalCount = 0;
            if (additional) {
                additionalCount = response.count(DnsSection.ADDITIONAL) - (upstreamOpt == null ? 0 : 1);
            }
            boolean withOpt = edns != null;

            out.writeShort(response.id());
            out.writeShort(flags(response, !answers));
            out.writeShort(response.count(DnsSection.QUESTION));
            out.writeShort(answers ? response.count(DnsSection.ANSWER) : 0);
            out.writeShort(answers ? response.count(DnsSection.AUTHORITY) : 0);
            out.writeShort(additionalCount + (withOpt ? 1 : 0));

            for (int i = 0, count = response.count(DnsSection.QUESTION); i < count; i++) {
                DnsQuestion question = response.recordAt(DnsSection.QUESTION, i);
                writeName(question.name());
                out.writeShort(question.type().intValue());
                out.writeShort(question.dnsClass());
            }
            if (answers) {
                writeSection(response, DnsSection.ANSWER);
                writeSection(response, DnsSection.AUTHORITY);
            }
            if (additional) {
                writeSection(response, DnsSection.ADDITIONAL);
            }
            if (withOpt) {
                writeOpt(upstreamOpt);
            }
        }

        static int flags(DnsResponse response, boolean truncated) {
            int flags = 1 << 15;
            flags |= (response.opCode().byteValue() & 0xF) << 11;
            if (response.isAuthoritativeAnswer()) {
                flags |= 1 << 10;
            }
            if (truncated || response.isTruncated()) {
                flags |= 1 << 9;
            }
            if (response.isRecursionDesired()) {
                flags |= 1 << 8;
            }
            if (response.isRecursionAvailable()) {
                flags |= 1 << 7;
            }
            flags |= (response.z() & 0x7) << 4;
            // The upper bits of extended RCODEs travel in the OPT record
            flags |= response.code().intValue() & 0xF;
            return flags;
        }

        void writeSection(DnsResponse response, DnsSection section) {
            for (int i = 0, count = response.count(section); i < count; i++) {
                DnsRecord record = response.recordAt(section, i);
                if (!DnsRecordType.OPT.equals(record.type())) {
                    writeRecord(record);
                }
            }
        }

        void writeRecord(DnsRecord record) {
            if (!(record instanceof DnsRawRecord)) {
                // Our decoders produce raw records only; anything else is written uncompressed
                try {
                    DnsRecordEncoder.DEFAULT.encodeRecord(record, out);
                } catch (Exception e) {
                    throw new EncoderException(e);
                }
                return;
            }
            ByteBuf content = ((DnsRawRecord) record).content();
            writeName(record.name());
            out.writeShort(record.type().intValue());
            out.writeShort(record.dnsClass());
            out.writeInt((int) record.timeToLive());
            out.writeShort(content.readableBytes());
            out.writeBytes(content, content.readerIndex(), content.readableBytes());
        }

        /**
         * Our OPT: the upstream's extended RCODE, version, flags (DO) and options, with our own payload size.
         * Without an upstream OPT (local errors, answers cached from non-EDNS queries), an empty one.
         */
        void writeOpt(@Nullable DnsRecord upstreamOpt) {
            ByteBuf options = upstreamOpt instanceof DnsRawRecord ? ((DnsRawRecord) upstreamOpt).content()
                    : Unpooled.EMPTY_BUFFER;
            long ttl = upstreamOpt == null ? 0 : upstreamOpt.timeToLive();
            out.writeByte(0);
            out.writeShort(DnsRecordType.OPT.intValue());
            out.writeShort(EDNS_UDP_PAYLOAD_SIZE);
            out.writeInt((int) ttl);
            out.writeShort(options.readableBytes());
            out.writeBytes(options, options.readerIndex(), options.readableBytes());
        }

        void writeName(String name) {
            String remaining = name.endsWith(".") ? name : name + ".";
            while (!remaining.equals(".") && !remaining.isEmpty()) {
                Integer pointer = nameOffsets.get(remaining);
                if (pointer != null) {
                    out.writeShort(0xC000 | pointer);
                    return;
                }
                int offset = out.writerIndex() - messageStart;
                if (offset <= MAX_POINTER_OFFSET) {
                    nameOffsets.put(remaining, offset);
                }
                int dot = remaining.indexOf('.');
                byte[] label = remaining.substring(0, dot).getBytes(StandardCharsets.UTF_8);
                if (label.length == 0 || label.length > 63) {
                    throw new EncoderException("Invalid label in name " + name);
                }
                out.writeByte(label.length);
                out.writeBytes(label);
                remaining = remaining.substring(dot + 1);
            }
            out.writeByte(0);
        }
    }
}
