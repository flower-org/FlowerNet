package com.flower.http;

import com.flower.dns.utils.DnsUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;

public class Http1TestClient {
    private static final String DOH_SERVER = "https://cloudflare-dns.com/dns-query";

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            SslContext sslContext = SslContextBuilder.forClient().build();
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
                                    String responseContent = response.content().toString(StandardCharsets.UTF_8);
                                    System.out.println("Response: " + responseContent);
                                    System.out.println("IpAddresses: " + DnsUtils.extractIpAddresses(responseContent));
                                    ReferenceCountUtil.release(response);
                                    ctx.close();
                                }
                            });
                        }
                    });

            Channel channel = bootstrap.connect("cloudflare-dns.com", 443).sync().channel();

            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/dns-query?name=example.com&type=A");

            request.headers().set(HttpHeaderNames.HOST, "cloudflare-dns.com");
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/dns-json");
            request.headers().set(HttpHeaderNames.ACCEPT, "application/dns-json");
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());

            channel.writeAndFlush(request);
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
