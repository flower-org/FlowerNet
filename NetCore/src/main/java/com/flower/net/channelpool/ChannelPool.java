package com.flower.net.channelpool;

import com.flower.net.utils.evictlist.EvictLinkedNode;
import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;

public interface ChannelPool {
    //TODO: attach channel info, e.g. HTTP path or some source, to allow multi-source
    Future<EvictLinkedNode<Channel>> getChannel();
    void returnChannel(EvictLinkedNode<Channel> channelNode);
    void returnFailedChannel(EvictLinkedNode<Channel> channelNode);
}
