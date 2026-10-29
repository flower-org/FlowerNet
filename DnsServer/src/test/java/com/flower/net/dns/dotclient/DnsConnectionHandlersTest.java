package com.flower.net.dns.dotclient;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsConnectionHandlersTest {
    DnsOverTlsClient client;

    @BeforeEach
    void setUp() throws Exception {
        // No connection is made until a query is sent
        client = new DnsOverTlsClient("1.1.1.1", 853, InsecureTrustManagerFactory.INSTANCE, false, null);
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    EmbeddedChannel dnsChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        client.addDnsHandlers(channel.pipeline());
        return channel;
    }

    /** Encodes a response the way the upstream server sends it over TCP. */
    static ByteBuf wireResponse(int id, DnsQuestion question) {
        EmbeddedChannel encoder = new EmbeddedChannel(new TcpDnsResponseEncoder());
        encoder.writeOutbound(new DefaultDnsResponse(id).setRecord(DnsSection.QUESTION, question));
        return encoder.readOutbound();
    }

    @Test
    void connectionStaysOpenAndDeliversSeveralResponses() {
        EmbeddedChannel channel = dnsChannel();
        DnsQuestion qa = new DefaultDnsQuestion("a.com.", DnsRecordType.A);
        DnsQuestion qb = new DefaultDnsQuestion("b.com.", DnsRecordType.A);
        List<String> received = new ArrayList<>();
        UpstreamQueryRegistry.PendingQuery a = client.pendingQueries().register(1, qa,
                r -> { received.add(r.id() + ":" + r.recordAt(DnsSection.QUESTION, 0).name()); r.release(); });
        UpstreamQueryRegistry.PendingQuery b = client.pendingQueries().register(2, qb,
                r -> { received.add(r.id() + ":" + r.recordAt(DnsSection.QUESTION, 0).name()); r.release(); });

        // Responses may arrive out of order, both on the same connection
        channel.writeInbound(wireResponse(b.upstreamId(), qb));
        assertTrue(channel.isOpen());
        channel.writeInbound(wireResponse(a.upstreamId(), qa));
        assertTrue(channel.isOpen());

        assertEquals(List.of("2:b.com.", "1:a.com."), received);
        assertEquals(0, client.pendingQueries().inFlightCount());
        assertFalse(channel.finish());
    }

    @Test
    void idleConnectionIsClosed() {
        EmbeddedChannel channel = dnsChannel();
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.ALL_IDLE_STATE_EVENT);
        assertFalse(channel.isOpen());
    }

    @Test
    void connectionIsClosedOnError() {
        EmbeddedChannel channel = dnsChannel();
        channel.pipeline().fireExceptionCaught(new RuntimeException("test"));
        assertFalse(channel.isOpen());
    }
}
