package com.flower.net.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import com.flower.net.dns.dotclient.ResolvedHosts;
import com.flower.net.dns.dotclient.viasocks.Socks5Credentials;
import com.flower.crypt.PkiUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

public class DnsServer implements AutoCloseable {
    final static Logger LOGGER = LoggerFactory.getLogger(DnsServer.class);

    private static String SOCKS_SERVER_ADDRESS = "";
    private static int SOCKS_SERVER_PORT = -1;
    private static final String OTHER_DNS_TLS_SERVER_ADDRESS = "1.1.1.1";
    private static final int OTHER_DNS_TLS_SERVER_PORT = 853;
    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("oneone_cert.pem");

    private static final int DNS_SERVER_PORT = 5300;
    private static final boolean DONT_USE_CACHE = false;

    public static void main(String[] args) throws SSLException, InterruptedException {
        // TODO: config / command line parameters
        int port = DNS_SERVER_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        boolean useCache = DONT_USE_CACHE;
        if (args.length > 1) {
            useCache = Boolean.parseBoolean(args[1]);
        }

        final DnsOverTlsClient client;
        if (args.length > 2) {
            SOCKS_SERVER_ADDRESS = args[2];
            SOCKS_SERVER_PORT = Integer.parseInt(args[3]);

            // Credentials come from the environment so the password doesn't show up in the process list
            String socksUsername = System.getenv("SOCKS_USERNAME");
            String socksPassword = System.getenv("SOCKS_PASSWORD");
            Socks5Credentials socksCredentials = socksUsername == null || socksUsername.isEmpty() ? null
                    : new Socks5Credentials(socksUsername, socksPassword == null ? "" : socksPassword);

            client = new DnsOverTlsClient(SOCKS_SERVER_ADDRESS,
                                          SOCKS_SERVER_PORT,
                                          socksCredentials,
                                          OTHER_DNS_TLS_SERVER_ADDRESS,
                                          OTHER_DNS_TLS_SERVER_PORT,
                                          TRUST_MANAGER,
                                          useCache,
                                          null);
        } else {
            client = new DnsOverTlsClient(OTHER_DNS_TLS_SERVER_ADDRESS,
                                          OTHER_DNS_TLS_SERVER_PORT,
                                          TRUST_MANAGER,
                                          useCache,
                                          null);
        }

        DnsServer server = start(port, client);
        try {
            server.udpChannel.closeFuture().sync();
        } finally {
            server.close();
        }
    }

    private final DnsOverTlsClient client;
    private final NioEventLoopGroup group;
    private final Channel udpChannel;
    private final Channel tcpChannel;

    private DnsServer(DnsOverTlsClient client, NioEventLoopGroup group, Channel udpChannel, Channel tcpChannel) {
        this.client = client;
        this.group = group;
        this.udpChannel = udpChannel;
        this.tcpChannel = tcpChannel;
    }

    /** By default the server only listens on, and answers, the local machine. */
    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";

    /**
     * Starts a server on {@value #DEFAULT_BIND_ADDRESS}:{@code port} (UDP and TCP) that forwards to 1.1.1.1 over TLS,
     * with {@link ClientAccess#defaults()}. Returns once it's listening.
     */
    public static DnsServer start(int port, boolean useCache) throws SSLException, InterruptedException {
        return start(port, new DnsOverTlsClient(OTHER_DNS_TLS_SERVER_ADDRESS, OTHER_DNS_TLS_SERVER_PORT,
                TRUST_MANAGER, useCache, null));
    }

    /** Like {@link #start(String, int, DnsOverTlsClient, ClientAccess)} on {@value #DEFAULT_BIND_ADDRESS} with the default access. */
    public static DnsServer start(int port, DnsOverTlsClient client) throws InterruptedException {
        return start(DEFAULT_BIND_ADDRESS, port, client, ClientAccess.defaults());
    }

    /**
     * Starts a server on {@code bindAddress}:{@code port} (UDP and TCP) that forwards through {@code client}, answering
     * only clients {@code access} allows. Use {@code "0.0.0.0"} or {@code "::"} to listen on all interfaces.
     * Returns once it's listening. The server takes ownership of the client and shuts it down on {@link #close()}.
     */
    public static DnsServer start(String bindAddress, int port, DnsOverTlsClient client, ClientAccess access)
            throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                            nioDatagramChannel.pipeline().addLast(new LenientDnsQueryDecoder());
                            nioDatagramChannel.pipeline().addLast(new DnsMessageHandler(client, access));
                        }
                    });

            // Clients retry over TCP when a UDP reply is truncated (RFC 7766)
            ServerBootstrap tcpBootstrap = new ServerBootstrap();
            tcpBootstrap.group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            DnsTcpMessageHandler.addHandlers(socketChannel.pipeline(), client, access);
                        }
                    });

            Channel udpChannel = bootstrap.bind(bindAddress, port).sync().channel();
            Channel tcpChannel = tcpBootstrap.bind(bindAddress, port).sync().channel();
            LOGGER.info("DNS Server Started at {} port {} (UDP and TCP)", bindAddress, port);
            return new DnsServer(client, group, udpChannel, tcpChannel);
        } catch (Throwable e) {
            group.shutdownGracefully();
            client.shutdown();
            throw e;
        }
    }

    /** The hosts this server has resolved for its clients, looked up by IP. */
    public ResolvedHosts resolvedHosts() {
        return client.resolvedHosts();
    }

    /** Stops listening and shuts down the upstream client. */
    @Override
    public void close() {
        udpChannel.close().syncUninterruptibly();
        tcpChannel.close().syncUninterruptibly();
        group.shutdownGracefully().syncUninterruptibly();
        client.shutdown();
    }
}
