package com.flower.socksserver;

import com.flower.conntrack.ConnectionListenerAndFilter;
import com.flower.conntrack.Destination;
import com.flower.utils.NonDnsHostnameChecker;
import com.flower.utils.ServerUtil;
import com.google.common.base.Preconditions;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.flower.conntrack.ConnectionId;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.flower.conntrack.ConnectionAttributes.CONNECTION_ID_KEY;
import static com.flower.conntrack.ConnectionAttributes.DESTINATION_KEY;
import static com.flower.conntrack.ConnectionAttributes.getConnectionInfo;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> implements ConnectionListenerAndFilter {
    final static AtomicLong SOCKS4_COUNTER = new AtomicLong(1);
    final static AtomicLong SOCKS5_COUNTER = new AtomicLong(1);
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServerHandler.class);

    final Supplier<Boolean> allowDirectAccessByIpAddress;
    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;
    @Nullable final Collection<ConnectionListenerAndFilter> connectionListenerAndFilters;

    public SocksServerHandler(Supplier<Boolean> allowDirectAccessByIpAddress,
                              Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider,
                              @Nullable Collection<ConnectionListenerAndFilter> connectionListenerAndFilters) {
        this.allowDirectAccessByIpAddress = allowDirectAccessByIpAddress;
        this.connectHandlerProvider = connectHandlerProvider;
        this.connectionListenerAndFilters = connectionListenerAndFilters;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) {
        switch (socksRequest.version()) {
            case SOCKS4a:
                if (!ctx.channel().hasAttr(CONNECTION_ID_KEY)) {
                    long socks4ConnectionNumber = SOCKS4_COUNTER.getAndIncrement();
                    ctx.channel().attr(CONNECTION_ID_KEY).set(new ConnectionId(socks4ConnectionNumber, socksRequest.version()));
                }

                Socks4CommandRequest socksV4CmdRequest = (Socks4CommandRequest) socksRequest;
                if (socksV4CmdRequest.type() == Socks4CommandType.CONNECT) {
                    if (!ctx.channel().hasAttr(DESTINATION_KEY)) {
                        ctx.channel().attr(DESTINATION_KEY).set(new Destination(socksV4CmdRequest.dstAddr(), socksV4CmdRequest.dstPort()));
                    }

                    if (approveConnection(socksV4CmdRequest.dstAddr(), socksV4CmdRequest.dstPort(), ctx.channel().remoteAddress()) == AddressCheck.CONNECTION_ALLOWED) {
                        ctx.pipeline().addLast(connectHandlerProvider.get());
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(socksRequest);
                    } else {
                        LOGGER.error("DISCONNECTED {} connection prohibited", getConnectionInfo(ctx));
                        ctx.close();
                    }
                } else {
                    LOGGER.error("DISCONNECTED {} unknown command", getConnectionInfo(ctx));
                    ctx.close();
                }
                break;
            case SOCKS5:
                if (!ctx.channel().hasAttr(CONNECTION_ID_KEY)) {
                    long socks5ConnectionNumber = SOCKS5_COUNTER.getAndIncrement();
                    ctx.channel().attr(CONNECTION_ID_KEY).set(new ConnectionId(socks5ConnectionNumber, socksRequest.version()));
                }

                if (socksRequest instanceof Socks5InitialRequest) {
                    String handlerAfterName = ServerUtil.getHandlerName(Socks5InitialRequestDecoder.class, ctx.pipeline());
                    ctx.pipeline().addBefore(handlerAfterName, "Socks5CommandRequestDecoder", new Socks5CommandRequestDecoder());
                    ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                /*
                    // auth support example
                    //!!!! DON'T USE addFirst here it'll screw up TLS handler !!!!
                    //ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                    //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
                } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
                    ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                    ctx.write(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));*/
                } else if (socksRequest instanceof Socks5CommandRequest) {
                    final Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest)socksRequest;
                    if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                        if (!ctx.channel().hasAttr(DESTINATION_KEY)) {
                            ctx.channel().attr(DESTINATION_KEY).set(new Destination(socks5CmdRequest.dstAddr(), socks5CmdRequest.dstPort()));
                        }

                        if (approveConnection(socks5CmdRequest.dstAddr(), socks5CmdRequest.dstPort(), ctx.channel().remoteAddress()) == AddressCheck.CONNECTION_ALLOWED) {
                            ctx.pipeline().addLast(connectHandlerProvider.get());
                            ctx.pipeline().remove(this);
                            ctx.fireChannelRead(socksRequest);
                        } else {
                            LOGGER.error("DISCONNECTED {} connection prohibited", getConnectionInfo(ctx));
                            ctx.close();
                        }
                    } else {
                        LOGGER.error("DISCONNECTED {} unknown command", getConnectionInfo(ctx));
                        ctx.close();
                    }
                } else {
                    //TODO: close feedback
                    LOGGER.error("DISCONNECTED {} unknown request", getConnectionInfo(ctx));
                    ctx.close();
                }
                break;
            case UNKNOWN:
                //TODO: close feedback
                LOGGER.error("DISCONNECTED {} unknown protocol", getConnectionInfo(ctx));
                Preconditions.checkNotNull(ctx).close();
                break;
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //TODO: close feedback
        LOGGER.error("DISCONNECTED {} exception caught: ", getConnectionInfo(ctx), cause);
        ServerUtil.closeOnFlush(ctx.channel());
    }

    @Override
    public AddressCheck approveConnection(String dstHost, int dstPort, SocketAddress from) {
        if (!allowDirectAccessByIpAddress.get()) {
            if (NonDnsHostnameChecker.isIPAddress(dstHost)) {
                LOGGER.error("CONNECTION_PROHIBITED: no direct IP access allowed {}:{} from {}", dstHost, dstPort, from);
                return AddressCheck.CONNECTION_PROHIBITED;
            }
        }

        AddressCheck addressCheck = AddressCheck.CONNECTION_ALLOWED;
        if (connectionListenerAndFilters != null) {
            for (ConnectionListenerAndFilter filter : connectionListenerAndFilters) {
                if (filter.approveConnection(dstHost, dstPort, from) == AddressCheck.CONNECTION_PROHIBITED) {
                    addressCheck = AddressCheck.CONNECTION_PROHIBITED;
                }
            }
        }
        return addressCheck;
    }
}
