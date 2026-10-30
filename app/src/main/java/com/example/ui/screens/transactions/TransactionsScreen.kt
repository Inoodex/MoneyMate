package com.example.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.TransactionEntity
import com.example.ui.components.FormatUtils
import com.example.ui.components.TransactionRowItem
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.viewmodel.DateRangeFilter
import com.example.viewmodel.FinanceViewModel
import com.example.viewmodel.SortOption
import com.example.viewmodel.TransactionFilterType

@Composable
fun TransactionsScreen(
    viewModel: FinanceViewModel,
    onSelectTransaction: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.filterType.collectAsStateWithLifecycle()
    val filterDateRange by viewModel.filterDateRange.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Title and Sort Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Transactions",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.testTag("sort_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort Options",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Date (Newest First)") },
                        onClick = {
                            viewModel.sortOption.value = SortOption.DATE_DESC
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Date (Oldest First)") },
                        onClick = {
                            viewModel.sortOption.value = SortOption.DATE_ASC
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Amount (High to Low)") },
                        onClick = {
                            viewModel.sortOption.value = SortOption.AMOUNT_DESC
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Amount (Low to High)") },
                        onClick = {
                            viewModel.sortOption.value = SortOption.AMOUNT_ASC
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.searchQuery.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_transactions_input"),
            placeholder = { Text("Search by note, category, or account...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Type Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                TransactionFilterType.ALL to "All Types",
                TransactionFilterType.EXPENSE to "Expenses",
                TransactionFilterType.INCOME to "Income",
                TransactionFilterType.TRANSFER to "Transfers"
            ).forEach { (type, label) ->
                val isSelected = filterType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.filterType.value = type },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (type) {
                            TransactionFilterType.INCOME -> IncomeGreen.copy(alpha = 0.2f)
                            TransactionFilterType.EXPENSE -> ExpenseRed.copy(alpha = 0.2f)
                            TransactionFilterType.TRANSFER -> TransferBlue.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Date Range Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                DateRangeFilter.ALL to "All Time",
                DateRangeFilter.TODAY to "Today",
                DateRangeFilter.THIS_WEEK to "This Week",
                DateRangeFilter.THIS_MONTH to "This Month",
                DateRangeFilter.THIS_YEAR to "This Year"
            ).forEach { (range, label) ->
                val isSelected = filterDateRange == range
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.filterDateRange.value = range },
                    label = { Text(label) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Results Summary Info
        val totalAmount = transactions.filter { it.type != "TRANSFER" }.sumOf {
            if (it.type == "INCOME") it.amount else -it.amount
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${transactions.size} transactions found",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Net: ${FormatUtils.formatMoney(totalAmount, userSettings.currencySymbol)}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (totalAmount >= 0) IncomeGreen else ExpenseRed
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Transaction List
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No matching transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Try clearing filters or search terms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions) { tx ->
                    TransactionRowItem(
                        transaction = tx,
                        currencySymbol = userSettings.currencySymbol,
                        onClick = { onSelectTransaction(tx) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        }
    }
}
