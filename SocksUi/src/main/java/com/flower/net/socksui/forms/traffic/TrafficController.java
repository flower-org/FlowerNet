package com.flower.net.socksui.forms.traffic;

public interface TrafficController {
    void clearCapturedData();
    void whitelist(String host, int port);
    void blacklist(String host, int port);
    void whitelistHost(String host);
    void blacklistHost(String host);
    void whitelistPort(int port);
    void blacklistPort(int port);
}
