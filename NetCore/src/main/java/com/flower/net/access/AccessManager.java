package com.flower.net.access;

import com.flower.net.config.access.AccessConfig;

import java.net.InetAddress;

public interface AccessManager {
    boolean isAllowed(InetAddress address);
    boolean isAllowed(String name);

    static AccessManager of(AccessConfig accessConfig) {
        return new ConfigAccessManager(accessConfig);
    }

    static AccessManager allowAll() {
        return new AccessManager() {
            @Override public boolean isAllowed(InetAddress address) { return true; }
            @Override public boolean isAllowed(String name) { return true; }
        };
    }
}
