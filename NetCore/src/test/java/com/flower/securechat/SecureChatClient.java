/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.flower.securechat;

import com.flower.utils.PkiUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import static com.flower.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;

/**
 * Simple SSL chat client modified from TelnetClient.
 */
public final class SecureChatClient {

    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "8992"));
//    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("test.crt");
//    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("ca.crt");
//    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("pkcs11_ca.crt");
//    private static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForCertificateResource("MY_REQ.crt");
    private static final TrustManagerFactory TRUST_MANAGER = InsecureTrustManagerFactory.INSTANCE;

    public static void main(String[] args) throws Exception {
//        KeyManagerFactory keyManager = PkiUtil.getKeyManagerFromPKCS11("/usr/lib/libeToken.so", "Qwerty123");
        KeyManagerFactory keyManager = PkiUtil.getKeyManagerFromResources("MY_REQ2.crt", "MY_REQ2.key", "");
        //KeyManagerFactory keyManager = PkiUtil.getKeyManagerFromResources("test.crt", "test.key", "");

        // Configure SSL.
        final SslContext sslCtx = SslContextBuilder.forClient()
            .protocols(TLS_PROTOCOLS)
            .ciphers(TLS_CIPHERS)
            .keyManager(keyManager)
            .clientAuth(ClientAuth.REQUIRE)
            .trustManager(TRUST_MANAGER)
            .build();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new SecureChatClientInitializer(sslCtx));

            // Start the connection attempt.
            Channel ch = b.connect(HOST, PORT).sync().channel();

            // Read commands from the stdin.
            ChannelFuture lastWriteFuture = null;
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            for (;;) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }

                // Sends the received line to the server.
                lastWriteFuture = ch.writeAndFlush(line + "\r\n");

                // If user typed the 'bye' command, wait until the server closes
                // the connection.
                if ("bye".equals(line.toLowerCase())) {
                    ch.closeFuture().sync();
                    break;
                }
            }

            // Wait until all messages are flushed before closing the channel.
            if (lastWriteFuture != null) {
                lastWriteFuture.sync();
            }
        } finally {
            // The connection is closed automatically on shutdown.
            group.shutdownGracefully();
        }
    }
}
