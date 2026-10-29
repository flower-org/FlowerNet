package com.flower.net.dns.dotclient.viasocks;

import com.flower.net.channelpool.AggressiveChannelPool;
import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.concurrent.*;

import javax.annotation.Nullable;
import java.net.InetAddress;

/**
 * Aggressively creates as many channels as it can until reaches `maxChannels`.
 * Never close the channels, evicting when they will be closed by the peer.
 * A channel joins the pool once the SOCKS5 tunnel and the TLS handshake through it are established.
 */
public class AggressiveViaSocksChannelPool extends AggressiveChannelPool {
    final String dotServerHost;
    final int dotServerPort;
    final DnsOverTlsClient dnsClient;

    public AggressiveViaSocksChannelPool(Bootstrap bootstrap,
                                         InetAddress connectAddress, int connectPort,
                                         String dotServerHost, int dotServerPort,
                                         DnsOverTlsClient dnsClient,
                                         int maxChannels,
                                         @Nullable String bindClientToIp) {
        super(bootstrap, connectAddress, connectPort, maxChannels,bindClientToIp);
        this.dotServerHost = dotServerHost;
        this.dotServerPort = dotServerPort;
        this.dnsClient = dnsClient;
    }

    @Override
    protected Future<?> onConnected(Channel channel) {
        Promise<Channel> tunnelReady = channel.eventLoop().newPromise();
        tunnelConnection(channel, dotServerHost, dotServerPort, dnsClient,
            sslHandshakeFuture -> {
                if (sslHandshakeFuture.isSuccess()) {
                    tunnelReady.trySuccess(channel);
                } else {
                    tunnelReady.tryFailure(sslHandshakeFuture.cause());
                }
            });
        return tunnelReady;
    }

    void tunnelConnection(Channel channel,
                          String dstAddr,
                          int dstPort,
                          DnsOverTlsClient dnsClient,
                          GenericFutureListener<Future<? super Channel>> sslHandshakeListener) {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new Socks5InitialResponseDecoder());
        pipeline.addLast(Socks5ClientEncoder.DEFAULT);
        pipeline.addLast(new Socks5ClientHandler(dstAddr, dstPort, dnsClient, sslHandshakeListener));

        channel.writeAndFlush(new DefaultSocks5InitialRequest(Socks5AuthMethod.NO_AUTH));
    }
}
