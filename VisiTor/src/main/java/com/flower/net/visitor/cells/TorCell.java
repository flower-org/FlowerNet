package com.flower.net.visitor.cells;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;

public interface TorCell {
    int circuitId();
    CellCode command();

    void writeMessageToBuffer(ByteBuf outBuffer);

    @Nullable
    static TorCell readFromBuffer(ByteBuf buffer) {
        return AbstractTorCell.readFromBuffer(buffer);
    }
}
