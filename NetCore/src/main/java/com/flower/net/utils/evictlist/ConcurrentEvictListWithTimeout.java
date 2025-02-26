package com.flower.net.utils.evictlist;

public class ConcurrentEvictListWithTimeout<T> extends ConcurrentEvictList<T> {
    public ConcurrentEvictListWithTimeout() {
        super();
    }

    public ConcurrentEvictListWithTimeout(boolean maintainCountReferences, boolean enableListeners) {
        super(maintainCountReferences, enableListeners);
    }

    public EvictLinkedNode<T> addElement(T value, long elementTimeoutMs) {
        MutableEvictLinkedNode<T> newElement = new TimeoutMutableEvictNode<>(value, elementTimeoutMs);
        return addElement(newElement);
    }

    /** We're forcing naiveCount since we rely on eviction mechanisms different from directly calling `markEvictable` */
    @Override
    public int nonEvictedCount() {
        return super.naiveCount(false);
    }
}
