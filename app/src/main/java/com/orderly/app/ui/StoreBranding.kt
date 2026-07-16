package com.orderly.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.orderly.app.R

/**
 * Store branding: accent, Material icon fallback, and optional local logo drawable.
 */
object StoreBranding {

    data class Brand(
        val initial: String,
        val accent: Color,
        val icon: ImageVector,
        @DrawableRes val logoRes: Int? = null
    )

    fun forStore(store: String): Brand {
        val key = store.trim().lowercase()
        val initial = store.trim().take(1).uppercase().ifBlank { "?" }
        return when {
            "amazon" in key -> Brand(initial, Color(0xFF232F3E), Icons.Outlined.ShoppingBag, R.drawable.ic_logo_amazon)
            "daraz" in key -> Brand(initial, Color(0xFFF57224), Icons.Outlined.LocalMall, R.drawable.ic_logo_daraz)
            "aliexpress" in key || "alibaba" in key ->
                Brand(initial, Color(0xFFFF6A00), Icons.Outlined.Language, R.drawable.ic_logo_aliexpress)
            "temu" in key -> Brand(initial, Color(0xFFFB7701), Icons.Outlined.LocalMall, R.drawable.ic_logo_temu)
            "ebay" in key -> Brand(initial, Color(0xFFE53238), Icons.Outlined.Store, R.drawable.ic_logo_ebay)
            "shein" in key -> Brand(initial, Color(0xFF000000), Icons.Outlined.Checkroom)
            "walmart" in key -> Brand(initial, Color(0xFF0071CE), Icons.Outlined.Store)
            "target" in key -> Brand(initial, Color(0xFFCC0000), Icons.Outlined.Store)
            "etsy" in key -> Brand(initial, Color(0xFFF56400), Icons.Outlined.Store)
            else -> Brand(initial, accentFromHash(key), Icons.Outlined.ShoppingBag)
        }
    }

    private fun accentFromHash(key: String): Color {
        val hues = listOf(
            Color(0xFF1D4ED8),
            Color(0xFF0F766E),
            Color(0xFFB45309),
            Color(0xFF7C3AED),
            Color(0xFFBE123C),
            Color(0xFF334155)
        )
        val idx = (key.hashCode().and(0x7FFFFFFF)) % hues.size
        return hues[idx]
    }
}

/** Pick a product-style icon from title keywords (keyboard, hub, mouse, …). */
object ProductIcons {

    fun forProduct(summary: String?): ImageVector {
        val t = summary.orEmpty().lowercase()
        return when {
            listOf("keyboard", "keycap", "mechanical").any { it in t } -> Icons.Outlined.Keyboard
            listOf("mouse", "trackpad").any { it in t } -> Icons.Outlined.Mouse
            listOf("hub", "dongle", "adapter", "usb-c", "usb c").any { it in t } -> Icons.Outlined.Devices
            listOf("camera", "lens", "photo").any { it in t } -> Icons.Outlined.PhotoCamera
            listOf("headphone", "earbud", "airpod", "headset").any { it in t } -> Icons.Outlined.Headphones
            listOf("watch", "band").any { it in t } -> Icons.Outlined.Watch
            listOf("phone", "iphone", "galaxy", "pixel").any { it in t } -> Icons.Outlined.Smartphone
            listOf("game", "controller", "xbox", "playstation").any { it in t } -> Icons.Outlined.SportsEsports
            listOf("shirt", "dress", "shoe", "apparel").any { it in t } -> Icons.Outlined.Checkroom
            else -> Icons.Outlined.Inventory2
        }
    }
}
