package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.Protocol;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Date;

public class CapturedRequest {
    private final String host;
    private final Integer port;
    private final Access access;
    private final Protocol protocol;
    /** Ture if not based on any rules, but based on general policy Allow/Deny */
    private final boolean isRuleMatched;
    private final boolean isDirectIpBlock;
    final long creationTimestamp;
    final @Nullable String fromHost;
    final @Nullable Integer fromPort;

    public CapturedRequest(String host, Integer port, Access access, boolean isRuleMatched, Protocol protocol,
                           boolean isDirectIpBlock, @Nullable String fromHost, @Nullable Integer fromPort) {
        this.host = host;
        this.port = port;
        this.access = access;
        this.protocol = protocol;
        this.isRuleMatched = isRuleMatched;
        this.isDirectIpBlock = isDirectIpBlock;
        this.creationTimestamp = System.currentTimeMillis();
        this.fromHost = fromHost;
        this.fromPort = fromPort;
    }

    /** Builds a request from a raw source address: resolved address -> IP, unresolved -> original host string */
    public static CapturedRequest of(String host, int port, Access access, boolean isRuleMatched, Protocol protocol,
                                     boolean isDirectIpBlock, @Nullable SocketAddress from) {
        String fromHost = null;
        Integer fromPort = null;
        if (from instanceof InetSocketAddress isa) {
            fromHost = isa.getAddress() != null ? isa.getAddress().getHostAddress() : isa.getHostString();
            fromPort = isa.getPort();
        }
        return new CapturedRequest(host, port, access, isRuleMatched, protocol, isDirectIpBlock, fromHost, fromPort);
    }

    public String getHost() { return host; }

    public int getPort() { return port; }

    public Access getAccess() { return access; }

    public boolean isRuleMatched() { return isRuleMatched; }

    public String getFilterResult() {
        return (access == Access.ALLOW ? "Allowed" : "Denied")
                + (isDirectIpBlock ? " (direct IP block)" : (isRuleMatched ? " (rule match)" : " (default)"));
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getDate() {
        return formatTimestamp(creationTimestamp);
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    static String formatTimestamp(long timestamp) {
        return String.format("%s", new Date(timestamp));
    }

    public @Nullable String getFromHost() {
        return fromHost;
    }

    public @Nullable Integer getFromPort() {
        return fromPort;
    }

    /** "host:port" (IPv6 in brackets); host only if port is unknown; empty if source is unknown */
    public String getFrom() {
        if (fromHost == null) {
            return "";
        }
        String host = fromHost.indexOf(':') >= 0 ? "[" + fromHost + "]" : fromHost;
        return fromPort == null ? host : host + ":" + fromPort;
    }
}
