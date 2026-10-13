package com.mebelsex.license;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden and normalisation tests for {@link MachineId}. The golden value pins the
 * hash/encoding so it can never change by accident (existing licenses would break).
 */
class MachineIdTest {

    /** Fixed raw GUID used for the golden value. */
    static final String FIXED_GUID = "4f2a9c1e-7b3d-4a58-9e21-c0d6b7a8f913";

    /** Fixed expected code for {@link #FIXED_GUID}. */
    static final String FIXED_CODE = "MX-2UJ9-8Q3J-Z7EK-5R7F";

    /** A second fixed GUID, only differing in case from its lower-case form. */
    static final String FIXED_GUID_2 = "d1e2f3a4-5b6c-7d8e-9f0a-1b2c3d4e5f60";

    static final String FIXED_CODE_2 = "MX-YV8Z-RYTT-5N46-LSRT";

    @Test
    void fixedGuidAlwaysYieldsTheSameCode() throws Exception {
        assertEquals(FIXED_CODE, MachineId.fromRawId(FIXED_GUID));
        assertEquals(FIXED_CODE, MachineId.fromRawId(FIXED_GUID.toUpperCase(Locale.ROOT)));
        assertEquals(FIXED_CODE, MachineId.fromRawId("   " + FIXED_GUID + "  "));
    }

    @Test
    void secondGoldenValueIsCaseInsensitive() throws Exception {
        assertEquals(FIXED_CODE_2, MachineId.fromRawId(FIXED_GUID_2));
        assertEquals(FIXED_CODE_2, MachineId.fromRawId(FIXED_GUID_2.toUpperCase(Locale.ROOT)));
    }

    @Test
    void computeUsesTheGivenSource() throws Exception {
        MachineIdSource source = () -> FIXED_GUID;
        assertEquals(FIXED_CODE, MachineId.compute(source));
    }

    @Test
    void computeRejectsMissingSource() {
        assertThrows(MachineIdException.class, () -> MachineId.compute(null));
    }

    @Test
    void fromRawIdRejectsBlankInput() {
        assertThrows(MachineIdException.class, () -> MachineId.fromRawId(null));
        assertThrows(MachineIdException.class, () -> MachineId.fromRawId("   "));
    }

    @Test
    void sourceFailurePropagates() {
        MachineIdSource broken = () -> {
            throw new MachineIdException("reg.exe yo'q");
        };
        assertThrows(MachineIdException.class, () -> MachineId.compute(broken));
    }

    @Test
    void normalizeAcceptsVariants() {
        String canonical = "MX-ABCD-EFGH-JKLM-NPQR";
        assertEquals(canonical, MachineId.normalize(canonical));
        assertEquals(canonical, MachineId.normalize("mx-abcd-efgh-jklm-npqr"));
        assertEquals(canonical, MachineId.normalize("MX ABCD EFGH JKLM NPQR"));
        assertEquals(canonical, MachineId.normalize("abcdefghjklmnpqr"));
        assertEquals(canonical, MachineId.normalize("  abcd-efgh-jklm-npqr  "));
    }

    @Test
    void normalizeRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> MachineId.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> MachineId.normalize(""));
        assertThrows(IllegalArgumentException.class, () -> MachineId.normalize("MX-ABCD-EFGH-JKLM-NPQ"));
        assertThrows(IllegalArgumentException.class, () -> MachineId.normalize("MX-ABCD-EFGH-JKLM-NPQI"));
        assertThrows(IllegalArgumentException.class, () -> MachineId.normalize("MX-ABCD-EFGH-JKLM-0000"));
    }

    @Test
    void codeUsesOnlyTheSafeAlphabetAndIsSixteenChars() throws Exception {
        String code = MachineId.fromRawId(FIXED_GUID);
        String letters = code.substring(MachineId.PREFIX.length() + 1).replace("-", "");
        assertEquals(16, letters.length());
        for (char c : letters.toCharArray()) {
            assertTrue(MachineId.ALPHABET.indexOf(c) >= 0, "unsafe character: " + c);
        }
        assertEquals("MX-", code.substring(0, 3));
    }

    @Test
    void formatRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> MachineId.format("ABC"));
        assertThrows(IllegalArgumentException.class, () -> MachineId.format(null));
    }
}
