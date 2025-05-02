package com.flower.net.visitor.cells;

import com.flower.net.visitor.certificates.TorUtils;
import io.netty.buffer.ByteBuf;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.flower.net.visitor.cells.CellCommand.NETINFO;
import static com.flower.net.visitor.certificates.TorUtils.FIXED_CELL_BODY_LEN;

/**
 * From https://spec.torproject.org/tor-spec/negotiating-channels.html#NETINFO-cells
 * <p>
 * - In the ATYPE/ALEN/AVAL fields, relays SHOULD send the addresses that they have advertised in their router descriptors.
 * Bridges and clients SHOULD send none of their own addresses.
 * <p>
 * - For the TIME field, relays send a (big-endian) integer holding the number of seconds since the Unix epoch.
 * Clients SHOULD send [00 00 00 00] as their timestamp, to avoid fingerprinting.
 * <p>
 * - Implementations MUST ignore unexpected bytes at the end of the NETINFO cell.
 * That's a reference to: https://spec.torproject.org/tor-spec/preliminaries.html#msg-len
 * Some message lengths are fixed in the Tor protocol.
 *  CELL_LEN(v), v < 4 = 512 (The length of a fixed-length cell.)
 */
public class NetInfoTorCell extends AbstractTorCell {
    enum NetInfoAddressType {
	    IP_V4(0x04),
    	IP_V6(0x06);

        public final int code;
        NetInfoAddressType(int code) {
            this.code = code;
        }

        public static NetInfoAddressType fromCode(int code) {
            switch(code) {
                case 0x04: return IP_V4;
                case 0x06: return IP_V6;
                default: throw new UnsupportedOperationException("TorCertificateKeyType code:" + code + " unknown");
            }
        }
    }

    public static class NetInfoAddress {
        final NetInfoAddressType addressType;
        final byte[] address;

        NetInfoAddress(NetInfoAddressType addressType, byte[] address) {
            this.addressType = addressType;
            this.address = address;
        }

        @Override
        public String toString() {
            String addressStr;
            try {
                addressStr = InetAddress.getByAddress(address).toString();
            } catch (UnknownHostException e) {
                addressStr = TorUtils.bytesToHex(address);
            }

            return "NetInfoAddress{" +
                    "addressType=" + addressType +
                    ", address=" + addressStr +
                    '}';
        }
    }

    /** Timestamp */
    public final long rawTime;
    public final Instant time;
    /** Other party’s address */
    public final NetInfoAddress otherAddress;
    /** This party’s addresses */
    public final List<NetInfoAddress> myAddresses = new ArrayList<>();

    public NetInfoTorCell(int circuitId, long rawTime, NetInfoAddress otherAddress, List<NetInfoAddress> myAddresses) {
        super(circuitId, NETINFO);
        this.rawTime = rawTime;
        this.time = Instant.ofEpochSecond(rawTime);
        this.otherAddress = otherAddress;
        this.myAddresses.addAll(myAddresses);
    }

    @Override
    public void writeToBuffer(ByteBuf outBuffer) {
        outBuffer.writeShort((short)circuitId);
        outBuffer.writeByte((byte)command.code);

        outBuffer.writeInt((int)rawTime);
        writeToBuffer(outBuffer, otherAddress);
        outBuffer.writeByte((byte)myAddresses.size());

        for(NetInfoAddress myAddresses : myAddresses) {
            writeToBuffer(outBuffer, myAddresses);
        }
    }

    void writeToBuffer(ByteBuf outBuffer, NetInfoAddress netInfoAddress) {
        outBuffer.writeByte((byte)netInfoAddress.addressType.code);
        outBuffer.writeByte((byte)netInfoAddress.address.length);
        outBuffer.writeBytes(netInfoAddress.address);
    }

    /** Called from TorCell.readFromBuffer(buffer); */
    static NetInfoTorCell readFromBuffer(int circuitId, CellCommand code, ByteBuf buffer) {
        assert(code == NETINFO);

        int readerIndexBefore = buffer.readerIndex();

        long rawTime = buffer.readInt() & 0xFFFFFFFFL;
        NetInfoAddress otherAddress = readNetInfoAddressFromBuffer(buffer);
        List<NetInfoAddress> myAddresses = new ArrayList<>();
        int addressCount = buffer.readByte() & 0xFF;
        for (int i = 0; i < addressCount; i++) {
            NetInfoAddress myAddress = readNetInfoAddressFromBuffer(buffer);
            myAddresses.add(myAddress);
        }
        buffer.readerIndex(readerIndexBefore + FIXED_CELL_BODY_LEN);

        return new NetInfoTorCell(circuitId, rawTime, otherAddress, myAddresses);
    }

    static NetInfoAddress readNetInfoAddressFromBuffer(ByteBuf buffer) {
        int addressTypeCode = buffer.readByte() & 0xFF;
        int addressLength = buffer.readByte() & 0xFF;
        byte[] address = new byte[addressLength];
        buffer.readBytes(address);

        return new NetInfoAddress(NetInfoAddressType.fromCode(addressTypeCode), address);
    }

    @Override
    public String toString() {
        return "NetInfoTorCell{" +
                "circuitId=" + circuitId +
                ", command=" + command +
                ", time=" + time +
                ", otherAddress=" + otherAddress +
                ", myAddresses=" + myAddresses +
                ", rawTime=" + rawTime +
                '}';
    }
}
