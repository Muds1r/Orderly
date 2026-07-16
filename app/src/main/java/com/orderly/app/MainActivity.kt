package com.orderly.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.orderly.app.ui.MainViewModel
import com.orderly.app.ui.SyncState
import com.orderly.app.ui.screens.DashboardScreen
import com.orderly.app.ui.screens.InsightsScreen
import com.orderly.app.ui.screens.OrderDetailScreen
import com.orderly.app.ui.screens.OrdersScreen
import com.orderly.app.ui.screens.SetupScreen
import com.orderly.app.ui.screens.SignOutConfirmDialog
import com.orderly.app.ui.theme.OrderlyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrderlyTheme {
                App()
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private const val BANNER_FRESH_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val viewModel: MainViewModel = viewModel()
    val accountName by viewModel.accountName.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    if (accountName == null) {
        SetupScreen(
            onConnect = { email, password -> viewModel.connect(email, password) },
            isConnecting = syncState is SyncState.Syncing,
            errorMessage = (syncState as? SyncState.Error)?.message,
            versionName = BuildConfig.VERSION_NAME
        )
        return
    }

    var syncBanner by remember { mutableStateOf<String?>(null) }
    var syncBannerError by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncState.Error -> {
                syncBanner = "Sync failed: ${state.message}"
                syncBannerError = true
            }
            is SyncState.Success -> {
                if (System.currentTimeMillis() - state.at <= BANNER_FRESH_MS) {
                    syncBanner = "Synced ${state.count} emails"
                    syncBannerError = false
                }
            }
            else -> Unit
        }
    }

    val tabs = listOf(
        Tab("dashboard", "Dashboard", Icons.Filled.Dashboard),
        Tab("orders", "Orders", Icons.Filled.Inventory2),
        Tab("insights", "Insights", Icons.Filled.Insights)
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isDetail = currentRoute?.startsWith("order/") == true
    val barSurface = MaterialTheme.colorScheme.surface

    val onOrderClick: (String) -> Unit = { id ->
        navController.navigate("order/${Uri.encode(id)}")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = barSurface,
                        scrolledContainerColor = barSurface
                    ),
                    navigationIcon = {
                        if (isDetail) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            Icon(
                                Icons.Outlined.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            "Orderly",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (!isDetail) {
                            IconButton(onClick = { showSignOut = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign out"
                                )
                            }
                        }
                        if (syncState is SyncState.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = {
                                viewModel.syncNow()
                            }) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = "Sync now",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(barSurface)) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val selected = when {
                            isDetail && tab.route == "orders" -> true
                            !isDetail && currentRoute == tab.route -> true
                            else -> false
                        }
                        PillNavItem(
                            label = tab.label,
                            icon = tab.icon,
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("dashboard") { DashboardScreen(viewModel, onOrderClick) }
                composable("orders") { OrdersScreen(viewModel, onOrderClick) }
                composable("insights") { InsightsScreen(viewModel) }
                composable(
                    route = "order/{orderId}",
                    arguments = listOf(navArgument("orderId") { type = NavType.StringType })
                ) { entry ->
                    val id = Uri.decode(entry.arguments?.getString("orderId").orEmpty())
                    OrderDetailScreen(
                    viewModel = viewModel,
                    orderId = id,
                    onRemoved = { navController.popBackStack() }
                )
                }
            }

            syncBanner?.let { message ->
                SyncBanner(
                    message = message,
                    isError = syncBannerError,
                    onDismiss = { syncBanner = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (showSignOut) {
        SignOutConfirmDialog(
            onConfirm = {
                showSignOut = false
                viewModel.signOut()
            },
            onDismiss = { showSignOut = false }
        )
    }
}

@Composable
private fun SyncBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (isError) Color(0xFFF87171) else Color(0xFF4ADE80),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            fontWeight = FontWeight.Medium
        )
        TextButton(onClick = onDismiss) {
            Text(
                "Dismiss",
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun PillNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
