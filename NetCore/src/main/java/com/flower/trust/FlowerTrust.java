package com.flower.trust;

import com.flower.utils.PkiUtil;

import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

public class FlowerTrust {
    public static final KeyStore TRUST_STORE_WITH_SERVER_CA = PkiUtil.loadTrustStore("server_CA2.crt");
    public static final TrustManagerFactory TRUST_MANAGER_WITH_SERVER_CA = PkiUtil.getTrustManagerForKeyStore(TRUST_STORE_WITH_SERVER_CA);

    public static final KeyStore TRUST_STORE_WITH_CLIENT_CA = PkiUtil.loadTrustStore("client_CA.crt");
    public static final TrustManagerFactory TRUST_MANAGER_WITH_CLIENT_CA = PkiUtil.getTrustManagerForKeyStore(TRUST_STORE_WITH_CLIENT_CA);
}
