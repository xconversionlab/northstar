package com.prithvi.northstar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BibleReadingPlanTest {
    @Test fun startsWithJohnOne() {
        assertEquals("John 1", BibleReadingPlan.chapterFor(0))
    }

    @Test fun continuesWithinJohnAndIntoActs() {
        assertEquals("John 21", BibleReadingPlan.chapterFor(20))
        assertEquals("Acts 1", BibleReadingPlan.chapterFor(21))
    }

    @Test fun continuesIntoRomansProverbsAndPsalms() {
        assertEquals("Romans 1", BibleReadingPlan.chapterFor(49))
        assertEquals("Proverbs 1", BibleReadingPlan.chapterFor(65))
        assertEquals("Psalms 1", BibleReadingPlan.chapterFor(96))
    }
}
