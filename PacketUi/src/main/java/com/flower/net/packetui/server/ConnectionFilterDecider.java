package com.flower.net.packetui.server;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.Protocol;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.dns.dotclient.ResolvedHosts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Decides packets by checking the remote endpoint against {@link ConnectionFilter}s.
 * Remote endpoint is the server: the endpoint a filter knows as a server (a host+port rule matches it).
 * Without a single such endpoint, it's the source for inbound hooks (PREROUTING/INPUT) and the destination for
 * outbound/forwarded ones, so replies from an allowed server are allowed too. Packets that aren't valid IP are dropped.
 */
public class ConnectionFilterDecider implements DeciderServer.PacketDecider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionFilterDecider.class);

    // Netfilter hooks (enum nf_inet_hooks)
    static final int NF_INET_PRE_ROUTING = 0;
    static final int NF_INET_LOCAL_IN = 1;

    private final Deque<ConnectionFilter> connectionFilters = new LinkedBlockingDeque<>();
    @Nullable private volatile ResolvedHosts resolvedHosts;

    /** When set, remote IPs that our DNS server resolved are checked as their host name instead. */
    public void setResolvedHosts(@Nullable ResolvedHosts resolvedHosts) {
        this.resolvedHosts = resolvedHosts;
    }

    public void addConnectionFilter(ConnectionFilter filter) {
        connectionFilters.add(filter);
    }

    public void removeConnectionFilter(ConnectionFilter filter) {
        connectionFilters.remove(filter);
    }

    @Override
    public boolean decide(byte[] packet, int packetId, int hwProto, int hook, int mark, int indev, int outdev) {
        PacketInfo info = PacketInfo.parse(packet);
        if (info == null) {
            LOGGER.debug("Dropping unparseable packet {} (hwProto 0x{}, hook {})",
                    packetId, Integer.toHexString(hwProto), hook);
            return false;
        }

        String srcHost = hostFor(info.srcHost());
        String dstHost = hostFor(info.dstHost());
        boolean remoteIsSrc = remoteIsSource(srcHost, info.srcPort(), dstHost, info.dstPort(), hook);
        String remoteHost = remoteIsSrc ? srcHost : dstHost;
        int remotePort = remoteIsSrc ? info.srcPort() : info.dstPort();
        InetSocketAddress local = remoteIsSrc
                ? InetSocketAddress.createUnresolved(info.dstHost(), info.dstPort())
                : InetSocketAddress.createUnresolved(info.srcHost(), info.srcPort());
        Protocol protocol = toProtocol(info.ipProtocol());

        boolean allow = true;
        for (ConnectionFilter filter : connectionFilters) {
            if (filter.approveConnection(remoteHost, remotePort, protocol, local) == Access.DENY) {
                allow = false;
            }
        }
        return allow;
    }

    /** The most recently resolved host name for {@code ip}, or {@code ip} itself if there's none. */
    String hostFor(String ip) {
        ResolvedHosts hosts = resolvedHosts;
        if (hosts == null) {
            return ip;
        }
        try {
            // An IP literal: parsed, never looked up
            List<String> names = hosts.hostsFor(InetAddress.getByName(ip));
            return names.isEmpty() ? ip : names.get(0);
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    /**
     * The remote endpoint is the server: the one endpoint some filter knows as a server (host+port rule match).
     * If neither or both are known servers, falls back to the hook: source for inbound, destination otherwise.
     */
    boolean remoteIsSource(String srcHost, int srcPort, String dstHost, int dstPort, int hook) {
        boolean srcIsServer = isKnownServer(srcHost, srcPort);
        boolean dstIsServer = isKnownServer(dstHost, dstPort);
        if (srcIsServer != dstIsServer) {
            return srcIsServer;
        }
        return isInbound(hook);
    }

    boolean isKnownServer(String host, int port) {
        for (ConnectionFilter filter : connectionFilters) {
            if (filter.isKnownServer(host, port)) {
                return true;
            }
        }
        return false;
    }

    static boolean isInbound(int hook) {
        return hook == NF_INET_PRE_ROUTING || hook == NF_INET_LOCAL_IN;
    }

    static Protocol toProtocol(int ipProtocol) {
        switch (ipProtocol) {
            case PacketInfo.PROTO_TCP: return Protocol.TCP;
            case PacketInfo.PROTO_UDP: return Protocol.UDP;
            case PacketInfo.PROTO_ICMP:
            case PacketInfo.PROTO_ICMPV6: return Protocol.ICMP;
            default: return Protocol.UNKNOWN;
        }
    }
}
