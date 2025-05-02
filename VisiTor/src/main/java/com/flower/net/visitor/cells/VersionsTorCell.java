package com.flower.net.visitor.cells;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

import static com.flower.net.visitor.cells.CellCommand.VERSIONS;

public class VersionsTorCell extends AbstractTorCell {
    protected final List<Integer> versions = new ArrayList<>();

    public VersionsTorCell(int circuitId, int... versionArr) {
        super(circuitId, VERSIONS);
        for(int v : versionArr) {
            versions.add(v);
        }
    }

    public VersionsTorCell(int circuitId, List<Integer> versionLst) {
        super(circuitId, VERSIONS);
        for(int v : versionLst) {
            versions.add(v);
        }
    }

    @Override
    public void writeToBuffer(ByteBuf outBuffer) {
        int payloadLength = versions.size() * 2;

        outBuffer.writeShort((short)circuitId);
        outBuffer.writeByte((byte)command.code);
        outBuffer.writeShort((short)payloadLength);
        for(int v : versions) {
            outBuffer.writeShort((short)v);
        }
    }

    /** Called from TorCell.readFromBuffer(buffer); */
    static VersionsTorCell readFromBuffer(int circuitId, CellCommand code, int payloadLength, ByteBuf buffer) {
        if (code != VERSIONS) {
            throw new RuntimeException("Expected CellCommand VERSIONS, got " + code);
        }

        List<Integer> versionList = new ArrayList<>();
        for (int i = 0; i < payloadLength/2; i++) {
            versionList.add(buffer.readShort() & 0xFFFF);
        }

        return new VersionsTorCell(circuitId, versionList);
    }

    @Override
    public String toString() {
        return "VersionsTorCell{" +
                "circuitId=" + circuitId +
                ", command=" + command + "/" + command.code +
                ", versions=" + versions +
                '}';
    }
}
