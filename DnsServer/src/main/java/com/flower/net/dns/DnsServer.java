package com.flower.net.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import com.flower.net.dns.dotclient.viasocks.Socks5Credentials;
import com.flower.crypt.PkiUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

public class DnsServer {
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

        final NioEventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                            nioDatagramChannel.pipeline().addLast(new LenientDnsQueryDecoder());
                            nioDatagramChannel.pipeline().addLast(new DnsMessageHandler(client));
                        }
                    })
                    .option(ChannelOption.SO_BROADCAST, true);

            // Clients retry over TCP when a UDP reply is truncated (RFC 7766)
            ServerBootstrap tcpBootstrap = new ServerBootstrap();
            tcpBootstrap.group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            DnsTcpMessageHandler.addHandlers(socketChannel.pipeline(), client);
                        }
                    });

            ChannelFuture future = bootstrap.bind(port).sync();
            ChannelFuture tcpFuture = tcpBootstrap.bind(port).sync();
            LOGGER.info("DNS Server Started at port {} (UDP and TCP)", port);

            future.channel().closeFuture().sync();
            tcpFuture.channel().close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
