package com.flower.utils.evictlist;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * List that operates under expectation that its elements will gradually become evictable (or "expire"), approximately
 * in the same order in which those elements were added.
 * Then it automatically evicts evictable (or "expired") elements.
 *
 * Ideally, all elements should have comparable life expectancy, since eviction will get stuck on elements with
 * abnormally long lifespans.
 * Great to use with anything that times out, like requests or connections.
 *
 * In a situation when all elements are evicted, one element (root) still stays in the list in evicted state,
 * for data structure to function normally and to avoid race conditions.
 * More generally, elements can exist in the list in evicted state, so please always check `isEvicted()`.
 */
public class ConcurrentEvictList<T> implements EvictLinkedList<T> {
    protected final AtomicReference<MutableEvictLinkedNode<T>> root;
    // TODO: we might want to avoid overhead on maintaining counts, and fall back to naive O(N) counting
    protected final AtomicInteger count;
    protected final AtomicInteger nonEvictedCount;
    protected final boolean maintainCountReferences;
    protected final boolean enableListeners;
    protected final ConcurrentHashMap<EvictionListener<T>, EvictionListener<T>> evictionListeners;

    public ConcurrentEvictList() {
        this(false, false);
    }

    public ConcurrentEvictList(boolean maintainCountReferences, boolean enableListeners) {
        this.root = new AtomicReference<>();
        this.count = new AtomicInteger(0);
        this.nonEvictedCount = new AtomicInteger(0);
        this.maintainCountReferences = maintainCountReferences;
        this.enableListeners = enableListeners;
        this.evictionListeners = new ConcurrentHashMap<>();
    }

    /** Root can be returned while in evicted state, please check `isEvicted`.
     * Root can be `null` if the list was newly created and didn't accept any elements yet.
     * Calling this method runs eviction */
    @Override
    public @Nullable MutableEvictLinkedNode<T> root() {
        return runEvictionAndGetNewRoot();
    }

    /** Total element count, including evicted */
    @Override
    public int count() {
        if (maintainCountReferences) {
            return count.get();
        } else {
            return naiveCount(true);
        }
    }

    /** Healthy (non-evicted) element count */
    @Override
    public int nonEvictedCount() {
        if (maintainCountReferences) {
            return nonEvictedCount.get();
        } else {
            return naiveCount(false);
        }
    }

    protected int naiveCount(boolean includeEvicted) {
        int count = 0;
        MutableEvictLinkedNode<T> cursor = root.get();
        while (cursor != null) {
            if (includeEvicted || !cursor.isEvicted()) {
                count++;
            }
            cursor = cursor.next();
        }
        return count;
    }

    /** Add a new element to the list.
     * Calling this method runs eviction */
    @Override
    public EvictLinkedNode<T> addElement(T value) {
        MutableEvictLinkedNode<T> newElement = createMutableEvictLinkedNode(value);
        return addElement(newElement);
    }

    protected MutableEvictLinkedNode<T> createMutableEvictLinkedNode(T value) {
        return new MutableEvictNode<>(value);
    }

    protected EvictLinkedNode<T> addElement(MutableEvictLinkedNode<T> newElement) {
        while (true) {
            MutableEvictLinkedNode<T> currentRoot = runEvictionAndGetNewRoot();
            if (currentRoot == null) {
                if (root.compareAndSet(null, newElement)) {
                    // Initialized root
                    if (maintainCountReferences) {
                        count.incrementAndGet();
                        nonEvictedCount.incrementAndGet();
                    }
                    // notify listeners
                    if (enableListeners) {
                        for (EvictionListener<T> evictionListener : evictionListeners.keySet()) {
                            evictionListener.added(newElement);
                        }
                    }
                    return newElement;
                } else {
                    currentRoot = root.get();
                }
            } else {
                MutableEvictLinkedNode<T> last = currentRoot;
                while (true) {
                    MutableEvictLinkedNode<T> next = last.next();
                    while (next != null) {
                        last = next;
                        next = last.next();
                    }
                    if (last.setNext(newElement)) {
                        if (maintainCountReferences) {
                            count.incrementAndGet();
                            nonEvictedCount.incrementAndGet();
                        }
                        // notify listeners
                        if (enableListeners) {
                            for (EvictionListener<T> evictionListener : evictionListeners.keySet()) {
                                evictionListener.added(newElement);
                            }
                        }
                        return newElement;
                    }
                }
            }
        }
    }

    /** Mark an element as evicted and make it eligible for removal by eviction.
     * This call by itself doesn't trigger eviction. */
    @Override
    public void markEvictable(EvictLinkedNode<T> element) {
        // Check for eviction and evict
        if (((MutableEvictLinkedNode<T>)element).markForEviction()) {
            if (maintainCountReferences) {
                nonEvictedCount.decrementAndGet();
            }
        }
    }

    protected static class EvictIterator<T> implements Iterator<EvictLinkedNode<T>> {
        @Nullable EvictLinkedNode<T> cursor;
        @Nullable final EvictLinkedNode<T> nonInclusiveEnd;

        protected EvictIterator(EvictLinkedNode<T> start, @Nullable EvictLinkedNode<T> nonInclusiveEnd) {
            this.cursor = start;
            this.nonInclusiveEnd = nonInclusiveEnd;
        }

