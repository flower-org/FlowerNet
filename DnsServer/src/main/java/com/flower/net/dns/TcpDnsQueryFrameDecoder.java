package com.flower.net.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes length-prefixed DNS queries from a TCP stream into {@link io.netty.handler.codec.dns.DnsQuery} objects.
 * Replaces Netty's {@code TcpDnsQueryDecoder}, which never releases its frames (4.1.119).
 */
public class TcpDnsQueryFrameDecoder extends LengthFieldBasedFrameDecoder {
    /** Exposes the datagram decoder's message parsing. */
    private static final class MessageDecoder extends DatagramDnsQueryDecoder {
        void decodeMessage(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
            decode(ctx, packet, out);
        }
    }

    private static final InetSocketAddress UNKNOWN_ADDRESS = InetSocketAddress.createUnresolved("unknown", 0);

    private final MessageDecoder messageDecoder = new MessageDecoder();

    public TcpDnsQueryFrameDecoder() {
        super(DnsWireEncoder.MAX_TCP_MESSAGE_SIZE, 0, 2, 0, 2);
    }

    @Override
    @Nullable
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null;
        }
        try {
            List<Object> out = new ArrayList<>(1);
            // Records hold retained slices of the frame, so it's freed once they're all released
            messageDecoder.decodeMessage(ctx, new DatagramPacket(frame, inet(ctx.channel().localAddress()),
                    inet(ctx.channel().remoteAddress())), out);
            return out.get(0);
        } finally {
            frame.release();
        }
    }

    private static InetSocketAddress inet(@Nullable SocketAddress address) {
        return address instanceof InetSocketAddress ? (InetSocketAddress) address : UNKNOWN_ADDRESS;
    }
}
