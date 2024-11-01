package com.flower.sockschain.client;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

/** We need this channel initializer to have our channel connect with empty pipeline.  */
public class EmptyPipelineChannelInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        //Nothing in the Pipeline
    }
}
