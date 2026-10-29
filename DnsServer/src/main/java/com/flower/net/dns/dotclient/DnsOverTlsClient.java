package com.flower.net.dns.dotclient;

import com.flower.net.channelpool.AggressiveChannelPool;
import com.flower.net.channelpool.ChannelPool;
import com.flower.net.dns.dotclient.viasocks.AggressiveViaSocksChannelPool;
import com.flower.net.utils.EmptyPipelineChannelInitializer;
import com.flower.net.utils.ServerUtil;
import com.flower.net.utils.evictlist.EvictLinkedNode;
import io.netty.bootstrap.Bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.SimpleChannelInboundHandler;

import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsQueryEncoder;
import io.netty.handler.codec.dns.TcpDnsResponseDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;
import static com.google.common.base.Preconditions.checkNotNull;

public class DnsOverTlsClient {
    static final Logger LOGGER = LoggerFactory.getLogger(DnsOverTlsClient.class);

    static final Long SSL_HANDSHAKE_TIMEOUT_MILLIS = 1000L;
    static final Long CALLBACK_EXPIRATION_TIMEOUT = 2500L;
    static final int MAX_PARALLEL_CHANNELS = 3;
    static final int MAX_QUERY_RETRY_COUNT = 2;
    /** Close idle connections before the server does (1.1.1.1 closes after 10s), so we don't write to a closing one. */
    static final long CHANNEL_IDLE_TIMEOUT_MILLIS = 7000L;

    @Nullable private final InetAddress socksServerAddress;
    @Nullable private final Integer socksServerPort;
    private final InetAddress dnsServerAddress;
    private final int dnsServerPort;
    @Nullable protected final String bindClientToIp;

    private final EventLoopGroup group;
    private final Bootstrap bootstrap;

    private final ChannelPool channelPool;
    private final UpstreamQueryRegistry pendingQueries;

    private final boolean useCache;
    @Nullable private final AnswerCache cache;

    private final SslContext sslCtx;

    public DnsOverTlsClient(String socksServerHost, int socksServerPort,
                            String dnsServerHost, int dnsServerPort,
                            TrustManagerFactory trustManager, boolean useCache, @Nullable String bindClientToIp) throws SSLException {
        this.socksServerAddress = ServerUtil.getByName(socksServerHost);
        this.socksServerPort = socksServerPort;

        this.dnsServerAddress = ServerUtil.getByName(dnsServerHost);
        this.dnsServerPort = dnsServerPort;
        this.bindClientToIp = bindClientToIp;


        // Configure SSL.
        this.sslCtx = SslContextBuilder
                .forClient()
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .trustManager(trustManager)
                .build();

        group = new NioEventLoopGroup();
        this.pendingQueries = new UpstreamQueryRegistry(group, CALLBACK_EXPIRATION_TIMEOUT);
        bootstrap = new Bootstrap();

        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new EmptyPipelineChannelInitializer());

        this.channelPool = new AggressiveViaSocksChannelPool(bootstrap, socksServerAddress, socksServerPort,
                dnsServerHost, dnsServerPort,
                this,
                MAX_PARALLEL_CHANNELS,
                bindClientToIp);

