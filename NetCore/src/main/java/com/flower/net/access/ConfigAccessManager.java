package com.flower.net.access;

import java.net.InetAddress;
import com.flower.net.config.access.AccessConfig;

public class ConfigAccessManager implements AccessManager {
    final AccessConfig accessConfig;

    public ConfigAccessManager(AccessConfig accessConfig) {
        this.accessConfig = accessConfig;
    }

    @Override
    public boolean isAllowed(InetAddress address) {
        return false;
    }

    @Override
    public boolean isAllowed(String name) {
        return false;
    }
}
