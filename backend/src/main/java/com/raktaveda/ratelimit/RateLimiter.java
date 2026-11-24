package com.raktaveda.ratelimit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A simple "sliding window" rate limiter that lives in this server's memory.
 *
 * Why not Bucket4j or Redis: this project runs as ONE server, so memory is enough, there
 * is nothing extra to install, and the code is short enough to read in a minute.
 * Limit: counts are lost on restart and are not shared between servers. If you ever run
 * two or more copies of the app, move this to Redis (or Bucket4j with Redis).
 *
 * Use one fixed window per key name (for example "auth:<ip>" is always 1 minute).
 */
@Component
public class RateLimiter {

    private static final int MAX_HITS_PER_KEY = 1000;
    private static final long KEEP_NANOS = Duration.ofHours(1).toNanos();

    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** Records a hit and returns true if it is within the limit; returns false (and records nothing) if not. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.nanoTime();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            purge(q, now, window.toNanos());
            if (q.size() >= limit) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    /** Records one hit without checking a limit (used to count failed logins). */
    public void record(String key) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            if (q.size() < MAX_HITS_PER_KEY) {
                q.addLast(System.nanoTime());
            }
        }
    }

    /** How many hits happened inside the window. */
    public int count(String key, Duration window) {
        Deque<Long> q = hits.get(key);
        if (q == null) {
            return 0;
        }
        synchronized (q) {
            purge(q, System.nanoTime(), window.toNanos());
            return q.size();
        }
    }

    public void reset(String key) {
        hits.remove(key);
    }

    private static void purge(Deque<Long> q, long now, long windowNanos) {
        while (!q.isEmpty() && now - q.peekFirst() > windowNanos) {
            q.pollFirst();
        }
    }

    /** Frees memory used by keys that have been quiet for an hour. */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.nanoTime();
        hits.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                purge(q, now, KEEP_NANOS);
                return q.isEmpty();
            }
        });
    }
}
