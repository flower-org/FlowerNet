package com.flower.net.dns.dotclient.viasocks;

import com.flower.net.channelpool.AggressiveChannelPool;
import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

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
    @Nullable final Socks5Credentials credentials;
    final DnsOverTlsClient dnsClient;

    public AggressiveViaSocksChannelPool(Bootstrap bootstrap,
                                         InetAddress connectAddress, int connectPort,
                                         String dotServerHost, int dotServerPort,
                                         DnsOverTlsClient dnsClient,
                                         int maxChannels,
                                         @Nullable String bindClientToIp) {
        this(bootstrap, connectAddress, connectPort, null, dotServerHost, dotServerPort, dnsClient, maxChannels,
                bindClientToIp);
    }

    public AggressiveViaSocksChannelPool(Bootstrap bootstrap,
                                         InetAddress connectAddress, int connectPort,
                                         @Nullable Socks5Credentials credentials,
                                         String dotServerHost, int dotServerPort,
                                         DnsOverTlsClient dnsClient,
                                         int maxChannels,
                                         @Nullable String bindClientToIp) {
        super(bootstrap, connectAddress, connectPort, maxChannels, bindClientToIp);
        this.dotServerHost = dotServerHost;
        this.dotServerPort = dotServerPort;
        this.credentials = credentials;
        this.dnsClient = dnsClient;
    }

    @Override
    protected Future<?> onConnected(Channel channel) {
        Promise<Channel> tunnelReady = channel.eventLoop().newPromise();
        Socks5TunnelHandler.install(channel, dotServerHost, dotServerPort, credentials, dnsClient, tunnelReady);
        return tunnelReady;
    }
}
