package com.flower.net.dns.dotclient.viasocks;

import com.flower.net.channelpool.AggressiveChannelPool;
import com.flower.net.dns.dotclient.DnsOverTlsClient;
import com.flower.net.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.util.concurrent.*;

import java.net.InetAddress;

/**
 * Aggressively creates as many channels as it can until reaches `maxChannels`.
 * Never close the channels, evicting when they will be closed by the peer.
 */
public class AggressiveViaSocksChannelPool extends AggressiveChannelPool {
    final String dotServerHost;
    final int dotServerPort;
    final DnsOverTlsClient dnsClient;

    public AggressiveViaSocksChannelPool(Bootstrap bootstrap,
                                         InetAddress connectAddress, int connectPort,
                                         String dotServerHost, int dotServerPort,
                                         DnsOverTlsClient dnsClient,
                                         int maxChannels) {
        super(bootstrap, connectAddress, connectPort, maxChannels);
        this.dotServerHost = dotServerHost;
        this.dotServerPort = dotServerPort;
        this.dnsClient = dnsClient;
    }

    @Override
    public Promise<EvictLinkedNode<Channel>> getChannel() {
        while (true) {
            if (giveChannelPromise()) {
                // 1. Aggressive approach - if we can create more channels, create more
                Promise<EvictLinkedNode<Channel>> outgoingChannelPromise = new DefaultPromise<>(bootstrap.config().group().next());
                bootstrap.connect(connectAddress, connectPort).addListener((ChannelFutureListener) connectFuture -> {
                    if (connectFuture.isSuccess()) {
                        // Connection established, send initial request
                        tunnelConnection(connectFuture.channel(), dotServerHost, dotServerPort, dnsClient,
                            sslHandshakeFuture -> {
                                if (sslHandshakeFuture.isSuccess()) {
                                    outgoingChannelPromise.setSuccess(addChannel(connectFuture.channel()));
                                } else {
                                    // Close the connection if the connection attempt has failed.
                                    failOnChannelPromise();
                                    outgoingChannelPromise.setFailure(sslHandshakeFuture.cause());
                                }
                            });
                    } else {
                        // Close the connection if the connection attempt has failed.
                        failOnChannelPromise();
                        outgoingChannelPromise.setFailure(connectFuture.cause());
                    }
                });
                return outgoingChannelPromise;
            } else {
                // 2. If max channels was reached, reuse
                EvictLinkedNode<Channel> nonEvictedNode = picker.getNonEvictedNode();
                if (nonEvictedNode != null) {
                    Promise<EvictLinkedNode<Channel>> channelPromise = new DefaultPromise<>(bootstrap.config().group().next());
                    channelPromise.setSuccess(nonEvictedNode);
                    return channelPromise;
                }
            }
        }
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
