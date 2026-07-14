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
    fun parsesDivoraConfirmationTotal2699() {
        val result = OrderParser.parse(
            messageId = "<divora-confirm@shopify>",
            fromHeader = "Divora <store+62945329263@t.shopifyemail.com>",
            subject = "Order #6214 confirmed",
            body = """
                Order #6214
                Thank you for your order!
                Order summary
                Intense Repair Serum - (5 IN 1) × 2
                Buy 1 - 1799
                BUY 2 (-Rs. 899)
                Rs. 3,598
                Rs. 2,699
                Subtotal
                Rs. 2,699
                Shipping
                Rs. 0
                Taxes
                Rs. 0
                Total
                Rs. 2,699
                You saved Rs. 899
                Total paid today
                Rs. 0
                Payment
                Cash on Delivery (COD)
            """.trimIndent(),
            timestamp = 1_700_000_400_000L
        )
        assertNotNull(result)
        val order = result!!.order
        assertEquals("Divora", order.store)
        assertEquals("6214", order.orderNumber)
        assertEquals(2699.0, order.amount!!, 0.001)
        assertEquals("PKR", order.currency)
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
                Buy 1 - 1799
                BUY 2 (-Rs. 899)
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
        // Ship mail has no Total — Buy 1 is a fallback; merge with confirmation keeps 2699
        assertEquals(1799.0, order.amount!!, 0.001)
        assertEquals("divora|6214", order.id)
    }

    @Test
    fun ignoresDiscountAmountInParens() {
        val result = OrderParser.parse(
            messageId = "<price@shop.com>",
            fromHeader = "Shop <orders@example-shop.com>",
            subject = "Order #9999 confirmed",
            body = """
                Order #9999
                Thank you for your order
                Subtotal Rs. 2,000
                BUY 2 (-Rs. 500)
                Free Shipping Above Rs1999
                Total: Rs. 1,500
            """.trimIndent(),
            timestamp = 1L
        )
        assertNotNull(result)
        assertEquals(1500.0, result!!.order.amount!!, 0.001)
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
    fun saeedGhaniShipmentSubjectIsInTransit() {
        val status = OrderParser.detectStatus(
            "A shipment from order #2856598 is on the way",
            "M&P tracking number: 560678910101060. will be delivered to you soon."
        )
        assertEquals(OrderStatus.IN_TRANSIT, status)
    }

    @Test
    fun saeedGhaniDispatchWithoutOrderNumberIsIgnored() {
        val result = OrderParser.parse(
            messageId = "<alerts@saeedghani.com>",
            fromHeader = "Saeed Ghani <no-reply@alerts.saeedghani.com>",
            subject = "Your Order Has Been Dispatched!",
            body = """
                Estimated delivery time is 3-6 working days.
                Call or WhatsApp us at 02137130284.
                Free Shipping Above Rs1999/-
            """.trimIndent(),
            timestamp = 1_700_000_450_000L
        )
        assertTrue(result == null)
    }

    @Test
    fun saeedGhaniConfirmationAndShipShareStableId() {
        val confirm = OrderParser.parse(
            messageId = "<sg1@saeedghani1888.com>",
            fromHeader = "Saeed Ghani <customercare@saeedghani1888.com>",
            subject = "Order #2856598 confirmed",
            body = """
                Order #2856598
                Thank you for your purchase!
                Your order has been received and now in process.
                Total Rs.1,386 PKR
            """.trimIndent(),
            timestamp = 1_700_000_400_000L
        )
        val ship = OrderParser.parse(
            messageId = "<sg2@saeedghani1888.com>",
            fromHeader = "Saeed Ghani <customercare@saeedghani1888.com>",
            subject = "A shipment from order #2856598 is on the way",
            body = """
                Order #2856598
                Your order has been picked up by the shipper and will be delivered to you soon.
                M&P tracking number: 560678910101060
            """.trimIndent(),
            timestamp = 1_700_000_500_000L
        )
        assertNotNull(confirm)
        assertNotNull(ship)
        assertEquals(confirm!!.order.id, ship!!.order.id)
        assertEquals("saeed ghani|2856598", confirm.order.id)
        assertEquals(OrderStatus.PROCESSING, confirm.order.status)
        assertEquals(OrderStatus.IN_TRANSIT, ship.order.status)
        assertEquals("560678910101060", ship.order.trackingNumber)
    }

    @Test
    fun ignoresPhoneAsTracking() {
        assertTrue(OrderParser.isPhoneLike("02137130284"))
        assertTrue(OrderParser.isPhoneLike("03001234567"))
        assertTrue(!OrderParser.isPhoneLike("560678910101060"))
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
