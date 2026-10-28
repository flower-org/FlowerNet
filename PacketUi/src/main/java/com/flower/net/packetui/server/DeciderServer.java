package com.flower.net.packetui.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/** nfbridge IPC decider server over a Unix-domain socket. Accepts one connection at a time until stopped. */
public final class DeciderServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeciderServer.class);

    private static final int MAGIC = 0x4E464252;      // "NFBR"
    private static final int VERSION = 1;
    private static final int TYPE_REQUEST = 1;
    private static final int TYPE_RESPONSE = 2;
    private static final int DECISION_ALLOW = 1;
    private static final int DECISION_DROP = 2;
    private static final int MAX_PAYLOAD = 65535;

    public interface PacketDecider {
        boolean decide(byte[] packet, int packetId, int hwProto, int hook, int mark, int indev, int outdev);
    }

    private final PacketDecider decider;

    @Nullable private volatile ServerSocketChannel server;
    @Nullable private volatile SocketChannel client;
    @Nullable private Thread acceptThread;
    @Nullable private Path socketFile;

    public DeciderServer(PacketDecider decider) {
        this.decider = decider;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized void start(Path socketPath) throws IOException {
        if (server != null) {
            throw new IllegalStateException("Server already running");
        }
        Files.deleteIfExists(socketPath);
        ServerSocketChannel newServer = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            newServer.bind(UnixDomainSocketAddress.of(socketPath));
        } catch (IOException | RuntimeException e) {
            closeQuietly(newServer);
            throw e;
        }
        server = newServer;
        socketFile = socketPath;

        Thread thread = new Thread(() -> acceptLoop(newServer), "decider-server-" + socketPath);
        thread.setDaemon(true);
        acceptThread = thread;
        thread.start();
    }

    public synchronized void stop() {
        ServerSocketChannel currentServer = server;
        if (currentServer == null) {
            return;
        }
        server = null;
        closeQuietly(currentServer);
        SocketChannel currentClient = client;
        if (currentClient != null) {
            closeQuietly(currentClient);
        }
        Thread thread = acceptThread;
        acceptThread = null;
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Path path = socketFile;
        socketFile = null;
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                LOGGER.warn("Failed to delete socket file {}", path, e);
            }
        }
    }

    private void acceptLoop(ServerSocketChannel serverChannel) {
        while (serverChannel.isOpen()) {
            try (SocketChannel ch = serverChannel.accept()) {
                client = ch;
                try (var in = new DataInputStream(new BufferedInputStream(Channels.newInputStream(ch)));
                     var out = new DataOutputStream(new BufferedOutputStream(Channels.newOutputStream(ch)))) {
                    serve(in, out);
                } finally {
                    client = null;
                }
            } catch (IOException e) {
                if (serverChannel.isOpen()) {
                    // nfbridge disconnected or sent a malformed request; accept the next connection.
                    LOGGER.debug("Connection ended: {}", e.toString());
                }
            }
        }
    }

    /** Serves one connection until nfbridge disconnects (EOF) or the stream goes bad. */
    private void serve(DataInputStream in, DataOutputStream out) throws IOException {
        while (true) {
            if (in.readInt() != MAGIC || in.readUnsignedShort() != VERSION
                    || in.readUnsignedShort() != TYPE_REQUEST) {
                throw new IOException("bad request header");
            }
            long corr = in.readLong();
            int packetId = in.readInt();
            int hwProto = in.readUnsignedShort();
            int hook = in.readUnsignedByte();
            in.readUnsignedByte();                   // flags, reserved
            int mark = in.readInt();
            int indev = in.readInt();
            int outdev = in.readInt();
            int len = in.readInt();
            if (len < 0 || len > MAX_PAYLOAD) {
                throw new IOException("bad payload length: " + len);
            }
            byte[] packet = in.readNBytes(len);
            if (packet.length != len) {
                throw new IOException("truncated payload");
            }

            boolean allow;
            try {
                allow = decider.decide(packet, packetId, hwProto, hook, mark, indev, outdev);
            } catch (RuntimeException e) {
                LOGGER.error("Decider failed, allowing packet {}", packetId, e);
                allow = true;
            }

            out.writeInt(MAGIC);
            out.writeShort(VERSION);
            out.writeShort(TYPE_RESPONSE);
            out.writeLong(corr);
            out.writeInt(allow ? DECISION_ALLOW : DECISION_DROP);
            out.flush();
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
