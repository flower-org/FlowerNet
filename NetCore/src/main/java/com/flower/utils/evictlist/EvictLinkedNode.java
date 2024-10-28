package com.flower.utils.evictlist;

import javax.annotation.Nullable;

public interface EvictLinkedNode<T> {
    @Nullable EvictLinkedNode<T> next();
    T value();
    boolean isEvicted();
}
