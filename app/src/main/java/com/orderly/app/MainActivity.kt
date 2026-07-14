package com.orderly.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

private const val SNACKBAR_FRESH_MS = 30_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val viewModel: MainViewModel = viewModel()
    val accountName by viewModel.accountName.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (accountName == null) {
        SetupScreen(
            onConnect = { email, password ->
                ensureNotificationPermission()
                viewModel.connect(email, password)
            },
            isConnecting = syncState is SyncState.Syncing,
            errorMessage = (syncState as? SyncState.Error)?.message
        )
        return
    }

    LaunchedEffect(Unit) {
        ensureNotificationPermission()
    }

    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncState.Error -> snackbarHostState.showSnackbar("Sync failed: ${state.message}")
            is SyncState.Success -> {
                if (System.currentTimeMillis() - state.at <= SNACKBAR_FRESH_MS) {
                    snackbarHostState.showSnackbar("Synced ${state.count} emails")
                }
            }
            else -> Unit
        }
    }

    val tabs = listOf(
        Tab("dashboard", "Dashboard", Icons.Default.Dashboard),
        Tab("orders", "Orders", Icons.Default.LocalShipping),
        Tab("insights", "Insights", Icons.Default.Insights)
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isDetail = currentRoute?.startsWith("order/") == true

    val onOrderClick: (String) -> Unit = { id ->
        navController.navigate("order/${Uri.encode(id)}")
    }

    val barContainer = MaterialTheme.colorScheme.surfaceContainerHigh

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = barContainer,
                    scrolledContainerColor = barContainer
                ),
                navigationIcon = {
                    if (isDetail) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    if (isDetail) {
                        Text(
                            "Order",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Column {
                            Text(
                                "Orderly",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                accountName ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (!isDetail) {
                        if (syncState is SyncState.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.padding(12.dp).height(24.dp))
                        } else {
                            IconButton(onClick = {
                                ensureNotificationPermission()
                                viewModel.syncNow()
                            }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync now")
                            }
                        }
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isDetail) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NavigationBar(
                        windowInsets = WindowInsets(0),
                        containerColor = barContainer,
                        tonalElevation = 0.dp
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                    Text(
                        "Made By Muds1r",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(barContainer)
                            .padding(top = 2.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(viewModel, onOrderClick) }
            composable("orders") { OrdersScreen(viewModel, onOrderClick) }
            composable("insights") { InsightsScreen(viewModel) }
            composable(
                route = "order/{orderId}",
                arguments = listOf(navArgument("orderId") { type = NavType.StringType })
            ) { entry ->
                val id = Uri.decode(entry.arguments?.getString("orderId").orEmpty())
                OrderDetailScreen(viewModel, id)
            }
        }
    }
}
