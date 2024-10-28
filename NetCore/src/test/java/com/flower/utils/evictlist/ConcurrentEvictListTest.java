package com.flower.utils.evictlist;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrentEvictListTest {
    @Test
    public void evictListBasicTest() {
        EvictLinkedList<String> evictList = new ConcurrentEvictList<>();

        assertNull(evictList.root());
        assertEquals(0, evictList.count());

        EvictLinkedNode<String> elem1 = evictList.addElement("Hello");
        assertEquals(1, evictList.count());
        assertEquals("Hello", evictList.root().value());

        EvictLinkedNode<String> elem2 = evictList.addElement(" world");
        assertEquals(2, evictList.count());
        assertEquals(" world", evictList.root().next().value());

        EvictLinkedNode<String> elem3 = evictList.addElement("!");
        assertEquals(3, evictList.count());
        assertEquals("!", evictList.root().next().next().value());

        evictList.markEvictable(elem1);
        assertEquals(3, evictList.count());
        assertEquals(2, evictList.nonEvictedCount());

        assertEquals(" world", evictList.root().value());
        assertEquals("!", evictList.root().next().value());
        assertEquals(2, evictList.count());

        evictList.markEvictable(elem2);
        evictList.markEvictable(elem3);

        assertEquals(2, evictList.count());
        assertEquals(0, evictList.nonEvictedCount());

        assertEquals("!", evictList.root().value());
        assertEquals(1, evictList.count());
        assertEquals(0, evictList.nonEvictedCount());
    }

    @Test
    public void evictListMultiThreadedTest() throws InterruptedException {
        EvictLinkedList<UUID> evictList = new ConcurrentEvictList<>();
        Random random = new Random();

        assertNull(evictList.root());
        assertEquals(0, evictList.count());

        runPayload(evictList, random);

        // When we call root we trigger eviction - also we note that Root itself won't be evicted
        assertTrue(evictList.root().isEvicted());
        assertEquals(1, evictList.count());

        // And let's do it once again for a good measure
        runPayload(evictList, random);

        assertTrue(evictList.root().isEvicted());
        assertEquals(1, evictList.count());
    }

    static void runPayload(EvictLinkedList<UUID> evictList, Random random) throws InterruptedException {
        int cycleCount = 10;
        int maxIterationCount = 1000;

        List<Thread> threads = new ArrayList<>();
        int threadCount = 16;
        for (int i = 0; i < threadCount; i++) {
            final int threadNo = i+1;
            threads.add(new Thread(() -> evictListLoad(evictList, cycleCount, maxIterationCount, random, threadNo)));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }

    static void evictListLoad(EvictLinkedList<UUID> evictList, int cycleCount, int maxIterationCount, Random random,
                              int threadNo) {
        for (int i = 0; i < cycleCount; i++) {
            int iterations = random.nextInt(maxIterationCount);
            List<EvictLinkedNode<UUID>> whatIAdded = new ArrayList<>();
            for (int j = 0; j < iterations; j++) {
                whatIAdded.add(evictList.addElement(UUID.randomUUID()));
            }
            for (EvictLinkedNode<UUID> node : whatIAdded) {
                evictList.markEvictable(node);
            }
            //System.out.println("Thread #" + threadNo + " cycle done " + (i+1) + " / " + cycleCount);
        }
    }
}
