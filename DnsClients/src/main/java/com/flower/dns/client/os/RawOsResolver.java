package com.flower.dns.client.os;

import com.flower.dns.DnsClient;
import com.flower.dns.utils.DnsUtils;
import com.flower.utils.ImmediateFuture;
import com.flower.utils.ServerUtil;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.concurrent.Future;

import java.net.InetAddress;

public class RawOsResolver implements DnsClient {
    @Override
    public Future<DnsResponse> query(String hostname) {
        InetAddress address = ServerUtil.getByName(hostname);
        return ImmediateFuture.of(DnsUtils.dnsResponseFromAddresses(hostname, address));
    }

    @Override
    public Future<DnsResponse> query(String hostname, long promiseTimeoutMs) {
        return query(hostname);
    }

    @Override
    public void shutdown() { }
}
