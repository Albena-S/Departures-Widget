package fr.departures

import fr.departures.widget.CellStyle
import fr.departures.widget.TimeCell
import fr.departures.widget.fitRow
import org.junit.Assert.assertEquals
import org.junit.Test

class RowFitTest {
    private fun c(t: String) = TimeCell(t, CellStyle.NORMAL)
    private val first = c("4 min")
    private val later = listOf(c("34 min"), c("12:55"))

    @Test fun shortButWideWidgetShowsAllTimes() {
        // ~4 cells wide, 1 row tall (the case that was blank)
        val f = fitRow(availableDp = 250f, badgeDp = 18f, first = first, later = later, label = null, compact = true)
        assertEquals(2, f.laterCount)
    }

    @Test fun narrowWidgetStillFitsOneMore() {
        // ~160 dp wide widget, compact
        val f = fitRow(availableDp = 136f, badgeDp = 18f, first = c("20 min"), later = listOf(c("49 min"), c("13:09")), label = null, compact = true)
        assertEquals(1, f.laterCount)
    }

    @Test fun tinyWidgetShowsOnlyFirst() {
        val f = fitRow(availableDp = 76f, badgeDp = 18f, first = first, later = later, label = null, compact = true)
        assertEquals(0, f.laterCount)
    }

    @Test fun stationLabelTakesPriorityOverThirdTime() {
        val f = fitRow(availableDp = 170f, badgeDp = 18f, first = first, later = later, label = "Ermont Halte", compact = true)
        assertEquals(true, f.showLabel)
        assertEquals(1, f.laterCount)
    }

    @Test fun labelDroppedWhenEvenOneTimeWouldNotFit() {
        val f = fitRow(availableDp = 90f, badgeDp = 18f, first = first, later = later, label = "Cernay", compact = true)
        assertEquals(false, f.showLabel)
    }

    @Test fun wideWidgetShowsLabelAndAllTimes() {
        val f = fitRow(availableDp = 290f, badgeDp = 22f, first = c("20 min"), later = listOf(c("49 min"), c("13:09")), label = "Ermont Halte", compact = false)
        assertEquals(true, f.showLabel)
        assertEquals(2, f.laterCount)
    }
}
