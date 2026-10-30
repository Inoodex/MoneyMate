package com.example.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.entity.TransactionEntity
import com.example.data.preferences.ThemeMode
import com.example.ui.screens.auth.PinLockScreen
import com.example.ui.screens.budgets.BudgetsScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.reports.ReportsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.transaction_entry.AddEditTransactionSheet
import com.example.ui.screens.transactions.TransactionsScreen
import com.example.ui.theme.MoneyMateTheme
import com.example.viewmodel.FinanceViewModel

@Composable
fun MainApp(viewModel: FinanceViewModel) {
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()

    val isDark = when (userSettings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MoneyMateTheme(darkTheme = isDark) {
        if (isAppLocked && userSettings.isPinLockEnabled && userSettings.pinCode.isNotBlank()) {
            PinLockScreen(
                userSettings = userSettings,
                onSuccess = { viewModel.unlockApp() }
            )
        } else {
            MainNavigationScaffold(viewModel = viewModel)
        }
    }
}

@Composable
fun MainNavigationScaffold(viewModel: FinanceViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()

    // Transaction entry sheet state
    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var initialSheetType by remember { mutableStateOf("EXPENSE") }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                BottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_${screen.route}")
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTransaction = null
                    initialSheetType = "EXPENSE"
                    showAddTransactionSheet = true
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.testTag("main_add_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Quick Add Transaction"
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToTransactions = {
                        navController.navigate(Screen.Transactions.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onQuickAddTransaction = { type ->
                        editingTransaction = null
                        initialSheetType = type
                        showAddTransactionSheet = true
                    },
                    onSelectTransaction = { tx ->
                        editingTransaction = tx
                        showAddTransactionSheet = true
                    }
                )
            }

            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    viewModel = viewModel,
                    onSelectTransaction = { tx ->
                        editingTransaction = tx
                        showAddTransactionSheet = true
                    }
                )
            }

            composable(Screen.Budgets.route) {
                BudgetsScreen(viewModel = viewModel)
            }

            composable(Screen.Reports.route) {
                ReportsScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }

        // Add / Edit Transaction Bottom Sheet
        if (showAddTransactionSheet) {
            AddEditTransactionSheet(
                viewModel = viewModel,
                existingTransaction = editingTransaction,
                initialType = initialSheetType,
                categories = allCategories,
                accounts = allAccounts,
                currencySymbol = userSettings.currencySymbol,
                onDismiss = {
                    showAddTransactionSheet = false
                    editingTransaction = null
                }
            )
        }
    }
}
