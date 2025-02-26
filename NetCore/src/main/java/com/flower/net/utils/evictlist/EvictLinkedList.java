package com.flower.net.utils.evictlist;

import javax.annotation.Nullable;
import java.util.Iterator;

public interface EvictLinkedList<T> {
    @Nullable
    EvictLinkedNode<T> root();
    Iterator<EvictLinkedNode<T>> iterator();
    int count();
    int nonEvictedCount();

    EvictLinkedNode<T> addElement(T element);
    void markEvictable(EvictLinkedNode<T> element);

    EvictListElementPicker<T> newElementPicker();

    void addListener(EvictionListener<T> listener);
    void removeListener(EvictionListener<T> listener);
}
