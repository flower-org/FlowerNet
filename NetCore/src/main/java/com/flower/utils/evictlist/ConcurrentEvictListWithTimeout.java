package com.flower.utils.evictlist;

public class ConcurrentEvictListWithTimeout<T> extends ConcurrentEvictList<T> {
    public ConcurrentEvictListWithTimeout() {
        super();
    }

    public ConcurrentEvictListWithTimeout(boolean maintainCountReferences) {
        super(maintainCountReferences);
    }

    public EvictLinkedNode<T> addElement(T value, long timeout) {
        MutableEvictLinkedNode<T> newElement = new TimeoutMutableEvictNode<>(value, timeout);
        return addElement(newElement);
    }

    /** We're forcing naiveCount since we rely on eviction mechanisms different from directly calling `markEvictable` */
    @Override
    public int nonEvictedCount() {
        return super.naiveCount(false);
    }
}
