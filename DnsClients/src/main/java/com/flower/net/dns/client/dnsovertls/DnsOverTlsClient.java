package com.flower.net.dns.client.dnsovertls;

import com.flower.net.dns.DnsClient;
import com.flower.net.dns.utils.DnsUtils;
import io.netty.handler.codec.dns.DefaultDnsQuery;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetAddress;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Resolves host names (A records) over DNS-over-TLS through DnsServer's client, which pools connections, retries
 * unanswered queries, closes idle connections and only accepts answers to the question that was asked.
 * Responses are unpooled copies: callers may keep them and read them from any thread, and needn't release them.
 */
public class DnsOverTlsClient implements DnsClient {
    private final com.flower.net.dns.dotclient.DnsOverTlsClient client;

    public DnsOverTlsClient(InetAddress dnsServerAddress, int dnsServerPort, TrustManagerFactory trustManager,
                            @Nullable String bindClientToIp) throws SSLException {
        // No answer cache or IP-to-host tracking: callers wrap this in DnsCache, and nothing looks IPs up
        this(new com.flower.net.dns.dotclient.DnsOverTlsClient(dnsServerAddress.getHostAddress(), dnsServerPort,
                trustManager, false, false, bindClientToIp));
    }

    /** Takes ownership of {@code client} and shuts it down on {@link #shutdown()}. */
    public DnsOverTlsClient(com.flower.net.dns.dotclient.DnsOverTlsClient client) {
        this.client = client;
    }

    @Override
    public void shutdown() {
        client.shutdown();
    }

    @Override
    public Promise<DnsResponse> query(String hostname, long promiseTimeoutMs) {
        Promise<DnsResponse> promise = query(hostname);
        ScheduledFuture<?> timeout = GlobalEventExecutor.INSTANCE.schedule(
                () -> promise.tryFailure(new TimeoutException("DNS query for " + hostname + " timed out after "
                        + promiseTimeoutMs + "ms")),
                promiseTimeoutMs, TimeUnit.MILLISECONDS);
        promise.addListener(future -> timeout.cancel(false));
        return promise;
    }

    /**
     * Always completes: with the upstream response (which may be an error such as NXDOMAIN or SERVFAIL),
     * or with SERVFAIL if upstream couldn't be reached.
     */
    @Override
    public Promise<DnsResponse> query(String hostname) {
        Promise<DnsResponse> promise = GlobalEventExecutor.INSTANCE.newPromise();
        // Upstream answers carry fully qualified names, and must match the question exactly
        String fqdn = hostname.endsWith(".") ? hostname : hostname + ".";
        DnsQuery query = new DefaultDnsQuery(ThreadLocalRandom.current().nextInt(0x10000), DnsOpCode.QUERY)
                .setRecursionDesired(true)
                .setRecord(DnsSection.QUESTION, new DefaultDnsQuestion(fqdn, DnsRecordType.A));
        try {
            client.query(query, response -> {
                try {
                    promise.trySuccess(DnsUtils.unpooledCopy(response));
                } finally {
                    response.release();
                }
            });
        } finally {
            query.release();
        }
        return promise;
    }
}
