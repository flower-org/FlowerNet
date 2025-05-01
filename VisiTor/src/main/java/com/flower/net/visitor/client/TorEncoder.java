package com.flower.net.visitor.client;

import com.flower.net.visitor.cells.TorCell;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class TorEncoder extends MessageToByteEncoder<TorCell> {
    @Override
    protected void encode(ChannelHandlerContext ctx, TorCell msg, ByteBuf out) throws Exception {
        msg.writeMessageToBuffer(out);
    }
}
