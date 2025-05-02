package com.flower.net.visitor.certificates;

import java.util.Arrays;

public class TorEdCertificateExtension {
    /** Type of extension */
    public final TorEdCertificateExtensionType extType;
    /** Control interpretation of extension */
    public final byte extFlags;
    /** Encoded extension body */
    public final byte[] extData;

    public TorEdCertificateExtension(TorEdCertificateExtensionType extType, byte extFlags, byte[] extData) {
        this.extType = extType;
        this.extFlags = extFlags;
        this.extData = extData;
    }

    @Override
    public String toString() {
        return "TorEdCertificateExtension{" +
                "extType=" + extType +
                ", extFlags=" + extFlags +
                ", extData=" + TorUtils.bytesToHex(extData) +
                '}';
    }
}
