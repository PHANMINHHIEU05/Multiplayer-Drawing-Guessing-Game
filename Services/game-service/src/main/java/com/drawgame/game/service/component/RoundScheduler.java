package com.drawgame.game.service.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class RoundScheduler {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public void scheduleRoundEnd(String roomId, long delayMs, Runnable task) {
        cancelScheduledTask(roomId);

        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error executing round end task for room {}", roomId, e);
            } finally {
                scheduledTasks.remove(roomId);
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        scheduledTasks.put(roomId, future);
        log.info("Scheduled round end for room {} in {} ms", roomId, delayMs);
    }

    public void cancelScheduledTask(String roomId) {
        ScheduledFuture<?> future = scheduledTasks.remove(roomId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            log.info("Cancelled scheduled round task for room {}", roomId);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
