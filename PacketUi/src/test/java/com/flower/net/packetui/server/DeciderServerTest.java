package com.flower.net.packetui.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeciderServerTest {
    record Request(byte[] packet, boolean truncated, int packetId) {}

    @TempDir Path dir;
    final List<Request> requests = new ArrayList<>();
    DeciderServer server;
    Path socket;

    @BeforeEach
    void start() throws IOException {
        server = new DeciderServer((packet, truncated, packetId, hwProto, hook, mark, indev, outdev) -> {
            synchronized (requests) {
                requests.add(new Request(packet, truncated, packetId));
            }
            return packetId % 2 == 0;
        });
        socket = dir.resolve("nfbridge.sock");
        server.start(socket);
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    static void writeRequest(DataOutputStream out, long corr, int packetId, int flags, byte[] payload)
            throws IOException {
        out.writeInt(0x4E464252);
        out.writeShort(1);
        out.writeShort(1);
        out.writeLong(corr);
        out.writeInt(packetId);
        out.writeShort(0x0800);
        out.writeByte(3);
        out.writeByte(flags);
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(2);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    /** @return the decision */
    static int readResponse(DataInputStream in, long corr) throws IOException {
        assertEquals(0x4E464252, in.readInt());
        assertEquals(1, in.readUnsignedShort());
        assertEquals(2, in.readUnsignedShort());
        assertEquals(corr, in.readLong());
        return in.readInt();
    }

    @Test
    public void passesTruncatedFlagToDecider() throws IOException {
        try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(socket));
            var out = new DataOutputStream(Channels.newOutputStream(ch));
            var in = new DataInputStream(Channels.newInputStream(ch));

            writeRequest(out, 1, 2, DeciderServer.FLAG_TRUNCATED, new byte[]{0x45, 1, 2});
            assertEquals(1, readResponse(in, 1)); // ALLOW
            writeRequest(out, 2, 3, 0, new byte[]{0x45, 4});
            assertEquals(2, readResponse(in, 2)); // DROP
        }
        synchronized (requests) {
            assertEquals(2, requests.size());
            assertTrue(requests.get(0).truncated());
            assertArrayEquals(new byte[]{0x45, 1, 2}, requests.get(0).packet());
            assertFalse(requests.get(1).truncated());
            assertArrayEquals(new byte[]{0x45, 4}, requests.get(1).packet());
        }
    }

    @Test
    public void reservedFlagsCloseConnection() throws IOException {
        try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(socket));
            var out = new DataOutputStream(Channels.newOutputStream(ch));
            var in = new DataInputStream(Channels.newInputStream(ch));

            writeRequest(out, 1, 2, 0x02, new byte[]{0x45});
            assertThrows(IOException.class, in::readInt);
        }
        synchronized (requests) {
            assertTrue(requests.isEmpty());
        }
    }
}
