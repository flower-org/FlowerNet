package com.flower.channelpool;

import com.flower.utils.evictlist.EvictLinkedNode;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Promise;

public interface ChannelPool {
    Promise<EvictLinkedNode<Channel>> getChannel();
    void returnChannel(EvictLinkedNode<Channel> channelNode);
    void returnFailedChannel(EvictLinkedNode<Channel> channelNode);
}
