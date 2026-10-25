package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.Protocol;

import javax.annotation.Nullable;
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

    public String getHost() { return host; }

    public int getPort() { return port; }

    public String getFilterResult() {
        return (access == Access.ALLOW ? "Allowed" : "Denied")
                + (isDirectIpBlock ? " (direct IP block)" : (isRuleMatched ? " (rule match)" : " (default)"));
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getDate() {
        return String.format("%s", new Date(creationTimestamp));
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
        return (fromPort == null || fromPort < 0) ? host : host + ":" + fromPort;
    }
}
