package com.mebelsex.license;

/**
 * Supplies this machine's raw hardware identifier (on Windows, the MachineGuid).
 * Kept as an interface so the source can be replaced in tests without touching the OS.
 */
@FunctionalInterface
public interface MachineIdSource {

    /**
     * Reads the raw identifier.
     *
     * @return the raw identifier text, never blank
     * @throws MachineIdException if the identifier is unavailable
     */
    String readRawId() throws MachineIdException;
}
