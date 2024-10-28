package com.flower.utils.evictlist;

// Extended implementation of EvictNode that supports timeout functionality
public class TimeoutMutableEvictNode<T> extends MutableEvictNode<T> {
    protected final long timeoutTimestamp;

    protected TimeoutMutableEvictNode(T value, long timeoutMs) {
        super(value);
        timeoutTimestamp = System.currentTimeMillis() + timeoutMs;
    }

    @Override
    public boolean isEvicted() {
        if (!super.isEvicted() && System.currentTimeMillis() > timeoutTimestamp) {
            markForEviction();
        }
        return super.isEvicted();
    }
}
