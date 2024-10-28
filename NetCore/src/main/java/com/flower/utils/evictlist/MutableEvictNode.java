package com.flower.utils.evictlist;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// Basic implementation of EvictNode
public class MutableEvictNode<T> implements MutableEvictLinkedNode<T> {
    protected final AtomicReference<MutableEvictLinkedNode<T>> nextElement;
    protected AtomicBoolean markedForEviction;
    protected final T value;

    protected MutableEvictNode(T value) {
        this.nextElement = new AtomicReference<>();
        this.value = value;
        this.markedForEviction = new AtomicBoolean(false);
    }

    @Override
    public T value() {
        return value;
    }

    /** Next can be set only once */
    @Override
    public boolean setNext(MutableEvictLinkedNode<T> next) {
        return nextElement.compareAndSet(null, next);
    }

    @Override
    @Nullable
    public MutableEvictLinkedNode<T> next() {
        return nextElement.get();
    }

    /** Can't be un-evicted */
    @Override
    public boolean markForEviction() {
        return markedForEviction.compareAndSet(false, true);
    }

    @Override
    public boolean isEvicted() { return markedForEviction.get(); }
}
