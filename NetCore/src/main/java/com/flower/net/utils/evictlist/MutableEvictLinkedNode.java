package com.flower.net.utils.evictlist;

import javax.annotation.Nullable;

public interface MutableEvictLinkedNode<T> extends EvictLinkedNode<T> {
    boolean setNext(MutableEvictLinkedNode<T> next);
    @Nullable
    MutableEvictLinkedNode<T> next();

    /** @return `false` - was already marked, no change; `true` - was marked for eviction */
    boolean markForEviction();
}
