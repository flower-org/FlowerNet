package com.flower.net.dns.dotclient;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.dns.DnsSection;

/**
 * Encodes a query for DNS over TCP/TLS (RFC 7766 length prefix), like Netty's {@code TcpDnsQueryEncoder},
 * but writes the whole header. Netty's encoder drops the Z bits, which carry the client's AD and CD flags
 * (RFC 4035 / RFC 6840), and puts the opcode at the wrong offset.
 */
public class TcpDnsQueryFullHeaderEncoder extends MessageToByteEncoder<DnsQuery> {
    private final DnsRecordEncoder recordEncoder = DnsRecordEncoder.DEFAULT;

    static int headerFlags(DnsQuery query) {
        int flags = (query.opCode().byteValue() & 0xF) << 11;
        if (query.isRecursionDesired()) {
            flags |= 1 << 8;
        }
        flags |= (query.z() & 0x7) << 4;
        return flags;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsQuery query, ByteBuf out) throws Exception {
        int start = out.writerIndex();
        out.writeShort(0);
        out.writeShort(query.id());
        out.writeShort(headerFlags(query));
        for (DnsSection section : DnsSection.values()) {
            out.writeShort(query.count(section));
        }
        for (DnsSection section : DnsSection.values()) {
            for (int i = 0, count = query.count(section); i < count; i++) {
                if (section == DnsSection.QUESTION) {
                    recordEncoder.encodeQuestion((DnsQuestion) query.recordAt(section, i), out);
                } else {
                    recordEncoder.encodeRecord(query.recordAt(section, i), out);
                }
            }
        }
        out.setShort(start, out.writerIndex() - start - 2);
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, DnsQuery msg, boolean preferDirect) {
        return preferDirect ? ctx.alloc().ioBuffer(512) : ctx.alloc().heapBuffer(512);
    }
}
