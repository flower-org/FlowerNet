package com.flower.utils.evictlist;

public class ConcurrentEvictListWithFixedTimeout<T> extends ConcurrentEvictList<T> {
    final long elementTimeout;

    public ConcurrentEvictListWithFixedTimeout(long elementTimeout) {
        this.elementTimeout = elementTimeout;
    }

    @Override
    protected MutableEvictLinkedNode<T> createMutableEvictLinkedNode(T value) {
        return new TimeoutMutableEvictNode<>(value, elementTimeout);
    }

    @Override
    public int nonEvictedCount() {
        return super.naiveNonEvictedCount();
    }
}
