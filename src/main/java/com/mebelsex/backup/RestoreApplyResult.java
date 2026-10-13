package com.mebelsex.backup;

/** Outcome of applying a pending restore on startup. */
public record RestoreApplyResult(boolean success, String detail) {
}
