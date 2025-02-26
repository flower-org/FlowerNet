package com.flower.net.utils.evictlist;

public class ConcurrentEvictListWithFixedTimeout<T> extends ConcurrentEvictList<T> {
    final long elementTimeoutMs;

    public ConcurrentEvictListWithFixedTimeout(long elementTimeoutMs) {
        this.elementTimeoutMs = elementTimeoutMs;
    }

    public ConcurrentEvictListWithFixedTimeout(boolean maintainCountReferences, boolean enableListeners, long elementTimeoutMs) {
        super(maintainCountReferences, enableListeners);
        this.elementTimeoutMs = elementTimeoutMs;
    }

    @Override
    protected MutableEvictLinkedNode<T> createMutableEvictLinkedNode(T value) {
        return new TimeoutMutableEvictNode<>(value, elementTimeoutMs);
    }

    /** We're forcing naiveCount since we rely on eviction mechanisms different from directly calling `markEvictable` */
    @Override
    public int nonEvictedCount() {
        return super.naiveCount(false);
    }
}
