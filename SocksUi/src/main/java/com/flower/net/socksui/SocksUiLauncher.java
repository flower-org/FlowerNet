package com.flower.net.socksui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RUN THIS, THIS IS THE CORRECT ONE
 */
public class SocksUiLauncher {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksUiLauncher.class);

    public static void main(String[] args) {
        LOGGER.info("Starting Socks UI");
        SocksUiApplication.main(args);
    }
}
