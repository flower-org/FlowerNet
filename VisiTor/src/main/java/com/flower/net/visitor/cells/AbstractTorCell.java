package com.flower.net.visitor.cells;

import io.netty.buffer.ByteBuf;

import javax.annotation.Nullable;

public abstract class AbstractTorCell implements TorCell {
    protected final int circuitId;
    protected final CellCode command;

    public AbstractTorCell(int circuitId, CellCode command) {
        this.circuitId = circuitId;
        this.command = command;
    }

    @Override
    public int circuitId() {
        return circuitId;
    }

    @Override
    public CellCode command() {
        return command;
    }

    @Override
    public abstract void writeToBuffer(ByteBuf outBuffer);

    @Nullable static TorCell readFromBuffer(ByteBuf buffer) {
        // Make sure that we have enough bytes to read header
        if (buffer.readableBytes() < 3) {
            // TODO: double-check: my guess is that this return will allow for more data to accumulate in the buffer
            return null;
        }

        int circuitId = buffer.readShort() & 0xFFFF;
        int code = buffer.readByte() & 0xFF;

        // TODO: process cells without payload here, I'm not sure if such cells exist though

        // Make sure that we have enough bytes to read payloadLength
        if (buffer.readableBytes() < 2) {
            // If not, reset reader position
            buffer.readerIndex(buffer.readerIndex() - 3);
            // TODO: double-check: (see above)
            return null;
        }

        int payloadLength = buffer.readShort() & 0xFFFF;
        if (buffer.readableBytes() < payloadLength) {
            buffer.readerIndex(buffer.readerIndex() - 5);
            // TODO: double-check: (see above)
            return null;
        }

        CellCode command = CellCode.fromCode(code);

        switch (command) {
            case VERSIONS:
                return VersionsTorCell.readFromBuffer(circuitId, command, payloadLength, buffer);
            case PADDING:
            case CREATE:
            case CREATED:
            case RELAY:
            case DESTROY:
            case CREATE_FAST:
            case CREATED_FAST:
            case NETINFO:
            case RELAY_EARLY:
            case VPADDING:
            case CERTS:
            case AUTH_CHALLENGE:
            case AUTHENTICATE:
            case AUTHORIZE:
            default:
                throw new UnsupportedOperationException("CellCommand Code " + code + " not supported");
        }
    }
}
