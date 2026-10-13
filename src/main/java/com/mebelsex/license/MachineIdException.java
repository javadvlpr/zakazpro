package com.mebelsex.license;

/**
 * Signals that a machine identifier could not be read or parsed.
 * Used by {@link MachineIdSource} implementations and {@link MachineId}.
 */
public class MachineIdException extends Exception {

    public MachineIdException(String message) {
        super(message);
    }

    public MachineIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
