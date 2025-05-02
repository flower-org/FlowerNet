package com.flower.net.visitor.cells;

import com.flower.net.visitor.certificates.TorUtils;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

import static com.flower.net.visitor.cells.CellCommand.AUTH_CHALLENGE;

/**
 * From https://spec.torproject.org/tor-spec/channels.html#does-initiator-authenticate
 * <p>
 * - As part of this handshake, the initiator MAY also prove cryptographic ownership of its own relay identities,
 * if it has any: public relays SHOULD prove their identities when they initiate a channel,
 * whereas clients and bridges SHOULD NOT do so.
 */
public class AuthChallengeTorCell extends AbstractTorCell {
    public final byte[] challenge;
    public final List<AuthMethod> methods = new ArrayList<>();

    enum AuthMethod {
        /** [00 01] RSA-SHA256-TLSSecret (Obsolete) */
        RSA_SHA256_TLSSECRET(0x0001),
        /** [00 02] (Historical, never implemented) */
        /** [00 03] Ed25519-SHA256-RFC5705 */
        ED25519_SHA256_RFC5705(0x0003);

        public final int code;
        AuthMethod(int code) {
            this.code = code;
        }

        public static AuthMethod fromCode(int code) {
            switch(code) {
                case 0x0001: return RSA_SHA256_TLSSECRET;
                case 0x0003: return ED25519_SHA256_RFC5705;
                default: throw new UnsupportedOperationException("AuthMethod code:" + code + " unknown");
            }
        }
    }

    public AuthChallengeTorCell(int circuitId, byte[] challenge, List<AuthMethod> methods) {
        super(circuitId, AUTH_CHALLENGE);
        this.challenge = challenge;
        this.methods.addAll(methods);
    }

    /** Called from TorCell.readFromBuffer(buffer); */
    static AuthChallengeTorCell readFromBuffer(int circuitId, CellCommand code, int payloadLength, ByteBuf buffer) {
        assert(code == AUTH_CHALLENGE);

        byte[] challenge = new byte[32];
        buffer.readBytes(challenge);

        List<AuthMethod> methodList = new ArrayList<>();
        int methodsCount = buffer.readShort() & 0xFF;
        for (int i = 0; i < methodsCount; i++) {
            methodList.add(AuthMethod.fromCode(buffer.readShort() & 0xFFFF));
        }

        return new AuthChallengeTorCell(circuitId, challenge, methodList);
    }

    @Override
    public void writeToBuffer(ByteBuf outBuffer) {
        outBuffer.writeBytes(challenge);
        outBuffer.writeShort(methods.size());
        for (AuthMethod method : methods) {
            outBuffer.writeShort(method.code);
        }
    }

    @Override
    public String toString() {
        return "AuthChallengeCell{" +
                "circuitId=" + circuitId +
                ", command=" + command +
                ", challenge=" + TorUtils.bytesToHex(challenge) +
                ", methods=" + methods +
                '}';
    }
}
