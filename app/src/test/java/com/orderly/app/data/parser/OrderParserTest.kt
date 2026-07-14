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
        assertEquals(OrderStatus.SHIPPED, order.status)
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
    fun parsesSaeedGhaniWithMnP() {
        val result = OrderParser.parse(
            messageId = "<sg@saeedghani1888.com>",
            fromHeader = "Saeed Ghani <customercare@saeedghani1888.com>",
            subject = "Your order is on the way",
            body = """
                Order #2856598
                Your order has been picked up by the shipper and will be delivered to you soon.
                M&P tracking number: 560678910101060
                Items in this shipment
                Sunblock SPF 60 with Vitamin C - 60ml × 1
            """.trimIndent(),
            timestamp = 1_700_000_400_000L
        )
        assertNotNull(result)
        val order = result!!.order
        assertEquals("Saeed Ghani", order.store)
        assertEquals("2856598", order.orderNumber)
        assertEquals("560678910101060", order.trackingNumber)
        assertEquals("M&P", order.carrier)
        assertEquals(OrderStatus.IN_TRANSIT, order.status)
    }

    @Test
    fun parsesDivoraShopifyLeopards() {
        val result = OrderParser.parse(
            messageId = "<divora@shopify>",
            fromHeader = "Divora <store+62945329263@t.shopifyemail.com>",
            subject = "A shipment from order #6214 is on the way",
            body = """
                Order #6214
                Your order is on the way. Track your shipment to see the delivery status.
                Leopards Courier tracking number: CC7527356953
                Intense Repair Serum - (5 IN 1) × 2
            """.trimIndent(),
            timestamp = 1_700_000_500_000L
        )
        assertNotNull(result)
        val order = result!!.order
        assertEquals("Divora", order.store)
        assertEquals("6214", order.orderNumber)
        assertEquals("CC7527356953", order.trackingNumber)
        assertEquals("Leopards", order.carrier)
        assertEquals(OrderStatus.IN_TRANSIT, order.status)
    }

    @Test
    fun storeRegistryResolvesDaraz() {
        assertEquals(
            "Daraz",
            StoreRegistry.storeFromSender("Daraz <noreply@mail.daraz.pk>")
        )
        assertEquals(
            "Saeed Ghani",
            StoreRegistry.storeFromSender("Saeed Ghani <customercare@saeedghani1888.com>")
        )
        assertEquals(
            "Divora",
            StoreRegistry.storeFromSender("Divora <store+62945329263@t.shopifyemail.com>")
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
    fun parsesUnknownXyzShopByDisplayName() {
        val result = OrderParser.parse(
            messageId = "<xyz@randomshop.pk>",
            fromHeader = "Cool Cosmetics <orders@coolcosmetics.pk>",
            subject = "Your order is on the way",
            body = """
                Order #991122
                Tracking number: CC1112223334
                Your package has shipped.
            """.trimIndent(),
            timestamp = 1_700_000_600_000L
        )
        assertNotNull(result)
        val order = result!!.order
        assertEquals("Cool Cosmetics", order.store)
        assertEquals("991122", order.orderNumber)
        assertEquals("CC1112223334", order.trackingNumber)
        assertEquals("Leopards", order.carrier)
    }

    @Test
    fun saeedGhaniOnTheWayIsNotDelivered() {
        val status = OrderParser.detectStatus(
            "Your order is on the way",
            "Your order has been picked up by the shipper and will be delivered to you soon."
        )
        assertEquals(OrderStatus.IN_TRANSIT, status)
    }

    @Test
    fun ignoresFoodpandaPromo() {
        val result = OrderParser.parse(
            messageId = "<fp@foodpanda.pk>",
            fromHeader = "foodpanda <deals@foodpanda.pk>",
            subject = "Offering 21% off on your next order",
            body = "Order now and save. Use code SAVE21. Order #FP12345",
            timestamp = 1L
        )
        assertTrue(result == null)
    }

    @Test
    fun ignoresPercentOffSubject() {
        assertTrue(
            OrderParser.isPromoOrUnrelated(
                "Shop <hello@random.pk>",
                "flash sale 30% off everything",
                "buy now"
            )
        )
    }

    @Test
    fun detectsMnPFromTrackingText() {
        assertEquals(
            PakCourier.MNP,
            PakCourier.detect("560678910101060", "M&P tracking number: 560678910101060")
        )
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
