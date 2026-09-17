package com.drawgame.game.service.component;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoundSchedulerTest {
    @Test
    void supportsMultipleIndependentTimersPerRoom() throws Exception {
        RoundScheduler scheduler = new RoundScheduler();
        try {
            CountDownLatch fired = new CountDownLatch(2);
            scheduler.schedule("room", "selection", 10, fired::countDown);
            scheduler.schedule("room", "hint-1", 10, fired::countDown);
            assertTrue(fired.await(2, TimeUnit.SECONDS));
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void cancelRoomRemovesItsOutstandingCallbacks() throws Exception {
        RoundScheduler scheduler = new RoundScheduler();
        try {
            AtomicInteger fired = new AtomicInteger();
            scheduler.schedule("room", "hint-1", 100, fired::incrementAndGet);
            scheduler.schedule("room", "recap", 100, fired::incrementAndGet);
            scheduler.cancelAll("room");
            Thread.sleep(160);
            assertEquals(0, fired.get());
        } finally {
            scheduler.shutdown();
        }
    }
}
