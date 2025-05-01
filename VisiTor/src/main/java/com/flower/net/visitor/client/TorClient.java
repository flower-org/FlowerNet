package com.flower.net.visitor.client;

import io.netty.util.concurrent.Future;

public interface TorClient {
    Future<String> loadDirectory();
    Future<String> connect2();
    Future<String> extend2();
    void shutdown();
}
