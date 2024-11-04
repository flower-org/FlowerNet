package com.flower.socksui.forms;

import com.flower.sockschain.config.SocksNode;
import com.flower.sockschain.config.SocksProtocolVersion;

import javax.annotation.Nullable;

public class FXSocksNode implements SocksNode {
    final SocksNode node;

    public FXSocksNode(SocksNode node) {
        this.node = node;
    }

    @Override
    public SocksProtocolVersion socksProtocolVersion() {
        return node.socksProtocolVersion();
    }

    @Override
    public String serverAddress() {
        return node.serverAddress();
    }

    @Override
    public int serverPort() {
        return node.serverPort();
    }

    @Nullable
    @Override
    public String clientCertificate() {
        return node.clientCertificate();
    }

    @Nullable
    @Override
    public String rootServerCertificate() {
        return node.rootServerCertificate();
    }

    // -------- METHODS FOR JAVAFX TABLE SUPPORT --------

    public SocksProtocolVersion getSocksProtocolVersion() {
        return socksProtocolVersion();
    }

    public String getServerAddress() {
        return serverAddress();
    }

    public int getServerPort() {
        return serverPort();
    }

    @Nullable
    public String getClientCertificate() {
        return clientCertificate();
    }

    @Nullable
    public String getRootServerCertificate() {
        return rootServerCertificate();
    }
}
