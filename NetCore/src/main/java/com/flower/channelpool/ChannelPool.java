package com.flower.channelpool;

import com.flower.utils.evictlist.EvictLinkedNode;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

public interface ChannelPool {
    Future<EvictLinkedNode<Channel>> getChannel();
    void returnChannel(EvictLinkedNode<Channel> channelNode);
    void returnFailedChannel(EvictLinkedNode<Channel> channelNode);
}
