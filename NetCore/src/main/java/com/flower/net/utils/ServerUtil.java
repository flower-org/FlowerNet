package com.flower.net.utils;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.Map;

import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_CIPHERS;
import static com.flower.net.socksserver.FlowerSslContextBuilder.TLS_PROTOCOLS;

public class ServerUtil {
    public static InetAddress getByName(String name) {
        try {
            return InetAddress.getByName(name);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private ServerUtil() {
    }

    @Nullable
    public static SslContext buildSslContext() throws CertificateException, SSLException {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return SslContextBuilder
                .forServer(ssc.certificate(), ssc.privateKey())
                .protocols(TLS_PROTOCOLS)
                .ciphers(TLS_CIPHERS)
                .build();
    }

    public static String showPipeline(ChannelPipeline pipeline) {
        StringBuilder stringBuilder = new StringBuilder();
        int index = 0;
        for (String name : pipeline.names()) {
            stringBuilder.append("Handler ").append(index++).append(": ").append(name).append(" / ").append("\n");
        }
        return stringBuilder.toString();
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    public static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Nullable
    public static String getHandlerName(Class handlerType, ChannelPipeline pipeline) {
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            ChannelHandler handler = entry.getValue();
            if (handler.getClass().equals(handlerType)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
