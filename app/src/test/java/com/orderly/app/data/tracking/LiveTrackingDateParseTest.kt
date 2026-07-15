package com.orderly.app.data.tracking

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
}