        @Override
        public boolean hasNext() {
            return cursor != null && cursor != nonInclusiveEnd;
        }

        @Override
        public @Nullable EvictLinkedNode<T> next() {
            if (!hasNext()) {
                return null;
            } else {
                EvictLinkedNode<T> next = cursor;
                // cursor is not null since we checked it in hasNext
                cursor = checkNotNull(cursor).next();
                return next;
            }
        }
    };


    /** Same as `root()` */
    public @Nullable MutableEvictLinkedNode<T> runEvictionAndGetNewRoot() {
        MutableEvictLinkedNode<T> currentRoot = root.get();
        if (currentRoot != null && currentRoot.isEvicted()) {
            MutableEvictLinkedNode<T> cursor = currentRoot;
            MutableEvictLinkedNode<T> next = cursor.next();

            int toEvictCount = 0;
            while (cursor.isEvicted() && next != null) {
                cursor = next;
                next = cursor.next();
                toEvictCount++;
            }

            //We end up getting either first non-evictable element or last element in the list, so we can safely evict everything before
            if (cursor != currentRoot) {
                if (root.compareAndSet(currentRoot, cursor)) {
                    // Successfully evicted up to our cursor
                    // update count
                    if (maintainCountReferences) {
                        while (true) {
                            int currentCount = count.get();
                            if (count.compareAndSet(currentCount, currentCount - toEvictCount)) {
                                break;
                            }
                        }
                    }

                    // notify listeners
                    if (enableListeners) {
                        EvictIterator<T> evictIterator = new EvictIterator<>(currentRoot, cursor);
                        for (EvictionListener<T> evictionListener : evictionListeners.keySet()) {
                            evictionListener.evicted(evictIterator);
                        }
                    }

                    return cursor;
                } else {
                    // Parallel eviction took precedence
                    return root.get();
                }
            } else {
                // Nothing to evict - root is the only element in the list and should stay in the list in evicted state to avoid race conditions
                return currentRoot;
            }
        } else {
            // Eviction not possible, root can't be evicted
            return currentRoot;
        }
    }

    public EvictListElementPicker<T> newElementPicker() {
        return new ConcurrentEvictListEvictListElementPicker<>(this);
    }

    /**
     * This enhances ConcurrentEvictList by providing a method to grab an un-evicted element in round-robin fashion.
     * Can be useful for resource pools.
     */
    protected static class ConcurrentEvictListEvictListElementPicker<T> implements EvictListElementPicker<T> {
        final AtomicReference<MutableEvictLinkedNode<T>> cursor;
        final ConcurrentEvictList<T> list;

        public ConcurrentEvictListEvictListElementPicker(ConcurrentEvictList<T> list) {
            this.cursor = new AtomicReference<>();
            this.list = list;
        }

        @Override
        public @Nullable T getNonEvictedValue() {
            EvictLinkedNode<T> nonEvictedNode = getNonEvictedNode();
            return nonEvictedNode == null ? null : nonEvictedNode.value();
        }

        @Override
        public @Nullable EvictLinkedNode<T> getNonEvictedNode() {
            if (cursor.get() == null) {
                MutableEvictLinkedNode<T> currentRoot = list.root();
                if (currentRoot == null) {
                    return null;
                }
                // The call to root() runs eviction in ConcurrentEvictList, so right after that we can check whether all elements are evicted
                if (list.count() == 1 && currentRoot.isEvicted()) {
                    return null;
                }
                if (cursor.compareAndSet(null, currentRoot)) {
                    if (!currentRoot.isEvicted()) {
                        return currentRoot;
                    }
                }
            }

            return getNextNonEvictedNode();
        }

        public @Nullable EvictLinkedNode<T> getNextNonEvictedNode() {
            // Cursor won't be null, since `compareAndSet(null, smth)` failed above
            MutableEvictLinkedNode<T> currentCursor = checkNotNull(cursor.get());
            MutableEvictLinkedNode<T> next = currentCursor.next();

            while (true) {
                while (true) {
                    if (next == null) {
                        // Reached the end of the list
                        break;
                    }

                    if (!next.isEvicted()) {
                        if (cursor.compareAndSet(currentCursor, next)) {
                            // Moving the cursor and returning the value
                            return next;
                        } else {
                            // Somebody already moved the cursor, adjusting
                            currentCursor = cursor.get();
                            next = checkNotNull(currentCursor).next();
                        }
                    } else {
                        // If it's evicted, go to the next one after that
                        next = next.next();
                    }
                }

                // Everything after the cursor is evicted, so let's flip over to start from the root for the second iteration
                MutableEvictLinkedNode<T> root = checkNotNull(list.root());
                // The call to root() runs eviction in ConcurrentEvictList, so right after that we can check whether all elements are evicted
                if (list.count() == 1 && root.isEvicted()) {
                    return null;
                }
                next = root;
            }
        }
    }

    @Override
    public void addListener(EvictionListener<T> listener) {
        if (enableListeners) {
            evictionListeners.put(listener, listener);
        } else {
            throw new IllegalStateException("Listeners disabled");
        }
    }

    @Override
    public void removeListener(EvictionListener<T> listener) {
        if (enableListeners) {
            evictionListeners.remove(listener);
        } else {
            throw new IllegalStateException("Listeners disabled");
        }
    }
}
