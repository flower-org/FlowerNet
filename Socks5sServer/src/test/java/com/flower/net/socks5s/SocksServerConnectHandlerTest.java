package com.flower.net.socks5s;

import com.flower.net.dns.DnsClient;
import com.flower.net.utils.ImmediateFuture;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocksServerConnectHandlerTest {
    /** Answers every query with {@code response}. */
    static DnsClient answering(DnsResponse response) {
        return new DnsClient() {
            @Override public Future<DnsResponse> query(String hostname) { return ImmediateFuture.of(response); }
            @Override public Future<DnsResponse> query(String hostname, long promiseTimeoutMs) { return query(hostname); }
            @Override public void shutdown() { }
        };
    }

    static Future<InetAddress> resolve(DnsResponse response, String host) {
        SocksServerConnectHandler handler =
                new SocksServerConnectHandler(Socks5sServer.allowAll(), answering(response), null);
        return handler.resolve(host, ImmediateEventExecutor.INSTANCE);
    }

    @Test
    void resolvesToAnAddressFromTheAnswer() throws Exception {
        DefaultDnsResponse response = new DefaultDnsResponse(1, DnsOpCode.QUERY);
        response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord("a.test.", DnsRecordType.A, 60,
                Unpooled.wrappedBuffer(new byte[] {10, 0, 0, 1})));
        Future<InetAddress> future = resolve(response, "a.test");
        assertTrue(future.isSuccess(), String.valueOf(future.cause()));
        assertEquals(InetAddress.getByName("10.0.0.1"), future.getNow());
    }

    @Test
    void failsWhenTheHostDoesNotExist() {
        DefaultDnsResponse response = new DefaultDnsResponse(1, DnsOpCode.QUERY, DnsResponseCode.NXDOMAIN);
        Future<InetAddress> future = resolve(response, "missing.test");
        // Used to stay incomplete forever, leaving the SOCKS client without a reply
        assertTrue(future.isDone(), "never completed");
        assertInstanceOf(UnknownHostException.class, future.cause());
        assertTrue(future.cause().getMessage().contains("missing.test"), future.cause().getMessage());
    }

    @Test
    void failsWhenTheAnswerHasNoAddresses() {
        Future<InetAddress> future = resolve(new DefaultDnsResponse(1, DnsOpCode.QUERY), "empty.test");
        assertTrue(future.isDone(), "never completed");
        assertInstanceOf(UnknownHostException.class, future.cause());
    }

    @Test
    void ipLiteralsSkipDns() throws Exception {
        Future<InetAddress> future = resolve(new DefaultDnsResponse(1, DnsOpCode.QUERY), "192.0.2.7");
        assertEquals(InetAddress.getByName("192.0.2.7"), future.getNow());
    }
}
