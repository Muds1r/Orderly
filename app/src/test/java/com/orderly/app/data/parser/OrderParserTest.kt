package com.orderly.app.data.parser

import com.orderly.app.data.db.OrderStatus
import com.orderly.app.data.tracking.PakCourier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderParserTest {

    @Test
    fun parsesAmazonConfirmation() {
        val result = OrderParser.parse(
            messageId = "<msg1@amazon.com>",
            fromHeader = "Amazon <auto-confirm@amazon.com>",
            subject = "Your Amazon.com order of Wireless Earbuds",
            body = """
                Thanks for your order!
                Order # 123-4567890-1234567
                Order Total: $49.99
                Item: Wireless Earbuds Pro
            """.trimIndent(),
            timestamp = 1_700_000_000_000L
        )
        assertNotNull(result)
        val order = result!!.order
        assertEquals("Amazon", order.store)
        assertEquals("123-4567890-1234567", order.orderNumber)
        assertEquals(OrderStatus.PROCESSING, order.status)
        assertEquals(49.99, order.amount!!, 0.001)
        assertEquals("amazon|123-4567890-1234567", order.id)
    }

    @Test
    fun parsesShippedStatusAndTracking() {
        val result = OrderParser.parse(
            messageId = "<msg2@temu.com>",
            fromHeader = "Temu <noreply@temu.com>",
            subject = "Your order has shipped",
            body = """
                Good news — your package is on the way.
                Order number: TM9876543210
                Tracking number: TN123456789XYZ
                Total: Rs. 2,450.00
            """.trimIndent(),
            timestamp = 1_700_000_100_000L
        )
        assertNotNull(result)
        val order = result!!.order
        assertEquals("Temu", order.store)
        assertEquals(OrderStatus.IN_TRANSIT, order.status)
        assertEquals("TM9876543210", order.orderNumber)
        assertEquals("TN123456789XYZ", order.trackingNumber)
        assertEquals(2450.0, order.amount!!, 0.001)
        assertEquals("PKR", order.currency)
        assertTrue(result.events.isNotEmpty())
    }

    @Test
    fun parsesDarazPostExTracking() {
        val result = OrderParser.parse(
            messageId = "<msg3@daraz.pk>",
            fromHeader = "Daraz <noreply@daraz.pk>",
            subject = "Your Daraz order has been shipped via PostEx",
            body = """
                Order number: 123456789012345
                Tracking number: PK-POSTEX-CX-9988776655
                Shipped from: Lahore warehouse
                Your package arrived at Karachi hub
            """.trimIndent(),
            timestamp = 1_700_000_200_000L
        )
        assertNotNull(result)
        val order = result!!.order
        assertEquals("Daraz", order.store)
        assertEquals("PostEx", order.carrier)
        assertEquals("CX-9988776655", order.trackingNumber)
        assertEquals("Lahore warehouse", order.shipFrom)
        assertTrue(result.events.any { it.location?.contains("Karachi", ignoreCase = true) == true })
    }

    @Test
    fun parsesLeopardsCnFromDaraz() {
        val result = OrderParser.parse(
            messageId = "<msg4@daraz.pk>",
            fromHeader = "Daraz <noreply@mail.daraz.pk>",
            subject = "Out for delivery",
            body = """
                Order # 555444333222111
                CN number: PK-LCS-GT7514208669
                Courier: Leopards
                Out for delivery in Multan
            """.trimIndent(),
            timestamp = 1_700_000_300_000L
        )
        assertNotNull(result)
        assertEquals("GT7514208669", result!!.order.trackingNumber)
        assertEquals("Leopards", result.order.carrier)
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, result.order.status)
    }

    @Test
    fun detectsPakCouriers() {
        assertEquals(PakCourier.POSTEX, PakCourier.detect("CX-1234567890"))
        assertEquals(PakCourier.TCS, PakCourier.detect("7770001234567"))
        assertEquals(PakCourier.LEOPARDS, PakCourier.detect("GT7514208669"))
        assertEquals("GT7514208669", PakCourier.normalizeCn("PK-LCS-GT7514208669"))
    }

    @Test
    fun storeRegistryResolvesDaraz() {
        assertEquals(
            "Daraz",
            StoreRegistry.storeFromSender("Daraz <noreply@mail.daraz.pk>")
        )
    }

    @Test
    fun detectDeliveredStatus() {
        val status = OrderParser.detectStatus(
            "Your package was delivered",
            "your package was delivered today"
        )
        assertEquals(OrderStatus.DELIVERED, status)
    }

    @Test
    fun ignoresUnknownSender() {
        val result = OrderParser.parse(
            messageId = "<x@spam.com>",
            fromHeader = "Promo <ads@random-promo.xyz>",
            subject = "Big sale",
            body = "Click here",
            timestamp = 1L
        )
        assertTrue(result == null)
    }
}
