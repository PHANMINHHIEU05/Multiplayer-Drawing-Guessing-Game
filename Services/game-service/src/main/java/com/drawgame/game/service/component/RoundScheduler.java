package com.drawgame.game.service.component;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Non-blocking phase timer scheduler with independent named callbacks per room. */
@Slf4j
@Component
public class RoundScheduler {
    private static final class TaskRef {
        volatile ScheduledFuture<?> future;
    }

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Map<String, Map<String, TaskRef>> tasksByRoom = new ConcurrentHashMap<>();

    public void schedule(String roomId, String taskName, long delayMs, Runnable action) {
        cancel(roomId, taskName);
        Map<String, TaskRef> roomTasks = tasksByRoom.computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>());
        TaskRef taskRef = new TaskRef();
        roomTasks.put(taskName, taskRef);
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                action.run();
            } catch (Exception e) {
                log.error("Scheduled game task failed: room={} task={}", roomId, taskName, e);
            } finally {
                roomTasks.remove(taskName, taskRef);
                if (roomTasks.isEmpty()) tasksByRoom.remove(roomId, roomTasks);
            }
        }, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        taskRef.future = future;
    }

    public void cancel(String roomId, String taskName) {
        Map<String, TaskRef> roomTasks = tasksByRoom.get(roomId);
        if (roomTasks == null) return;
        TaskRef task = roomTasks.remove(taskName);
        if (task != null && task.future != null && !task.future.isDone()) task.future.cancel(false);
        if (roomTasks.isEmpty()) tasksByRoom.remove(roomId, roomTasks);
    }

    public void cancelAll(String roomId) {
        Map<String, TaskRef> roomTasks = tasksByRoom.remove(roomId);
        if (roomTasks == null) return;
        roomTasks.values().forEach(task -> {
            if (task.future != null && !task.future.isDone()) task.future.cancel(false);
        });
    }

    /** Legacy single-task adapter; new lifecycle code should name each phase callback. */
    public void scheduleRoundEnd(String roomId, long delayMs, Runnable task) {
        cancelAll(roomId);
        schedule(roomId, "legacy", delayMs, task);
    }

    /** Legacy adapter; cancels all callbacks for this room. */
    public void cancelScheduledTask(String roomId) {
        cancelAll(roomId);
    }

    public int scheduledTaskCount(String roomId) {
        Map<String, TaskRef> roomTasks = tasksByRoom.get(roomId);
        return roomTasks == null ? 0 : roomTasks.size();
    }

    @PreDestroy
    public void shutdown() {
        tasksByRoom.keySet().forEach(this::cancelAll);
        executor.shutdownNow();
    }
}
