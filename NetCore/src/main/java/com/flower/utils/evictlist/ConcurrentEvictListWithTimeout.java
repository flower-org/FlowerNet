package com.flower.utils.evictlist;

public class ConcurrentEvictListWithTimeout<T> extends ConcurrentEvictList<T> {
    public EvictLinkedNode<T> addElement(T value, long timeout) {
        MutableEvictLinkedNode<T> newElement = new TimeoutMutableEvictNode<>(value, timeout);
        return addElement(newElement);
    }

    @Override
    public int nonEvictedCount() {
        return super.naiveNonEvictedCount();
    }
}
