package com.flower.sockschain.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class SocksChainClientPipelineManager {
    //TODO: support for other SOCKS versions
    public static void initSocks5Pipeline(Channel channel, String address, int port, SocksChainClient client,
                                          @Nullable SslContext sslCtx,
                                          @Nullable GenericFutureListener<Future<? super Channel>> sslHandshakeListener) {
        ChannelPipeline pipeline = channel.pipeline();

        SslHandler sslHandler = null;
        if (sslCtx != null) {
            sslHandler = sslCtx.newHandler(channel.alloc());
            pipeline.addLast(sslHandler);
        }

        pipeline.addLast(new Socks5InitialResponseDecoder());
        pipeline.addLast(Socks5ClientEncoder.DEFAULT);

        // and then business logic.
        pipeline.addLast(new SocksChainClientHandler(address, port, client));

        if (sslCtx != null) {
            if (sslHandshakeListener != null) {
                checkNotNull(sslHandler).handshakeFuture().addListener(sslHandshakeListener);
            }
        } else {
            if (sslHandshakeListener != null) {
                try {
                    Future<? super Channel> channelFuture = ImmediateEventExecutor.INSTANCE.newSucceededFuture(channel);
                    sslHandshakeListener.operationComplete(channelFuture);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    //TODO: support for other SOCKS versions
    public static void cleanupSocks5Pipeline(ChannelPipeline pipeline) {
        pipeline.remove(SocksChainClientConnectHandler.class);//This can't stay, should be removed.
        pipeline.remove(Socks5CommandResponseDecoder.class);//This is useless, SUCCESS state just wastes cycles.
        pipeline.remove(Socks5InitialResponseDecoder.class);//This is useless, SUCCESS state just wastes cycles.
        pipeline.remove(Socks5ClientEncoder.class);//This doesn't make any sense. Protocol is done, no more messages to follow.
    }
}
