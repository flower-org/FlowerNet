package com.flower.socksui.forms;

import com.flower.sockschain.config.SocksNode;
import com.flower.sockschain.config.SocksProtocolVersion;
import com.flower.sockschain.config.certs.local.LocalCertificate;
import com.flower.sockschain.config.certs.remote.RemoteCertificate;

import javax.annotation.Nullable;

public class FXSocksNode implements SocksNode {
    public final SocksNode node;

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
    public LocalCertificate clientCertificate() {
        return node.clientCertificate();
    }

    @Nullable
    @Override
    public RemoteCertificate rootServerCertificate() {
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
    public LocalCertificate getClientCertificate() { return clientCertificate(); }

    @Nullable
    public RemoteCertificate getRootServerCertificate() {
        return rootServerCertificate();
    }
}
