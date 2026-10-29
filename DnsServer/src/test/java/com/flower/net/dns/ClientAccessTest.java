package com.flower.net.dns;

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.flower.net.dns.ClientAccess.Decision.ALLOW;
import static com.flower.net.dns.ClientAccess.Decision.DROP;
import static com.flower.net.dns.ClientAccess.Decision.REFUSE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAccessTest {
    private static InetSocketAddress client(String ip) {
        return new InetSocketAddress(ip, 40000);
    }

    @Test
    void defaultsAllowOnlyLocalNetworks() {
        ClientAccess access = ClientAccess.defaults();
        for (String ip : List.of("127.0.0.1", "::1", "10.1.2.3", "172.31.0.1", "192.168.1.1", "fd00::1", "fe80::1")) {
            assertEquals(ALLOW, access.check(client(ip)), ip);
        }
        for (String ip : List.of("8.8.8.8", "172.32.0.1", "2001:db8::1")) {
            assertEquals(REFUSE, access.check(client(ip)), ip);
        }
    }

    @Test
    void customSubnets() {
        ClientAccess access = new ClientAccess(List.of("203.0.113.0/24"), 10, 10);
        assertEquals(ALLOW, access.check(client("203.0.113.7")));
        assertEquals(REFUSE, access.check(client("127.0.0.1")));
        ClientAccess everyone = new ClientAccess(List.of("0.0.0.0/0", "::/0"), 10, 10);
        assertEquals(ALLOW, everyone.check(client("8.8.8.8")));
        assertEquals(ALLOW, everyone.check(client("2001:db8::1")));
    }

    @Test
    void rateLimitsEachClientWithABurst() {
        AtomicLong now = new AtomicLong();
        ClientAccess access = new ClientAccess(ClientAccess.LOCAL_NETWORKS, 2, 3, now::get);
        for (int i = 0; i < 3; i++) {
            assertEquals(ALLOW, access.check(client("10.0.0.1")));
        }
        assertEquals(DROP, access.check(client("10.0.0.1")));
        // Other clients have their own bucket
        assertEquals(ALLOW, access.check(client("10.0.0.2")));

        now.addAndGet(500_000_000L);
        assertEquals(ALLOW, access.check(client("10.0.0.1")));
        assertEquals(DROP, access.check(client("10.0.0.1")));

        // Refills up to the burst, no further
        now.addAndGet(60_000_000_000L);
        for (int i = 0; i < 3; i++) {
            assertEquals(ALLOW, access.check(client("10.0.0.1")));
        }
        assertEquals(DROP, access.check(client("10.0.0.1")));
    }

    @Test
    void inProcessChannelsAreAllowed() {
        ClientAccess access = new ClientAccess(List.of("203.0.113.0/24"), 1, 1);
        EmbeddedChannel channel = new EmbeddedChannel();
        assertEquals(ALLOW, access.check(channel.remoteAddress()));
        assertEquals(ALLOW, access.check(channel.remoteAddress()));
        channel.finishAndReleaseAll();
    }
}
