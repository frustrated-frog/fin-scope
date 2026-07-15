package com.finscope.domain.instrument;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstrumentCodeCanonicalizerTest {

    @Test
    void canonicalizesMarketIntelCodesForQuant() {
        assertEquals("600519.SH", InstrumentCodeCanonicalizer.canonical("600519", "SH"));
        assertEquals("000001.SZ", InstrumentCodeCanonicalizer.canonical("000001", "SZ"));
        assertEquals("430047.BJ", InstrumentCodeCanonicalizer.canonical("430047", "BJ"));
        assertEquals("600519.SH", InstrumentCodeCanonicalizer.canonical("600519.SH", null));
        assertEquals("600519.SH", InstrumentCodeCanonicalizer.canonical("600519.SH", "SH"));
    }

    @Test
    void rejectsUnsupportedMarketsMalformedCodesAndConflicts() {
        assertThrows(IllegalArgumentException.class,
                () -> InstrumentCodeCanonicalizer.canonical("600519", "NYSE"));
        assertThrows(IllegalArgumentException.class,
                () -> InstrumentCodeCanonicalizer.canonical("", "SH"));
        assertThrows(IllegalArgumentException.class,
                () -> InstrumentCodeCanonicalizer.canonical("   ", "SH"));
        assertThrows(IllegalArgumentException.class,
                () -> InstrumentCodeCanonicalizer.canonical("60051", "SH"));
        assertThrows(IllegalArgumentException.class,
                () -> InstrumentCodeCanonicalizer.canonical("600519.US", null));
        assertThrows(IllegalArgumentException.class,
                () -> InstrumentCodeCanonicalizer.canonical("600519.sh", null));
        assertThrows(IllegalArgumentException.class,
                () -> InstrumentCodeCanonicalizer.canonical("600519", null));
        assertThrows(IllegalArgumentException.class,
                () -> InstrumentCodeCanonicalizer.canonical("600519.SH", "SZ"));
    }
}
