package com.flower.dns.client;

import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.util.concurrent.Promise;

public interface DnsClient {
    Promise<DefaultDnsResponse> query(String hostname);
    Promise<DefaultDnsResponse> query(String hostname, long timeoutMillis);
    void shutdown();
}
