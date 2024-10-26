/*
 * Copyright 2022 The Netty Project
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
package com.flower.socks5s;

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
import java.security.cert.CertificateException;
import java.util.Map;

/**
 * Some useful methods for server side.
 */
public final class ServerUtil {

    private static final boolean SSL = System.getProperty("ssl") != null;

    private ServerUtil() {
    }

    @Nullable
    public static SslContext buildSslContext() throws CertificateException, SSLException {
        if (!SSL) {
            return null;
        }
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        return SslContextBuilder
                .forServer(ssc.certificate(), ssc.privateKey())
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
