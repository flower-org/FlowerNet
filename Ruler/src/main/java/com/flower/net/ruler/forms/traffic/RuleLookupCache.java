package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Caches rule lookup answers per destination. Answers stay valid until {@link #invalidate()}, which must be called
 * right after anything that can change them (rules, list activation, list order).
 * <p>
 * Thread-safe. Entries are stamped with the generation read before the lookup; only entries of the current generation
 * are served, so a lookup racing with a rule change can't leave a stale answer behind.
 */
final class RuleLookupCache {
    static final long DEFAULT_MAX_SIZE = 10_000;

    /**
     * @param access access of the first matching rule among active lists, null if none matched
     * @param knownServer whether an active list has a host+port rule for the destination
     */
    record Result(@Nullable Access access, boolean knownServer) {}

    private record Key(String host, int port) {}
    private record Entry(long generation, Result result) {}

    private final BiFunction<String, Integer, Result> lookup;
    private final Cache<Key, Entry> cache;
    private final AtomicLong generation = new AtomicLong();

    RuleLookupCache(BiFunction<String, Integer, Result> lookup) {
        this(lookup, DEFAULT_MAX_SIZE);
    }

    RuleLookupCache(BiFunction<String, Integer, Result> lookup, long maxSize) {
        this.lookup = lookup;
        this.cache = Caffeine.newBuilder().maximumSize(maxSize).build();
    }

    Result get(String host, int port) {
        long currentGeneration = generation.get();
        Key key = new Key(host, port);
        Entry entry = cache.getIfPresent(key);
        if (entry != null && entry.generation() == currentGeneration) {
            return entry.result();
        }
        Result result = lookup.apply(host, port);
        cache.put(key, new Entry(currentGeneration, result));
        return result;
    }

    void invalidate() {
        generation.incrementAndGet();
        cache.invalidateAll();
    }

    /** Number of cached entries, after pending evictions. */
    long size() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
