package com.flower.net.access;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.AccessConfig;

import java.net.InetAddress;

public interface AccessManager {
    Access accessCheck(InetAddress address, int port);
    Access accessCheck(String name, int port);

    static AccessManager of(AccessConfig accessConfig) {
        return new ConfigAccessManager(accessConfig);
    }

    static AccessManager allowAll() {
        return new AccessManager() {
            @Override public Access accessCheck(InetAddress address, int port) { return Access.ALLOW; }
            @Override public Access accessCheck(String name, int port) { return Access.ALLOW; }
        };
    }
}
