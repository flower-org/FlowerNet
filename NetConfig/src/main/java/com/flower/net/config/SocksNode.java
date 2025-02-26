package com.flower.net.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flower.net.config.certs.local.LocalCertificate;
import com.flower.net.config.certs.remote.ImmutableRemoteCertificate;
import com.flower.net.config.certs.remote.ImmutableRemoteCertificateFileConfig;
import com.flower.net.config.certs.remote.RemoteCertificate;
import com.flower.pkitool.PkiUtil;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.security.KeyStore;

@Value.Immutable
@JsonSerialize(as = ImmutableSocksNode.class)
@JsonDeserialize(as = ImmutableSocksNode.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface SocksNode {
    @JsonProperty
    SocksProtocolVersion socksProtocolVersion();

    @JsonProperty
    String serverAddress();

    @JsonProperty
    int serverPort();

    //TLS Settings for Socks5t and Socks+
    @JsonProperty
    @Nullable LocalCertificate clientCertificate();

    @JsonProperty
    @Nullable RemoteCertificate rootServerCertificate();

    default String getClientCertificateStr() {
        if (clientCertificate() != null) {
            if (clientCertificate().fileConfig() != null) {
                return clientCertificate().fileConfig().certificateFile();
            } else if (clientCertificate().pkcs11Config() != null) {
                return clientCertificate().pkcs11Config().libraryPath();
            } else if (clientCertificate().resourceConfig() != null) {
                return clientCertificate().resourceConfig().certificateResourceName();
            } else if (clientCertificate().bksConfig() != null) {
                return clientCertificate().bksConfig().bksFile();
            }
        }
        return "N/A";
    }

    default String getRootServerCertificateStr() {
        if (rootServerCertificate() != null) {
            if (rootServerCertificate().fileConfig() != null) {
                return rootServerCertificate().fileConfig().certificateFile();
            } else if (rootServerCertificate().pkcs11Config() != null) {
                return rootServerCertificate().pkcs11Config().libraryPath();
            } else if (rootServerCertificate().resourceConfig() != null) {
                return rootServerCertificate().resourceConfig().certificateResourceName();
            } else if (rootServerCertificate().bksConfig() != null) {
                return rootServerCertificate().bksConfig().bksFile();
            }
        }
        return "N/A";
    }

    static SocksNode of(SocksProtocolVersion socksProtocolVersion, String serverAddress, int serverPort) {
        return ImmutableSocksNode.builder()
                .socksProtocolVersion(socksProtocolVersion)
                .serverAddress(serverAddress)
                .serverPort(serverPort)
                .build();
    }

    static SocksNode of(SocksProtocolVersion socksProtocolVersion, String serverAddress, int serverPort, String certificateFilename) {
        return ImmutableSocksNode.builder()
                .socksProtocolVersion(socksProtocolVersion)
                .serverAddress(serverAddress)
                .serverPort(serverPort)
                .rootServerCertificate(ImmutableRemoteCertificate.builder()
                    .fileConfig(
                        ImmutableRemoteCertificateFileConfig.builder().certificateFile(certificateFilename).build()
                    ).build())
                .build();
    }

    default TrustManagerFactory buildTrustManagerFactory() {
        if (rootServerCertificate() != null) {
            if (rootServerCertificate().fileConfig() != null) {
                File certificateFile = new File(rootServerCertificate().fileConfig().certificateFile());
                try {
                    KeyStore keyStore = PkiUtil.loadTrustStore(certificateFile);
                    return PkiUtil.getTrustManagerForKeyStore(keyStore);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException("Configured certificate File not found", e);
                }
            } else if (rootServerCertificate().resourceConfig() != null) {
                KeyStore keyStore = PkiUtil.loadTrustStore(rootServerCertificate().resourceConfig().certificateResourceName());
                return PkiUtil.getTrustManagerForKeyStore(keyStore);
            } else if (rootServerCertificate().pkcs11Config() != null) {
                throw new UnsupportedOperationException("PKCS#11 config not supported yet");
            } else if (rootServerCertificate().bksConfig() != null) {
                throw new UnsupportedOperationException("BKS config not supported yet");
            }
        }

        throw new RuntimeException("Certificate File config not found");
    }
}
