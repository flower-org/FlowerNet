package com.flower.utils;

import io.netty.handler.codec.dns.DnsResponse;
import io.netty.util.concurrent.Promise;

public interface DnsClient {
    /** Warning: Response loss is a possibility. */
    Promise<DnsResponse> query(String hostname);
    Promise<DnsResponse> query(String hostname, long promiseTimeoutMs);
    void shutdown();
}
