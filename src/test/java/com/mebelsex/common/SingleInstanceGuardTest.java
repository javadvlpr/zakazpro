package com.mebelsex.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceGuardTest {

    @TempDir
    Path tempDir;

    @Test
    void firstAcquireSucceeds() {
        SingleInstanceGuard guard = new SingleInstanceGuard();
        try {
            assertTrue(guard.tryAcquire(tempDir.resolve("mebel.db.lock"), Duration.ZERO));
            assertTrue(guard.isHeld());
        } finally {
            guard.release();
        }
    }

    @Test
    void secondAcquireInTheSameJvmFails() {
        Path lock = tempDir.resolve("mebel.db.lock");
        SingleInstanceGuard first = new SingleInstanceGuard();
        SingleInstanceGuard second = new SingleInstanceGuard();
        try {
            assertTrue(first.tryAcquire(lock, Duration.ZERO));
            assertFalse(second.tryAcquire(lock, Duration.ZERO));
            assertFalse(second.isHeld());
        } finally {
            first.release();
            second.release();
        }
    }

    @Test
    void acquireSucceedsAgainAfterRelease() {
        Path lock = tempDir.resolve("mebel.db.lock");
        SingleInstanceGuard first = new SingleInstanceGuard();
        assertTrue(first.tryAcquire(lock, Duration.ZERO));
        first.release();
        assertFalse(first.isHeld());

        SingleInstanceGuard second = new SingleInstanceGuard();
        try {
            assertTrue(second.tryAcquire(lock, Duration.ZERO));
        } finally {
            second.release();
        }
    }

    @Test
    void waitTimesOutWhileAnotherHolderKeepsTheLock() {
        Path lock = tempDir.resolve("mebel.db.lock");
        SingleInstanceGuard first = new SingleInstanceGuard();
        SingleInstanceGuard second = new SingleInstanceGuard();
        try {
            assertTrue(first.tryAcquire(lock, Duration.ZERO));
            long start = System.nanoTime();
            assertFalse(second.tryAcquire(lock, Duration.ofMillis(500)));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMs >= 400, "should have waited for the timeout, waited " + elapsedMs + " ms");
        } finally {
            first.release();
            second.release();
        }
    }

    @Test
    void waitSucceedsWhenTheOtherHolderReleases() throws Exception {
        Path lock = tempDir.resolve("mebel.db.lock");
        SingleInstanceGuard first = new SingleInstanceGuard();
        SingleInstanceGuard second = new SingleInstanceGuard();
        try {
            assertTrue(first.tryAcquire(lock, Duration.ZERO));

            Thread releaser = new Thread(() -> {
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                first.release();
            });
            releaser.start();

            assertTrue(second.tryAcquire(lock, Duration.ofSeconds(5)));
            releaser.join();
        } finally {
            first.release();
            second.release();
        }
    }
}
