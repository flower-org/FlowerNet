package com.flower.utils.evictlist;

import javax.annotation.Nullable;

public interface EvictLinkedList<T> {
    @Nullable
    EvictLinkedNode<T> root();
    int count();
    int nonEvictedCount();

    EvictLinkedNode<T> addElement(T element);
    void markEvictable(EvictLinkedNode<T> element);

    EvictListElementPicker<T> getElementPicker();

    void addListener(EvictionListener<T> listener);
    void removeListener(EvictionListener<T> listener);
}
