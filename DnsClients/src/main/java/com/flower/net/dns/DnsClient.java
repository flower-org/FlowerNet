package com.flower.net.dns;

import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.concurrent.Future;

public interface DnsClient {
    /** Warning: Response loss is a possibility. */
    Future<DnsResponse> query(String hostname);
    Future<DnsResponse> query(String hostname, long promiseTimeoutMs);
    void shutdown();
}
