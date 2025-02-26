package com.flower.net.utils.evictlist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcurrentEvictListsWithTimeoutTest {
    @Test
    public void test() throws InterruptedException {
        ConcurrentEvictListWithFixedTimeout<Integer> list = new ConcurrentEvictListWithFixedTimeout<>(1000);
        for (int i = 0; i < 1000; i++) {
            list.addElement(i);
        }

        assertEquals(1000, list.nonEvictedCount());
        assertEquals(1000, list.count());

        Thread.sleep(1000);

        assertEquals(1000, list.count());
        assertEquals(0, list.nonEvictedCount());
    }

    @Test
    public void test2() throws InterruptedException {
        ConcurrentEvictListWithTimeout<Integer> list = new ConcurrentEvictListWithTimeout<>();
        for (int i = 0; i < 500; i++) {
            list.addElement(i);
        }
        for (int i = 500; i < 1000; i++) {
            list.addElement(i, 1000);
        }

        assertEquals(1000, list.nonEvictedCount());
        assertEquals(1000, list.count());

        //Just in case give it a bit more time
        Thread.sleep(1100);

        assertEquals(1000, list.count());
        assertEquals(500, list.nonEvictedCount());
    }
}
