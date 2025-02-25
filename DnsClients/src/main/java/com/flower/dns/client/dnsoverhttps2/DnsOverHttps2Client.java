package com.flower.dns.client.dnsoverhttps2;

import com.flower.dns.DnsClient;
import com.flower.dns.utils.DnsUtils;
import com.flower.utils.PromiseUtil;
import com.flower.utils.evictlist.ConcurrentEvictListWithFixedTimeout;
import com.flower.utils.evictlist.EvictLinkedList;
import com.flower.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static com.flower.dns.client.dnsoverhttps2.Http2ClientInitializer.SETTINGS_FUTURE_KEY;
import static com.flower.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;
import static io.netty.handler.codec.http.HttpMethod.GET;

public class DnsOverHttps2Client implements DnsClient {
    static final Logger LOGGER = LoggerFactory.getLogger(DnsOverHttps2Client.class);

    public static final Long DEFAULT_CALLBACK_EXPIRATION_TIMEOUT_MILLIS = 2500L;
    public static final Long DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS = 1000L;

    // TODO: matching by hostname is suboptimal. Implement more robust matching
    //  (streamId, when Netty will fix https://github.com/netty/netty/issues/14856)
    private final EvictLinkedList<Pair<String, Promise<DnsResponse>>> callbacks;

    private final EventLoopGroup group;
    private final Bootstrap bootstrap;
    private final InetSocketAddress dnsServerAddress;
    private final String dnsServerPathPrefix;

    public DnsOverHttps2Client(InetAddress dnsServerAddress, int dnsServerPort, String dnsServerPathPrefix,
                               TrustManagerFactory trustManager) throws SSLException {
        this(dnsServerAddress, dnsServerPort, dnsServerPathPrefix, DEFAULT_CALLBACK_EXPIRATION_TIMEOUT_MILLIS,
                DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS, trustManager);
    }

    public DnsOverHttps2Client(InetAddress dnsServerAddress, int dnsServerPort, String dnsServerPathPrefix,
                               long callbackExpirationTimeoutMillis, long sslHandshakeTimeoutMillis,
                               TrustManagerFactory trustManager) throws SSLException {
        this.dnsServerAddress = new InetSocketAddress(dnsServerAddress, dnsServerPort);
        this.dnsServerPathPrefix = dnsServerPathPrefix;
        this.callbacks = new ConcurrentEvictListWithFixedTimeout<>(callbackExpirationTimeoutMillis);

        // Configure SSL.
        SslContext sslCtx = SslContextBuilder
                .forClient()
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .trustManager(trustManager)
                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();

        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE, sslHandshakeTimeoutMillis, dnsServerAddress, dnsServerPort,
                    () -> new SimpleChannelInboundHandler<>() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            LOGGER.error("DnsOverUdpClient.exceptionCaught", cause);
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
                            String responseContent = response.content().toString(StandardCharsets.UTF_8);

                            Pair<String, List<InetAddress>> resp = DnsUtils.extractIpAddresses(responseContent);
                            DnsResponse msg = DnsUtils.dnsResponseFromAddresses(resp.getKey(), resp.getValue());
                            handleQueryResp(resp.getKey(), msg);
                        }
                    }));
    }

    @Override
    public void shutdown() {
        group.shutdownGracefully();
    }

    @Override
    public Promise<DnsResponse> query(String hostname, long promiseTimeoutMs) {
        Promise<DnsResponse> promise = query(hostname);
        return PromiseUtil.withTimeout(bootstrap.config().group().next(), promise, promiseTimeoutMs);
    }

    @Override
    public Promise<DnsResponse> query(String hostname) {
        Promise<DnsResponse> channelPromise = new DefaultPromise<>(bootstrap.config().group().next());
        callbacks.addElement(Pair.of(hostname, channelPromise));

        String dnsServerIpAddressStr = dnsServerAddress.getAddress().getHostAddress();

        ChannelFuture cf = bootstrap.connect(dnsServerAddress.getAddress(), dnsServerAddress.getPort());
        cf.addListener(future -> {
            if (future.isSuccess()) {
                Channel channel = cf.channel();

                // Wait for the HTTP/2 upgrade to occur.
                // This synchronization is actually important, requests won't go through until it's done
                Promise<Http2Settings> settingsFuturePromise = channel.attr(SETTINGS_FUTURE_KEY).get();
                PromiseUtil.withTimeout(channel.eventLoop(), settingsFuturePromise, 5000)
                    .addListener(future2 -> {
                        if (future2.isSuccess()) {
                            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, GET,
                                    dnsServerPathPrefix + hostname + "&type=A");
                            request.headers().set(HttpHeaderNames.HOST, dnsServerIpAddressStr);
                            request.headers().set(HttpHeaderNames.ACCEPT, "application/dns-json");
                            request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTPS.name());
                            request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/dns-json");
                            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());

                            channel.writeAndFlush(request);
                        } else {
                            channelPromise.setFailure(future2.cause());
                        }
                    });
            } else {
                channelPromise.setFailure(future.cause());
            }
        });

        return channelPromise;
    }

    protected void handleQueryResp(String hostname, DnsResponse msg) {
        Promise<DnsResponse> promise = findCallback(hostname);
        if (promise != null && !promise.isDone()) { promise.setSuccess(msg); }
    }

    protected @Nullable Promise<DnsResponse> findCallback(String hostname) {
        Iterator<EvictLinkedNode<Pair<String, Promise<DnsResponse>>>> iterator = callbacks.iterator();
        while (iterator.hasNext()) {
            EvictLinkedNode<Pair<String, Promise<DnsResponse>>> cursor = iterator.next();
            String key = cursor.value().getKey();
            if (key.equals(hostname) || (key+".").equals(hostname)) {
                callbacks.markEvictable(cursor);
                return cursor.value().getValue();
            }
        }
        return null;
    }
}
