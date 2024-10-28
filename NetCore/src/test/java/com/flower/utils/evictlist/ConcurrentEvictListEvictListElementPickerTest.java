package com.flower.utils.evictlist;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ConcurrentEvictListEvictListElementPickerTest {
    @Test
    public void testSingle() {
        EvictLinkedList<String> list = new ConcurrentEvictList<>();
        EvictListElementPicker<String> picker = list.getElementPicker();

        assertNull(picker.getNonEvictedValue());
        assertNull(picker.getNonEvictedValue());

        EvictLinkedNode<String> elem1 = list.addElement("Hello");
        EvictLinkedNode<String> elem2 = list.addElement(" ");
        EvictLinkedNode<String> elem3 = list.addElement("world");
        EvictLinkedNode<String> elem4 = list.addElement("!");

        assertEquals("Hello", picker.getNonEvictedValue());
        assertEquals(" ", picker.getNonEvictedValue());
        assertEquals("world", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());
        assertEquals("Hello", picker.getNonEvictedValue());
        assertEquals(" ", picker.getNonEvictedValue());
        assertEquals("world", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());

        list.markEvictable(elem3);

        assertEquals("Hello", picker.getNonEvictedValue());
        assertEquals(" ", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());
        assertEquals("Hello", picker.getNonEvictedValue());
        assertEquals(" ", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());

        list.markEvictable(elem2);

        assertEquals("Hello", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());
        assertEquals("Hello", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());

        list.markEvictable(elem1);

        assertEquals("!", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());
        assertEquals("!", picker.getNonEvictedValue());

        assertEquals(1, list.count());

        list.markEvictable(elem4);
        assertNull(picker.getNonEvictedValue());
    }

    @Test
    public void testMulti() throws InterruptedException {
        int elementCount = 100;
        int iterationCount = 1000;
        int threadCount = 16;

        HashMap<Integer, AtomicInteger> stats = new HashMap<>();
        EvictLinkedList<Integer> list = new ConcurrentEvictList<>();
        EvictListElementPicker<Integer> picker = list.getElementPicker();

        for (int i = 0; i < elementCount; i++) {
            list.addElement(i);
            stats.put(i, new AtomicInteger(0));
        }

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            threads.add(new Thread(() -> runElementPicker(picker, iterationCount, stats)));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        int expectedAcquisitions = iterationCount * threadCount / elementCount;
        for (int i = 0; i < elementCount; i++) {
            assertEquals(expectedAcquisitions, stats.get(i).get());
        }
    }

    public static void runElementPicker(EvictListElementPicker<Integer> picker, int iterationCount,
                                        HashMap<Integer, AtomicInteger> stats) {
        for (int j = 0; j < iterationCount; j++) {
            Integer value = picker.getNonEvictedValue();
            stats.get(value).incrementAndGet();
        }
    }
}
