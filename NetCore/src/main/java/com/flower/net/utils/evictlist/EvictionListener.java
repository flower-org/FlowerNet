package com.flower.net.utils.evictlist;

import java.util.Iterator;

public interface EvictionListener<T> {
    void added(EvictLinkedNode<T> element);
    void evicted(Iterator<EvictLinkedNode<T>> evictedIterator);
}
