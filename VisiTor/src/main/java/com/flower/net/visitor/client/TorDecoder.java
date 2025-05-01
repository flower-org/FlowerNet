package com.flower.net.visitor.client;

import com.flower.net.visitor.cells.TorCell;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class TorDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        TorCell cell = TorCell.readFromBuffer(in);
        if (cell != null) {
            out.add(cell);
        }
    }
}
