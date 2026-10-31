package com.flower.net.packetui.server;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.Protocol;
import com.flower.net.conntrack.ConnectionFilter;
import com.flower.net.conntrack.TrafficDirection;
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
 * Remote endpoint is the server, picked by the first of these that singles out one endpoint:
 * <ol>
 *     <li>a filter knows it as a server (any rule matches it: rules describe servers);</li>
 *     <li>it's outside the internal subnets while the other endpoint is inside (see {@link #setInternalSubnets});</li>
 *     <li>our DNS server resolved a name to its IP (clients look servers up, not the other way around);</li>
 *     <li>the hook: the source for inbound hooks (PREROUTING/INPUT), the destination for outbound/forwarded ones,
 *     so replies from an allowed server are allowed too.</li>
 * </ol>
 * Packets that aren't valid IP are dropped.
 */
public class ConnectionFilterDecider implements DeciderServer.PacketDecider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionFilterDecider.class);

    // Netfilter hooks (enum nf_inet_hooks)
    static final int NF_INET_PRE_ROUTING = 0;
    static final int NF_INET_LOCAL_IN = 1;
    static final int NF_INET_FORWARD = 2;
    static final int NF_INET_LOCAL_OUT = 3;

    private final Deque<ConnectionFilter> connectionFilters = new LinkedBlockingDeque<>();
    @Nullable private volatile ResolvedHosts resolvedHosts;
    private volatile Subnets internalSubnets = Subnets.EMPTY;

    /**
     * Our own networks and addresses (e.g. the LAN and the router's public IP). When exactly one endpoint is inside them
     * and no rule singles out a server, the other endpoint is the server.
     */
    public void setInternalSubnets(Subnets internalSubnets) {
        this.internalSubnets = internalSubnets;
    }

    /**
     * When set, IPs that our DNS server resolved are checked as their host name instead,
     * and count as servers when neither rules nor internal subnets single out one endpoint.
     */
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
        boolean remoteIsSrc = remoteIsSource(info, srcHost, dstHost, hook);
        String remoteHost = remoteIsSrc ? srcHost : dstHost;
        int remotePort = remoteIsSrc ? info.srcPort() : info.dstPort();
        InetSocketAddress local = remoteIsSrc
                ? InetSocketAddress.createUnresolved(info.dstHost(), info.dstPort())
                : InetSocketAddress.createUnresolved(info.srcHost(), info.srcPort());
        Protocol protocol = toProtocol(info.ipProtocol());
        TrafficDirection direction = toDirection(hook);

        boolean allow = true;
        for (ConnectionFilter filter : connectionFilters) {
            if (filter.approveConnection(remoteHost, remotePort, protocol, local, direction) == Access.DENY) {
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
     * The remote endpoint is the server, the one endpoint singled out by (first that decides):
     * a filter knows it as a server (any rule matches it); it's outside the internal subnets while the other is inside;
     * our DNS server resolved it. Otherwise falls back to the hook: source for inbound, destination otherwise.
     * @param srcHost the source as checked against rules: its resolved host name, or the IP if there's none
     * @param dstHost the destination as checked against rules, likewise
     */
    boolean remoteIsSource(PacketInfo info, String srcHost, String dstHost, int hook) {
        boolean srcIsServer = isKnownServerFromRules(srcHost, info.srcPort());
        boolean dstIsServer = isKnownServerFromRules(dstHost, info.dstPort());
        if (srcIsServer != dstIsServer) {
            return srcIsServer;
        }
        Subnets subnets = internalSubnets;
        boolean srcInternal = subnets.contains(info.srcHost());
        boolean dstInternal = subnets.contains(info.dstHost());
        if (srcInternal != dstInternal) {
            return !srcInternal;
        }
        // hostFor returns the IP itself when our DNS server never resolved it
        boolean srcResolved = !srcHost.equals(info.srcHost());
        boolean dstResolved = !dstHost.equals(info.dstHost());
        if (srcResolved != dstResolved) {
            return srcResolved;
        }
        return isInbound(hook);
    }

    boolean isKnownServerFromRules(String host, int port) {
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

    /** null for hooks PacketUi doesn't queue from (PREROUTING/POSTROUTING), where the chain is ambiguous */
    static @Nullable TrafficDirection toDirection(int hook) {
        switch (hook) {
            case NF_INET_LOCAL_IN: return TrafficDirection.IN;
            case NF_INET_LOCAL_OUT: return TrafficDirection.OUT;
            case NF_INET_FORWARD: return TrafficDirection.FORWARD;
            default: return null;
        }
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
