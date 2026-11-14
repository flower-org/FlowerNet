package com.flower.net.dns;

import com.flower.net.dns.dotclient.DnsOverTlsClient;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void skipsAddressesNotOnAnyInterface() throws Exception {
        int port = freePort();
        // 192.0.2.1 is reserved for documentation (RFC 5737), so it's on no interface: like ::1 with IPv6 disabled
        try (DnsServer ignored = DnsServer.start(List.of("127.0.0.1", "192.0.2.1"), port, client(),
                ClientAccess.defaults())) {
            InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
            assertThrows(BindException.class, () -> new DatagramSocket(address));
        }
        assertThrows(BindException.class,
                () -> DnsServer.start(List.of("192.0.2.1"), port, client(), ClientAccess.defaults()));
    }

    @Test
    void failsWhenPortIsInUse() throws Exception {
        try (DatagramSocket taken = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
            assertThrows(BindException.class, () -> DnsServer.start(List.of("127.0.0.1"), taken.getLocalPort(),
                    client(), ClientAccess.defaults()));
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void listensOnIpv4AndIpv6Loopback() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try (DnsServer ignored = DnsServer.start(port, client())) {
            // Binding locally, not connecting, so local packet filtering can't affect the result
            for (String host : DnsServer.DEFAULT_BIND_ADDRESSES) {
                assumeTrue(DnsServer.isAvailable(host), host + " isn't on any interface (IPv6 disabled?)");
                InetSocketAddress address = new InetSocketAddress(host, port);
                assertThrows(BindException.class, () -> new ServerSocket().bind(address), "TCP " + address);
                assertThrows(BindException.class, () -> new DatagramSocket(address), "UDP " + address);
            }
        }
    }
}