        this.useCache = useCache;
        if (useCache) { cache = new AnswerCache(); }
                else  { cache = null; }
    }

    public DnsOverTlsClient(String dnsServerHost, int dnsServerPort, TrustManagerFactory trustManager, boolean useCache,
                            @Nullable String bindClientToIp) throws SSLException {
        this.bindClientToIp = bindClientToIp;
        this.socksServerAddress = null;
        this.socksServerPort = null;
        this.dnsServerAddress = ServerUtil.getByName(dnsServerHost);
        this.dnsServerPort = dnsServerPort;

        // Configure SSL.
        this.sslCtx = SslContextBuilder
                .forClient()
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .trustManager(trustManager)
                .build();

        group = new NioEventLoopGroup();
        this.pendingQueries = new UpstreamQueryRegistry(group, CALLBACK_EXPIRATION_TIMEOUT);
        bootstrap = new Bootstrap();
        bootstrap.group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    SslHandler sslHandler = sslCtx.newHandler(ch.alloc());
                    sslHandler.setHandshakeTimeoutMillis(SSL_HANDSHAKE_TIMEOUT_MILLIS);
                    sslHandler.handshakeFuture().addListener(
                        future -> {
                            if (!future.isSuccess()) {
                                LOGGER.error("DnsOverTlsClient - TLS Handshake Failed", future.cause());
                            }
                        }
                    );

                    p.addLast(sslHandler);
                    addDnsHandlers(p);
                }
            });
        this.channelPool = new AggressiveChannelPool(bootstrap, dnsServerAddress, dnsServerPort, MAX_PARALLEL_CHANNELS, bindClientToIp);

        this.useCache = useCache;
        if (useCache) { cache = new AnswerCache(); }
                else  { cache = null; }
    }

    /**
     * Sends the query upstream and calls the consumer with the response.
     * The caller keeps ownership of {@code query}; it's not retained or released here.
     * The response passed to the consumer carries the original query's ID.
     */
    public void query(DnsQuery query, Consumer<DefaultDnsResponse> dnsResponseConsumer) {
        DnsQuestion qQuestion = query.recordAt(DnsSection.QUESTION, 0);
        if (useCache) {
            DefaultDnsResponse response = checkNotNull(cache).getResponseIfPresent(qQuestion);
            if (response != null) {
                LOGGER.info("{} | Cache hit!", query.id());
                dnsResponseConsumer.accept(response);
                return;
            }
        }

        UpstreamQueryRegistry.PendingQuery pendingQuery;
        try {
            pendingQuery = pendingQueries.register(query.id(), qQuestion, dnsResponseConsumer);
        } catch (IllegalStateException e) {
            LOGGER.warn("{} | Can't send query upstream: {}", query.id(), e.getMessage());
            return;
        }
        LOGGER.debug("{} | Sending upstream as {}", query.id(), pendingQuery.upstreamId());
        sendUpstream(copyWithId(query, pendingQuery.upstreamId()), pendingQuery, 0);
    }

    /**
     * Copies the query's header and records under a new ID.
     * Records are duplicated and retained, so the copy is independent of the original's reference count.
     */
    public static DnsQuery copyWithId(DnsQuery query, int id) {
        DnsQuery copy = new DefaultDnsQuery(id, query.opCode())
                .setRecursionDesired(query.isRecursionDesired())
                .setZ(query.z());
        for (DnsSection section : DnsSection.values()) {
            for (int i = 0, count = query.count(section); i < count; i++) {
                DnsRecord record = query.recordAt(section, i);
                copy.addRecord(section, record instanceof DnsRawRecord
                        ? ((DnsRawRecord) record).retainedDuplicate()
                        : record);
            }
        }
        return copy;
    }

    /** Takes ownership of {@code upstreamQuery} and releases it once sent or abandoned. */
    protected void sendUpstream(DnsQuery upstreamQuery, UpstreamQueryRegistry.PendingQuery pendingQuery, int retry) {
        if (retry > MAX_QUERY_RETRY_COUNT) {
            LOGGER.info("{} | DoT Server request failed after retry# {}", pendingQuery.originalId(), retry);
            pendingQueries.cancel(pendingQuery);
            upstreamQuery.release();
            return;
        }

        channelPool.getChannel().addListener(channelFuture -> {
            if (channelFuture.isSuccess()) {
                @SuppressWarnings("unchecked")
                EvictLinkedNode<Channel> channelRecord = (EvictLinkedNode<Channel>)channelFuture.get();
                final Channel ch = channelRecord.value();
                // The encoder releases the message it writes; keep our own reference for retries.
                upstreamQuery.retain();
                ch.writeAndFlush(upstreamQuery)
                    .addListener(
                        writeFuture -> {
                            if (!writeFuture.isSuccess()) {
                                channelPool.returnFailedChannel(channelRecord);
                                sendUpstream(upstreamQuery, pendingQuery, retry + 1);
                            } else {
                                channelPool.returnChannel(channelRecord);
                                upstreamQuery.release();
                            }
                        }
                    );
            } else {
                LOGGER.info("{} | DoT Server request failed to get channel", pendingQuery.originalId());
                pendingQueries.cancel(pendingQuery);
                upstreamQuery.release();
            }
        });
    }

    /**
     * Adds DNS-over-TCP framing and response handling after the TLS handler.
     * Connections are kept open and shared by many queries (RFC 7766): responses are matched by ID,
     * idle connections are closed, and a connection is closed on error so the pool evicts it.
     */
    public void addDnsHandlers(ChannelPipeline p) {
        p.addLast(new IdleStateHandler(0, 0, CHANNEL_IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            .addLast(new TcpDnsQueryEncoder())
            .addLast(new TcpDnsResponseDecoder())
            .addLast(new SimpleChannelInboundHandler<DefaultDnsResponse>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, DefaultDnsResponse msg) {
                    handleQueryResp(msg);
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                    if (evt instanceof IdleStateEvent) {
                        LOGGER.debug("Closing idle DoT connection {}", ctx.channel());
                        ctx.close();
                    } else {
                        super.userEventTriggered(ctx, evt);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    LOGGER.error("DoT connection error, closing {}", ctx.channel(), cause);
                    ctx.close();
                }
            });
    }

    public void shutdown() {
        group.shutdownGracefully();
    }

    public SslContext getSslCtx() {
        return sslCtx;
    }

    UpstreamQueryRegistry pendingQueries() {
        return pendingQueries;
    }

    public void handleQueryResp(DefaultDnsResponse msg) {
        UpstreamQueryRegistry.PendingQuery pendingQuery = pendingQueries.complete(msg);
        if (pendingQuery != null) {
            msg.setId(pendingQuery.originalId());
            pendingQuery.callback().accept(msg);
            if (useCache) {
                checkNotNull(cache).putResponse(pendingQuery.question(), msg);
            }
        }
    }
}
