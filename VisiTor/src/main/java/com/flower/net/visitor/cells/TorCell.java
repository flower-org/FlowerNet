package com.flower.net.visitor.cells;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;

public interface TorCell {
    int circuitId();
    CellCommand command();

    void writeToBuffer(ByteBuf outBuffer);

    @Nullable
    static TorCell readFromBuffer(ByteBuf buffer) {
        return AbstractTorCell.readFromBuffer(buffer);
    }
}
