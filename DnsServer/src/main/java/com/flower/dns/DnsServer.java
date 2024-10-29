package com.flower.dns;

import com.flower.dns.dotclient.DnsOverTlsClient;
import com.flower.utils.PkiUtil;
import com.flower.utils.ServerUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;

public class DnsServer {
    final static Logger LOGGER = LoggerFactory.getLogger(DnsServer.class);

    private static final InetAddress OTHER_DNS_TLS_SERVER_ADDRESS = ServerUtil.getByName("1.1.1.1");
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

        final DnsOverTlsClient client = new DnsOverTlsClient(OTHER_DNS_TLS_SERVER_ADDRESS,
                                                             OTHER_DNS_TLS_SERVER_PORT,
                                                             TRUST_MANAGER,
                                                             useCache);
        final NioEventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel nioDatagramChannel) {
                            nioDatagramChannel.pipeline().addLast(new DatagramDnsQueryDecoder());
                            nioDatagramChannel.pipeline().addLast(new DatagramDnsResponseEncoder());
                            nioDatagramChannel.pipeline().addLast(new DnsMessageHandler(client));
                        }
                    })
                    .option(ChannelOption.SO_BROADCAST, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            LOGGER.info("DNS Server Started at port {}", port);

            future.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
