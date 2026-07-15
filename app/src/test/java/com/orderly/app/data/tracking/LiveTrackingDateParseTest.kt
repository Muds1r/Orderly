package com.orderly.app.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LiveTrackingDateParseTest {

    @Test
    fun parsesLeopardsEmbeddedDate() {
        val ms = LiveTrackingClient.parseDateForTest(
            "14 July, 2026 (20:32) Dispatched to ISLAMABAD"
        )
        assertNotNull(ms)
        val cal = Calendar.getInstance().apply { timeInMillis = ms!! }
        assertTrue(cal.get(Calendar.YEAR) == 2026)
        assertTrue(cal.get(Calendar.MONTH) == Calendar.JULY)
        assertTrue(cal.get(Calendar.DAY_OF_MONTH) == 14)
        assertTrue(cal.get(Calendar.HOUR_OF_DAY) == 20)
        assertTrue(cal.get(Calendar.MINUTE) == 32)
    }

    @Test
    fun parsesEarlierPickupBeforeDispatch() {
        val picked = LiveTrackingClient.parseDateForTest(
            "14 July, 2026 (18:18) Shipment picked in CHICHA WATNI"
        )
        val dispatched = LiveTrackingClient.parseDateForTest(
            "14 July, 2026 (20:32) Dispatched to ISLAMABAD"
        )
        assertNotNull(picked)
        assertNotNull(dispatched)
        assertTrue(picked!! < dispatched!!)
    }

    @Test
    fun parsesBothLeopardsTimelineEvents() {
        val html = """
            <div class="tracking-list">
              <div class="tracking-item">
                <div class="tracking-icon"></div>
                <div class="tracking-date">14 July, 2026 (20:32)</div>
                <div class="tracking-content">Dispatched to ISLAMABAD</div>
              </div>
              <div class="tracking-item">
                <div class="tracking-icon"></div>
                <div class="tracking-date">14 July, 2026 (18:18)</div>
                <div class="tracking-content">Shipment picked in CHICHA WATNI</div>
              </div>
            </div>
        """.trimIndent()

        val result = LiveTrackingClient.parseLeopardsHtmlForTest(html, "CC7527356953")
        assertNotNull(result)
        assertEquals(2, result!!.events.size)
        assertTrue(result.events[0].occurredAt < result.events[1].occurredAt)
        assertTrue(result.events[0].description.contains("picked", ignoreCase = true))
        assertTrue(result.events[1].description.contains("Dispatched", ignoreCase = true))
        assertTrue(
            result.events[1].location.equals("Islamabad", ignoreCase = true) ||
                result.events[1].description.contains("ISLAMABAD", ignoreCase = true)
        )
    }
}
