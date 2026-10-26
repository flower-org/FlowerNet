package com.flower.net.packetui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CORRECT, RUN THIS, THIS IS THE CORRECT ONE
 */
public class PacketUiLauncher {
    final static Logger LOGGER = LoggerFactory.getLogger(PacketUiLauncher.class);

    public static void main(String[] args) {
        LOGGER.info("Starting Packet UI");
        PacketUiApplication.main(args);
    }
}
