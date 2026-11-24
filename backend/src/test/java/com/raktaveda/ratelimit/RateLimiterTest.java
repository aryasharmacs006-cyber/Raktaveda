package com.raktaveda.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter();

    @Test
    void allowsUpToTheLimitThenBlocks() {
        Duration window = Duration.ofMinutes(1);
        assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, window)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, window)).isFalse();
    }

    @Test
    void keysAreIndependent() {
        Duration window = Duration.ofMinutes(1);
        assertThat(limiter.tryAcquire("a", 1, window)).isTrue();
        assertThat(limiter.tryAcquire("a", 1, window)).isFalse();
        assertThat(limiter.tryAcquire("b", 1, window)).isTrue();
    }

    @Test
    void countsAndResetsFailures() {
        Duration window = Duration.ofMinutes(15);
        limiter.record("f");
        limiter.record("f");
        assertThat(limiter.count("f", window)).isEqualTo(2);
        limiter.reset("f");
        assertThat(limiter.count("f", window)).isZero();
    }

    @Test
    void oldHitsFallOutOfTheWindow() throws InterruptedException {
        Duration window = Duration.ofMillis(50);
        assertThat(limiter.tryAcquire("w", 1, window)).isTrue();
        assertThat(limiter.tryAcquire("w", 1, window)).isFalse();
        Thread.sleep(80);
        assertThat(limiter.tryAcquire("w", 1, window)).isTrue();
    }
}
