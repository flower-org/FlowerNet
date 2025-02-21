package com.flower.socksui.forms.traffic;

import com.flower.conntrack.AddressCheck;

import java.net.SocketAddress;
import java.util.Date;

public class CapturedRequest {
    private final String host;
    private final Integer port;
    private final AddressCheck filterResult;
    /** Ture if not based on any rules, but based on general policy Whitelist/Blacklist */
    private final boolean isRuleMatched;
    private final boolean isDirectIpBlock;
    final long creationTimestamp;
    final SocketAddress fromAddress;

    public CapturedRequest(String host, Integer port, AddressCheck filterResult, boolean isRuleMatched, boolean isDirectIpBlock, SocketAddress fromAddress) {
        this.host = host;
        this.port = port;
        this.filterResult = filterResult;
        this.isRuleMatched = isRuleMatched;
        this.isDirectIpBlock = isDirectIpBlock;
        this.creationTimestamp = System.currentTimeMillis();
        this.fromAddress = fromAddress;
    }

    public String getHost() { return host; }

    public int getPort() { return port; }

    public String getFilterResult() {
        return (filterResult == AddressCheck.CONNECTION_ALLOWED ? "Allowed" : "Prohibited")
                + (isDirectIpBlock ? " (direct IP block)" : (isRuleMatched ? " (rule match)" : " (default)"));
    }

    public String getDate() {
        return String.format("%s", new Date(creationTimestamp));
    }

    public String getFrom() {
        return fromAddress.toString();
    }
}
