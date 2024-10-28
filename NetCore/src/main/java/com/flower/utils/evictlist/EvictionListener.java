package com.flower.utils.evictlist;

public interface EvictionListener<T> {
    void evicted(EvictLinkedNode<T> element);
}
