package com.mebelsex.common;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Pure Java (no Spring, no JavaFX) single-instance guard.
 * Holds an exclusive OS file lock (FileChannel + FileLock) for the process lifetime;
 * the lock file itself may stay on disk, only the OS lock matters, so a crashed
 * process releases it automatically.
 */
public final class SingleInstanceGuard {

    private static final long POLL_MILLIS = 250;

    private FileChannel channel;
    private FileLock lock;

    /**
     * Tries to acquire the lock, polling every 250 ms until {@code waitUpTo} elapses.
     * A null or zero duration means a single attempt.
     *
     * @return true when the lock is held by this guard, false when another instance holds it.
     */
    public boolean tryAcquire(Path lockFile, Duration waitUpTo) {
        long waitNanos = waitUpTo == null ? 0L : Math.max(0L, waitUpTo.toNanos());
        long deadline = System.nanoTime() + waitNanos;
        while (true) {
            if (attempt(lockFile)) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** True while this guard holds the lock. */
    public boolean isHeld() {
        return lock != null && lock.isValid();
    }

    /** Releases the lock and closes the channel (safe to call twice). */
    public void release() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (IOException ignored) {
            // best effort
        } finally {
            lock = null;
        }
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {
            // best effort
        } finally {
            channel = null;
        }
    }

    private boolean attempt(Path lockFile) {
        try {
            Path parent = lockFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileChannel ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock acquired = ch.tryLock();
                if (acquired == null) {
                    ch.close();
                    return false;
                }
                this.channel = ch;
                this.lock = acquired;
                return true;
            } catch (OverlappingFileLockException e) {
                // same JVM already holds the lock
                ch.close();
                return false;
            }
        } catch (IOException e) {
            // cannot create/open the lock file: treat as unavailable
            return false;
        }
    }
}
