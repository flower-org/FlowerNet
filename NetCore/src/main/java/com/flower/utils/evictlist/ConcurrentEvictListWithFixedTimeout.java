package com.flower.utils.evictlist;

public class ConcurrentEvictListWithFixedTimeout<T> extends ConcurrentEvictList<T> {
    final long elementTimeout;

    public ConcurrentEvictListWithFixedTimeout(long elementTimeout) {
        this.elementTimeout = elementTimeout;
    }

    public ConcurrentEvictListWithFixedTimeout(boolean maintainCountReferences, boolean enableListeners, long elementTimeout) {
        super(maintainCountReferences, enableListeners);
        this.elementTimeout = elementTimeout;
    }

    @Override
    protected MutableEvictLinkedNode<T> createMutableEvictLinkedNode(T value) {
        return new TimeoutMutableEvictNode<>(value, elementTimeout);
    }

    /** We're forcing naiveCount since we rely on eviction mechanisms different from directly calling `markEvictable` */
    @Override
    public int nonEvictedCount() {
        return super.naiveCount(false);
    }
}
