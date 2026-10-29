package com.flower.net.dns.dotclient.viasocks;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5PasswordAuthRequest;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5ClientEncoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5Message;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponse;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthResponseDecoder;
import io.netty.handler.codec.socksx.v5.Socks5PasswordAuthStatus;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Opens a SOCKS5 tunnel (RFC 1928) to the DoT server, then starts TLS and DNS framing through it.
 * The handshake runs as a strict sequence: greeting, optional username/password authentication (RFC 1929), CONNECT.
 * Any unexpected or failed step fails {@code tunnelReady} and closes the channel; the pool bounds the total time.
 */
public final class Socks5TunnelHandler extends SimpleChannelInboundHandler<Socks5Message> {
    static final String DECODER_NAME = "socks5ResponseDecoder";
    /** Proxies add round trips, so the TLS handshake through them gets longer than a direct one. */
    public static final long TLS_HANDSHAKE_TIMEOUT_MILLIS = 5_000L;

    enum State { GREETING, AUTHENTICATING, CONNECTING, DONE }

    private final String dstHost;
    private final int dstPort;
    @Nullable private final Socks5Credentials credentials;
    private final DnsOverTlsClient dnsClient;
    /** Succeeds once the TLS handshake through the tunnel completes; fails with the reason otherwise. */
    private final Promise<Channel> tunnelReady;
    private State state = State.GREETING;

    Socks5TunnelHandler(String dstHost, int dstPort, @Nullable Socks5Credentials credentials,
                        DnsOverTlsClient dnsClient, Promise<Channel> tunnelReady) {
        this.dstHost = dstHost;
        this.dstPort = dstPort;
        this.credentials = credentials;
        this.dnsClient = dnsClient;
        this.tunnelReady = tunnelReady;
    }

    /** Starts the handshake on a channel connected to the proxy. */
    public static void install(Channel channel, String dstHost, int dstPort, @Nullable Socks5Credentials credentials,
                               DnsOverTlsClient dnsClient, Promise<Channel> tunnelReady) {
        channel.pipeline()
                .addLast(Socks5ClientEncoder.DEFAULT)
                .addLast(DECODER_NAME, new Socks5InitialResponseDecoder())
                .addLast(new Socks5TunnelHandler(dstHost, dstPort, credentials, dnsClient, tunnelReady));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        if (credentials == null) {
            send(ctx, new DefaultSocks5InitialRequest(Socks5AuthMethod.NO_AUTH));
        } else {
            send(ctx, new DefaultSocks5InitialRequest(Socks5AuthMethod.NO_AUTH, Socks5AuthMethod.PASSWORD));
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Socks5Message message) {
        if (message.decoderResult().isFailure()) {
            fail(ctx, new IOException(prefix() + "malformed response from proxy", message.decoderResult().cause()));
            return;
        }
        if (state == State.GREETING && message instanceof Socks5InitialResponse response) {
            onGreeting(ctx, response.authMethod());
        } else if (state == State.AUTHENTICATING && message instanceof Socks5PasswordAuthResponse response) {
            if (Socks5PasswordAuthStatus.SUCCESS.equals(response.status())) {
                connect(ctx);
            } else {
                fail(ctx, prefix() + "proxy rejected the username/password");
            }
        } else if (state == State.CONNECTING && message instanceof Socks5CommandResponse response) {
            if (Socks5CommandStatus.SUCCESS.equals(response.status())) {
                startTls(ctx);
            } else {
                fail(ctx, prefix() + "CONNECT rejected with status " + response.status());
            }
        } else {
            fail(ctx, prefix() + "unexpected " + message.getClass().getSimpleName() + " in state " + state);
        }
    }

    private void onGreeting(ChannelHandlerContext ctx, Socks5AuthMethod method) {
        if (Socks5AuthMethod.NO_AUTH.equals(method)) {
            connect(ctx);
        } else if (Socks5AuthMethod.PASSWORD.equals(method) && credentials != null) {
            state = State.AUTHENTICATING;
            ctx.pipeline().replace(DECODER_NAME, DECODER_NAME, new Socks5PasswordAuthResponseDecoder());
            send(ctx, new DefaultSocks5PasswordAuthRequest(credentials.username(), credentials.password()));
        } else if (Socks5AuthMethod.PASSWORD.equals(method)) {
            fail(ctx, prefix() + "proxy requires a username/password, but none is configured");
        } else {
            fail(ctx, prefix() + "proxy accepts none of the offered authentication methods (chose " + method + ")");
        }
    }

    private void connect(ChannelHandlerContext ctx) {
        state = State.CONNECTING;
        ctx.pipeline().replace(DECODER_NAME, DECODER_NAME, new Socks5CommandResponseDecoder());
        Socks5AddressType addressType = NetUtil.isValidIpV4Address(dstHost) ? Socks5AddressType.IPv4
                : NetUtil.isValidIpV6Address(dstHost) ? Socks5AddressType.IPv6 : Socks5AddressType.DOMAIN;
        send(ctx, new DefaultSocks5CommandRequest(Socks5CommandType.CONNECT, addressType, dstHost, dstPort));
    }

    /** The tunnel is up: the rest of the stream is TLS, so the SOCKS handlers make way for TLS and DNS. */
    private void startTls(ChannelHandlerContext ctx) {
        state = State.DONE;
        Channel channel = ctx.channel();
        SslHandler sslHandler = dnsClient.newSslHandler(channel, TLS_HANDSHAKE_TIMEOUT_MILLIS);
        sslHandler.handshakeFuture().addListener(handshake -> {
            if (handshake.isSuccess()) {
                tunnelReady.trySuccess(channel);
            } else {
                tunnelReady.tryFailure(handshake.cause());
            }
        });
        ctx.pipeline().addLast(sslHandler);
        dnsClient.addDnsHandlers(ctx.pipeline());
        ctx.pipeline().remove(this);
        // Removed last: any bytes it buffered after the CONNECT reply are passed on to the TLS handler
        ctx.pipeline().remove(DECODER_NAME);
        ctx.pipeline().remove(Socks5ClientEncoder.class);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        tunnelReady.tryFailure(new IOException(prefix() + "proxy closed the connection during " + state));
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        fail(ctx, cause);
    }

    private void send(ChannelHandlerContext ctx, Object message) {
        ctx.writeAndFlush(message).addListener(write -> {
            if (!write.isSuccess()) {
                fail(ctx, write.cause());
            }
        });
    }

    private String prefix() {
        return "SOCKS5 tunnel to " + dstHost + ":" + dstPort + " failed: ";
    }

    private void fail(ChannelHandlerContext ctx, String reason) {
        fail(ctx, new IOException(reason));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void fail(ChannelHandlerContext ctx, Throwable cause) {
        tunnelReady.tryFailure(cause);
        ctx.channel().close();
    }
}
