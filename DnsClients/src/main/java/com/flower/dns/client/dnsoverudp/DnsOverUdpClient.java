package com.flower.dns.client.dnsoverudp;

import com.flower.dns.client.DnsClient;
import com.flower.dns.client.utils.PromiseUtil;
import com.flower.utils.ServerUtil;
import com.flower.utils.evictlist.ConcurrentEvictListWithFixedTimeout;
import com.flower.utils.evictlist.EvictLinkedList;
import com.flower.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryEncoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseDecoder;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;

public class DnsOverUdpClient implements DnsClient {
    static final Logger LOGGER = LoggerFactory.getLogger(DnsOverUdpClient.class);

    public static final Long DEFAULT_CALLBACK_EXPIRATION_TIMEOUT_MILLIS = 2500L;

    private final EvictLinkedList<Pair<Integer, Promise<DefaultDnsResponse>>> callbacks;

    private final EventLoopGroup group;
    private final Bootstrap bootstrap;
    private final InetSocketAddress dnsServerAddress;
    private final Channel udpSocket;

    public DnsOverUdpClient(String dnsServerHost, int dnsServerPort) throws InterruptedException {
        this(ServerUtil.getByName(dnsServerHost), dnsServerPort);
    }

    public DnsOverUdpClient(InetAddress dnsServerAddress, int dnsServerPort) throws InterruptedException {
        this(dnsServerAddress, dnsServerPort, DEFAULT_CALLBACK_EXPIRATION_TIMEOUT_MILLIS);
    }

    public DnsOverUdpClient(InetAddress dnsServerAddress, int dnsServerPort, long callbackExpirationTimeoutMillis) throws InterruptedException {
        this.dnsServerAddress = new InetSocketAddress(dnsServerAddress, dnsServerPort);
        this.callbacks = new ConcurrentEvictListWithFixedTimeout<>(callbackExpirationTimeoutMillis);

        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
            .channel(NioDatagramChannel.class)
            .handler(new ChannelInitializer<DatagramChannel>() {
                @Override
                protected void initChannel(DatagramChannel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast(new DatagramDnsQueryEncoder())
                            .addLast(new DatagramDnsResponseDecoder())
                            .addLast(new SimpleChannelInboundHandler<DatagramDnsResponse>() {
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    LOGGER.error("DnsOverUdpClient.exceptionCaught", cause);
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsResponse msg) {
                                    try {
                                        handleQueryResp(msg);
                                    } finally {
                                        ctx.close();
                                    }
                                }
                            });
                }
            });
        this.udpSocket = bootstrap.bind(0).sync().channel();
    }

    @Override
    public void shutdown() {
        group.shutdownGracefully();
    }

    @Override
    public Promise<DefaultDnsResponse> query(String hostname, long timeoutMillis) {
        Promise<DefaultDnsResponse> promise = query(hostname);
        return PromiseUtil.withTimeout(bootstrap.config().group().next(), promise, timeoutMillis);
    }

    @Override
    public Promise<DefaultDnsResponse> query(String hostname) {
        DnsQuery query = new DatagramDnsQuery(null, dnsServerAddress, 1).setRecord(
                DnsSection.QUESTION,
                new DefaultDnsQuestion(hostname, DnsRecordType.A));
        Promise<DefaultDnsResponse> channelPromise = new DefaultPromise<>(bootstrap.config().group().next());
        callbacks.addElement(Pair.of(query.id(), channelPromise));
        udpSocket.writeAndFlush(query);
        return channelPromise;
    }

    protected void handleQueryResp(DefaultDnsResponse msg) {
        Promise<DefaultDnsResponse> promise = findCallback(msg.id());
        if (promise != null && !promise.isDone()) { promise.setSuccess(msg); }
    }

    protected @Nullable Promise<DefaultDnsResponse> findCallback(int queryId) {
        Iterator<EvictLinkedNode<Pair<Integer, Promise<DefaultDnsResponse>>>> iterator = callbacks.iterator();
        while (iterator.hasNext()) {
            EvictLinkedNode<Pair<Integer, Promise<DefaultDnsResponse>>> cursor = iterator.next();
            if (cursor.value().getKey() == queryId) {
                callbacks.markEvictable(cursor);
                return cursor.value().getValue();
            }
        }
        return null;
    }
}
