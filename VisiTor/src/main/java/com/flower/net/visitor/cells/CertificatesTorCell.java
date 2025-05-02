package com.flower.net.visitor.cells;

import com.flower.net.visitor.certificates.TorCertificate;
import com.flower.net.visitor.certificates.TorCertificateType;
import io.netty.buffer.ByteBuf;

import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import static com.flower.net.visitor.cells.CellCommand.CERTS;

public class CertificatesTorCell extends AbstractTorCell {
    public final List<TorCertificate> certificates = new ArrayList<>();

    public CertificatesTorCell(int circuitId, List<TorCertificate> certificateList) {
        super(circuitId, CERTS);
        for(TorCertificate certificate : certificateList) {
            certificates.add(certificate);
        }
    }

    @Override
    public void writeToBuffer(ByteBuf outBuffer) {
        outBuffer.writeShort((short)circuitId);
        outBuffer.writeByte((byte)command.code);

        int payloadLength = 1;//certCount
        for (TorCertificate cert : certificates) {
            payloadLength += 1;//certTypeCode
            payloadLength += 2;//certBufferSize
            payloadLength += cert.certificateBuffer.length;
        }

        outBuffer.writeShort((short)payloadLength);
        outBuffer.writeByte((byte)certificates.size());
        for (TorCertificate cert : certificates) {
            outBuffer.writeByte((byte)cert.certificateType.code);
            outBuffer.writeShort((short)cert.certificateBuffer.length);
            outBuffer.writeBytes(cert.certificateBuffer);
        }
    }

    static int checkPayloadLength(int payloadLength, int toRead) {
        if (payloadLength < toRead) {
            throw new RuntimeException(String.format("Payload Length %d insufficient to read %d bytes, " +
                    "TorCell likely corrupted.", payloadLength, toRead));
        }
        payloadLength -= toRead;
        return payloadLength;
    }

    /** Called from TorCell.readFromBuffer(buffer); */
    static CertificatesTorCell readFromBuffer(int circuitId, CellCommand code, int payloadLength, ByteBuf buffer) {
        if (code != CERTS) {
            throw new RuntimeException("Expected CellCommand CERTS, got " + code);
        }

        payloadLength = checkPayloadLength(payloadLength, 1);
        int certCount = buffer.readByte() & 0xFF;

        List<TorCertificate> certList = new ArrayList<>();
        for (int i = 0; i < certCount; i++) {
            payloadLength = checkPayloadLength(payloadLength, 1);
            int certTypeCode = buffer.readByte() & 0xFF;
            payloadLength = checkPayloadLength(payloadLength, 2);
            int certBufferSize = buffer.readShort() & 0xFFFF;
            payloadLength = checkPayloadLength(payloadLength, certBufferSize);
            byte[] certBuffer = new byte[certBufferSize];
            buffer.readBytes(certBuffer);

            try {
                certList.add(new TorCertificate(TorCertificateType.fromCode(certTypeCode), certBuffer));
            } catch (CertificateException | NoSuchAlgorithmException | SignatureException e) {
                throw new RuntimeException(e);
            }
        }

        return new CertificatesTorCell(circuitId, certList);
    }

    @Override
    public String toString() {
        return "CertificatesTorCell{" +
                "circuitId=" + circuitId +
                ", command=" + command + "/" + command.code +
                ", certificateCount=" + certificates.size() +
                ", certificates=" + certificates +
                '}';
    }
}
