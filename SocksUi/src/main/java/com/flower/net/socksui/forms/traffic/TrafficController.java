package com.flower.net.socksui.forms.traffic;

public interface TrafficController {
    void clearCapturedData();
    void allow(String host, int port);
    void deny(String host, int port);
    void allowHost(String host);
    void denyHost(String host);
    void allowPort(int port);
    void denyPort(int port);
}
