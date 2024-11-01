package com.flower.sockschain.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import javax.annotation.Nullable;

public class SocksChainClientPipelineManager {
    //TODO: support for other SOCKS versions
    public static void initSocks5Pipeline(Channel channel, String address, int port, SocksChainClient client,
                                          @Nullable SslContext sslCtx,
                                          @Nullable GenericFutureListener<Future<? super Channel>> sslHandshakeListener) {
        ChannelPipeline pipeline = channel.pipeline();

        if (sslCtx != null) {
            SslHandler sslHandler = sslCtx.newHandler(channel.alloc());
            pipeline.addLast(sslHandler);

            if (sslHandshakeListener != null) {
                sslHandler.handshakeFuture().addListener(sslHandshakeListener);
            }
        }

        pipeline.addLast(new Socks5InitialResponseDecoder());
        pipeline.addLast(Socks5ClientEncoder.DEFAULT);

        // and then business logic.
        pipeline.addLast(new SocksChainClientHandler(address, port, client));
    }

    //TODO: support for other SOCKS versions
    public static void cleanupSocks5Pipeline(ChannelPipeline pipeline) {
        pipeline.remove(SocksChainClientConnectHandler.class);//This can't stay, should be removed.
        pipeline.remove(Socks5CommandResponseDecoder.class);//This is useless, SUCCESS state just wastes cycles.
        pipeline.remove(Socks5InitialResponseDecoder.class);//This is useless, SUCCESS state just wastes cycles.
        pipeline.remove(Socks5ClientEncoder.class);//This doesn't make any sense. Protocol is done, no more messages to follow.
    }
}
