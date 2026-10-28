package com.flower.net.packetui.server;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.Protocol;
import com.flower.net.conntrack.ConnectionFilter;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConnectionFilterDeciderTest {
    static final int LOCAL_IN = 1;
    static final int LOCAL_OUT = 3;

    static byte[] ipv4(int protocol, int[] src, int[] dst, int srcPort, int dstPort, int fragmentOffset) {
        byte[] p = new byte[28];
        p[0] = 0x45;
        p[6] = (byte) (fragmentOffset >>> 8);
        p[7] = (byte) fragmentOffset;
        p[9] = (byte) protocol;
        for (int i = 0; i < 4; i++) {
            p[12 + i] = (byte) src[i];
            p[16 + i] = (byte) dst[i];
        }
        p[20] = (byte) (srcPort >>> 8);
        p[21] = (byte) srcPort;
        p[22] = (byte) (dstPort >>> 8);
        p[23] = (byte) dstPort;
        return p;
    }

    /** IPv6 ::1 -> 2001:db8::5 with a hop-by-hop header before TCP */
    static byte[] ipv6WithHopByHop(int srcPort, int dstPort) {
        byte[] p = new byte[40 + 8 + 20];
        p[0] = 0x60;
        p[6] = 0; // hop-by-hop
        p[23] = 1; // src ::1
        p[24] = 0x20; p[25] = 0x01; p[26] = 0x0d; p[27] = (byte) 0xb8; p[39] = 5;
        p[40] = 6; // next: TCP
        p[41] = 0; // (0 + 1) * 8 bytes
        p[48] = (byte) (srcPort >>> 8);
        p[49] = (byte) srcPort;
        p[50] = (byte) (dstPort >>> 8);
        p[51] = (byte) dstPort;
        return p;
    }

    record Call(String host, int port, SocketAddress from, @Nullable Protocol protocol) {}

    static class RecordingFilter implements ConnectionFilter {
        final List<Call> calls = new ArrayList<>();
        final Access result;

        RecordingFilter(Access result) { this.result = result; }

        @Override
        public Access approveConnection(String dstHost, int dstPort, Protocol protocol, @Nullable SocketAddress from) {
            calls.add(new Call(dstHost, dstPort, from, protocol));
            return result;
        }

        @Override public void allow(String host, int port) {}
        @Override public void deny(String host, int port) {}
        @Override public void allowHost(String host) {}
        @Override public void denyHost(String host) {}
        @Override public void allowPort(int port) {}
        @Override public void denyPort(int port) {}
    }

    @Test
    public void parsesIpv4Tcp() {
        PacketInfo info = PacketInfo.parse(ipv4(6, new int[]{10, 0, 0, 2}, new int[]{93, 184, 216, 34}, 40000, 443, 0));
        assertEquals(new PacketInfo(6, "10.0.0.2", 40000, "93.184.216.34", 443), info);
    }

    @Test
    public void ipv4NonFirstFragmentHasNoPorts() {
        PacketInfo info = PacketInfo.parse(ipv4(17, new int[]{1, 2, 3, 4}, new int[]{5, 6, 7, 8}, 53, 53, 10));
        assertNotNull(info);
        assertEquals(0, info.srcPort());
        assertEquals(0, info.dstPort());
    }

    @Test
    public void parsesIpv6CanonicalWithExtensionHeader() {
        PacketInfo info = PacketInfo.parse(ipv6WithHopByHop(50000, 80));
        assertEquals(new PacketInfo(6, "::1", 50000, "2001:db8::5", 80), info);
    }

    @Test
    public void rejectsGarbage() {
        assertNull(PacketInfo.parse(new byte[0]));
        assertNull(PacketInfo.parse(new byte[]{0x45, 0, 0}));
        assertNull(PacketInfo.parse(new byte[20])); // version 0
    }

    @Test
    public void outboundChecksDestination() {
        ConnectionFilterDecider decider = new ConnectionFilterDecider();
        RecordingFilter filter = new RecordingFilter(Access.ALLOW);
        decider.addConnectionFilter(filter);

        byte[] packet = ipv4(17, new int[]{10, 0, 0, 2}, new int[]{8, 8, 8, 8}, 40000, 53, 0);
        assertTrue(decider.decide(packet, 1, 0x0800, LOCAL_OUT, 0, 0, 2));
        Call call = filter.calls.get(0);
        assertEquals("8.8.8.8", call.host());
        assertEquals(53, call.port());
        assertEquals(Protocol.UDP, call.protocol());
        InetSocketAddress from = (InetSocketAddress) call.from();
        assertEquals("10.0.0.2", from.getHostString());
        assertEquals(40000, from.getPort());
    }

    @Test
    public void inboundChecksSourceAndDenies() {
        ConnectionFilterDecider decider = new ConnectionFilterDecider();
        RecordingFilter filter = new RecordingFilter(Access.DENY);
        decider.addConnectionFilter(filter);

        byte[] packet = ipv4(6, new int[]{93, 184, 216, 34}, new int[]{10, 0, 0, 2}, 443, 40000, 0);
        assertFalse(decider.decide(packet, 1, 0x0800, LOCAL_IN, 0, 2, 0));
        assertEquals("93.184.216.34", filter.calls.get(0).host());
        assertEquals(443, filter.calls.get(0).port());
    }

    @Test
    public void icmpHasNoPortAndUnparseableIsDropped() {
        ConnectionFilterDecider decider = new ConnectionFilterDecider();
        RecordingFilter filter = new RecordingFilter(Access.ALLOW);
        decider.addConnectionFilter(filter);

        assertTrue(decider.decide(ipv4(1, new int[]{10, 0, 0, 2}, new int[]{1, 1, 1, 1}, 0, 0, 0), 1, 0x0800, LOCAL_OUT, 0, 0, 2));
        assertEquals(0, filter.calls.get(0).port());
        assertEquals(Protocol.ICMP, filter.calls.get(0).protocol());

        assertFalse(decider.decide(new byte[]{1, 2, 3}, 2, 0, LOCAL_OUT, 0, 0, 2));
    }
}
