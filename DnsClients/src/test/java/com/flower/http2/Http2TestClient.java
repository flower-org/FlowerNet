package com.flower.http2;

import com.flower.utils.PromiseUtil;
import com.flower.utils.ServerUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import static com.flower.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public final class Http2TestClient {
    public static void main(String[] args) throws Exception {
        final String host = "1.1.1.1";
        final int port = 443;
        final String url = "/dns-query?name=example.com&type=A";
        final String url2 = "/dns-query?name=google.com&type=A";

        // Configure SSL.
        final SslContext sslCtx = SslContextBuilder.forClient()
                .protocols(TLS_PROTOCOLS)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)




                //TODO: Trust Manager
                .trustManager(InsecureTrustManagerFactory.INSTANCE)





                .applicationProtocolConfig(new ApplicationProtocolConfig(
                        Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1))
                .build();

        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Http2ClientInitializer initializer = new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE, ServerUtil.getByName(host), port,
                new SimpleChannelInboundHandler<>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext c, FullHttpResponse msg) throws Exception {
                        Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
                        if (streamId == null) {
                            System.err.println("HttpResponseHandler unexpected message received: " + msg);
                            return;
                        }

                        // Do stuff with the message (for now just print it)
                        ByteBuf content = msg.content();
                        if (content.isReadable()) {
                            int contentLength = content.readableBytes();
                            byte[] arr = new byte[contentLength];
                            content.readBytes(arr);
                            System.out.println(new String(arr, 0, contentLength, CharsetUtil.UTF_8));
                        }

                        System.out.println("---Stream id: " + streamId + " received---");
                    }
                });

        try {
            // Configure the client.
            Bootstrap b = new Bootstrap();
            b.group(workerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.remoteAddress(host, port);
            b.handler(initializer);

            // Start the client.
            Channel channel = b.connect().syncUninterruptibly().channel();
            System.out.println("Connected to [" + host + ':' + port + ']');

            // Wait for the HTTP/2 upgrade to occur.
            // This wait is actually important, requests won't go through until it's done
            PromiseUtil.withTimeout(channel.eventLoop(), initializer.settingsPromise(), 5000)
                .addListener(future -> {
                    if (future.isSuccess()) {
                        System.out.println("HTTP/2 settings obtained " + future.get());

                        String hostName = host + ':' + port;
                        System.err.println("Sending request(s)...");

                        //Request 1
                        sendRequest(channel, hostName, url);

                        //Request 2
                        sendRequest(channel, hostName, url2);

                        channel.flush();
                        System.out.println("Finished HTTP/2 request(s)");
                    } else {
                        System.out.println("Issue obtaining HTTP/2 settings");
                        future.cause().printStackTrace();
                    }
                });

            Thread.sleep(100_000);

            // Wait until the connection is closed.
            channel.close().syncUninterruptibly();
        } finally {
            workerGroup.shutdownGracefully();
        }
    }

    public static void sendRequest(Channel channel, String hostName, String path) {
        HttpScheme scheme = HttpScheme.HTTPS;
        // Create a simple GET request.
        FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, path, Unpooled.EMPTY_BUFFER);
        request.headers().add(HttpHeaderNames.HOST, hostName);
        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());
        request.headers().add(HttpHeaderNames.ACCEPT, "application/dns-json");
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);

        channel.write(request);
    }
}