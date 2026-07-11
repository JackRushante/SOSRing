package com.lorenzomarci.sosring

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPaletteTest {

    @Test fun storedOrdinals_resolveAllPalettes() {
        AppPalette.values().forEach { palette ->
            assertEquals(palette, AppPalette.fromStoredOrdinal(palette.ordinal))
        }
    }

    @Test fun invalidStoredOrdinal_fallsBackToIndaco() {
        assertEquals(AppPalette.INDACO, AppPalette.fromStoredOrdinal(-1))
        assertEquals(AppPalette.INDACO, AppPalette.fromStoredOrdinal(Int.MAX_VALUE))
    }
}
