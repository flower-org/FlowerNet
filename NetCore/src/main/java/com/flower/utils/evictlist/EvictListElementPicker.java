package com.flower.utils.evictlist;

import javax.annotation.Nullable;

public interface EvictListElementPicker<T> {
    @Nullable T getNonEvictedValue();
    @Nullable EvictLinkedNode<T> getNonEvictedNode();
}
