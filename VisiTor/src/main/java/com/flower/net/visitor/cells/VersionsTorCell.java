package com.flower.net.visitor.cells;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

import static com.flower.net.visitor.cells.CellCode.VERSIONS;

public class VersionsTorCell extends AbstractTorCell {
    protected final List<Integer> versionList = new ArrayList<>();

    public VersionsTorCell(int circuitId, int... versions) {
        super(circuitId, VERSIONS);
        for(int v : versions) {
            versionList.add(v);
        }
    }

    public VersionsTorCell(int circuitId, List<Integer> versions) {
        super(circuitId, VERSIONS);
        for(int v : versions) {
            versionList.add(v);
        }
    }

    @Override
    public void writeMessageToBuffer(ByteBuf outBuffer) {
        int payloadLength = versionList.size() * 2;

        outBuffer.writeShort((short)circuitId);
        outBuffer.writeByte((byte)command.code);
        outBuffer.writeShort((short)payloadLength);
        for(int v : versionList) {
            outBuffer.writeShort((short)v);
        }
    }

    /** Called from TorCell.readFromBuffer(buffer); */
    static VersionsTorCell readFromBuffer(int circuitId, CellCode code, int payloadLength, ByteBuf buffer) {
        assert(code == VERSIONS);

        List<Integer> versionList = new ArrayList<>();
        for (int i = 0; i < payloadLength/2; i++) {
            versionList.add(buffer.readShort() & 0xFFFF);
        }

        return new VersionsTorCell(circuitId, versionList);
    }
}
