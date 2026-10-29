package com.flower.net.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Answers queries over TCP (RFC 7766): several queries may be in flight on one connection, and replies are sent as
 * they arrive, in any order. Idle connections are closed. A malformed message closes the connection, since the
 * stream can't be resynchronized.
 */
public class DnsTcpMessageHandler extends AbstractDnsQueryHandler<DnsQuery> {
    /** RFC 7766 6.2.3 suggests an idle timeout in the order of seconds. */
    public static final long IDLE_TIMEOUT_MILLIS = 10_000L;

    public DnsTcpMessageHandler(DnsOverTlsClient client, ClientAccess access) {
        super(client, access);
    }

    /** Uses the default {@link ClientAccess}, shared by all handlers added this way. */
    public static void addHandlers(ChannelPipeline pipeline, DnsOverTlsClient client) {
        addHandlers(pipeline, client, ClientAccess.sharedDefault());
    }

    /** {@code access} must be shared by all connections, so rate limits apply per client rather than per connection. */
    public static void addHandlers(ChannelPipeline pipeline, DnsOverTlsClient client, ClientAccess access) {
        pipeline.addLast(new IdleStateHandler(0, 0, IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                .addLast(new TcpDnsQueryFrameDecoder())
                .addLast(new DnsTcpMessageHandler(client, access));
    }

    @Override
    protected SocketAddress clientAddress(ChannelHandlerContext ctx, DnsQuery query) {
        return ctx.channel().remoteAddress();
    }

    @Override
    protected Consumer<DefaultDnsResponse> replier(ChannelHandlerContext ctx, DnsQuery query) {
        DnsWireEncoder.ClientEdns edns = DnsWireEncoder.ClientEdns.of(query);
        return response -> {
            ByteBuf buf = ctx.alloc().ioBuffer();
            try {
                buf.writeShort(0);
                DnsWireEncoder.encode(response, edns, DnsWireEncoder.MAX_TCP_MESSAGE_SIZE, buf);
                buf.setShort(0, buf.readableBytes() - 2);
            } catch (RuntimeException e) {
                buf.release();
                throw e;
            }
            ctx.writeAndFlush(buf).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        };
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            close(ctx);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.info("Closing TCP connection from {}: {}", ctx.channel().remoteAddress(), cause.toString());
        close(ctx);
    }

    /** Closing can't usefully fail. */
    @SuppressWarnings("FutureReturnValueIgnored")
    private static void close(ChannelHandlerContext ctx) {
        ctx.close();
    }
}
