package com.flower.socksui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocksUiLauncher {
    final static Logger LOGGER = LoggerFactory.getLogger(SocksUiLauncher.class);

    public static void main(String[] args) {
        LOGGER.info("Starting Socks UI");
        SocksUiApplication.main(args);
    }
}
