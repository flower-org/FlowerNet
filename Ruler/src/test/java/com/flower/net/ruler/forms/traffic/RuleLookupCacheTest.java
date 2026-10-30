package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleLookupCacheTest {
    @Test
    void cachesUntilInvalidated() {
        AtomicInteger lookups = new AtomicInteger();
        AtomicReference<Access> access = new AtomicReference<>(Access.ALLOW);
        RuleLookupCache cache = new RuleLookupCache((host, port) -> {
            lookups.incrementAndGet();
            return new RuleLookupCache.Result(access.get(), port == 443);
        });

        assertEquals(Access.ALLOW, cache.get("a.com", 443).access());
        assertTrue(cache.get("a.com", 443).knownServer());
        assertEquals(1, lookups.get());

        // Separate key
        assertFalse(cache.get("a.com", 80).knownServer());
        assertEquals(2, lookups.get());

        access.set(Access.DENY);
        assertEquals(Access.ALLOW, cache.get("a.com", 443).access());

        cache.invalidate();
        assertEquals(Access.DENY, cache.get("a.com", 443).access());
        assertEquals(3, lookups.get());
    }

    @Test
    void cachesNoMatch() {
        AtomicInteger lookups = new AtomicInteger();
        RuleLookupCache cache = new RuleLookupCache((host, port) -> {
            lookups.incrementAndGet();
            return new RuleLookupCache.Result(null, false);
        });

        assertNull(cache.get("a.com", 443).access());
        assertNull(cache.get("a.com", 443).access());
        assertEquals(1, lookups.get());
    }

    @Test
    void lookupRacingWithInvalidationIsNotServedLater() {
        AtomicReference<Access> access = new AtomicReference<>(Access.ALLOW);
        AtomicReference<RuleLookupCache> cacheRef = new AtomicReference<>();
        AtomicInteger lookups = new AtomicInteger();
        RuleLookupCache cache = new RuleLookupCache((host, port) -> {
            Access result = access.get();
            if (lookups.incrementAndGet() == 1) {
                // Rules change after this lookup read them, before its answer is cached
                access.set(Access.DENY);
                cacheRef.get().invalidate();
            }
            return new RuleLookupCache.Result(result, false);
        });
        cacheRef.set(cache);

        assertEquals(Access.ALLOW, cache.get("a.com", 443).access());
        assertEquals(Access.DENY, cache.get("a.com", 443).access());
    }

    @Test
    void sizeIsBounded() {
        RuleLookupCache cache = new RuleLookupCache((host, port) -> new RuleLookupCache.Result(null, false), 10);
        for (int port = 0; port < 1000; port++) {
            cache.get("a.com", port);
        }
        assertTrue(cache.size() <= 10);
    }

    @Test
    void invalidateClearsEntries() {
        RuleLookupCache cache = new RuleLookupCache((host, port) -> new RuleLookupCache.Result(null, false));
        cache.get("a.com", 1);
        cache.get("a.com", 2);
        assertEquals(2, cache.size());
        cache.invalidate();
        assertEquals(0, cache.size());
    }
}
