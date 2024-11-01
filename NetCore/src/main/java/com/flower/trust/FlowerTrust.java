package com.flower.trust;

import com.flower.utils.PkiUtil;

import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

public class FlowerTrust {
    public static final KeyStore TRUST_STORE = PkiUtil.loadTrustStore("flower_ca.crt");
    public static final TrustManagerFactory TRUST_MANAGER = PkiUtil.getTrustManagerForKeyStore(TRUST_STORE);
}
