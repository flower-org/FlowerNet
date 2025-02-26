package com.flower.net.dns.client.dnsoverhttps1;

import com.flower.net.dns.DnsClient;
import com.flower.net.dns.utils.DnsUtils;
import com.flower.net.utils.PromiseUtil;
import com.flower.net.utils.evictlist.ConcurrentEvictListWithFixedTimeout;
import com.flower.net.utils.evictlist.EvictLinkedList;
import com.flower.net.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
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

import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;

public class DnsOverHttps1Client implements DnsClient {
    static final Logger LOGGER = LoggerFactory.getLogger(DnsOverHttps1Client.class);

    public static final Long DEFAULT_CALLBACK_EXPIRATION_TIMEOUT_MILLIS = 2500L;
    public static final Long DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS = 1000L;

    // TODO: matching by hostname is suboptimal. Implement more robust matching
    //  (mb. embed callback in channel using ChannelAttributes)
    private final EvictLinkedList<Pair<String, Promise<DnsResponse>>> callbacks;

    private final EventLoopGroup group;
    private final Bootstrap bootstrap;
    private final InetSocketAddress dnsServerAddress;
    private final String dnsServerPathPrefix;

    public DnsOverHttps1Client(InetAddress dnsServerAddress, int dnsServerPort, String dnsServerPathPrefix, TrustManagerFactory trustManager) throws SSLException {
        this(dnsServerAddress, dnsServerPort, dnsServerPathPrefix, DEFAULT_CALLBACK_EXPIRATION_TIMEOUT_MILLIS,
                DEFAULT_SSL_HANDSHAKE_TIMEOUT_MILLIS, trustManager);
    }

    public DnsOverHttps1Client(InetAddress dnsServerAddress, int dnsServerPort, String dnsServerPathPrefix,
                               long callbackExpirationTimeoutMillis, long sslHandshakeTimeoutMillis,
                               TrustManagerFactory trustManager) throws SSLException {
        this.dnsServerAddress = new InetSocketAddress(dnsServerAddress, dnsServerPort);
        this.dnsServerPathPrefix = dnsServerPathPrefix;
        this.callbacks = new ConcurrentEvictListWithFixedTimeout<>(callbackExpirationTimeoutMillis);

        SslContext sslCtx = SslContextBuilder
                .forClient()
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .trustManager(trustManager)
                .build();

        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
                    sslHandler.setHandshakeTimeoutMillis(sslHandshakeTimeoutMillis);
                    sslHandler.handshakeFuture().addListener(
                            future -> {
                                if (!future.isSuccess()) {
                                    LOGGER.error("DnsOverTlsClient - TLS Handshake Failed", future.cause());
                                }
                            }
                    );

                    ch.pipeline().addLast(sslHandler);
                    ch.pipeline().addLast(new HttpClientCodec());
                    ch.pipeline().addLast(new HttpObjectAggregator(65536));
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            LOGGER.error("DnsOverUdpClient.exceptionCaught", cause);
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
                            String responseContent = response.content().toString(StandardCharsets.UTF_8);
                            System.out.println("Response: " + responseContent);
                            System.out.println("IpAddresses: " + DnsUtils.extractIpAddresses(responseContent));
                            ctx.close();

                            Pair<String, List<InetAddress>> resp = DnsUtils.extractIpAddresses(responseContent);
                            DnsResponse msg = DnsUtils.dnsResponseFromAddresses(resp.getKey(), resp.getValue());
                            handleQueryResp(resp.getKey(), msg);
                        }
                    });
                }
            });
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
        String dnsServerIpAddressStr = dnsServerAddress.getAddress().getHostAddress();

        ChannelFuture cf = bootstrap.connect(dnsServerAddress.getAddress(), dnsServerAddress.getPort());
        cf.addListener(future -> {
            Channel channel = cf.channel();
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                    dnsServerPathPrefix + hostname + "&type=A");

            request.headers().set(HttpHeaderNames.HOST, dnsServerIpAddressStr);
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/dns-json");
            request.headers().set(HttpHeaderNames.ACCEPT, "application/dns-json");
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());

            channel.writeAndFlush(request);
        });

        Promise<DnsResponse> channelPromise = new DefaultPromise<>(bootstrap.config().group().next());
        callbacks.addElement(Pair.of(hostname, channelPromise));

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
