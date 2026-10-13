package com.mebelsex.license;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pure tests for {@link WindowsMachineGuidSource#parseMachineGuid(String)}. */
class MachineGuidParserTest {

    private static final String GUID = "d1e2f3a4-5b6c-7d8e-9f0a-1b2c3d4e5f60";

    @Test
    void parsesValidOutput() throws Exception {
        String output = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography\r\n"
                + "    MachineGuid    REG_SZ    " + GUID + "\r\n";
        assertEquals(GUID, WindowsMachineGuidSource.parseMachineGuid(output));
    }

    @Test
    void parsesOutputWithAUnicodePrefixAndExtraLines() throws Exception {
        String output = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography\n"
                + "    (Standart)    REG_SZ    \n"
                + "    MachineGuid    REG_SZ    " + GUID + "\n"
                + "    Boshqa    REG_SZ    qiymat\n";
        assertEquals(GUID, WindowsMachineGuidSource.parseMachineGuid(output));
    }

    @Test
    void missingValueThrows() {
        String output = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography\r\n";
        assertThrows(MachineIdException.class, () -> WindowsMachineGuidSource.parseMachineGuid(output));
    }

    @Test
    void valueWithoutAGuidThrows() {
        assertThrows(MachineIdException.class,
                () -> WindowsMachineGuidSource.parseMachineGuid("MachineGuid    REG_SZ    noma'lum\r\n"));
    }

    @Test
    void garbageThrows() {
        assertThrows(MachineIdException.class,
                () -> WindowsMachineGuidSource.parseMachineGuid("bu oddiy matn, GUID yo'q"));
    }

    @Test
    void nullThrows() {
        assertThrows(MachineIdException.class, () -> WindowsMachineGuidSource.parseMachineGuid(null));
    }
}
