package com.flower.net.socksserver;

import com.flower.net.config.access.Access;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.ConnectionInfo;
import com.flower.net.conntrack.ConnectionId;
import com.flower.net.conntrack.ConnectionListener;
import com.flower.net.conntrack.Destination;
import com.flower.net.utils.IpAddressUtil;
import com.flower.net.utils.ServerUtil;
import com.google.common.base.Preconditions;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.v4.DefaultSocks4CommandResponse;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandStatus;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandRequestDecoder;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequest;
import io.netty.handler.codec.socksx.v5.Socks5InitialRequestDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.flower.net.conntrack.ConnectionAttributes.*;

@ChannelHandler.Sharable
public final class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> implements ConnectionListener {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksServerHandler.class);

    final static AtomicLong SOCKS4_COUNTER = new AtomicLong(1);
    final static AtomicLong SOCKS5_COUNTER = new AtomicLong(1);

    final Supplier<Boolean> allowDirectAccessByIpAddress;
    final Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider;
    @Nullable private final Collection<ConnectionFilter> connectionFilters;
    @Nullable private final Collection<ConnectionListener> connectionListeners;

    public SocksServerHandler(Supplier<Boolean> allowDirectAccessByIpAddress,
                              Supplier<SimpleChannelInboundHandler<SocksMessage>> connectHandlerProvider,
                              @Nullable Collection<ConnectionFilter> connectionFilters,
                              @Nullable Collection<ConnectionListener> connectionListeners) {
        this.allowDirectAccessByIpAddress = allowDirectAccessByIpAddress;
        this.connectHandlerProvider = connectHandlerProvider;
        this.connectionFilters = connectionFilters;
        this.connectionListeners = connectionListeners;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) {
        switch (socksRequest.version()) {
            case SOCKS4a:
                if (!ctx.channel().hasAttr(CONNECTION_ID_KEY)) {
                    long socks4ConnectionNumber = SOCKS4_COUNTER.getAndIncrement();
                    ctx.channel().attr(CONNECTION_ID_KEY).set(new ConnectionId(socks4ConnectionNumber, socksRequest.version()));
                }
                if (!ctx.channel().hasAttr(SOURCE_KEY)) {
                    ctx.channel().attr(SOURCE_KEY).set(ctx.channel().remoteAddress());
                }
                if (!ctx.channel().hasAttr(CONNECTION_LISTENER_KEY)) {
                    ctx.channel().attr(CONNECTION_LISTENER_KEY).set(this);
                }

                Socks4CommandRequest socksV4CmdRequest = (Socks4CommandRequest) socksRequest;
                if (socksV4CmdRequest.type() == Socks4CommandType.CONNECT) {
                    if (!ctx.channel().hasAttr(DESTINATION_KEY)) {
                        ctx.channel().attr(DESTINATION_KEY).set(new Destination(socksV4CmdRequest.dstAddr(), socksV4CmdRequest.dstPort()));
                    }

                    if (approveConnection(socksV4CmdRequest.dstAddr(), socksV4CmdRequest.dstPort(), ctx.channel().remoteAddress()) == Access.ALLOW) {
                        connecting(getConnectionInfo(ctx));

                        ctx.pipeline().addLast(connectHandlerProvider.get());
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(socksRequest);
                    } else {
                        LOGGER.error("DISCONNECTED {} connection prohibited", getConnectionInfo(ctx));
                        ctx.channel().writeAndFlush(new DefaultSocks4CommandResponse(Socks4CommandStatus.REJECTED_OR_FAILED));
                        ServerUtil.closeOnFlush(ctx.channel());
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
                if (!ctx.channel().hasAttr(SOURCE_KEY)) {
                    ctx.channel().attr(SOURCE_KEY).set(ctx.channel().remoteAddress());
                }
                if (!ctx.channel().hasAttr(CONNECTION_LISTENER_KEY)) {
                    ctx.channel().attr(CONNECTION_LISTENER_KEY).set(this);
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

                        if (approveConnection(socks5CmdRequest.dstAddr(), socks5CmdRequest.dstPort(), ctx.channel().remoteAddress()) == Access.ALLOW) {
                            connecting(getConnectionInfo(ctx));

                            ctx.pipeline().addLast(connectHandlerProvider.get());
                            ctx.pipeline().remove(this);
                            ctx.fireChannelRead(socksRequest);
                        } else {
                            LOGGER.error("DISCONNECTED {} connection prohibited", getConnectionInfo(ctx));
                            ctx.channel().writeAndFlush(new DefaultSocks5CommandResponse(Socks5CommandStatus.FORBIDDEN, socks5CmdRequest.dstAddrType()));
                            ServerUtil.closeOnFlush(ctx.channel());
                        }
                    } else {
                        LOGGER.error("DISCONNECTED {} unknown command", getConnectionInfo(ctx));
                        ctx.close();
                    }
                } else {
                    LOGGER.error("DISCONNECTED {} unknown request", getConnectionInfo(ctx));
                    ctx.close();
                }
                break;
            case UNKNOWN:
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

    public Access approveConnection(String dstHost, int dstPort, SocketAddress from) {
        if (!allowDirectAccessByIpAddress.get()) {
            if (IpAddressUtil.isIPAddress(dstHost)) {
                LOGGER.error("CONNECTION_PROHIBITED: no direct IP access allowed {}:{} from {}", dstHost, dstPort, from);
                return Access.DENY;
            }
        }

        Access access = Access.ALLOW;
        if (connectionFilters != null) {
            for (ConnectionFilter filter : connectionFilters) {
                if (filter.approveConnection(dstHost, dstPort, from) == Access.DENY) {
                    access = Access.DENY;
                }
            }
        }
        return access;
    }

    @Override
    public void connecting(ConnectionInfo connectionInfo) {
        if (connectionListeners != null) {
            for (ConnectionListener listener : connectionListeners) {
                listener.connecting(connectionInfo);
            }
            connectionInfo.channel.closeFuture().addListener((ChannelFutureListener) future -> reportDisconnect(future.channel(), "Channel closed"));
        }
    }

    @Override
    public void disconnecting(ConnectionId connectionId, String reason) {
        if (connectionListeners != null) {
            for (ConnectionListener listener : connectionListeners) {
                listener.disconnecting(connectionId, reason);
            }
        }
    }
}
