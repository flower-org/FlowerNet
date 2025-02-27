package com.flower.net.access;

import java.net.InetAddress;

import com.flower.net.config.access.Access;
import com.flower.net.config.access.AccessConfig;

public class ConfigAccessManager implements AccessManager {
    final AccessConfig accessConfig;

    public ConfigAccessManager(AccessConfig accessConfig) {
        this.accessConfig = accessConfig;
    }

    @Override
    public Access accessCheck(InetAddress address, int port) {
        return Access.ALLOW;
    }

    @Override
    public Access accessCheck(String name, int port) {
        return Access.DENY;
    }
}
