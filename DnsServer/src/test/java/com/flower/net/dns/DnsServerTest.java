package com.flower.net.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertSame;

@Timeout(30)
class DnsServerTest {
    private static DnsOverTlsClient client() throws Exception {
        return new DnsOverTlsClient("127.0.0.1", 1, InsecureTrustManagerFactory.INSTANCE, true, null);
    }

    @Test
    void exposesResolvedHostsAndReleasesPortOnClose() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        DnsOverTlsClient client = client();
        try (DnsServer server = DnsServer.start(port, client)) {
            assertSame(client.resolvedHosts(), server.resolvedHosts());
        }
        // The port is free again once closed
        DnsServer.start(port, client()).close();
    }
}
