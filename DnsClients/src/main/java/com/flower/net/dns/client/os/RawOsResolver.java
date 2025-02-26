package com.flower.net.dns.client.os;

import com.flower.net.dns.DnsClient;
import com.flower.net.dns.utils.DnsUtils;
import com.flower.net.utils.ImmediateFuture;
import com.flower.net.utils.ServerUtil;
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
