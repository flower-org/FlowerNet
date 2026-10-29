package com.flower.net.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decodes UDP queries. A packet that can't be parsed gets FORMERR if its header is readable (RFC 1035 4.1.1),
 * and is dropped otherwise. Packets with the QR bit set are responses and are never answered, to avoid loops.
 */
public class LenientDnsQueryDecoder extends DatagramDnsQueryDecoder {
    final static Logger LOGGER = LoggerFactory.getLogger(LenientDnsQueryDecoder.class);
    static final int HEADER_LENGTH = 12;

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
        ByteBuf content = packet.content();
        int start = content.readerIndex();
        try {
            super.decode(ctx, packet, out);
        } catch (Exception e) {
            if (content.writerIndex() - start < HEADER_LENGTH) {
                LOGGER.debug("Dropped a {}-byte packet from {}", content.writerIndex() - start, packet.sender());
                return;
            }
            int id = content.getUnsignedShort(start);
            int flags = content.getUnsignedShort(start + 2);
            if ((flags & 0x8000) != 0) {
                LOGGER.debug("{} | Dropped a response from {}", id, packet.sender());
                return;
            }
            LOGGER.info("{} | Malformed query from {}: {}", id, packet.sender(), e.toString());
            DefaultDnsResponse reply = AbstractDnsQueryHandler.errorReply(id, DnsOpCode.valueOf((flags >> 11) & 0xF),
                    (flags & 0x0100) != 0, DnsResponseCode.FORMERR);
            try {
                DnsMessageHandler.sendUdp(ctx, reply, null, packet.recipient(), packet.sender());
            } finally {
                reply.release();
            }
        }
    }
}
