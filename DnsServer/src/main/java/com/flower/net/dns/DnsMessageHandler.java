package com.flower.net.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsResponse;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Consumer;

/** Answers UDP queries; replies larger than the client can receive are truncated, so it retries over TCP. */
public class DnsMessageHandler extends AbstractDnsQueryHandler<DatagramDnsQuery> {
    /** Uses the default {@link ClientAccess}, shared by all handlers created this way. */
    public DnsMessageHandler(DnsOverTlsClient client) {
        this(client, ClientAccess.sharedDefault());
    }

    public DnsMessageHandler(DnsOverTlsClient client, ClientAccess access) {
        super(client, access);
    }

    @Override
    protected SocketAddress clientAddress(ChannelHandlerContext ctx, DatagramDnsQuery query) {
        return query.sender();
    }

    @Override
    protected Consumer<DefaultDnsResponse> replier(ChannelHandlerContext ctx, DatagramDnsQuery query) {
        InetSocketAddress queryRecipient = query.recipient();
        InetSocketAddress querySender = query.sender();
        DnsWireEncoder.ClientEdns edns = DnsWireEncoder.ClientEdns.of(query);
        return response -> sendUdp(ctx, response, edns, queryRecipient, querySender);
    }

    /** Doesn't take ownership of {@code response}. */
    static void sendUdp(ChannelHandlerContext ctx, DefaultDnsResponse response,
                        @Nullable DnsWireEncoder.ClientEdns edns,
                        InetSocketAddress queryRecipient, InetSocketAddress querySender) {
        ByteBuf buf = ctx.alloc().ioBuffer();
        try {
            if (DnsWireEncoder.encode(response, edns, DnsWireEncoder.maxUdpSize(edns), buf)) {
                LOGGER.debug("{} | Truncated to fit {} bytes", response.id(), DnsWireEncoder.maxUdpSize(edns));
            }
        } catch (RuntimeException e) {
            buf.release();
            throw e;
        }
        // The reply goes back from where the query was received to whoever sent it
        ctx.writeAndFlush(new DatagramPacket(buf, /* recipient= */ querySender, /* sender= */ queryRecipient))
                .addListener(f -> {
                    if (!f.isSuccess()) {
                        LOGGER.warn("{} | Failed to send the response to {}", response.id(), querySender, f.cause());
                    }
                });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("DnsMessageHandler.exceptionCaught", cause);
    }
}
