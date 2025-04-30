package com.flower.net.visitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CORRECT, RUN THIS, THIS IS THE CORRECT ONE
 */
public class VisiTorLauncher {
    final static Logger LOGGER = LoggerFactory.getLogger(VisiTorLauncher.class);

    public static void main(String[] args) {
        LOGGER.info("Starting VisiTOR");
        VisiTorApplication.main(args);
    }
}
